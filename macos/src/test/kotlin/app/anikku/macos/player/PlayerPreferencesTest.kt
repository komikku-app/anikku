package app.anikku.macos.player

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [PlayerPreferences].
 *
 * Tests that default values are correct and that preferences can
 * be read/written via the underlying [MacOSPreferenceStore].
 */
class PlayerPreferencesTest {

    private val tempFile = File.createTempFile("player-prefs-test", ".json").also {
        it.deleteOnExit()
    }
    private val store = MacOSPreferenceStore(tempFile)
    private val prefs = PlayerPreferences(store)

    @Test
    fun `default playback speed is 1_0`() {
        assertEquals(1.0f, prefs.defaultPlaybackSpeed.get())
    }

    @Test
    fun `default resume from last position is true`() {
        assertTrue(prefs.resumeFromLastPosition.get())
    }

    @Test
    fun `default auto-play next episode is true`() {
        assertTrue(prefs.autoPlayNextEpisode.get())
    }

    @Test
    fun `default skip intro is true`() {
        assertTrue(prefs.skipIntroOutro.get())
    }

    @Test
    fun `default volume is 100`() {
        assertEquals(100, prefs.defaultVolume.get())
    }

    @Test
    fun `default subtitle font size is 55`() {
        assertEquals(55, prefs.subtitleFontSize.get())
    }

    @Test
    fun `default seek increment is 10 seconds`() {
        assertEquals(10, prefs.seekIncrement.get())
    }

    @Test
    fun `default hardware decoding uses safe copy mode`() {
        assertEquals("auto-copy-safe", prefs.hardwareDecoding.get())
    }

    @Test
    fun `default equalizer is disabled`() {
        assertFalse(prefs.equalizerEnabled.get())
    }

    @Test
    fun `default equalizer gains are all zero`() {
        val gains = prefs.equalizerGains.get()
        assertEquals(10, gains.size)
        gains.forEach { gain ->
            assertEquals(0f, gain)
        }
    }

    @Test
    fun `default screenshot format is png`() {
        assertEquals("png", prefs.screenshotFormat.get())
    }

    @Test
    fun `default cache size is 150 MB`() {
        assertEquals(150, prefs.cacheSize.get())
    }

    @Test
    fun `default preferred subtitle language is eng`() {
        assertEquals("eng", prefs.preferredSubtitleLanguage.get())
    }

    @Test
    fun `set and get default playback speed`() {
        prefs.defaultPlaybackSpeed.set(1.5f)
        assertEquals(1.5f, prefs.defaultPlaybackSpeed.get())
    }

    @Test
    fun `set and get volume`() {
        prefs.defaultVolume.set(75)
        assertEquals(75, prefs.defaultVolume.get())
    }

    @Test
    fun `set and get auto-play next episode`() {
        prefs.autoPlayNextEpisode.set(false)
        assertFalse(prefs.autoPlayNextEpisode.get())
    }

    @Test
    fun `set and get equalizer enabled`() {
        prefs.equalizerEnabled.set(true)
        assertTrue(prefs.equalizerEnabled.get())
    }

    @Test
    fun `preferences persist after store reload`() {
        prefs.defaultPlaybackSpeed.set(2.0f)
        prefs.autoPlayNextEpisode.set(false)

        // Create a new store from the same file — should reload persisted values
        val reloadedStore = MacOSPreferenceStore(tempFile)
        val reloadedPrefs = PlayerPreferences(reloadedStore)

        assertEquals(2.0f, reloadedPrefs.defaultPlaybackSpeed.get())
        assertFalse(reloadedPrefs.autoPlayNextEpisode.get())
    }

    @Test
    fun `equalizer gains serialize and deserialize correctly`() {
        val testGains = listOf(0f, 1.5f, -2.0f, 3.0f, -4.5f, 0f, 1.0f, -1.0f, 2.0f, -3.0f)
        prefs.equalizerGains.set(testGains)

        val loaded = prefs.equalizerGains.get()
        assertEquals(10, loaded.size)
        testGains.forEachIndexed { i, expected ->
            assertEquals(expected, loaded[i])
        }
    }

    @Test
    fun `subtitle delay defaults to 0`() {
        assertEquals(0.0f, prefs.subtitleDelay.get())
    }

    @Test
    fun `set and get subtitle delay`() {
        prefs.subtitleDelay.set(0.5f)
        assertEquals(0.5f, prefs.subtitleDelay.get())
    }
}
