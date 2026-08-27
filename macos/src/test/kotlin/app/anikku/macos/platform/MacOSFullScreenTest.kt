package app.anikku.macos.platform

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.awt.Frame

class MacOSFullScreenTest {

    @Test
    fun `toggleFullScreen with non-displayed Frame returns false`() {
        val frame = Frame("Test")
        // Frame is not displayed/peer-initialized, so getNSWindowPointer returns 0

        val result = MacOSFullScreen.toggleFullScreen(frame)

        assertFalse(result, "Should return false for non-realized Frame")
        assertFalse(MacOSFullScreen.isJnaAvailable,
            "isJnaAvailable should remain false after failed toggle")
    }

    @Test
    fun `toggleFullScreen with disposed Frame returns false`() {
        val frame = Frame("Test")
        frame.dispose()

        // toggleFullScreen should handle disposed frames gracefully
        val result = MacOSFullScreen.toggleFullScreen(frame)

        // Should return false without throwing
        assertFalse(result)
    }

    @Test
    fun `isJnaAvailable is initially false`() {
        assertFalse(MacOSFullScreen.isJnaAvailable)
    }

    @Test
    fun `toggleFullScreen does not throw on repeated calls`() {
        val frame = Frame("Test")

        // Should handle multiple calls without throwing
        repeat(5) {
            MacOSFullScreen.toggleFullScreen(frame)
        }
    }

    @Test
    fun `toggleFullScreen returns false on non-realized Frame`() {
        val frame = Frame("Test")

        // On a headless/non-realized Frame, should fail gracefully
        val result = MacOSFullScreen.toggleFullScreen(frame)
        assertFalse(result)
    }
}
