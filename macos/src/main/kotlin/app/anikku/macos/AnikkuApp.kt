package app.anikku.macos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPosition
import kotlin.math.roundToInt
import app.anikku.macos.ui.MacOSMenuBarFactory
import app.anikku.macos.platform.MacOSDockManager
import app.anikku.macos.platform.BackgroundJobConfiguration
import app.anikku.macos.platform.LocalBackgroundJobs
import app.anikku.macos.ui.GlobalKeyboardShortcuts
import app.anikku.macos.ui.components.AboutDialog
import app.anikku.macos.ui.components.KeyboardShortcutsDialog
import app.anikku.macos.ui.components.WhatsNewDialog
import app.anikku.macos.ui.screens.browse.BrowseTab
import app.anikku.macos.ui.screens.onboarding.OnboardingScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.TabSwitchHandler
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.update.AppInfo
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.watch.LocalWatchTogetherServer
import app.anikku.macos.platform.watch.LocalWatchTogetherTunnel
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.backup.LocalBackupManager
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.platform.library.LocalAnimeSourceMatcher
import app.anikku.macos.platform.library.LocalLibraryAutoLinkService
import app.anikku.macos.platform.library.LocalNewEpisodeRepository
import app.anikku.macos.platform.local.LocalLocalLibraryRepository
import app.anikku.macos.platform.library.LibraryAutoLinkService
import app.anikku.macos.platform.preference.BookmarkStore
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.platform.auth.AniListSyncService
import app.anikku.macos.platform.auth.LocalAniListSyncService
import app.anikku.macos.platform.auth.LocalTrackerLibrarySyncService
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.anilist.AniListSearchClient
import app.anikku.macos.platform.anilist.LocalAniListSearchClient
import app.anikku.macos.platform.auth.TrackerLibrarySyncService
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerOAuthManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.platform.network.ChromeCDPClient
import app.anikku.macos.platform.security.MacOSKeychain
import app.anikku.macos.platform.discord.LocalDiscordRPC
import app.anikku.macos.platform.sync.LocalGoogleDriveService
import app.anikku.macos.platform.sync.LocalSyncYomiService
import app.anikku.macos.platform.network.ProxyConfig
import app.anikku.macos.platform.network.ProxyType
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastHost
import app.anikku.macos.ui.components.ToastHostState
import app.anikku.macos.ui.settings.LocalSettingsState
import app.anikku.macos.ui.settings.SettingsState
import app.anikku.macos.ui.settings.AppLockController
import app.anikku.macos.ui.settings.LocalAppLockController
import app.anikku.macos.ui.security.AppLockScreen
import app.anikku.macos.ui.theme.AnikkuTheme

import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.awt.event.WindowAdapter

internal const val IMAGE_MEMORY_CACHE_BYTES = 256L * 1024L * 1024L
internal const val IMAGE_DISK_CACHE_BYTES = 512L * 1024L * 1024L

/**
 * Periodic tracker-sync loop shared by MAL + Kitsu. Waits one full interval
 * between checks; a tracker that isn't logged in is skipped until the next
 * tick. Imported/updated entries kick the auto-link pass so source-less
 * imports become playable.
 */
private suspend fun periodicTrackerSync(
    intervalHours: Int,
    loggedIn: () -> Boolean,
    lastSyncAt: () -> Long,
    setLastSyncAt: (Long) -> Unit,
    sync: suspend () -> app.anikku.macos.platform.auth.SyncOutcome,
    onImported: () -> Unit,
) {
    if (intervalHours <= 0) return
    while (true) {
        delay(intervalHours * 3_600_000L)
        if (!loggedIn()) continue
        val due = lastSyncAt() == 0L ||
            System.currentTimeMillis() - lastSyncAt() >= intervalHours * 3_600_000L
        if (due) {
            val result = sync()
            if (result.errors.isEmpty()) setLastSyncAt(System.currentTimeMillis())
            if (result.imported > 0 || result.updated > 0) onImported()
        }
    }
}

