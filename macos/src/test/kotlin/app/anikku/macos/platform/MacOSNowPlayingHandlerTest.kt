package app.anikku.macos.platform

import org.junit.jupiter.api.Test

/**
 * Headless smoke test for the Now Playing / media-key bridge. Every native
 * call is guarded, so on any platform (including CI without a GUI session)
 * the full lifecycle must complete without throwing.
 */
class MacOSNowPlayingHandlerTest {

    @Test
    fun `command registration and metadata updates do not throw`() {
        MacOSNowPlayingHandler.registerCommands()
        MacOSNowPlayingHandler.updateNowPlaying(
            title = "Frieren — Ep 3",
            artist = "Frieren",
            durationSeconds = 1440.0,
            elapsedSeconds = 120.5,
            playing = true,
            rate = 1.0,
        )
        MacOSNowPlayingHandler.updateNowPlaying(
            title = "Frieren — Ep 3",
            artist = "Frieren",
            durationSeconds = 1440.0,
            elapsedSeconds = 130.0,
            playing = false,
            rate = 0.0,
        )
        MacOSNowPlayingHandler.clearNowPlaying()
        MacOSNowPlayingHandler.unregisterCommands()

        // Re-register proves the retained-state cleanup path is safe.
        MacOSNowPlayingHandler.registerCommands()
        MacOSNowPlayingHandler.unregisterCommands()
    }

    @Test
    fun `blank titles are ignored without touching the widget`() {
        MacOSNowPlayingHandler.updateNowPlaying(
            title = "",
            artist = null,
            durationSeconds = 0.0,
            elapsedSeconds = 0.0,
            playing = false,
            rate = 0.0,
        )
    }
}
