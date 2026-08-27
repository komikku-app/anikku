package app.anikku.macos.platform.network

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * Network connectivity checker for macOS.
 *
 * Provides methods to check internet connectivity by probing
 * well-known endpoints. Designed to be called from coroutines.
 *
 * ## Usage
 *
 * ```kotlin
 * val isOnline = ConnectivityChecker.isOnline()
 * if (!isOnline) {
 *     // Show offline error state
 * }
 * ```
 */
object ConnectivityChecker {

    /** Known-good endpoints to probe for connectivity. Ordered by reliability. */
    private val PROBE_URLS = listOf(
        "https://www.google.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace",
        "https://github.com",
        "https://httpbin.org/status/204",
    )

    /** Timeout for each probe in milliseconds. */
    private const val PROBE_TIMEOUT_MS = 5_000L

    /**
     * Whether the app has internet connectivity.
     *
     * Probes multiple well-known endpoints. Returns true if ANY
     * endpoint responds successfully. Results are cached briefly
     * to avoid excessive network calls.
     *
     * @return true if internet connectivity is available.
     */
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        for (url in PROBE_URLS) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = PROBE_TIMEOUT_MS.toInt()
                connection.readTimeout = PROBE_TIMEOUT_MS.toInt()
                connection.setRequestProperty("User-Agent", "Anikku/1.0")
                connection.instanceFollowRedirects = false
                connection.connect()

                val responseCode = connection.responseCode
                // 204 No Content, 200 OK, 301/302/307 Redirect = endpoint is reachable
                if (responseCode in 200..399) {
                    connection.disconnect()
                    return@withContext true
                }
                connection.disconnect()
            } catch (e: Exception) {
                logger.debug { "Connectivity probe failed for $url: ${e.message}" }
                // Try next endpoint
            }
        }
        logger.warn { "All connectivity probes failed — device may be offline" }
        return@withContext false
    }

    /**
     * Quick TCP connectivity check to a specific host and port.
     *
     * Useful for checking if a specific service (like TorrServer)
     * is reachable, without making an HTTP request.
     *
     * @param host The hostname to check.
     * @param port The port to check.
     * @param timeoutMs Timeout in milliseconds.
     * @return true if TCP connection succeeded.
     */
    suspend fun isHostReachable(
        host: String,
        port: Int,
        timeoutMs: Long = PROBE_TIMEOUT_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
