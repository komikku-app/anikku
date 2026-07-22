package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * Cloudflare bypass interceptor using Chrome DevTools Protocol.
 *
 * When a Cloudflare challenge is detected (HTTP 400/403/429/503 with Cloudflare
 * this interceptor:
 *
 * 1. Launches headless Chrome via [ChromeCDPClient]
 * 2. Navigates to the protected URL and waits for the JS challenge to resolve
 * 3. Extracts `cf_clearance` and `__cf_bm` cookies
 * 4. Injects cookies into [MacOSCookieJar]
 * 5. Retries the original request with the Cloudflare cookies attached
 *
 * Cookies are cached per-domain in [MacOSCookieJar], so subsequent requests
 * to the same domain don't need to re-solve the challenge (until cookies expire).
 *
 * If Chrome is not installed, falls back to pass-through (original behavior).
 * The `X-CF-Bypass-Attempted` header prevents infinite retry loops.
 */
class CloudflareInterceptor(
    private val cookieJar: MacOSCookieJar,
    private val userAgentProvider: () -> String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Check for infinite retry loop guard BEFORE proceeding.
        // Use the original request (before any headers were added) to check.
        if (request.header(X_CF_BYPASS) != null) {
            logger.debug { "Cloudflare bypass already attempted for ${request.url} — skipping bypass" }
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        // Not a Cloudflare block — pass through
        if (!isCloudflareBlock(response)) {
            return response
        }

        val url = request.url.toString()
        val host = request.url.host
        val httpCode = response.code
        logger.warn { "🛡 Cloudflare block detected: $url (HTTP $httpCode)" }

        // Record ONE diagnostic entry per CF block (start with detection, update inline)
        val diag = DiagnosticEntry(host = host, httpCode = httpCode, wasBlockDetected = true)
        diagnostics.add(diag)

        // Close the blocked response body
        response.close()

        // Check if Chrome is available
        if (!ChromeCDPClient.isChromeInstalled) {
            logger.warn { "Chrome not installed — Cloudflare bypass unavailable, marking as attempted" }
            return chain.proceed(request.newBuilder().header(X_CF_BYPASS, "1").build())
        }

        // Update diagnostic: bypass attempted
        diag.bypassAttempted = true

        // Attempt bypass via headless Chrome
        val userAgent = userAgentProvider()
        val cookies = ChromeCDPClient.fetchCloudflareCookies(url, userAgent)

        if (cookies.isEmpty()) {
            logger.warn { "❌ Cloudflare bypass failed for ${request.url.host}" }
            return chain.proceed(request.newBuilder().header(X_CF_BYPASS, "1").build())
        }

        // Inject cookies into the cookie jar for future requests
        injectCookies(request.url.host, cookies)

        // Retry the request with Cloudflare cookies + bypass-attempted header
        logger.info { "Retrying with Cloudflare cookies for ${request.url.host}" }
        val retryRequest = request.newBuilder()
            .header(X_CF_BYPASS, "1")
            .build()
        val retryResponse = chain.proceed(retryRequest)

        if (isCloudflareBlock(retryResponse)) {
            logger.warn { "Still blocked after bypass for ${request.url.host}" }
        } else {
            logger.info { "✅ Cloudflare bypass successful for ${request.url.host}" }
            diag.bypassSucceeded = true
        }

        return retryResponse
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun isCloudflareBlock(response: Response): Boolean {
        // Fast path: 403 or 503 with Cloudflare headers → definitely Cloudflare
        if (response.code in CLOUDFLARE_CODES) {
            val server = response.header("Server") ?: ""
            if (server in CLOUDFLARE_SERVERS) return true
            if (response.header("cf-ray") != null) return true
        }

        // Check for non-Cloudflare WAFs (DataDome, Akamai, Imperva) via response headers.
        // These WAFs use different blocking mechanisms but Chrome CDP can bypass them too.
        if (isNonCloudflareWaf(response)) return true

        // Slow path for HTTP 400/406/429: peek body for challenge patterns.
        // Some sites (e.g. AllAnime) return HTTP 400 for Cloudflare blocks instead
        // of the typical 403/503. Some return 406 (Not Acceptable) with CF challenge.
        if (response.code in CLOUDFLARE_EXTENDED_CODES) {
            return try {
                val bodyPeek = response.peekBody(BODY_PEEK_BYTES).string()
                bodyPeek.contains("cf-browser-verify") ||
                    bodyPeek.contains("cf_chl_opt") ||
                    bodyPeek.contains("_cf_chl_ctx") ||
                    bodyPeek.contains("Checking your browser") ||
                    bodyPeek.contains("cf-wrapper") ||
                    bodyPeek.contains("challenge-platform") ||
                    bodyPeek.contains("Just a moment") ||
                    bodyPeek.contains("Attention Required!") ||
                    bodyPeek.contains("cloudflareinsights.com") ||
                    bodyPeek.contains("__cf_chl") ||
                    bodyPeek.contains("turnstile") ||
                    bodyPeek.contains("challenges.cloudflare.com") ||
                    (bodyPeek.contains("jschl") && bodyPeek.contains("cf-")) ||
                    (response.code == 400 && bodyPeek.contains("<html", ignoreCase = true) &&
                        (bodyPeek.contains("cloudflare") || bodyPeek.contains("cf-"))) ||
                    bodyPeek.contains("datadome") || bodyPeek.contains("geo.captcha-delivery.com") ||
                    bodyPeek.contains("akamai") || bodyPeek.contains("imperva") ||
                    bodyPeek.contains("_abck") || bodyPeek.contains("bm_sz")
            } catch (_: Exception) { false }
        }

        // Aggressive bypass: ANY HTTP 403 or 503 (even without recognizable WAF markers)
        // triggers the Chrome CDP bypass. Many anime streaming sites use custom error
        // pages or generic 503 responses when blocking bots. We skip genuine server
        // errors (Apache/nginx default pages, JSON API errors, 500s) to avoid wasting
        // Chrome CDP on real server problems.
        if (response.code in CLOUDFLARE_CODES) {
            return try {
                val bodyPeek = response.peekBody(BODY_PEEK_BYTES).string()
                !isGenuineServerError(bodyPeek)
            } catch (_: Exception) {
                // Can't read body — assume it could be a block and try bypass
                true
            }
        }

        return false
    }

    /**
     * Check if the response body looks like a genuine server error page
     * (not a bot challenge). If true, we skip the Chrome CDP bypass to avoid
     * wasting time on sites that are genuinely broken.
     */
    private fun isGenuineServerError(body: String): Boolean {
        if (body.isEmpty()) return false
        // Apache/Nginx default error pages
        if (body.contains("Apache Server") || body.contains("nginx/") || body.contains("<address>")) return true
        // Generic 403/503 messages that are NOT bot challenges.
        // Keep threshold conservative (100 bytes) and check for script/challenge
        // indicators — Cloudflare pages often have minimal markup but always
        // include JavaScript or challenge-related keywords.
        val bareBody = body.length < 100
        if (bareBody && body.contains("403 Forbidden") &&
            !body.contains("script", ignoreCase = true) &&
            !body.contains("challenge", ignoreCase = true) &&
            !body.contains("window.", ignoreCase = true)) return true
        if (bareBody && body.contains("503 Service") &&
            !body.contains("script", ignoreCase = true) &&
            !body.contains("challenge", ignoreCase = true) &&
            !body.contains("window.", ignoreCase = true)) return true
        // JSON API errors — these are legitimate server responses to bad requests
        if (body.trimStart().startsWith("{") && (body.contains("\"error\"") || body.contains("\"message\""))) return true
        // IIS default pages
        if (body.contains("Server Error") && body.contains("IIS")) return true
        // Genuine maintenance page (not a bot-block masquerading as maintenance)
        if (body.contains("<title>Maintenance</title>") || body.contains("scheduled maintenance")) return true
        return false
    }

    /**
     * Detect non-Cloudflare WAFs via response headers.
     * These WAFs set distinctive headers that signal a bot challenge.
     * Chrome CDP can solve them the same way it solves Cloudflare.
     */
    private fun isNonCloudflareWaf(response: Response): Boolean {
        // DataDome: sets X-DataDome header with blocked/challenge status
        val dd = response.header("X-DataDome") ?: response.header("x-datadome")
        if (dd != null) return true

        // Akamai: sets X-Akamai-Edge or X-Akamai-Request-ID on challenges
        if (response.header("X-Akamai-Edge") != null && response.code in CLOUDFLARE_CODES) return true

        // Imperva/Incapsula: sets X-Iinfo or X-CDN headers on challenges
        val imperva = response.header("X-Iinfo") ?: response.header("x-iinfo")
        if (imperva != null && response.code in CLOUDFLARE_CODES) return true

        return false
    }

    private fun injectCookies(host: String, cookies: Map<String, String>) {
        val cleanHost = host.removePrefix(".")
        val domain = ".$cleanHost"
        for ((name, value) in cookies) {
            try {
                val cookie = okhttp3.Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .httpOnly()
                    .secure()
                    .build()
                val url = okhttp3.HttpUrl.Builder()
                    .scheme("https")
                    .host(cleanHost)
                    .build()
                cookieJar.saveFromResponse(url, listOf(cookie))
                logger.debug { "Injected cookie $name for $cleanHost" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to inject cookie $name for $cleanHost" }
            }
        }
    }

    /**
     * Per-request diagnostic entry for Cloudflare/CDP bypass tracking.
     *
     * This is NOT a data class — [bypassAttempted] and [bypassSucceeded]
     * are mutable so the interceptor can update bypass results inline
     * without creating duplicate entries per block event.
     *
     * Used by ExtensionCompatibilityTest to show which extensions are
     * Cloudflare-blocked and whether the CDP bypass succeeded.
     */
    class DiagnosticEntry(
        val host: String,
        val httpCode: Int,
        val wasBlockDetected: Boolean,
        var bypassAttempted: Boolean = false,
        var bypassSucceeded: Boolean = false,
        val timestamp: Instant = Instant.now(),
    ) {
        override fun toString(): String =
            "$host:$httpCode block=$wasBlockDetected bypass=$bypassSucceeded"

        override fun equals(other: Any?): Boolean =
            other is DiagnosticEntry && other.host == host && other.timestamp == timestamp

        override fun hashCode(): Int = 31 * host.hashCode() + timestamp.hashCode()
    }

    companion object {
        private const val X_CF_BYPASS = "X-CF-Bypass-Attempted"
        private const val BODY_PEEK_BYTES = 8192L
        private val CLOUDFLARE_CODES = setOf(403, 503)
        private val CLOUDFLARE_EXTENDED_CODES = setOf(400, 406, 429)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare-nginx", "cloudflare")

        private val diagnostics = ConcurrentLinkedQueue<DiagnosticEntry>()

        fun getDiagnostics(): List<DiagnosticEntry> = diagnostics.toList()
        fun clearDiagnostics() { diagnostics.clear() }

        /**
         * Summary of Cloudflare/CDP bypass activity for the current diagnostic window.
         * Deduplicates entries by host so multiple blocks to the same domain count as one.
         * Convenience for test reporting.
         */
        fun diagnosticSummary(): String {
            val entries = diagnostics.toList()
            if (entries.isEmpty()) return "—"
            // Deduplicate by host — one diagnostic entry per CF block event
            val uniqueByHost = entries.distinctBy { it.host }
            val succeeded = uniqueByHost.count { it.bypassSucceeded }
            val attempted = uniqueByHost.count { it.bypassAttempted }
            val total = uniqueByHost.size
            return when {
                total == 0 -> "No WAF"
                succeeded > 0 -> "CF:$total ✅$succeeded"
                attempted > 0 -> "CF:$total ❌${attempted - succeeded}"
                else -> "CF:$total ⚠"
            }
        }
    }
}
