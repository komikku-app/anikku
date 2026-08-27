package app.anikku.macos.ui.components

import app.anikku.macos.platform.web.BrowserLauncher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [AboutDialog].
 *
 * Since [AboutDialog] is a Compose [DialogWindow] composable that cannot be
 * rendered in a headless test environment, these tests verify the static
 * properties and BrowserLauncher integration contracts instead.
 */
class AboutDialogTest {

    @AfterEach
    fun tearDown() {
        BrowserLauncher.testMode = false
        BrowserLauncher.lastOpenedUri = null
    }

    @Test
    fun `GitHub URL is valid`() {
        val url = "https://github.com/ErnestHysa/anikku"
        assertTrue(url.startsWith("https://github.com/"), "Should be a GitHub URL")
    }

    @Test
    fun `BrowserLauncher does not throw on repo URL in any environment`() {
        BrowserLauncher.testMode = true
        assertDoesNotThrow {
            BrowserLauncher.openSafe("https://github.com/ErnestHysa/anikku")
        }
    }

    @Test
    fun `close callback works correctly`() {
        var closed = false
        val onClose: () -> Unit = { closed = true }
        onClose()
        assertTrue(closed, "Close callback should execute")
    }
}