/**
 * The app's main window frame. Used by the player for native macOS fullscreen
 * (double-click / F key / menu) via MacOSFullScreen.toggleFullScreen.
 */
val LocalAppWindow = compositionLocalOf<Frame?> { null }

internal fun imageDiskCacheDirectory(userHome: String = System.getProperty("user.home")): java.io.File =
    java.io.File(
        userHome,
        "Library${java.io.File.separator}Application Support${java.io.File.separator}Anikku${java.io.File.separator}cache${java.io.File.separator}images",
    )

/**
 * Anikku macOS — Entry Point.
 *
 * Launches the Compose Desktop application with:
 * - macOS application initialization (logging, Koin DI, storage, database)
 * - Native menu bar (File, Edit, View, Window, Help) per AD-04 (Phase 9.1)
 * - Material 3 theme system (18+ color schemes)
 * - Voyager tab navigation with desktop sidebar rail
 * - Coil 3 image loading with OkHttp network layer
 * - Onboarding flow on first launch (Phase 5.12)
 * - Dock menu with Play/Pause, Next Episode (Phase 9.6)
 */
fun main() = application {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageDiskCacheDirectory())
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
    val app = AnikkuApplication()

    // Window size/position persist across launches via preferences.
    val windowPrefs = app.preferenceStore
    val savedWidth = windowPrefs.getInt("window_width", 0).get()
    val savedHeight = windowPrefs.getInt("window_height", 0).get()
    val savedX = windowPrefs.getInt("window_x", Int.MIN_VALUE).get()
    val savedY = windowPrefs.getInt("window_y", Int.MIN_VALUE).get()
    val windowState = rememberWindowState(
        size = if (savedWidth > 0 && savedHeight > 0) {
            DpSize(savedWidth.dp, savedHeight.dp)
        } else {
            DpSize(1280.dp, 800.dp)
        },
        position = if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
            WindowPosition(savedX.dp, savedY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    // Debounced save of the window bounds (position + size).
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position to windowState.size }
            .debounce(500)
            .collect { (position, size) ->
                if (position.isSpecified) {
                    windowPrefs.getInt("window_x", Int.MIN_VALUE).set(position.x.value.roundToInt())
                    windowPrefs.getInt("window_y", Int.MIN_VALUE).set(position.y.value.roundToInt())
                }
                windowPrefs.getInt("window_width", 0).set(size.width.value.roundToInt())
                windowPrefs.getInt("window_height", 0).set(size.height.value.roundToInt())
            }
    }

    Window(
        onCloseRequest = {
            // A shutdown step must never block quitting — if any resource
            // teardown throws (Discord RPC, Sparkle, TorrServer...), the close
            // request would die before exitApplication() and the window would
            // stay stuck open. Exit unconditionally.
            runCatching { app.onShutdown() }
            exitApplication()
        },
        title = "Anikku",
        state = windowState,
    ) {
        val settingsState = remember {
            SettingsState(
                preferenceStore = app.preferenceStore,
                secretStore = MacOSKeychain(service = "anikku-proxy", account = "network"),
            )
        }
        if (settingsState.appLockEnabled && !app.biometricAuth.isPinSet) {
            // Never strand a user behind a lock whose Keychain credential is
            // missing or inaccessible.
            settingsState.appLockEnabled = false
        }
        var isLocked by remember {
            mutableStateOf(settingsState.appLockEnabled && app.biometricAuth.isPinSet)
        }

        DisposableEffect(window, settingsState) {
            val focusListener = object : WindowAdapter() {
                override fun windowGainedFocus(event: java.awt.event.WindowEvent?) {
                    app.onAppFocused(enableDiscord = settingsState.discordRichPresenceEnabled)
                }

                override fun windowLostFocus(event: java.awt.event.WindowEvent?) {
                    app.onAppBlurred()
                    if (settingsState.appLockEnabled && settingsState.lockOnBlur && app.biometricAuth.isPinSet) {
                        isLocked = true
                    }
                }
            }
            window.addWindowFocusListener(focusListener)
            if (window.isFocused) app.onAppFocused(enableDiscord = settingsState.discordRichPresenceEnabled)
            onDispose {
                window.removeWindowFocusListener(focusListener)
                app.onAppBlurred()
            }
        }

        // Wire proxy settings from UI to network helper.
        // The proxyProvider lambda reads the current settings on every client build.
        app.networkHelper.proxyProvider = {
            val type = settingsState.proxyType
            if (type != ProxyType.DISABLED && settingsState.proxyHost.isNotBlank()) {
                ProxyConfig(
                    type = type,
                    host = settingsState.proxyHost,
                    port = settingsState.proxyPort,
                    username = settingsState.proxyUsername,
                    password = settingsState.proxyPassword,
                )
            } else {
                null
            }
        }
        // Rebuild the client so the proxy takes effect immediately
        app.networkHelper.rebuildClient()

        LaunchedEffect(
            settingsState.autoBackupIntervalHours,
            settingsState.libraryUpdateIntervalHours,
            settingsState.googleDriveSyncIntervalHours,
            settingsState.syncYomiIntervalHours,
        ) {
            app.backgroundJobs.configure(
                BackgroundJobConfiguration(
                    automaticBackupHours = settingsState.autoBackupIntervalHours,
                    libraryUpdateHours = settingsState.libraryUpdateIntervalHours,
                    googleDriveSyncHours = settingsState.googleDriveSyncIntervalHours,
                    syncYomiSyncHours = settingsState.syncYomiIntervalHours,
                ),
            )
            if (window.isFocused) app.backgroundJobs.onAppFocused()
        }

        // Wire Chrome path from settings to CDP client
        ChromeCDPClient.customChromePath = settingsState.chromePath
        ChromeCDPClient.debugMode = settingsState.cdpDebugMode
        val bookmarkStore = remember { BookmarkStore(app.preferenceStore) }
        val libraryRepository = remember { app.libraryRepository }
        val historyRepository = remember { app.historyRepository }
        val downloadManager = remember {
            try {
                org.koin.core.context.GlobalContext.get().get<MacOSDownloadManager>()
            } catch (e: Exception) {
                println("[AnikkuApp] Failed to resolve MacOSDownloadManager from Koin: ${e.message}")
                null
            }
        }
        val toastHostState = remember { ToastHostState() }

        var showAboutDialog by remember { mutableStateOf(false) }

        // Phase 5.12: Check if onboarding has been completed
        val onboardingCompletePref = remember {
            app.preferenceStore.getBoolean("onboarding_completed", false)
        }
        val onboardingStepPref = remember {
            app.preferenceStore.getInt("onboarding_step", 0)
        }
        var showOnboarding by remember {
            mutableStateOf(!onboardingCompletePref.get())
        }

        // Last active tab — restore it on launch, persist on every switch.
        val lastTabPref = remember { app.preferenceStore.getInt("last_tab_index", 0) }
        // Set by the onboarding "Browse sources" CTA while MainWindow is not
        // yet composed (TabSwitchHandler has no listener then); consumed as
        // MainWindow's initial tab.
        var pendingInitialTab by remember { mutableStateOf(-1) }

        // "What's New" — shown once per version after an update, never on a
        // brand-new install (onboarding completion stamps last_launched_version).
        var showWhatsNew by remember {
            mutableStateOf(
                onboardingCompletePref.get() &&
                    app.preferenceStore.getString("last_launched_version", "").get() !=
                    AppInfo.VERSION,
            )
        }

        // Set up the macOS native menu bar via java.awt
        val onQuit = {
            runCatching { app.onShutdown() }
            exitApplication()
        }
        val onSettings = { TabSwitchHandler.switchTo(8) }
        val onOpenBackup = {
            val parentFrame = window as? Frame
            val fileChooser = java.awt.FileDialog(parentFrame, "Restore Backup", java.awt.FileDialog.LOAD)
            fileChooser.setFilenameFilter { _, name ->
                name.endsWith(MacOSBackupManager.BACKUP_EXTENSION, ignoreCase = true) ||
                    name.endsWith(MacOSBackupManager.ANDROID_BACKUP_EXTENSION, ignoreCase = true)
            }
            fileChooser.isVisible = true
            if (fileChooser.file != null) {
                val backupFile = java.io.File(fileChooser.directory, fileChooser.file)
                app.applicationScope.launch(Dispatchers.IO) {
                    val result = app.backupManager.importFrom(backupFile)
                    withContext(Dispatchers.Main) {
                        if (result.success) {
                            toastHostState.show(
                                "Restored: ${result.libraryCount} library, ${result.historyCount} history entries",
                                ToastDuration.LONG,
                            )
                        } else {
                            toastHostState.show(
                                "Restore failed: ${result.error ?: "Unknown error"}",
                                ToastDuration.LONG,
                                true,
                            )
                        }
                    }
                }
            }
        }
        // Wire the menu bar save-backup callback
        MacOSMenuBarFactory.onSaveBackup = {
            app.applicationScope.launch(Dispatchers.IO) {
                val result = app.backupManager.exportToDir(app.storageProvider.backupsDirectory)
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        toastHostState.show(
                            "Backup created: ${result.name}",
                            ToastDuration.LONG,
                        )
                    } else {
                        toastHostState.show(
                            "Backup creation failed",
                            ToastDuration.LONG,
                            true,
                        )
                    }
                }
            }
        }
        // Wire the menu bar playback actions to a global handler.
        // The PlayerScreen registers its own handler when active; this
        // default handler shows a toast when no video is playing.
        if (MacOSMenuBarFactory.onPlaybackAction == null) {
            MacOSMenuBarFactory.onPlaybackAction = { action ->
                toastHostState.show(
                    "No video playing — open an episode to use playback controls",
                    ToastDuration.SHORT,
                )
            }
        }
        // Track whether the About dialog was opened from the menu Check for Updates
        var autoCheckUpdates by remember { mutableStateOf(false) }

        val onAbout = { showAboutDialog = true; autoCheckUpdates = false }
        val onCheckForUpdates = {
            showAboutDialog = true
            autoCheckUpdates = true
        }
        (window as? Frame)?.let { frame ->
            MacOSMenuBarFactory.attach(
                frame, onQuit, onSettings, onOpenBackup, onAbout, onCheckForUpdates,
            )
        }

        // MainWindow owns the sidebar/search callbacks because their state is
        // composable-local. These two callbacks are application-wide.
        GlobalKeyboardShortcuts.onOpenSettings = { TabSwitchHandler.switchTo(8) }
        GlobalKeyboardShortcuts.onNewSource = { TabSwitchHandler.switchTo(4) }

        // Help > Keyboard Shortcuts… opens an app-level reference dialog.
        var showShortcutsDialog by remember { mutableStateOf(false) }
        GlobalKeyboardShortcuts.onShowShortcuts = { showShortcutsDialog = true }
        if (showShortcutsDialog) {
            KeyboardShortcutsDialog(onDismiss = { showShortcutsDialog = false })
        }

        // Phase 5.6: Wire extension manager to BrowseTab
        BrowseTab.setExtensionManager(app.extensionManager)
        BrowseTab.setPreferenceStore(app.preferenceStore)

        // Phase 9.6: Initialize Dock integration (non-fatal — app works without dock features)
        try {
            MacOSDockManager.setPlayPauseCallback {
                MacOSMenuBarFactory.onPlaybackAction?.invoke(MacOSMenuBarFactory.PlaybackAction.PLAY_PAUSE)
            }
            MacOSDockManager.setNextEpisodeCallback {
                toastHostState.show("No next episode — open the player first", ToastDuration.SHORT)
            }
            MacOSDockManager.setBadgeCount(0) // Clear badge on launch
            MacOSDockManager.createDockMenu() // Create dock menu with Play/Pause and Next Episode
        } catch (e: Exception) {
            // Dock features are best-effort; app continues without them
            println("[AnikkuApp] Dock integration failed (non-fatal): ${e.message}")
        }

        val keychain = remember { MacOSKeychain(service = "anikku", account = "anikku-app") }

        val trackerManager = remember {
            TrackerManager(
                oauthManager = app.networkHelper.client.let { TrackerOAuthManager(it) },
                tokenStore = app.preferenceStore.let { TrackerTokenStore(it, keychain) },
                httpClient = app.networkHelper.client,
            )
        }

        val anilistSyncService = remember {
            AniListSyncService(
                trackerManager = trackerManager,
                libraryRepository = libraryRepository,
                historyRepository = historyRepository,
            )
        }

        // MAL + Kitsu sync (2-way) — same pull/push model as AniList.
        val trackerLibrarySyncService = remember {
            TrackerLibrarySyncService(
                trackerManager = trackerManager,
                libraryRepository = libraryRepository,
                historyRepository = historyRepository,
            )
        }

        val libraryAutoLinkService = remember { app.libraryAutoLinkService }

        // Public (unauthenticated) AniList search — used by the Torrents tab to
        // resolve grouped Nyaa titles to canonical anime entries.
        val anilistSearchClient = remember { AniListSearchClient(app.networkHelper.client) }

        // Periodic 2-way AniList sync. Waits one full interval between checks
        // (restarts when the setting changes); failures are silent so the next
        // tick retries. Manual "Sync library now" lives in Settings > Tracking.
        LaunchedEffect(settingsState.anilistSyncIntervalHours) {
            val intervalHours = settingsState.anilistSyncIntervalHours
            if (intervalHours <= 0) return@LaunchedEffect
            while (true) {
                delay(intervalHours * 3_600_000L)
                if (!anilistSyncService.canSync()) continue
                val due = settingsState.anilistLastSyncAt == 0L ||
                    System.currentTimeMillis() - settingsState.anilistLastSyncAt >= intervalHours * 3_600_000L
                if (due) {
                    val result = anilistSyncService.syncNow()
                    if (result.errors.isEmpty()) {
                        settingsState.anilistLastSyncAt = System.currentTimeMillis()
                    }
                    // Fresh tracker imports land in the library without a
                    // streaming source; auto-link them in the background so
                    // they become playable without manual matching.
                    if (result.imported > 0 || result.updated > 0) {
                        app.applicationScope.launch {
                            runCatching { libraryAutoLinkService.autoLink() }
                        }
                    }
                }
            }
        }

        // Periodic MAL + Kitsu sync — same cadence model as AniList above.
        // Runs the shared TrackerLibrarySyncService when the tracker is logged
        // in and its interval is enabled; failures are silent so the next tick
        // retries.
        LaunchedEffect(settingsState.malSyncIntervalHours) {
            periodicTrackerSync(
                intervalHours = settingsState.malSyncIntervalHours,
                loggedIn = { trackerManager.isLoggedIn("myanimelist") },
                lastSyncAt = { settingsState.malLastSyncAt },
                setLastSyncAt = { settingsState.malLastSyncAt = it },
                sync = { trackerLibrarySyncService.syncNow("myanimelist") },
                onImported = { app.applicationScope.launch { runCatching { libraryAutoLinkService.autoLink() } } },
            )
        }
        LaunchedEffect(settingsState.kitsuSyncIntervalHours) {
            periodicTrackerSync(
                intervalHours = settingsState.kitsuSyncIntervalHours,
                loggedIn = { trackerManager.isLoggedIn("kitsu") },
                lastSyncAt = { settingsState.kitsuLastSyncAt },
                setLastSyncAt = { settingsState.kitsuLastSyncAt = it },
                sync = { trackerLibrarySyncService.syncNow("kitsu") },
                onImported = { app.applicationScope.launch { runCatching { libraryAutoLinkService.autoLink() } } },
            )
        }

        CompositionLocalProvider(
            LocalAppWindow provides (window as? Frame),
            LocalSettingsState provides settingsState,
            LocalAppLockController provides remember(app.biometricAuth) {
                AppLockController(
                    authentication = app.biometricAuth,
                    lockNow = { if (settingsState.appLockEnabled) isLocked = true },
                )
            },
            LocalBookmarkStore provides bookmarkStore,
            LocalLibraryRepository provides libraryRepository,
            LocalHistoryRepository provides historyRepository,
            LocalDownloadManager provides downloadManager,
            LocalLocalLibraryRepository provides app.localLibraryRepository,
            LocalExtensionManager provides app.extensionManager,
            LocalAnimeSourceMatcher provides app.animeSourceMatcher,
            LocalLibraryAutoLinkService provides libraryAutoLinkService,
            LocalNewEpisodeRepository provides app.newEpisodeRepository,
            LocalBackupManager provides app.backupManager,
            LocalToastHost provides toastHostState,
            LocalTrackerManager provides trackerManager,
            LocalAniListSearchClient provides anilistSearchClient,
            LocalAniListSyncService provides anilistSyncService,
            LocalTrackerLibrarySyncService provides trackerLibrarySyncService,
            LocalDiscordRPC provides app.discordRPC,
            LocalGoogleDriveService provides app.googleDriveService,
            LocalSyncYomiService provides app.syncYomiService,
            LocalBackgroundJobs provides app.backgroundJobs,
            LocalWatchTogetherServer provides app.watchTogetherServer,
            LocalWatchTogetherTunnel provides app.watchTogetherTunnel,
        ) {
            AnikkuTheme(
                theme = settingsState.theme,
                isAmoledOLED = settingsState.isAmoledOLED,
                isDarkOverride = settingsState.themeMode,
            ) {
                // NOTE: no root-level SelectionContainer. Wrapping the whole app
                // made every Text selectable — including inside popups (settings
                // DropdownMenu) and the player's animated panels — which crashes
                // with "layouts are not part of the same hierarchy" when a press
                // lands on text across layout roots (SelectionManager
                // convertToContainerCoordinates). Copy actions use explicit
                // clipboard buttons instead.
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            initialStep = onboardingStepPref.get(),
                            onStepChanged = { onboardingStepPref.set(it) },
                            onComplete = {
                                onboardingCompletePref.set(true)
                                onboardingStepPref.set(0)
                                app.preferenceStore.getString("last_launched_version", "")
                                    .set(AppInfo.VERSION)
                                showOnboarding = false
                            },
                            onBrowseSources = { pendingInitialTab = 4 },
                        ).Content()
                    } else if (isLocked && settingsState.appLockEnabled) {
                        AppLockScreen(
                            biometricAvailable = app.biometricAuth.isBiometricAvailable,
                            useBiometrics = settingsState.useBiometrics,
                            onVerifyPin = app.biometricAuth::verifyPin,
                            onBiometricUnlock = {
                                withContext(Dispatchers.IO) {
                                    app.biometricAuth.authenticateWithBiometrics(
                                        reason = "Unlock your Anikku library",
                                    )
                                }
                            },
                            onUnlocked = { isLocked = false },
                        )
                    } else {
                        MainWindow(
                            initialTabIndex = if (pendingInitialTab >= 0) pendingInitialTab else lastTabPref.get(),
                            onTabIndexChange = { index ->
                                pendingInitialTab = -1
                                lastTabPref.set(index)
                            },
                        )
                    }
                    ToastHost(state = toastHostState)
                }

                if (showAboutDialog) {
                    AboutDialog(
                        onCloseRequest = { showAboutDialog = false },
                        updateChecker = app.appUpdateChecker,
                        sparkleUpdater = app.sparkleUpdater,
                        autoCheck = autoCheckUpdates,
                    )
                }

                if (showWhatsNew) {
                    WhatsNewDialog(
                        onDismiss = {
                            app.preferenceStore.getString("last_launched_version", "")
                                .set(AppInfo.VERSION)
                            showWhatsNew = false
                        },
                    )
                }
            }
        }
    }
}
