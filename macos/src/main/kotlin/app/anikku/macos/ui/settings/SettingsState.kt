package app.anikku.macos.ui.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import app.anikku.macos.platform.network.ProxyType
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.ui.theme.AnikkuTheme

/** Preference key for the "New episode notifications" toggle (shared with background jobs). */
const val KEY_NEW_EPISODE_NOTIFICATIONS = "new_episode_notifications_enabled"

/**
 * Mutable settings state accessible throughout the Compose tree via CompositionLocal.
 *
 * Holds user-configurable preferences that affect the app's appearance.
 * When a [MacOSPreferenceStore] is provided, theme and AMOLED preferences
 * are persisted to a JSON file so they survive app restarts.
 *
 * Usage:
 * - **Read**: `LocalSettingsState.current.theme` — get current theme
 * - **Write**: `settingsState.theme = AnikkuTheme.Theme.SAPPHIRE` — change theme (auto-saves)
 * - **AMOLED**: `settingsState.isAmoledOLED = true` — toggle AMOLED black (auto-saves)
 */
/**
 * Theme mode: follow system, force light, or force dark.
 * Addresses Phase 9.7: Dark Mode Detection.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Proxy type enum is defined in app.anikku.macos.platform.network.ProxyType
 * for shared use between settings and network infrastructure.
 */

