package app.anikku.macos.platform.auth

import fi.iki.elonen.NanoHTTPD
import io.github.oshai.kotlinlogging.KotlinLogging
import app.anikku.macos.platform.web.BrowserLauncher
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Embedded HTTP server for handling OAuth callbacks.
 *
 * On Android, OAuth callbacks are received via intent filters (Custom Tabs).
 * On macOS desktop, there is no intent system. Instead, we register a localhost
 * redirect URI, open the system browser to the OAuth authorization URL, and
 * capture the callback on this server.
 *
 * ## Usage (Phase 7.1)
 *
 * ```kotlin
 * val oauth = OAuthServer()
 * val callbackUrl = oauth.start(8080, "/callback")
 * val fullUrl = "https://example.com/auth?client_id=...&redirect_uri=$callbackUrl"
 * BrowserLauncher.open(fullUrl)
 * val code = oauth.awaitCallback(60, TimeUnit.SECONDS)
 * oauth.stop()
 * ```
 */
class OAuthServer(
    private val host: String = "127.0.0.1",
    port: Int = 0,
) : NanoHTTPD(if (port > 0) port else 0) {

    private var callbackFuture: CompletableFuture<Map<String, String>>? = null
    private var callbackPath: String = "/callback"

    /** Effective redirect URI when a provider requires an exact fixed callback. */
    private var effectiveRedirectUri: String? = null

    /** Whether the server is currently running. */
    var isRunning: Boolean = false
        private set

    /**
     * Start the OAuth callback server.
     *
     * @param port The port to listen on (0 = auto-assign).
     * @param callbackPath The path for the callback (e.g., "/callback").
     * @param redirectUriOverride When set (providers like AniList that reject
     *   dynamic loopback ports), the server binds [port] on [host] but reports
     *   this exact URL as the redirect — the browser reaches it via localhost.
     * @return The full redirect URI the OAuth provider should redirect to.
     */
    fun start(port: Int = 0, callbackPath: String = "/callback", redirectUriOverride: String? = null): String {
        if (isRunning) stop()

        this.callbackPath = callbackPath
        callbackFuture = CompletableFuture()

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        isRunning = true

        val redirectUri = redirectUriOverride ?: "http://$host:$listeningPort$callbackPath"
        effectiveRedirectUri = redirectUriOverride
        logger.info { "OAuth server started on $redirectUri" }
        return redirectUri
    }

    /**
     * Wait for the OAuth callback to be received.
     *
     * @param timeout Maximum time to wait.
     * @param unit Time unit for the timeout.
     * @return Map of query parameters received in the callback, or null on timeout.
     */
    fun awaitCallback(timeout: Long = 60, unit: TimeUnit = TimeUnit.SECONDS): Map<String, String>? {
        return try {
            callbackFuture?.get(timeout, unit)
        } catch (e: Exception) {
            logger.warn(e) { "OAuth callback timed out after $timeout ${unit.name.lowercase()}" }
            null
        }
    }

    /**
     * Wait for the OAuth callback and extract a specific parameter.
     *
     * @param param The query parameter name to extract.
     * @param timeout Maximum time to wait.
     * @param unit Time unit for the timeout.
     * @return The parameter value, or null if not present or timeout.
     */
    fun awaitParameter(
        param: String,
        timeout: Long = 60,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): String? {
        return awaitCallback(timeout, unit)?.get(param)
    }

    /**
     * Stop the server and clean up.
     */
    override fun stop() {
        callbackFuture?.completeExceptionally(IllegalStateException("OAuth server stopped"))
        callbackFuture = null
        super.stop()
        isRunning = false
        logger.info { "OAuth server stopped" }
    }

    /**
     * Build an OAuth authorization URL with the redirect_uri parameter set to this server.
     */
    fun buildAuthorizationUrl(
        authEndpoint: String,
        clientId: String,
        useCallbackPath: String = "/callback",
        scope: String? = null,
        state: String? = null,
    ): String {
        val redirectUri = if (isRunning) {
            effectiveRedirectUri ?: "http://$host:$listeningPort$useCallbackPath"
        } else {
            start(port = 0, callbackPath = useCallbackPath)
        }

        val sb = StringBuilder(authEndpoint)
        sb.append(if (authEndpoint.contains('?')) "&" else "?")
        sb.append("response_type=code")
        sb.append("&client_id=").append(clientId)
        sb.append("&redirect_uri=").append(URI(redirectUri).toASCIIString())
        if (scope != null) sb.append("&scope=").append(scope)
        if (state != null) sb.append("&state=").append(state)

        return sb.toString()
    }

    /**
     * Open the system browser to an OAuth authorization URL.
     * Delegates to [BrowserLauncher.open] for consistent browser launching
     * with proper EDT dispatch.
     */
    fun openBrowser(url: String) {
        BrowserLauncher.openSafe(url)
    }

    /**
     * Perform the full OAuth authorization code flow:
     * 1. Start local server
     * 2. Open browser to auth URL
     * 3. Wait for callback
     * 4. Return the authorization code
     *
     * @param authUrl The full authorization URL (without redirect_uri).
     * @param callbackPath The callback path to listen on.
     * @param timeout Maximum time to wait.
     * @param unit Time unit for the timeout.
     * @return The authorization code, or null on failure.
     */
    fun performOAuthFlow(
        authUrl: String,
        callbackPath: String = "/callback",
        timeout: Long = 120,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): String? {
        val redirectUri = start(port = 0, callbackPath = callbackPath)
        val fullUrl = "$authUrl&redirect_uri=${URI(redirectUri).toASCIIString()}"

        logger.info { "Opening browser for OAuth: ${authUrl.take(80)}..." }
        openBrowser(fullUrl)

        logger.info { "Waiting for OAuth callback on $redirectUri..." }
        val params = awaitCallback(timeout, unit)
        stop()

        val code = params?.get("code")
        if (code != null) {
            logger.info { "OAuth authorization code received" }
        } else {
            logger.warn { "OAuth callback received but no authorization code found" }
        }

        return code
    }

    // -------------------------------------------------------------------------
    // NanoHTTPd request handler
    // -------------------------------------------------------------------------

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return if (uri == callbackPath) {
            handleCallback(session)
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not found — use the callback path: $callbackPath",
            )
        }
    }

    /**
     * Handle an OAuth callback request and complete the future.
     */
    private fun handleCallback(session: IHTTPSession): Response {
        val params = session.parms ?: emptyMap()

        logger.info { "OAuth callback received with params: ${params.keys}" }

        // NanoHTTPD writes the returned response after serve() completes. If
        // the waiting OAuth coroutine stops the server immediately, the socket
        // can close before the browser receives this completion page. Delay
        // future completion briefly so the response is handed back first.
        val future = callbackFuture
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute {
            future?.complete(params)
        }

        // Return a friendly "you can close this tab" HTML page
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html",
            """
            <!DOCTYPE html>
            <html>
            <head><title>Authorization Complete</title></head>
            <body style="display:flex;align-items:center;justify-content:center;height:100vh;
                         font-family:-apple-system,BlinkMacSystemFont,sans-serif;
                         background:#1a1a2e;color:white;">
                <div style="text-align:center;">
                    <h1>Authorization Complete</h1>
                    <p>You can close this tab and return to Anikku.</p>
                </div>
            </body>
            </html>
            """.trimIndent(),
        )
    }
}
