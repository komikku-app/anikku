package app.anikku.macos.platform.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OAuthServerTest {

    private lateinit var server: OAuthServer

    @BeforeEach
    fun setUp() {
        server = OAuthServer()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `server starts and provides a redirect URI`() {
        val redirectUri = server.start(port = 0, callbackPath = "/callback")
        assertTrue(redirectUri.startsWith("http://127.0.0.1:"))
        assertTrue(redirectUri.endsWith("/callback"))
        assertTrue(server.isRunning)
    }

    @Test
    fun `redirect override reports the exact uri for strict providers`() {
        val redirectUri = server.start(
            port = 0,
            callbackPath = "/callback",
            redirectUriOverride = "http://localhost:8080/callback",
        )
        assertEquals("http://localhost:8080/callback", redirectUri)

        // The authorize URL must carry the same exact redirect.
        val authUrl = server.buildAuthorizationUrl(
            authEndpoint = "https://anilist.co/api/v2/oauth/authorize",
            clientId = "47764",
        )
        assertTrue(authUrl.contains("redirect_uri=http://localhost:8080/callback"))
    }

    @Test
    fun `server handles callback request with query parameters`() {
        val redirectUri = server.start(port = 0, callbackPath = "/callback")
        val port = redirectUri.substringAfter("http://127.0.0.1:").substringBefore("/").toInt()

        // Make a request to the callback endpoint
        val url = URL("http://127.0.0.1:$port/callback?code=test_auth_code&state=test_state")
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        val responseCode = connection.responseCode
        assertEquals(200, responseCode)

        // Read the response
        val reader = BufferedReader(InputStreamReader(connection.inputStream))
        val response = reader.readText()
        reader.close()

        assertTrue(response.contains("Authorization Complete"))
    }

    @Test
    fun `wait for callback receives parameters from background request`() {
        val redirectUri = server.start(port = 0, callbackPath = "/callback")
        val port = redirectUri.substringAfter("http://127.0.0.1:").substringBefore("/").toInt()

        // Make callback request on a background thread using executor
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            Thread.sleep(300) // Give the server time to be ready
            val url = URL("http://127.0.0.1:$port/callback?code=test123&state=mystate")
            val conn = url.openConnection()
            conn.connect()
            // Read and discard the response body (required for HTTP connection reuse)
            conn.content
        }

        // Wait for the callback with timeout
        val params = server.awaitCallback(timeout = 10, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertNotNull(params, "Callback parameters should not be null")
        assertEquals("test123", params?.get("code"))
        assertEquals("mystate", params?.get("state"))
    }

    @Test
    fun `wait for parameter extracts specific query param`() {
        val code = server.performOAuthFlow(
            authUrl = "https://example.com/auth?client_id=test",
            callbackPath = "/callback",
            timeout = 1,
        )
        // Should timeout since no browser opens in test
        assertNull(code)
    }

    @Test
    fun `server stops correctly`() {
        server.start(port = 0, "/callback")
        assertTrue(server.isRunning)
        server.stop()
        assertFalse(server.isRunning)
    }

    @Test
    fun `build authorization URL includes all parameters`() {
        val url = server.buildAuthorizationUrl(
            authEndpoint = "https://myanimelist.net/v1/oauth2/authorize",
            clientId = "test_client_id",
            scope = "write:animelist",
            state = "test_state",
        )
        assertTrue(url.contains("client_id=test_client_id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=write:animelist"))
        assertTrue(url.contains("state=test_state"))
    }

    @Test
    fun `handleCallback with session parms returns correct parameters`() {
        val redirectUri = server.start(port = 0, callbackPath = "/callback")
        val port = redirectUri.substringAfter("http://127.0.0.1:").substringBefore("/").toInt()

        // Make request synchronously - the handleCallback should complete the future
        val url = URL("http://127.0.0.1:$port/callback?code=sync_code")
        url.openStream().use { it.readBytes() }

        // Verify the callback was received (params should now be available)
        val params = server.awaitCallback(timeout = 3, TimeUnit.SECONDS)
        assertNotNull(params)
        assertEquals("sync_code", params?.get("code"))
    }
}
