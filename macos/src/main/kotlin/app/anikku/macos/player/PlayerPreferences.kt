package app.anikku.macos.player

import app.anikku.macos.platform.preference.MacOSPreferenceStore

/**
 * Player preferences backed by [MacOSPreferenceStore].
 *
 * Mirrors the Android player settings (AdvancedPlayerPreferences.kt).
 * All preferences are persisted to the JSON preference file and survive
 * app restarts.
 *
 * ## Usage
 *
 * ```kotlin
 * val prefs = PlayerPreferences(preferenceStore)
 * val speed = prefs.defaultPlaybackSpeed.get()
 * prefs.defaultPlaybackSpeed.set(1.5)
 * ```
 */
class PlayerPreferences(
    private val store: MacOSPreferenceStore,
) {

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    /** Default playback speed (0.25–4.0). */
    val defaultPlaybackSpeed = store.getFloat("player_default_speed", 1.0f)

    /** Resume playback from last position. */
    val resumeFromLastPosition = store.getBoolean("player_resume_from_last", true)

    /** Auto-play the next episode when the current one ends. */
    val autoPlayNextEpisode = store.getBoolean("player_auto_play_next", true)

    /** Skip intro/outro when available. */
    val skipIntroOutro = store.getBoolean("player_skip_intro", true)

    /** Skip to next episode after credits. */
    val skipToNextEpisode = store.getBoolean("player_skip_to_next", false)

    // -------------------------------------------------------------------------
    // Audio
    // -------------------------------------------------------------------------

    /** Default volume level (0–200). */
    val defaultVolume = store.getInt("player_default_volume", 100)

    /** Remember audio track selection per episode. */
    val rememberAudioTrack = store.getBoolean("player_remember_audio_track", true)

    /** Remember subtitle track selection per episode. */
    val rememberSubtitleTrack = store.getBoolean("player_remember_subtitle_track", true)

    // -------------------------------------------------------------------------
    // Subtitle
    // -------------------------------------------------------------------------

    /** Default subtitle font size in arbitrary units. */
    val subtitleFontSize = store.getInt("player_subtitle_font_size", 55)

    /** Subtitle delay in seconds. */
    val subtitleDelay = store.getFloat("player_subtitle_delay", 0.0f)

    /** Preferred subtitle language (ISO 639-1 code). */
    val preferredSubtitleLanguage = store.getString("player_preferred_subtitle_lang", "eng")

    // -------------------------------------------------------------------------
    // Seek
    // -------------------------------------------------------------------------

    /** Seek increment for forward/backward buttons (seconds). */
    val seekIncrement = store.getInt("player_seek_increment", 10)

    /** Seek increment for keyboard shortcuts (seconds). */
    val seekIncrementKeyboard = store.getInt("player_seek_increment_keyboard", 5)

    // -------------------------------------------------------------------------
    // Hardware & Performance
    // -------------------------------------------------------------------------

    /** Hardware decoding preference (safe copy mode supports the software render API). */
    val hardwareDecoding = store.getString("player_hardware_decoding", "auto-copy-safe")

    /** Cache size in MB. */
    val cacheSize = store.getInt("player_cache_size", 150)

    // -------------------------------------------------------------------------
    // Equalizer
    // -------------------------------------------------------------------------

    /** Whether the equalizer is enabled. */
    val equalizerEnabled = store.getBoolean("player_equalizer_enabled", false)

    /** Equalizer band gains (10 bands, -12 to +12 dB each). */
    val equalizerGains = store.getObject(
        key = "player_equalizer_gains",
        defaultValue = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        serializer = { gains -> gains.joinToString(",") { String.format("%.1f", it) } },
        deserializer = { str ->
            str.split(",").mapNotNull { it.trim().toFloatOrNull() }
                .ifEmpty { listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
        },
    )

    // -------------------------------------------------------------------------
    // Screenshots
    // -------------------------------------------------------------------------

    /** Screenshot format (png, jpg, webp). */
    val screenshotFormat = store.getString("player_screenshot_format", "png")

    /** Screenshot directory override (empty = default pictures directory). */
    val screenshotDirectory = store.getString("player_screenshot_dir", "")
}
