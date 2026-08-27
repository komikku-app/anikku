package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * DNS resolver that tries the system DNS first, then falls back to
 * DNS-over-HTTPS (Cloudflare) if the system DNS fails.
 *
 * This is the single most impactful fix for extension reliability —
 * many anime streaming sites have unstable DNS or use domain fronting
 * that works with DoH but not system resolvers.
 *
 * ## Usage
 *
 * ```kotlin
 * OkHttpClient.Builder()
 *     .dns(FallbackDns)
 *     .build()
 * ```
 */
object FallbackDns : Dns {

    /** System DNS — OkHttp's default. */
    private val systemDns: Dns = Dns.SYSTEM

    /** DoH-over-HTTPS client — lightweight, no interceptors. */
    private val dohClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Cloudflare DoH provider URL. */
    private val dohUrl = HttpUrl.Builder()
        .scheme("https")
        .host("cloudflare-dns.com")
        .addPathSegment("dns-query")
        .addQueryParameter("name", "{host}")
        .addQueryParameter("type", "A")
        .build()

    /**
     * Resolve hostnames:
     * 1. Try system DNS first
     * 2. On failure, try Cloudflare DoH
     * 3. If DoH also fails, throw the original UnknownHostException
     */
    override fun lookup(hostname: String): List<InetAddress> {
        // First try system DNS (fast path)
        try {
            val systemResult = systemDns.lookup(hostname)
            if (systemResult.isNotEmpty()) {
                return systemResult
            }
        } catch (_: UnknownHostException) {
            // System DNS failed — fall through to DoH
        } catch (_: Exception) {
            // Other errors (e.g., security) — still try DoH
        }

        // System DNS failed — try Cloudflare DoH
        logger.info { "System DNS failed for $hostname — trying Cloudflare DoH..." }
        val dohResult = resolveViaDoh(hostname)
        if (dohResult.isNotEmpty()) {
            val addresses = dohResult.joinToString(", ") { it.hostAddress ?: "?" }
            logger.info { "DoH resolved $hostname -> $addresses" }
            return dohResult
        }
        logger.warn { "DoH also failed for $hostname" }
        throw UnknownHostException("System DNS and DoH both failed for $hostname")
    }

    /**
     * Resolve a hostname via Cloudflare's DNS-over-HTTPS API.
     *
     * Uses the `?name=HOST&type=A` GET endpoint which returns a JSON response
     * with the resolved IP addresses. Parses the JSON manually to avoid
     * pulling in additional dependencies.
     */
    private fun resolveViaDoh(hostname: String): List<InetAddress> {
        val url = dohUrl.newBuilder()
            .setEncodedQueryParameter("name", hostname)
            .setQueryParameter("type", "A")
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "application/dns-json")
            .build()

        val response = dohClient.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()

        if (!response.isSuccessful) {
            return emptyList()
        }

        // Parse JSON manually: extract "Answer"[]["data"] fields that are IP v4 addresses
        val addresses = mutableListOf<InetAddress>()
        val answerPattern = Regex("\"data\"\\s*:\\s*\"(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\"")

        // Find the "Answer" array in the JSON
        val answerStart = body.indexOf("\"Answer\"")
        if (answerStart < 0) return emptyList()

        // Extract all IP addresses from the Answer array
        val answerJson = body.substring(answerStart)
        for (match in answerPattern.findAll(answerJson)) {
            val ipStr = match.groupValues[1]
            try {
                addresses.add(InetAddress.getByName(ipStr))
            } catch (_: Exception) {
                // Skip invalid IPs
            }
        }

        return addresses
    }
}
