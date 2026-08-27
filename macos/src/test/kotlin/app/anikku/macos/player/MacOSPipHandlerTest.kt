package app.anikku.macos.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MacOSPipHandlerTest {

    @Test
    fun `pip is not visible initially`() {
        val handler = MacOSPipHandler()
        assertFalse(handler.isPipVisible)
    }

    @Test
    fun `open pip window sets visibility`() {
        val handler = MacOSPipHandler()
        handler.openPipWindow("Test Title", null)
        assertTrue(handler.isPipVisible)
        assertEquals("Test Title", handler.pipTitle)
    }

    @Test
    fun `close pip window clears visibility`() {
        val handler = MacOSPipHandler()
        handler.openPipWindow("Test", null)
        assertTrue(handler.isPipVisible)
        handler.closePipWindow()
        assertFalse(handler.isPipVisible)
    }

    @Test
    fun `toggle pip opens when closed`() {
        val handler = MacOSPipHandler()
        val result = handler.togglePip("Test", null)
        assertTrue(result)
        assertTrue(handler.isPipVisible)
    }

    @Test
    fun `toggle pip closes when open`() {
        val handler = MacOSPipHandler()
        handler.openPipWindow("Test", null)
        val result = handler.togglePip("New Title", null)
        assertFalse(result)
        assertFalse(handler.isPipVisible)
    }

    @Test
    fun `open pip sets title correctly`() {
        val handler = MacOSPipHandler()
        handler.openPipWindow("Attack on Titan - Episode 3", null)
        assertEquals("Attack on Titan - Episode 3", handler.pipTitle)
    }

    @Test
    fun `close pip is safe when already closed`() {
        val handler = MacOSPipHandler()
        handler.closePipWindow() // Should not throw
        handler.closePipWindow() // Should not throw
        assertFalse(handler.isPipVisible)
    }
}