class SettingsState(
    private val preferenceStore: MacOSPreferenceStore? = null,
    private val secretStore: MacOSSecretStore? = null,
) {

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_AMOLED_OLED = "amoled_oled"
        private const val KEY_THEME_MODE = "theme_mode"

        // Security settings. The PIN hash itself is stored only in Keychain by
        // MacOSBiometricAuth; preferences contain non-secret behavior flags.
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_LOCK_ON_BLUR = "app_lock_on_blur"
        private const val KEY_USE_BIOMETRICS = "app_lock_use_biometrics"

        // Connections settings
        private const val KEY_DISCORD_RICH_PRESENCE = "discord_rich_presence"

        // Background jobs (0 disables the corresponding job).
        private const val KEY_AUTO_BACKUP_INTERVAL_HOURS = "auto_backup_interval_hours"
        private const val KEY_LIBRARY_UPDATE_INTERVAL_HOURS = "library_update_interval_hours"
        private const val KEY_GOOGLE_DRIVE_SYNC_INTERVAL_HOURS = "google_drive_sync_interval_hours"
        private const val KEY_SYNCYOMI_INTERVAL_HOURS = "syncyomi_interval_hours"

        // Player settings
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_RESUME_FROM_LAST = "resume_from_last"
        private const val KEY_SKIP_INTRO = "skip_intro"
        private const val KEY_DEFAULT_SPEED = "default_playback_speed"
        private const val KEY_VOLUME = "player_volume"
        private const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        private const val KEY_SUBTITLE_POSITION = "subtitle_position"
        private const val KEY_CLIP_CAPTURE_SECONDS = "player_clip_capture_seconds"

        // AniList tracker sync
        private const val KEY_ANILIST_SYNC_INTERVAL_HOURS = "anilist_sync_interval_hours"
        private const val KEY_ANILIST_LAST_SYNC_AT = "anilist_last_sync_at"
        private const val KEY_MAL_SYNC_INTERVAL_HOURS = "mal_sync_interval_hours"
        private const val KEY_MAL_LAST_SYNC_AT = "mal_last_sync_at"
        private const val KEY_KITSU_SYNC_INTERVAL_HOURS = "kitsu_sync_interval_hours"
        private const val KEY_KITSU_LAST_SYNC_AT = "kitsu_last_sync_at"

        // Download settings
        private const val KEY_SIMULTANEOUS_DOWNLOADS = "simultaneous_downloads"

        // Network settings
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USERNAME = "proxy_username"
        private const val KEY_PROXY_PASSWORD = "proxy_password"
        private const val KEY_CHROME_PATH = "chrome_path"
        private const val KEY_CDP_DEBUG_MODE = "cdp_debug_mode"
    }

    private val themePref = preferenceStore?.getString(KEY_THEME, AnikkuTheme.Theme.DEFAULT.name)
    private val amoledPref = preferenceStore?.getBoolean(KEY_AMOLED_OLED, false)
    private val themeModePref = preferenceStore?.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)

    /** Backing state for the current theme. Loaded from preferences when store is available. */
    private val _theme = mutableStateOf(loadTheme())

    /** Currently selected color scheme theme. Setting this auto-persists to disk when store is available. */
    var theme: AnikkuTheme.Theme
        get() = _theme.value
        set(value) {
            _theme.value = value
            themePref?.set(value.name)
        }

    /** Backing state for AMOLED mode. Loaded from preferences when store is available. */
    private val _isAmoledOLED = mutableStateOf(amoledPref?.get() ?: false)

    /** Whether to use AMOLED pure black backgrounds in dark mode. Setting this auto-persists to disk when store is available. */
    var isAmoledOLED: Boolean
        get() = _isAmoledOLED.value
        set(value) {
            _isAmoledOLED.value = value
            amoledPref?.set(value)
        }

    /** Backing state for the theme mode (system/light/dark). Phase 9.7. */
    private val _themeMode = mutableStateOf(loadThemeMode())

    /**
     * Theme mode: SYSTEM (follow macOS), LIGHT (force light), or DARK (force dark).
     * Setting this auto-persists to disk when store is available.
     */
    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            _themeMode.value = value
            themeModePref?.set(value.name)
        }

    // -------------------------------------------------------------------------
    // Security settings
    // -------------------------------------------------------------------------

    private val appLockPref = preferenceStore?.getBoolean(KEY_APP_LOCK_ENABLED, false)
    private val lockOnBlurPref = preferenceStore?.getBoolean(KEY_LOCK_ON_BLUR, true)
    private val useBiometricsPref = preferenceStore?.getBoolean(KEY_USE_BIOMETRICS, true)
    private val _appLockEnabled = mutableStateOf(appLockPref?.get() ?: false)
    private val _lockOnBlur = mutableStateOf(lockOnBlurPref?.get() ?: true)
    private val _useBiometrics = mutableStateOf(useBiometricsPref?.get() ?: true)

    var appLockEnabled: Boolean
        get() = _appLockEnabled.value
        set(value) {
            _appLockEnabled.value = value
            appLockPref?.set(value)
        }

    var lockOnBlur: Boolean
        get() = _lockOnBlur.value
        set(value) {
            _lockOnBlur.value = value
            lockOnBlurPref?.set(value)
        }

    var useBiometrics: Boolean
        get() = _useBiometrics.value
        set(value) {
            _useBiometrics.value = value
            useBiometricsPref?.set(value)
        }

    // -------------------------------------------------------------------------
    // Connections settings
    // -------------------------------------------------------------------------

    private val discordRichPresencePref = preferenceStore?.getBoolean(KEY_DISCORD_RICH_PRESENCE, false)
    private val _discordRichPresenceEnabled = mutableStateOf(discordRichPresencePref?.get() ?: false)

    /** Whether playback metadata may be published to the local Discord client. */
    var discordRichPresenceEnabled: Boolean
        get() = _discordRichPresenceEnabled.value
        set(value) {
            _discordRichPresenceEnabled.value = value
            discordRichPresencePref?.set(value)
        }

    private val autoBackupIntervalPref = preferenceStore?.getInt(KEY_AUTO_BACKUP_INTERVAL_HOURS, 12)
    private val libraryUpdateIntervalPref = preferenceStore?.getInt(KEY_LIBRARY_UPDATE_INTERVAL_HOURS, 0)
    private val googleDriveSyncIntervalPref = preferenceStore?.getInt(KEY_GOOGLE_DRIVE_SYNC_INTERVAL_HOURS, 0)
    private val syncYomiIntervalPref = preferenceStore?.getInt(KEY_SYNCYOMI_INTERVAL_HOURS, 0)
    private val _autoBackupIntervalHours = mutableStateOf(sanitizeJobInterval(autoBackupIntervalPref?.get() ?: 12))
    private val _libraryUpdateIntervalHours = mutableStateOf(sanitizeJobInterval(libraryUpdateIntervalPref?.get() ?: 0))
    private val _googleDriveSyncIntervalHours = mutableStateOf(sanitizeJobInterval(googleDriveSyncIntervalPref?.get() ?: 0))
    private val _syncYomiIntervalHours = mutableStateOf(sanitizeJobInterval(syncYomiIntervalPref?.get() ?: 0))

    var autoBackupIntervalHours: Int
        get() = _autoBackupIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _autoBackupIntervalHours.value = sanitized
            autoBackupIntervalPref?.set(sanitized)
        }

    var libraryUpdateIntervalHours: Int
        get() = _libraryUpdateIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _libraryUpdateIntervalHours.value = sanitized
            libraryUpdateIntervalPref?.set(sanitized)
        }

    var googleDriveSyncIntervalHours: Int
        get() = _googleDriveSyncIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _googleDriveSyncIntervalHours.value = sanitized
            googleDriveSyncIntervalPref?.set(sanitized)
        }

    var syncYomiIntervalHours: Int
        get() = _syncYomiIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _syncYomiIntervalHours.value = sanitized
            syncYomiIntervalPref?.set(sanitized)
        }

    // -------------------------------------------------------------------------
    // Player settings
    // -------------------------------------------------------------------------

    private val autoPlayPref = preferenceStore?.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    private val _autoPlayNext = mutableStateOf(autoPlayPref?.get() ?: true)

    /** Whether to automatically play the next episode when the current one ends. */
    var autoPlayNextEpisode: Boolean
        get() = _autoPlayNext.value
        set(value) {
            _autoPlayNext.value = value
            autoPlayPref?.set(value)
        }

    private val resumePref = preferenceStore?.getBoolean(KEY_RESUME_FROM_LAST, true)
    private val _resumeFromLast = mutableStateOf(resumePref?.get() ?: true)

    /** Whether to resume playback from the last watched position. */
    var resumeFromLastPosition: Boolean
        get() = _resumeFromLast.value
        set(value) {
            _resumeFromLast.value = value
            resumePref?.set(value)
        }

    private val newEpisodeNotificationsPref = preferenceStore?.getBoolean(KEY_NEW_EPISODE_NOTIFICATIONS, true)
    private val _newEpisodeNotifications = mutableStateOf(newEpisodeNotificationsPref?.get() ?: true)

    /** Whether to fire macOS notifications for newly discovered episodes. */
    var newEpisodeNotificationsEnabled: Boolean
        get() = _newEpisodeNotifications.value
        set(value) {
            _newEpisodeNotifications.value = value
            newEpisodeNotificationsPref?.set(value)
        }

    private val skipIntroPref = preferenceStore?.getBoolean(KEY_SKIP_INTRO, false)
    private val _skipIntro = mutableStateOf(skipIntroPref?.get() ?: false)

    /** Whether to skip intros when chapter markers are available. */
    var skipIntro: Boolean
        get() = _skipIntro.value
        set(value) {
            _skipIntro.value = value
            skipIntroPref?.set(value)
        }

    private val defaultSpeedPref = preferenceStore?.getFloat(KEY_DEFAULT_SPEED, 1.0f)
    private val _defaultSpeed = mutableStateOf(sanitizePlaybackSpeed(defaultSpeedPref?.get() ?: 1.0f))

    /** Default playback speed (1.0 = normal). Invalid values fall back to normal speed. */
    var defaultPlaybackSpeed: Float
        get() = _defaultSpeed.value
        set(value) {
            val sanitized = sanitizePlaybackSpeed(value)
            _defaultSpeed.value = sanitized
            defaultSpeedPref?.set(sanitized)
        }

    private val volumePref = preferenceStore?.getInt(KEY_VOLUME, 100)
    private val _volume = mutableStateOf((volumePref?.get() ?: 100).coerceIn(0, 200))

    /**
     * Last used volume (0-200, mpv scale). Saved whenever the player volume
     * changes so the next session starts where the user left it.
     */
    var volume: Int
        get() = _volume.value
        set(value) {
            val clamped = value.coerceIn(0, 200)
            _volume.value = clamped
            volumePref?.set(clamped)
        }

    private val subtitleFontSizePref = preferenceStore?.getFloat(KEY_SUBTITLE_FONT_SIZE, 55f)
    private val _subtitleFontSize = mutableStateOf(
        (subtitleFontSizePref?.get() ?: 55f).coerceIn(20f, 160f),
    )

    /** Subtitle font size (20-160), applied to the mpv sub-font-size property. */
    var subtitleFontSize: Float
        get() = _subtitleFontSize.value
        set(value) {
            val clamped = value.coerceIn(20f, 160f)
            _subtitleFontSize.value = clamped
            subtitleFontSizePref?.set(clamped)
        }

    private val clipCaptureSecondsPref = preferenceStore?.getInt(KEY_CLIP_CAPTURE_SECONDS, 5)
    private val _clipCaptureSeconds = mutableStateOf(
        (clipCaptureSecondsPref?.get() ?: 5).coerceIn(3, 10),
    )

    /** GIF clip length in seconds (3/5/10); applies when the player opens. */
    var clipCaptureSeconds: Int
        get() = _clipCaptureSeconds.value
        set(value) {
            val clamped = value.coerceIn(3, 10)
            _clipCaptureSeconds.value = clamped
            clipCaptureSecondsPref?.set(clamped)
        }

    private val subtitlePositionPref = preferenceStore?.getInt(KEY_SUBTITLE_POSITION, 100)
    private val _subtitlePosition = mutableStateOf(
        (subtitlePositionPref?.get() ?: 100).coerceIn(0, 150),
    )

    /** Subtitle vertical position (0-150), applied to the mpv sub-pos property. */
    var subtitlePosition: Int
        get() = _subtitlePosition.value
        set(value) {
            val clamped = value.coerceIn(0, 150)
            _subtitlePosition.value = clamped
            subtitlePositionPref?.set(clamped)
        }

    // -------------------------------------------------------------------------
    // AniList tracker sync
    // -------------------------------------------------------------------------

    private val anilistSyncIntervalPref = preferenceStore?.getInt(KEY_ANILIST_SYNC_INTERVAL_HOURS, 0)
    private val _anilistSyncIntervalHours = mutableStateOf(sanitizeJobInterval(anilistSyncIntervalPref?.get() ?: 0))
    private val anilistLastSyncPref = preferenceStore?.getLong(KEY_ANILIST_LAST_SYNC_AT, 0L)
    private val _anilistLastSyncAt = mutableStateOf(anilistLastSyncPref?.get() ?: 0L)

    /** How often to auto-sync the library with AniList (hours; 0 disables). */
    var anilistSyncIntervalHours: Int
        get() = _anilistSyncIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _anilistSyncIntervalHours.value = sanitized
            anilistSyncIntervalPref?.set(sanitized)
        }

    /** Epoch millis of the last successful AniList library sync (0 = never). */
    var anilistLastSyncAt: Long
        get() = _anilistLastSyncAt.value
        set(value) {
            _anilistLastSyncAt.value = value
            anilistLastSyncPref?.set(value)
        }

    // -------------------------------------------------------------------------
    // MAL + Kitsu tracker sync (mirrors AniList above)
    // -------------------------------------------------------------------------

    private val malSyncIntervalPref = preferenceStore?.getInt(KEY_MAL_SYNC_INTERVAL_HOURS, 0)
    private val _malSyncIntervalHours = mutableStateOf(sanitizeJobInterval(malSyncIntervalPref?.get() ?: 0))
    private val malLastSyncPref = preferenceStore?.getLong(KEY_MAL_LAST_SYNC_AT, 0L)
    private val _malLastSyncAt = mutableStateOf(malLastSyncPref?.get() ?: 0L)

    /** How often to auto-sync the library with MyAnimeList (hours; 0 disables). */
    var malSyncIntervalHours: Int
        get() = _malSyncIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _malSyncIntervalHours.value = sanitized
            malSyncIntervalPref?.set(sanitized)
        }

    /** Epoch millis of the last successful MAL library sync (0 = never). */
    var malLastSyncAt: Long
        get() = _malLastSyncAt.value
        set(value) {
            _malLastSyncAt.value = value
            malLastSyncPref?.set(value)
        }

    private val kitsuSyncIntervalPref = preferenceStore?.getInt(KEY_KITSU_SYNC_INTERVAL_HOURS, 0)
    private val _kitsuSyncIntervalHours = mutableStateOf(sanitizeJobInterval(kitsuSyncIntervalPref?.get() ?: 0))
    private val kitsuLastSyncPref = preferenceStore?.getLong(KEY_KITSU_LAST_SYNC_AT, 0L)
    private val _kitsuLastSyncAt = mutableStateOf(kitsuLastSyncPref?.get() ?: 0L)

    /** How often to auto-sync the library with Kitsu (hours; 0 disables). */
    var kitsuSyncIntervalHours: Int
        get() = _kitsuSyncIntervalHours.value
        set(value) {
            val sanitized = sanitizeJobInterval(value)
            _kitsuSyncIntervalHours.value = sanitized
            kitsuSyncIntervalPref?.set(sanitized)
        }

    /** Epoch millis of the last successful Kitsu library sync (0 = never). */
    var kitsuLastSyncAt: Long
        get() = _kitsuLastSyncAt.value
        set(value) {
            _kitsuLastSyncAt.value = value
            kitsuLastSyncPref?.set(value)
        }

    // -------------------------------------------------------------------------
    // Download settings
    // -------------------------------------------------------------------------

    private val simultaneousPref = preferenceStore?.getInt(KEY_SIMULTANEOUS_DOWNLOADS, 3)
    private val _simultaneousDownloads = mutableStateOf(
        (simultaneousPref?.get() ?: 3).coerceIn(1, 10),
    )

    /** Number of simultaneous downloads (1-10). */
    var simultaneousDownloads: Int
        get() = _simultaneousDownloads.value
        set(value) {
            val clamped = value.coerceIn(1, 10)
            _simultaneousDownloads.value = clamped
            simultaneousPref?.set(clamped)
        }

    // -------------------------------------------------------------------------
    // Network settings
    // -------------------------------------------------------------------------

    private val proxyTypePref = preferenceStore?.getString(KEY_PROXY_TYPE, ProxyType.DISABLED.name)
    private val _proxyType = mutableStateOf(
        runCatching { ProxyType.valueOf(proxyTypePref?.get() ?: ProxyType.DISABLED.name) }
            .getOrDefault(ProxyType.DISABLED),
    )

    /** Proxy type: DISABLED, HTTP, SOCKS4, SOCKS5. */
    var proxyType: ProxyType
        get() = _proxyType.value
        set(value) {
            _proxyType.value = value
            proxyTypePref?.set(value.name)
        }

    private val proxyHostPref = preferenceStore?.getString(KEY_PROXY_HOST, "")
    private val _proxyHost = mutableStateOf(proxyHostPref?.get() ?: "")

    /** Proxy hostname or IP address. */
    var proxyHost: String
        get() = _proxyHost.value
        set(value) {
            _proxyHost.value = value
            proxyHostPref?.set(value)
        }

    private val proxyPortPref = preferenceStore?.getInt(KEY_PROXY_PORT, 8080)
    private val _proxyPort = mutableStateOf(
        (proxyPortPref?.get() ?: 8080).coerceIn(1, 65_535),
    )

    /** Proxy port number. */
    var proxyPort: Int
        get() = _proxyPort.value
        set(value) {
            val sanitized = value.coerceIn(1, 65_535)
            _proxyPort.value = sanitized
            proxyPortPref?.set(sanitized)
        }

    private val proxyUsernamePref = preferenceStore?.getString(KEY_PROXY_USERNAME, "")
    private val _proxyUsername = mutableStateOf(proxyUsernamePref?.get() ?: "")

    /** Proxy authentication username. */
    var proxyUsername: String
        get() = _proxyUsername.value
        set(value) {
            _proxyUsername.value = value
            proxyUsernamePref?.set(value)
        }

    private val legacyProxyPasswordPref = preferenceStore?.getString(KEY_PROXY_PASSWORD, "")
    private val _proxyPassword = mutableStateOf(loadProxyPassword())
    private val _proxyPasswordStorageError = mutableStateOf<String?>(null)

    /** Proxy authentication password. Persisted only in macOS Keychain. */
    var proxyPassword: String
        get() = _proxyPassword.value
        set(value) {
            _proxyPassword.value = value
            if (secretStore == null) return
            val stored = if (value.isBlank()) {
                secretStore.delete(KEY_PROXY_PASSWORD)
            } else {
                secretStore.store(KEY_PROXY_PASSWORD, value)
            }
            _proxyPasswordStorageError.value = if (stored) null else {
                secretStore.lastError ?: "Unable to store proxy password in macOS Keychain"
            }
        }

    val proxyPasswordStorageError: String? get() = _proxyPasswordStorageError.value

    private val chromePathPref = preferenceStore?.getString(KEY_CHROME_PATH, "")
    private val _chromePath = mutableStateOf(chromePathPref?.get() ?: "")

    /**
     * Custom Chrome/Chromium executable path. Empty = auto-detect.
     * macOS default: /Applications/Google Chrome.app/Contents/MacOS/Google Chrome
     */
    var chromePath: String
        get() = _chromePath.value
        set(value) {
            _chromePath.value = value
            chromePathPref?.set(value)
        }

    private val cdpDebugPref = preferenceStore?.getBoolean(KEY_CDP_DEBUG_MODE, false)
    private val _cdpDebugMode = mutableStateOf(cdpDebugPref?.get() ?: false)

    /**
     * Chrome DevTools Protocol Debug Mode.
     * When enabled, the ChromeCDPClient logs every WebSocket message
     * sent/received during Cloudflare bypass attempts at INFO level.
     * Useful for troubleshooting WAF bypass issues.
     */
    var cdpDebugMode: Boolean
        get() = _cdpDebugMode.value
        set(value) {
            _cdpDebugMode.value = value
            cdpDebugPref?.set(value)
        }

    /**
     * Resolve the effective Chrome executable path.
     * Returns user-configured path if set, otherwise auto-detects from standard install locations.
     */
    fun resolveChromePath(): String {
        if (_chromePath.value.isNotBlank()) return _chromePath.value
        // Standard macOS Chrome location
        val standardPath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        if (java.io.File(standardPath).isFile) return standardPath
        // Chromium via Homebrew
        val chromiumPath = "/opt/homebrew/bin/chromium"
        if (java.io.File(chromiumPath).isFile) return chromiumPath
        // Brave
        val bravePath = "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
        if (java.io.File(bravePath).isFile) return bravePath
        // Microsoft Edge
        val edgePath = "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
        if (java.io.File(edgePath).isFile) return edgePath
        return standardPath
    }

    /**
     * Resolve the effective dark mode from the theme mode and system preference.
     * This is called from @Composable context (AnikkuApp.kt passes it to AnikkuTheme).
     * @param isSystemDark Whether the macOS system is in dark mode.
     */
    fun resolveIsDark(isSystemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    private fun sanitizePlaybackSpeed(value: Float): Float =
        value.takeIf { it.isFinite() }?.coerceIn(0.25f, 4.0f) ?: 1.0f

    private fun sanitizeJobInterval(value: Int): Int = value.coerceIn(0, 24 * 30)

    /** Load theme from preferences if store is available, falling back to DEFAULT. */
    private fun loadTheme(): AnikkuTheme.Theme {
        val name = themePref?.get() ?: return AnikkuTheme.Theme.DEFAULT
        return try {
            AnikkuTheme.Theme.valueOf(name)
        } catch (_: Exception) {
            AnikkuTheme.Theme.DEFAULT
        }
    }

    /** Load theme mode from preferences if store is available, falling back to SYSTEM. */
    private fun loadThemeMode(): ThemeMode {
        val name = themeModePref?.get() ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadProxyPassword(): String {
        if (secretStore == null) return ""
        val secured = secretStore.retrieve(KEY_PROXY_PASSWORD)
        if (!secured.isNullOrEmpty()) {
            legacyProxyPasswordPref?.delete()
            return secured
        }

        // One-time migration from builds that persisted proxy credentials in
        // preferences. Delete plaintext only after a successful Keychain write.
        val legacy = legacyProxyPasswordPref?.get().orEmpty()
        if (legacy.isNotBlank() && secretStore.store(KEY_PROXY_PASSWORD, legacy)) {
            legacyProxyPasswordPref?.delete()
            return legacy
        }
        return ""
    }
}

/**
 * CompositionLocal providing the mutable [SettingsState] to the Compose tree.
 * Must be provided via CompositionLocalProvider in AnikkuApp.kt.
 */
val LocalSettingsState = compositionLocalOf { SettingsState() }
