package app.anikku.macos.platform.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

/**
 * An OkHttp interceptor that handles rate limiting per host.
 *
 * Ported from the Android core/common interceptor.
 * Replaces `android.os.SystemClock.elapsedRealtime()` with `System.nanoTime()`.
 *
 * Examples:
 *   permits = 5, period = 1.seconds  => 5 requests per second
 *   permits = 10, period = 2.minutes => 10 requests per 2 minutes
 *
 * @param permits Number of requests allowed within a period.
 * @param period  The limiting duration. Defaults to 1 second.
 */
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(null, permits, period))

/**
 * Legacy API using java.util.concurrent.TimeUnit.
 * Kept for compatibility with existing extension code.
 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimit(
    permits: Int,
    period: Long,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(RateLimitInterceptor(null, permits, period.toDuration(unit.toDurationUnit())))

/**
 * Per-host rate limiting interceptor.
 *
 * Uses a sliding window with a semaphore for thread safety.
 * Cached responses (networkResponse == null) are removed from the queue
 * so they don't count against the rate limit.
 */
internal class RateLimitInterceptor(
    private val host: String?,
    private val permits: Int,
    period: Duration,
) : Interceptor {

    private val requestQueue = ArrayDeque<Long>(permits)
    private val rateLimitNanos = period.inWholeNanoseconds
    private val fairLock = Semaphore(1, true)

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        if (call.isCanceled()) throw IOException("Canceled")

        val request = chain.request()
        when (host) {
            null, request.url.host -> {} // rate limit this host
            else -> return chain.proceed(request) // not rate limited
        }

        try {
            fairLock.acquire()
        } catch (e: InterruptedException) {
            throw IOException(e)
        }

        val queue = this.requestQueue
        val timestamp: Long

        try {
            synchronized(queue) {
                while (queue.size >= permits) {
                    // Remove expired entries from the sliding window
                    val windowStart = System.nanoTime() - rateLimitNanos
                    var hasRemovedExpired = false
                    while (queue.isNotEmpty() && queue.first <= windowStart) {
                        queue.removeFirst()
                        hasRemovedExpired = true
                    }

                    if (call.isCanceled()) {
                        throw IOException("Canceled")
                    } else if (hasRemovedExpired) {
                        break
                    } else {
                        // Wait for the oldest entry to expire
                        try {
                            (queue as Object).wait(
                                (queue.first - windowStart) / 1_000_000 // nanos to millis
                            )
                        } catch (_: InterruptedException) {
                            continue
                        }
                    }
                }

                // Add this request to the queue
                timestamp = System.nanoTime()
                queue.addLast(timestamp)
            }
        } finally {
            fairLock.release()
        }

        val response = chain.proceed(request)

        // If the response came from cache (no network call), remove it from the queue
        // so it doesn't count against the rate limit
        if (response.networkResponse == null) {
            synchronized(queue) {
                if (queue.isEmpty() || timestamp < queue.first) return@synchronized
                queue.removeFirstOccurrence(timestamp)
                (queue as Object).notifyAll()
            }
        }

        return response
    }
}
