package app.anikku.macos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.anikku.macos.ui.MacOSMenuBarFactory
import app.anikku.macos.platform.MacOSDockManager
import app.anikku.macos.platform.BackgroundJobConfiguration
import app.anikku.macos.platform.LocalBackgroundJobs
import app.anikku.macos.ui.GlobalKeyboardShortcuts
import app.anikku.macos.ui.components.AboutDialog
import app.anikku.macos.ui.screens.browse.BrowseTab
import app.anikku.macos.ui.screens.onboarding.OnboardingScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import app.anikku.macos.ui.MainWindow
import app.anikku.macos.ui.TabSwitchHandler
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.backup.LocalBackupManager
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.platform.preference.BookmarkStore
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerOAuthManager
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.platform.network.ChromeCDPClient
import app.anikku.macos.platform.security.MacOSKeychain
import app.anikku.macos.platform.discord.LocalDiscordRPC
import app.anikku.macos.platform.sync.LocalGoogleDriveService
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
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.awt.event.WindowAdapter

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
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
    val app = AnikkuApplication()

    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 800.dp),
    )

    Window(
        onCloseRequest = {
            app.onShutdown()
            exitApplication()
        },
        title = "Anikku",
        state = windowState,
    ) {
        val settingsState = remember { SettingsState(app.preferenceStore) }
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
        ) {
            app.backgroundJobs.configure(
                BackgroundJobConfiguration(
                    automaticBackupHours = settingsState.autoBackupIntervalHours,
                    libraryUpdateHours = settingsState.libraryUpdateIntervalHours,
                    googleDriveSyncHours = settingsState.googleDriveSyncIntervalHours,
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

        // Set up the macOS native menu bar via java.awt
        val onQuit = {
            app.onShutdown()
            exitApplication()
        }
        val onSettings = { TabSwitchHandler.switchTo(4) }
        val onOpenBackup = {
            val parentFrame = window as? Frame
            val fileChooser = java.awt.FileDialog(parentFrame, "Restore Backup", java.awt.FileDialog.LOAD)
            fileChooser.setFilenameFilter { _, name ->
                name.endsWith(MacOSBackupManager.BACKUP_EXTENSION)
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

        // Phase 9.2: Initialize global keyboard shortcuts
        GlobalKeyboardShortcuts.initialize(
            onToggleSidebar = { /* Future: toggle sidebar visibility */ },
            onOpenSearch = { /* Future: focus search in current screen */ },
            onOpenSettings = { TabSwitchHandler.switchTo(4) },
            onNewSource = { TabSwitchHandler.switchTo(3) },
        )

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

        CompositionLocalProvider(
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
            LocalExtensionManager provides app.extensionManager,
            LocalBackupManager provides app.backupManager,
            LocalToastHost provides toastHostState,
            LocalTrackerManager provides trackerManager,
            LocalDiscordRPC provides app.discordRPC,
            LocalGoogleDriveService provides app.googleDriveService,
            LocalBackgroundJobs provides app.backgroundJobs,
        ) {
            AnikkuTheme(
                theme = settingsState.theme,
                isAmoledOLED = settingsState.isAmoledOLED,
                isDarkOverride = settingsState.themeMode,
            ) {
                SelectionContainer {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    if (showOnboarding) {
                        OnboardingScreen(
                            initialStep = onboardingStepPref.get(),
                            onStepChanged = { onboardingStepPref.set(it) },
                            onComplete = {
                                onboardingCompletePref.set(true)
                                onboardingStepPref.set(0)
                                showOnboarding = false
                            },
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
                        MainWindow()
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
                } // End SelectionContainer
            }
        }
    }
}
