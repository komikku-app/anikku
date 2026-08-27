package app.anikku.macos.platform.auth

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class TrackerOAuthManagerTest {

    private var lastInterceptor: LastCallInterceptor? = null
    private lateinit var manager: TrackerOAuthManager

    /**
     * OkHttp interceptor that captures the last request and returns a custom response.
     */
    class LastCallInterceptor : Interceptor {
        var statusCode: Int = 200
        var responseBody: String = "{}"

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    @BeforeEach
    fun setUp() {
        lastInterceptor = LastCallInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(lastInterceptor!!)
            .build()
        manager = TrackerOAuthManager(
            client = client,
            tokenUrlOverrides = mapOf(
                "myanimelist" to "http://mock/myanimelist/token",
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        lastInterceptor = null
    }

    @Test
    fun `supports all known trackers`() {
        assertTrue(manager.supportsOAuth("myanimelist"))
        assertTrue(manager.supportsOAuth("anilist"))
        assertTrue(manager.supportsOAuth("kitsu"))
        assertTrue(manager.supportsOAuth("shikimori"))
    }

    @Test
    fun `does not support unknown tracker`() {
        assertFalse(manager.supportsOAuth("unknown_tracker"))
    }

    @Test
    fun `exchange authorization code returns token on success`() {
        // Configure interceptor to return a successful token response
        lastInterceptor!!.statusCode = 200
        lastInterceptor!!.responseBody = """{
            "access_token": "test_access_token",
            "refresh_token": "test_refresh_token",
            "token_type": "Bearer",
            "expires_in": 3600,
            "scope": "read write"
        }"""

        val token = manager.exchangeAuthorizationCode(
            tracker = "myanimelist",
            code = "auth_code_123",
            clientId = "client_123",
            clientSecret = "secret_456",
            redirectUri = "http://127.0.0.1:8080/callback",
        )

        assertNotNull(token, "Token should not be null for successful exchange")
        assertEquals("test_access_token", token?.accessToken)
        assertEquals("test_refresh_token", token?.refreshToken)
        assertEquals("Bearer", token?.tokenType)
    }

    @Test
    fun `exchange returns null on failed response`() {
        lastInterceptor!!.statusCode = 400
        lastInterceptor!!.responseBody = """{"error": "invalid_grant"}"""

        val token = manager.exchangeAuthorizationCode(
            tracker = "myanimelist",
            code = "bad_code",
            clientId = "client_123",
            clientSecret = "secret_456",
            redirectUri = "http://127.0.0.1:8080/callback",
        )

        assertNull(token)
    }

    @Test
    fun `initiateLogin returns null for unknown tracker`() {
        val result = manager.initiateLogin(
            tracker = "unknown",
            clientId = "client_123",
        )
        assertNull(result, "Should return null for unknown tracker")
    }

    @Test
    fun `initiateLogin times out and returns null when no callback received`() {
        // In headless CI, BrowserLauncher.openSafe is a no-op,
        // so the callback never arrives and the method times out.
        val result = manager.initiateLogin(
            tracker = "myanimelist",
            clientId = "client_123",
            timeout = 1,
            unit = TimeUnit.SECONDS,
        )
        assertNull(result, "Should return null after timeout since no browser callback")
    }

    @Test
    fun `completeLogin returns null for unknown tracker`() {
        val result = manager.completeLogin(
            tracker = "unknown",
            clientId = "client_123",
            clientSecret = "secret_456",
        )
        assertNull(result, "Should return null for unknown tracker")
    }

    @Test
    fun `anilist requires the exact registered redirect`() {
        // AniList rejects dynamic loopback ports with "invalid_client" — the
        // configured redirect must match the developer's registration exactly.
        assertEquals(
            "http://localhost:8080/callback",
            manager.oauthConfigs.getValue("anilist").redirectUri,
        )
    }

    @Test
    fun `token response correctly reports expiration`() {
        val oldToken = TokenResponse(
            accessToken = "test",
            refreshToken = "test",
            createdAt = (System.currentTimeMillis() / 1000) - 4000,
            expiresIn = 3600,
        )
        assertTrue(oldToken.isExpired, "Token created 4000s ago with 3600s expiry should be expired")

        val freshToken = TokenResponse(
            accessToken = "test",
            refreshToken = "test",
            createdAt = System.currentTimeMillis() / 1000,
            expiresIn = 3600,
        )
        assertFalse(freshToken.isExpired, "Freshly created token should not be expired")
    }
}
