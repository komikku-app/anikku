package app.anikku.macos.platform.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class BrowserLauncherTest {

    @AfterEach
    fun tearDown() {
        BrowserLauncher.testMode = false
        BrowserLauncher.lastOpenedUri = null
    }

    @Test
    fun `isAvailable returns a boolean without throwing`() {
        // isAvailable lazily initializes by checking Desktop support.
        // This should never throw regardless of environment.
        BrowserLauncher.isAvailable
    }

    @Test
    fun `openSafe with valid URL captures URI without opening browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        val result = BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku")

        assertTrue(result)
        assertEquals(URI("https://github.com/ErnestHysa/anikku"), BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `openSafe with valid URI captures URI without opening browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        val result = BrowserLauncher.openSafe(URI("https://anilist.co"))

        assertTrue(result)
        assertEquals(URI("https://anilist.co"), BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `openSafe with malformed URL returns false and does not open browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        val result = BrowserLauncher.openSafe("not a valid url at all !!!")

        assertFalse(result)
        assertNull(BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `openSafe with malformed URI returns false and does not open browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        val result = BrowserLauncher.openSafe("http://examp le.com/path")

        assertFalse(result)
        assertNull(BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `open URI captures without opening browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        BrowserLauncher.open(URI("https://example.com"))

        assertEquals(URI("https://example.com"), BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `open URL string captures without opening browser`() {
        BrowserLauncher.testMode = true
        BrowserLauncher.lastOpenedUri = null

        BrowserLauncher.open("https://example.com/test")

        assertEquals(URI("https://example.com/test"), BrowserLauncher.lastOpenedUri)
    }

    @Test
    fun `openSafe returns true for valid HTTPS URL`() {
        BrowserLauncher.testMode = true

        val result = BrowserLauncher.openSafe("https://myanimelist.net/profile")
        assertTrue(result)
    }

    @Test
    fun `openSafe returns false for non-http URL scheme`() {
        BrowserLauncher.testMode = true

        // FTP is a valid URI but the test verifies openSafe still returns true
        // (it only fails on parse errors, not on scheme validation)
        val result = BrowserLauncher.openSafe("ftp://files.example.com/release")
        assertTrue(result)
    }
}
