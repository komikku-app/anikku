package app.anikku.macos.ui.settings

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.ui.theme.AnikkuTheme
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SettingsStateTest {

    // ---------------------------------------------------------------------------
    // Null store (no-arg constructor) — in-memory only, no persistence
    // ---------------------------------------------------------------------------

    @Test
    fun `no-arg constructor uses DEFAULT theme`() {
        val state = SettingsState()
        assertEquals(AnikkuTheme.Theme.DEFAULT, state.theme)
    }

    @Test
    fun `no-arg constructor has AMOLED disabled`() {
        val state = SettingsState()
        assertFalse(state.isAmoledOLED, "AMOLED should default to false")
    }

    @Test
    fun `setting theme updates getter when store is null`() {
        val state = SettingsState()

        state.theme = AnikkuTheme.Theme.SAPPHIRE
        assertEquals(AnikkuTheme.Theme.SAPPHIRE, state.theme)

        state.theme = AnikkuTheme.Theme.MATRIX
        assertEquals(AnikkuTheme.Theme.MATRIX, state.theme)

        state.theme = AnikkuTheme.Theme.DEFAULT
        assertEquals(AnikkuTheme.Theme.DEFAULT, state.theme)
    }

    @Test
    fun `setting AMOLED updates getter when store is null`() {
        val state = SettingsState()

        state.isAmoledOLED = true
        assertTrue(state.isAmoledOLED)

        state.isAmoledOLED = false
        assertFalse(state.isAmoledOLED)
    }

    @Test
    fun `theme can cycle through all values without error`() {
        val state = SettingsState()

        AnikkuTheme.Theme.entries.forEach { theme ->
            state.theme = theme
            assertEquals(theme, state.theme, "Setting theme to ${theme.displayName} failed")
        }
    }

    // ---------------------------------------------------------------------------
    // Persisted store — theme/AMOLED survive across SettingsState instances
    // ---------------------------------------------------------------------------

    @Test
    fun `loads theme from prefs on init`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)

        // Pre-populate the store
        store.getString("theme", AnikkuTheme.Theme.DEFAULT.name)
            .set(AnikkuTheme.Theme.SAPPHIRE.name)

        val state = SettingsState(store)
        assertEquals(AnikkuTheme.Theme.SAPPHIRE, state.theme)
    }

    @Test
    fun `loads AMOLED from prefs on init`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)

        store.getBoolean("amoled_oled", false).set(true)

        val state = SettingsState(store)
        assertTrue(state.isAmoledOLED, "AMOLED should be loaded as true")
    }

    @Test
    fun `changing theme persists to store`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)
        val state = SettingsState(store)

        state.theme = AnikkuTheme.Theme.NORD

        // Create a new SettingsState from the same store — should pick up the persisted value
        val fresh = SettingsState(store)
        assertEquals(AnikkuTheme.Theme.NORD, fresh.theme)
    }

    @Test
    fun `changing AMOLED persists to store`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)
        val state = SettingsState(store)

        state.isAmoledOLED = true

        val fresh = SettingsState(store)
        assertTrue(fresh.isAmoledOLED, "AMOLED should persist as true")
    }

    @Test
    fun `Discord Rich Presence setting defaults off and persists opt in`(@TempDir tempDir: Path) {
        val store = MacOSPreferenceStore(File(tempDir.toFile(), "preferences.json"))
        assertFalse(SettingsState(store).discordRichPresenceEnabled)

        SettingsState(store).discordRichPresenceEnabled = true

        assertTrue(SettingsState(store).discordRichPresenceEnabled)
    }

    @Test
    fun `multiple theme changes all persist`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)
        val state = SettingsState(store)

        state.theme = AnikkuTheme.Theme.MOCHA
        state.theme = AnikkuTheme.Theme.LAVENDER
        state.theme = AnikkuTheme.Theme.YOTSUBA

        val fresh = SettingsState(store)
        assertEquals(AnikkuTheme.Theme.YOTSUBA, fresh.theme)
    }

    @Test
    fun `multiple AMOLED toggles all persist`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)
        val state = SettingsState(store)

        state.isAmoledOLED = true
        state.isAmoledOLED = false
        state.isAmoledOLED = true

        val fresh = SettingsState(store)
        assertTrue(fresh.isAmoledOLED, "Final AMOLED value should be true")
    }

    @Test
    fun `corrupted theme name falls back to DEFAULT`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)

        // Write a garbage theme name
        store.getString("theme", AnikkuTheme.Theme.DEFAULT.name)
            .set("NON_EXISTENT_THEME")

        val state = SettingsState(store)
        assertEquals(
            AnikkuTheme.Theme.DEFAULT, state.theme,
            "Corrupted theme should fall back to DEFAULT",
        )
    }

    @Test
    fun `missing theme key falls back to DEFAULT`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        // Fresh store with no "theme" key set
        val store = MacOSPreferenceStore(prefsFile)

        val state = SettingsState(store)
        assertEquals(AnikkuTheme.Theme.DEFAULT, state.theme)
    }

    @Test
    fun `missing AMOLED key falls back to false`(@TempDir tempDir: Path) {
        val prefsFile = File(tempDir.toFile(), "preferences.json")
        val store = MacOSPreferenceStore(prefsFile)

        val state = SettingsState(store)
        assertFalse(state.isAmoledOLED)
    }

    @Test
    fun `invalid settings are normalized to safe bounds`() {
        val state = SettingsState()

        state.proxyPort = 0
        assertEquals(1, state.proxyPort)
        state.proxyPort = 70_000
        assertEquals(65_535, state.proxyPort)

        state.simultaneousDownloads = 0
        assertEquals(1, state.simultaneousDownloads)
        state.simultaneousDownloads = 99
        assertEquals(10, state.simultaneousDownloads)

        state.defaultPlaybackSpeed = Float.NaN
        assertEquals(1.0f, state.defaultPlaybackSpeed)
        state.defaultPlaybackSpeed = 10.0f
        assertEquals(4.0f, state.defaultPlaybackSpeed)
    }

    @Test
    fun `invalid persisted settings load with safe defaults`() {
        val file = File.createTempFile("settings-invalid-", ".json").apply { deleteOnExit() }
        file.writeText("""
            {"proxy_port":70000,"simultaneous_downloads":0,"default_playback_speed":"not-a-number"}
        """.trimIndent())
        val state = SettingsState(MacOSPreferenceStore(file))

        assertEquals(65_535, state.proxyPort)
        assertEquals(1, state.simultaneousDownloads)
        assertEquals(1.0f, state.defaultPlaybackSpeed)
    }

    @Test
    fun `null store does not throw on any operation`() {
        val state = SettingsState(null)

        // All operations should be no-ops, not throws
        state.theme = AnikkuTheme.Theme.DOOM
        state.isAmoledOLED = true
        state.isAmoledOLED = false
        state.theme = AnikkuTheme.Theme.DEFAULT

        assertEquals(AnikkuTheme.Theme.DEFAULT, state.theme)
        assertFalse(state.isAmoledOLED)
    }

    @Test
    fun `app lock settings use secure defaults and persist`(@TempDir tempDir: Path) {
        val store = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val state = SettingsState(store)
        assertFalse(state.appLockEnabled)
        assertTrue(state.lockOnBlur)
        assertTrue(state.useBiometrics)

        state.appLockEnabled = true
        state.lockOnBlur = false
        state.useBiometrics = false

        val restarted = SettingsState(store)
        assertTrue(restarted.appLockEnabled)
        assertFalse(restarted.lockOnBlur)
        assertFalse(restarted.useBiometrics)
    }

    // ---------------------------------------------------------------------------
    // LocalSettingsState CompositionLocal
    // ---------------------------------------------------------------------------
    // Note: LocalSettingsState.current is @Composable and cannot be tested
    // without Compose UI test infrastructure. The default factory (no-arg
    // SettingsState()) is indirectly tested via the null-store tests above.
}
