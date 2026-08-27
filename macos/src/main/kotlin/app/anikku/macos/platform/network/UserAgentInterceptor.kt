package app.anikku.macos.platform.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds a custom User-Agent header to all requests.
 */
class UserAgentInterceptor(
    private val userAgentProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgentProvider())
            .build()
        return chain.proceed(request)
    }
}
