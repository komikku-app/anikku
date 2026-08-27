@file:Suppress("unused")

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stub for Android-only [rateLimitHost] extension functions.
 *
 * These functions are defined in `core/common/src/androidMain/` and are NOT
 * compiled into the JVM JAR (common-jvm.jar), which only includes `commonMain`
 * and `jvmMain` source sets. Extensions that call these functions will fail
 * with unresolved reference during compilation without these stubs.
 *
 * The stubs provide no-op implementations so extensions can compile.
 * Rate limiting is silently skipped on macOS (acceptable for desktop usage).
 */

fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: java.util.concurrent.TimeUnit = java.util.concurrent.TimeUnit.SECONDS,
) = this

@Suppress("UNUSED")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
) = this

@Suppress("UNUSED")
fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
) = this
