package app.anikku.macos.platform.auth

import app.anikku.macos.platform.web.BrowserLauncher
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Manages OAuth token exchange and refresh for tracker services.
 *
 * ## Supported Trackers
 *
 * - **MyAnimeList** — OAuth 2.0 authorization code flow
 * - **AniList** — OAuth 2.0 authorization code flow
 * - **Kitsu** — OAuth 2.0 authorization code flow
 * - **MangaUpdates** — API key based
 * - **Shikimori** — OAuth 2.0 authorization code flow
 *
 * ## Usage
 *
 * ```kotlin
 * val manager = TrackerOAuthManager(httpClient)
 * val token = manager.exchangeAuthorizationCode(
 *     tracker = "myanimelist",
 *     code = "abc123",
 *     clientId = "...",
 *     clientSecret = "...",
 *     redirectUri = "http://127.0.0.1:8080/callback",
 * )
 * ```
 */
class TrackerOAuthManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** For testing: override token URLs. Key = tracker name, Value = token URL. */
    private val tokenUrlOverrides: Map<String, String> = emptyMap(),
) {

    internal val oauthConfigs = mapOf(
        "myanimelist" to TrackerOAuthConfig(
            authorizeUrl = "https://myanimelist.net/v1/oauth2/authorize",
            tokenUrl = "https://myanimelist.net/v1/oauth2/token",
            scope = "write:animelist",
        ),
        "anilist" to TrackerOAuthConfig(
            authorizeUrl = "https://anilist.co/api/v2/oauth/authorize",
            tokenUrl = "https://anilist.co/api/v2/oauth/token",
            // AniList rejects dynamic loopback ports with "invalid_client";
            // the registered redirect must match exactly.
            redirectUri = "http://localhost:8080/callback",
        ),
        "kitsu" to TrackerOAuthConfig(
            authorizeUrl = "https://kitsu.io/api/oauth/authorize",
            tokenUrl = "https://kitsu.io/api/oauth/token",
        ),
        "shikimori" to TrackerOAuthConfig(
            authorizeUrl = "https://shikimori.one/oauth/authorize",
            tokenUrl = "https://shikimori.one/oauth/token",
            scope = "user_rates",
        ),
    )

    /**
     * Exchange an authorization code for access and refresh tokens.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param code The authorization code from the OAuth callback.
     * @param clientId OAuth client ID.
     * @param clientSecret OAuth client secret.
     * @param redirectUri The redirect URI used in the authorization request.
     * @param codeVerifier Optional PKCE code verifier.
     * @return Token response with access/refresh tokens, or null on failure.
     */
    fun exchangeAuthorizationCode(
        tracker: String,
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String? = null,
    ): TokenResponse? {
        val config = oauthConfigs[tracker] ?: run {
            logger.warn { "Unknown tracker: $tracker" }
            return null
        }

        val tokenUrl = tokenUrlOverrides[tracker] ?: config.tokenUrl

        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .apply {
                if (codeVerifier != null) add("code_verifier", codeVerifier)
            }
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                logger.warn { "Token exchange failed for $tracker: ${response.code} $bodyString" }
                return null
            }

            val jsonObj = json.parseToJsonElement(bodyString).jsonObject
            TokenResponse(
                accessToken = jsonObj["access_token"]?.jsonPrimitive?.content ?: return null,
                refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: "",
                tokenType = jsonObj["token_type"]?.jsonPrimitive?.content ?: "Bearer",
                expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
                scope = jsonObj["scope"]?.jsonPrimitive?.content ?: "",
                createdAt = System.currentTimeMillis() / 1000,
            )
        } catch (e: Exception) {
            logger.error(e) { "Token exchange failed for $tracker" }
            null
        }
    }

    /**
     * Refresh an expired access token using the refresh token.
     *
     * @param tracker The tracker name.
     * @param refreshToken The refresh token.
     * @param clientId OAuth client ID.
     * @param clientSecret OAuth client secret.
     * @return New token response, or null on failure.
     */
    fun refreshToken(
        tracker: String,
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): TokenResponse? {
        val config = oauthConfigs[tracker] ?: return null

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(body)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return null

            if (!response.isSuccessful) {
                logger.warn { "Token refresh failed for $tracker: ${response.code}" }
                return null
            }

            val jsonObj = json.parseToJsonElement(bodyString).jsonObject
            TokenResponse(
                accessToken = jsonObj["access_token"]?.jsonPrimitive?.content ?: return null,
                refreshToken = jsonObj["refresh_token"]?.jsonPrimitive?.content ?: refreshToken,
                tokenType = jsonObj["token_type"]?.jsonPrimitive?.content ?: "Bearer",
                expiresIn = jsonObj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600,
                scope = jsonObj["scope"]?.jsonPrimitive?.content ?: "",
                createdAt = System.currentTimeMillis() / 1000,
            )
        } catch (e: Exception) {
            logger.error(e) { "Token refresh failed for $tracker" }
            null
        }
    }

    /**
     * Revoke a token for a tracker.
     *
     * @param tracker The tracker name.
     * @param accessToken The access token to revoke.
     */
    fun revokeToken(tracker: String, accessToken: String): Boolean {
        val config = oauthConfigs[tracker] ?: return false

        return try {
            val body = FormBody.Builder()
                .add("token", accessToken)
                .build()

            val request = Request.Builder()
                .url(config.tokenUrl.replace("/token", "/revoke"))
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            logger.warn(e) { "Token revocation failed for $tracker" }
            false
        }
    }

    /**
     * Check if a tracker supports OAuth-based login.
     */
    fun supportsOAuth(tracker: String): Boolean = tracker in oauthConfigs

    /**
     * Validate an existing access token by making a simple API call.
     *
     * @param tracker The tracker name.
     * @param accessToken The access token to validate.
     * @return true if the token is valid.
     */
    fun validateToken(tracker: String, accessToken: String): Boolean {
        val testUrl = when (tracker) {
            "myanimelist" -> "https://api.myanimelist.net/v2/users/@me"
            "anilist" -> "https://graphql.anilist.co"
            "kitsu" -> "https://kitsu.io/api/edge/users?filter[self]=true"
            "shikimori" -> "https://shikimori.one/api/users/whoami"
            else -> return false
        }

        return try {
            val request = Request.Builder()
                .url(testUrl)
                .header("Authorization", "Bearer $accessToken")
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initiate the OAuth authorization code flow.
     *
     * Starts a local HTTP server, opens the system browser to the tracker's
     * authorization URL via [BrowserLauncher], and waits for the callback.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param clientId OAuth client ID.
     * @param callbackPath The callback path to listen on.
     * @param timeout Maximum time to wait for the callback.
     * @param unit Time unit for the timeout.
     * @return The authorization code, or null if the flow failed or timed out.
     */
    fun initiateLogin(
        tracker: String,
        clientId: String,
        callbackPath: String = "/callback",
        timeout: Long = 300,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): String? {
        val config = oauthConfigs[tracker] ?: run {
            logger.warn { "Unknown tracker: $tracker" }
            return null
        }

        val oauthServer = OAuthServer()
        val authUrl = oauthServer.buildAuthorizationUrl(
            authEndpoint = config.authorizeUrl,
            clientId = clientId,
            useCallbackPath = callbackPath,
            scope = config.scope.ifEmpty { null },
        )

        logger.info { "Initiating OAuth login for $tracker on ${authUrl.take(80)}..." }

        // BrowserLauncher opens the system browser (Safari/Chrome) with proper EDT dispatch
        BrowserLauncher.openSafe(authUrl)

        logger.info { "Waiting for OAuth callback on $callbackPath..." }
        val params = oauthServer.awaitCallback(timeout, unit)
        oauthServer.stop()

        val code = params?.get("code")
        if (code != null) {
            logger.info { "OAuth authorization code received for $tracker" }
        } else {
            logger.warn { "OAuth callback for $tracker received but no authorization code found" }
        }

        return code
    }

    /**
     * Complete the full OAuth login flow:
     * 1. Creates an [OAuthServer] to handle the callback
     * 2. Builds the authorization URL with the tracker's config
     * 3. Opens the system browser via [BrowserLauncher]
     * 4. Waits for the OAuth callback
     * 5. Exchanges the authorization code for access/refresh tokens
     *
     * The same [OAuthServer] instance and redirect URI are used for both
     * the authorization request and the token exchange, ensuring consistency
     * that OAuth providers require.
     *
     * @param tracker The tracker name (e.g., "myanimelist", "anilist").
     * @param clientId OAuth client ID.
     * @param clientSecret OAuth client secret.
     * @param codeVerifier Optional PKCE code verifier.
     * @param callbackPath The callback path to listen on.
     * @param timeout Maximum time to wait for user to complete OAuth in browser.
     * @param unit Time unit for the timeout.
     * @return Token response with access/refresh tokens, or null on failure.
     */
    fun completeLogin(
        tracker: String,
        clientId: String,
        clientSecret: String,
        codeVerifier: String? = null,
        callbackPath: String = "/callback",
        timeout: Long = 300,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): TokenResponse? {
        val config = oauthConfigs[tracker] ?: run {
            logger.warn { "Unknown tracker: $tracker" }
            return null
        }

        // Start server once — use the same redirect URI for auth + token exchange.
        // IMPORTANT: NanoHTTPD binds the port from its CONSTRUCTOR (the start()
        // argument is not used for the socket), so the fixed redirect requires
        // constructing the server with that port.
        val fixedRedirect = config.redirectUri
        val fixedPort = fixedRedirect?.let {
            runCatching { java.net.URI(it).port }.getOrDefault(8080).let { p -> if (p > 0) p else 8080 }
        }
        val oauthServer = if (fixedPort != null) OAuthServer(port = fixedPort) else OAuthServer()
        val redirectUri: String = if (fixedRedirect != null) {
            val fixedPath = runCatching { java.net.URI(fixedRedirect).path }.getOrDefault(callbackPath)
            oauthServer.start(
                port = fixedPort ?: 8080,
                callbackPath = fixedPath,
                redirectUriOverride = fixedRedirect,
            )
        } else {
            oauthServer.start(port = 0, callbackPath = callbackPath)
        }

        val authUrl = oauthServer.buildAuthorizationUrl(
            authEndpoint = config.authorizeUrl,
            clientId = clientId,
            useCallbackPath = callbackPath,
            scope = config.scope.ifEmpty { null },
        )

        logger.info { "Initiating OAuth login for $tracker via BrowserLauncher..." }
        BrowserLauncher.openSafe(authUrl)

        logger.info { "Waiting for OAuth callback on $redirectUri..." }
        val params = oauthServer.awaitCallback(timeout, unit)
        oauthServer.stop()

        val code = params?.get("code") ?: return null

        return exchangeAuthorizationCode(
            tracker = tracker,
            code = code,
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            codeVerifier = codeVerifier,
        )
    }

    /**
     * OAuth configuration for a tracker.
     */
    data class TrackerOAuthConfig(
        val authorizeUrl: String,
        val tokenUrl: String,
        val scope: String = "",
        /** Exact loopback redirect required by providers that reject dynamic ports (AniList). */
        val redirectUri: String? = null,
    )
}

/**
 * Response from an OAuth token exchange.
 */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val scope: String = "",
    val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    /** Whether the token is expired. */
    val isExpired: Boolean
        get() = (System.currentTimeMillis() / 1000) >= (createdAt + expiresIn - 60)
}
