package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private val logger = KotlinLogging.logger {}

/**
 * Diagnostic HTTP logging interceptor for debugging extension API errors.
 *
 * Logs full request/response details when errors occur, so users can
 * see exactly what each extension sends and receives. This is essential
 * for diagnosing HTTP 400/403/404/405 errors, JSON parse failures, and
 * Cloudflare blocks.
 *
 * ## What gets logged
 *
 * **Always (for non-2xx responses):**
 * - Request URL, method, headers
 * - Response code, headers
 * - Response body (first 2KB)
 *
 * **In debug mode (for all responses):**
 * - Request URL + response code (summary only)
 *
 * ## Usage
 *
 * Add to OkHttpClient as an application interceptor:
 * ```kotlin
 * builder.addInterceptor(DiagnosticLoggingInterceptor(isDebugBuild))
 * ```
 *
 * This is an APPLICATION interceptor (not network), so it sees the
 * final request after all other interceptors have modified it.
 */
class DiagnosticLoggingInterceptor(
    private val isDebugBuild: Boolean = false,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            logger.warn { "❌ HTTP request failed: ${request.method} ${request.url}" }
            logger.warn { "   Error: ${e.message}" }
            // Log request headers for diagnosis
            request.headers.forEach { (name, value) ->
                if (isSensitiveHeader(name)) {
                    logger.debug { "   Request: $name: <redacted>" }
                } else {
                    logger.debug { "   Request: $name: $value" }
                }
            }
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        val code = response.code

        if (isDebugBuild) {
            // Debug mode: log all responses briefly
            logger.info { "${request.method} ${request.url} → $code (${duration}ms)" }
        }

        if (!response.isSuccessful) {
            logErrorResponse(request, response, duration)
        }

        return response
    }

    private fun logErrorResponse(
        request: okhttp3.Request,
        response: Response,
        duration: Long,
    ) {
        val code = response.code
        val url = request.url.toString()

        logger.warn { "━━━ Extension API Error ━━━" }
        logger.warn { "→ ${request.method} $url" }
        logger.warn { "← HTTP $code (${duration}ms)" }

        // Log request headers (skip sensitive ones)
        request.headers.forEach { (name, value) ->
            if (isSensitiveHeader(name)) {
                logger.warn { "  Request: $name: <redacted>" }
            } else {
                logger.warn { "  Request: $name: $value" }
            }
        }

        // Log response headers
        response.headers.forEach { (name, value) ->
            logger.warn { "  Response: $name: $value" }
        }

        // Log response body (first 2KB)
        try {
            val bodyString = response.peekBody(2048).string()
            if (bodyString.isNotBlank()) {
                val preview = if (bodyString.length > 2048) {
                    bodyString.take(2000) + "... [truncated]"
                } else {
                    bodyString
                }
                logger.warn { "  Body: $preview" }

                // Detect common error patterns
                when {
                    bodyString.contains("cf-browser-verify") ||
                        bodyString.contains("cf_chl_opt") ||
                        bodyString.contains("Just a moment") ||
                        bodyString.contains("Checking your browser") ->
                        logger.warn { "  ⚠ Cloudflare challenge detected in response body" }
                    bodyString.contains("<html", ignoreCase = true) && code in 400..499 ->
                        logger.warn { "  ⚠ HTML error page returned instead of API response" }
                    bodyString.startsWith("{") || bodyString.startsWith("[") ->
                        logger.warn { "  ℹ JSON response (check for error messages in the JSON)" }
                }
            } else {
                logger.warn { "  Body: <empty>" }
            }
        } catch (e: Exception) {
            logger.warn { "  Body: <unreadable: ${e.message}>" }
        }

        logger.warn { "━━━━━━━━━━━━━━━━━━━━━━━━" }
    }

    private fun isSensitiveHeader(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("authorization") ||
            lower.contains("cookie") ||
            lower.contains("x-api-key") ||
            lower.contains("api-key")
    }
}
