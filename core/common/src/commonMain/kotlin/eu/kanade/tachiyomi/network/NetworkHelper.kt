package eu.kanade.tachiyomi.network

import okhttp3.OkHttpClient

/**
 * Platform-specific HTTP client helper.
 * Android: uses Context, WebView-based Cloudflare bypass, system CookieJar.
 * JVM: uses a simplified OkHttpClient setup.
 */
expect open class NetworkHelper {
    val client: OkHttpClient

    fun clientWithTimeOut(
        connectTimeout: Long = 30,
        readTimeout: Long = 30,
        callTimeout: Long = 120,
    ): OkHttpClient

    fun defaultUserAgentProvider(): String
}
