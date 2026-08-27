package app.anikku.macos.platform

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent

class MacOSDockManagerTest {

    @AfterEach
    fun resetCallbacks() {
        MacOSDockManager.setPlayPauseCallback {}
        MacOSDockManager.setNextEpisodeCallback {}
    }

    @Test
    fun `set badge count does not throw`() {
        // In test environment, Dock bridge may not be available
        // But calling it should not throw
        MacOSDockManager.setBadgeCount(5)
        assert(true)
    }

    @Test
    fun `clear badge does not throw`() {
        MacOSDockManager.clearBadge()
        assert(true)
    }

    @Test
    fun `request user attention does not throw`() {
        MacOSDockManager.requestUserAttention(critical = false)
        assert(true)
    }

    @Test
    fun `critical user attention does not throw`() {
        MacOSDockManager.requestUserAttention(critical = true)
        assert(true)
    }

    @Test
    fun `set play pause callback does not throw`() {
        MacOSDockManager.setPlayPauseCallback { /* no-op */ }
        assert(true)
    }

    @Test
    fun `set next episode callback does not throw`() {
        MacOSDockManager.setNextEpisodeCallback { /* no-op */ }
        assert(true)
    }

    @Test
    fun `create dock menu does not throw`() {
        MacOSDockManager.createDockMenu()
        assert(true)
    }

    @Test
    fun `set badge count to zero is safe`() {
        MacOSDockManager.setBadgeCount(0)
        assert(true)
    }

    @Test
    fun `set badge count to negative is safe`() {
        MacOSDockManager.setBadgeCount(-1)
        assert(true)
    }

    @Test
    fun `onPlayPause callback is invoked when set`() {
        var invoked = false
        MacOSDockManager.setPlayPauseCallback { invoked = true }
        MacOSDockManager.onPlayPause()
        assert(invoked)
    }

    @Test
    fun `onNextEpisode callback is invoked when set`() {
        var invoked = false
        MacOSDockManager.setNextEpisodeCallback { invoked = true }
        MacOSDockManager.onNextEpisode()
        assert(invoked)
    }

    @Test
    fun `callbacks are independent`() {
        var playInvoked = false
        var nextInvoked = false
        MacOSDockManager.setPlayPauseCallback { playInvoked = true }
        MacOSDockManager.setNextEpisodeCallback { nextInvoked = true }
        MacOSDockManager.onPlayPause()
        assert(playInvoked)
        assert(!nextInvoked)
    }

    @Test
    fun `dock menu has native playback items wired to current callbacks`() {
        var playInvoked = false
        var nextInvoked = false
        MacOSDockManager.setPlayPauseCallback { playInvoked = true }
        MacOSDockManager.setNextEpisodeCallback { nextInvoked = true }

        val menu = MacOSDockManager.buildDockMenu()
        assertEquals(2, menu.itemCount)
        assertEquals("Play / Pause", menu.getItem(0).label)
        assertEquals("Next Episode", menu.getItem(1).label)

        menu.getItem(0).actionListeners.single().actionPerformed(
            ActionEvent(menu.getItem(0), ActionEvent.ACTION_PERFORMED, "play"),
        )
        menu.getItem(1).actionListeners.single().actionPerformed(
            ActionEvent(menu.getItem(1), ActionEvent.ACTION_PERFORMED, "next"),
        )
        assert(playInvoked)
        assert(nextInvoked)
    }
}
