package app.anikku.macos.ui.settings

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Persistence checks for the Tier-1 settings: subtitle appearance
 * (font size / position) and the AniList sync cadence.
 */
class SettingsStateTier1Test {

    @TempDir
    lateinit var tempDir: Path

    private fun store() = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())

    @Test
    fun `subtitle appearance and anilist sync settings persist across reloads`() {
        val state = SettingsState(preferenceStore = store())
        state.subtitleFontSize = 88f
        state.subtitlePosition = 120
        state.anilistSyncIntervalHours = 24
        state.anilistLastSyncAt = 123456L

        val reloaded = SettingsState(preferenceStore = store())
        assertEquals(88f, reloaded.subtitleFontSize)
        assertEquals(120, reloaded.subtitlePosition)
        assertEquals(24, reloaded.anilistSyncIntervalHours)
        assertEquals(123456L, reloaded.anilistLastSyncAt)
    }

    @Test
    fun `values clamp to valid ranges`() {
        val state = SettingsState(preferenceStore = store())
        state.subtitleFontSize = 500f
        state.subtitlePosition = 999
        state.anilistSyncIntervalHours = -5
        assertEquals(160f, state.subtitleFontSize)
        assertEquals(150, state.subtitlePosition)
        assertEquals(0, state.anilistSyncIntervalHours)
    }

    @Test
    fun `defaults match expected values`() {
        val state = SettingsState(preferenceStore = null)
        assertEquals(55f, state.subtitleFontSize)
        assertEquals(100, state.subtitlePosition)
        assertEquals(0, state.anilistSyncIntervalHours)
        assertEquals(0L, state.anilistLastSyncAt)
    }
}
