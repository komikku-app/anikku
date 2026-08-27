package app.anikku.macos.platform.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MacOSCookieJarTest {

    private val testUrl = "https://example.com".toHttpUrl()
    private val testUrl2 = "https://anilist.co".toHttpUrl()

    @Test
    fun `saveFromResponse then loadForRequest returns cookies`(@TempDir tempDir: File) {
        val jar = MacOSCookieJar(File(tempDir, "cookies.json"))
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("example.com")
            .path("/")
            .build()

        jar.saveFromResponse(testUrl, listOf(cookie))
        val loaded = jar.loadForRequest(testUrl)

        assertEquals(1, loaded.size)
        assertEquals("session", loaded[0].name)
        assertEquals("abc123", loaded[0].value)
    }

    @Test
    fun `cookies scoped to domain`(@TempDir tempDir: File) {
        val jar = MacOSCookieJar(File(tempDir, "cookies.json"))
        val cookie = Cookie.Builder()
            .name("token")
            .value("xyz")
            .domain("example.com")
            .path("/")
            .build()

        jar.saveFromResponse(testUrl, listOf(cookie))

        // Should not return cookie for a different domain
        val loaded = jar.loadForRequest(testUrl2)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `removeAll clears all cookies`(@TempDir tempDir: File) {
        val jar = MacOSCookieJar(File(tempDir, "cookies.json"))
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc")
            .domain("example.com")
            .path("/")
            .build()

        jar.saveFromResponse(testUrl, listOf(cookie))
        jar.removeAll()
        assertTrue(jar.loadForRequest(testUrl).isEmpty())
    }

    @Test
    fun `remove specific cookies by name`(@TempDir tempDir: File) {
        val jar = MacOSCookieJar(File(tempDir, "cookies.json"))
        val session = Cookie.Builder()
            .name("session")
            .value("abc")
            .domain("example.com")
            .path("/")
            .build()
        val csrf = Cookie.Builder()
            .name("csrf")
            .value("xyz")
            .domain("example.com")
            .path("/")
            .build()

        jar.saveFromResponse(testUrl, listOf(session, csrf))
        val removed = jar.remove(testUrl, cookieNames = listOf("session"))
        assertEquals(1, removed)

        val remaining = jar.loadForRequest(testUrl)
        assertEquals(1, remaining.size)
        assertEquals("csrf", remaining[0].name)
    }

    @Test
    fun `cookies persist across jar instances`(@TempDir tempDir: File) {
        val cookieFile = File(tempDir, "persist.json")

        MacOSCookieJar(cookieFile).apply {
            val cookie = Cookie.Builder()
                .name("persistent")
                .value("yes")
                .domain("example.com")
                .path("/")
                .build()
            saveFromResponse(testUrl, listOf(cookie))
        }

        val reloaded = MacOSCookieJar(cookieFile)
        val loaded = reloaded.loadForRequest(testUrl)
        assertEquals(1, loaded.size)
        assertEquals("persistent", loaded[0].name)
        assertEquals("yes", loaded[0].value)
    }

    @Test
    fun `secure cookies handled correctly`(@TempDir tempDir: File) {
        val jar = MacOSCookieJar(File(tempDir, "cookies.json"))
        val secureCookie = Cookie.Builder()
            .name("secure_token")
            .value("secret")
            .domain("example.com")
            .path("/")
            .secure()
            .build()

        jar.saveFromResponse(testUrl, listOf(secureCookie))
        val loaded = jar.loadForRequest(testUrl)

        assertEquals(1, loaded.size)
        assertTrue(loaded[0].secure)
    }
}
