package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * OkHttp interceptor that retries requests on transient HTTP errors with exponential backoff.
 *
 * Many anime streaming sites experience transient failures (502 Bad Gateway, 503 Service
 * Unavailable, 504 Gateway Timeout) due to CDN issues or server load. This interceptor
 * automatically retries such requests up to [maxRetries] times with exponential backoff.
 *
 * ## Which errors are retried
 * - **HTTP 429** (Too Many Requests) — rate limited, wait and retry
 * - **HTTP 502** (Bad Gateway) — upstream server issue, often transient
 * - **HTTP 503** (Service Unavailable) — server overloaded
 * - **HTTP 504** (Gateway Timeout) — upstream timeout
 * - **IOException** (connection errors) — network issues, DNS failures
 *
 * ## What is NOT retried
 * - HTTP 4xx errors (except 429) — these are client-side issues
 * - HTTP 5xx errors other than 502/503/504 — these are server bugs
 * - Requests with the `X-Retry-Attempted` header set (prevents retry loops)
 */
class HttpRetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000L,
) : Interceptor {

    companion object {
        private const val X_RETRY_ATTEMPTED = "X-Retry-Attempted"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Prevent infinite retry loops
        if (request.header(X_RETRY_ATTEMPTED) != null) {
            return chain.proceed(request)
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= maxRetries) {
            try {
                val response = if (attempt == 0) {
                    // First attempt — no special header
                    chain.proceed(request)
                } else {
                    // Retry — mark with the header
                    val retryRequest = request.newBuilder()
                        .header(X_RETRY_ATTEMPTED, attempt.toString())
                        .build()
                    chain.proceed(retryRequest)
                }

                if (response.isSuccessful || !isRetryableError(response.code)) {
                    // Successful response or non-retryable error — return as-is
                    return response
                }

                // Retryable error — close response body and wait
                val code = response.code
                response.close()

                if (attempt < maxRetries) {
                    val delayMs = calculateDelay(attempt, code)
                    logger.warn { "🔁 Retry $attempt/$maxRetries for ${request.url} (HTTP $code) — waiting ${delayMs}ms" }
                    Thread.sleep(delayMs)
                    attempt++
                } else {
                    logger.warn { "❌ Max retries ($maxRetries) reached for ${request.url} (HTTP $code)" }
                    // Return the last response we got (before closing)
                    return chain.proceed(request.newBuilder()
                        .header(X_RETRY_ATTEMPTED, "maxed")
                        .build())
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayMs = calculateDelay(attempt, 0)
                    logger.warn { "🔁 Retry $attempt/$maxRetries for ${request.url} — IOException: ${e.message}" }
                    Thread.sleep(delayMs)
                    attempt++
                } else {
                    logger.error(e) { "❌ Max retries ($maxRetries) reached with IO error for ${request.url}" }
                    throw e
                }
            }
        }

        throw lastException ?: IOException("Max retries ($maxRetries) exceeded for ${request.url}")
    }

    private fun isRetryableError(code: Int): Boolean {
        // 429: Too Many Requests — rate limited, wait and retry
        // 502: Bad Gateway — upstream server issue, often transient
        // 503: Service Unavailable — server overloaded
        // 504: Gateway Timeout — upstream timeout
        // 520: Cloudflare-origin error — web server returns empty/unknown response
        return code == 429 || code == 502 || code == 503 || code == 504 || code == 520
    }

    /**
     * Calculate delay with exponential backoff + jitter.
     * For 429 (rate limit), add extra jitter to avoid thundering herd.
     */
    private fun calculateDelay(attempt: Int, code: Int): Long {
        val baseDelay = baseDelayMs * 2.0.pow(attempt).toLong()
        val jitter = Random.nextLong(0, baseDelay)
        val extraDelay = if (code == 429) Random.nextLong(500, 2000) else 0L
        return (baseDelay + jitter + extraDelay).coerceAtMost(30_000L)
    }
}
