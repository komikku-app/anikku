package app.anikku.macos

import app.anikku.macos.di.appModule
import app.anikku.macos.di.domainModule
import app.anikku.macos.di.platformModule
import app.anikku.macos.platform.BackgroundTaskScheduler
import app.anikku.macos.platform.MacOSBackgroundJobs
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.MacOSCustomAnimeRepository
import app.anikku.macos.platform.database.MacOSDatabaseDriver
import app.anikku.macos.platform.discord.DiscordRPC
import app.anikku.macos.platform.extension.MacOSExtensionLoader
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.CrashReporter
import app.anikku.macos.platform.logging.MacOSLogger
import app.anikku.macos.platform.logging.TerminalErrorLogger
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.library.MacOSLibraryUpdateService
import app.anikku.macos.platform.migration.MacOSMigrationManager
import app.anikku.macos.platform.network.ChromeCDPClient
import app.anikku.macos.platform.network.MacOSCookieJar
import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSBiometricAuth
import app.anikku.macos.platform.security.MacOSKeychain
import app.anikku.macos.platform.storage.MacOSStorageManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import app.anikku.macos.platform.sync.GoogleDriveRestClient
import app.anikku.macos.platform.sync.MacOSGoogleDriveService
import app.anikku.macos.platform.sync.MacOSSyncYomiService
import app.anikku.macos.platform.update.AppUpdateChecker
import app.anikku.macos.platform.update.SparkleUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

/**
 * macOS desktop application initialization.
 * Replaces the Android App.kt Application class lifecycle.
 *
 * Responsibilities:
 * - Initialize logging
 * - Set up Koin dependency injection
 * - Configure storage directories
 * - Initialize database
 * - Set up background task scheduler
 *
 * This is called once at application startup from AnikkuApp.kt.
 */
class AnikkuApplication {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val storageProvider = MacOSStorageProvider()
    val preferenceStore: MacOSPreferenceStore
    val migrationManager: MacOSMigrationManager
    val backgroundScheduler = BackgroundTaskScheduler(applicationScope)
    val databaseDriver: MacOSDatabaseDriver

    // Phase 2: Storage & Data
    val storageManager: MacOSStorageManager
    val customAnimeRepository: MacOSCustomAnimeRepository
    val libraryRepository: LibraryRepository
    val historyRepository: HistoryRepository
    val downloadRepository: DownloadRepository
    val backupManager: MacOSBackupManager
    val libraryUpdateService: MacOSLibraryUpdateService

    // Phase 3: Networking
    val networkHelper: MacOSNetworkHelper
    val cookieJar: MacOSCookieJar

    // Phase 3: Extension system
    val extensionManager: MacOSExtensionManager

    // Phase 7: Advanced Features
    val discordRPC: DiscordRPC
    val googleDriveService: MacOSGoogleDriveService
    val syncYomiService: MacOSSyncYomiService
    val backgroundJobs: MacOSBackgroundJobs
    val notificationManager: MacOSNotificationManager
    val biometricAuth: MacOSBiometricAuth
    val appUpdateChecker: AppUpdateChecker
    val sparkleUpdater: SparkleUpdater

    private val shutdownStarted = AtomicBoolean(false)

    init {
        // 1. Ensure storage directories exist
        storageProvider.ensureDirectories()

        // 2. Initialize logging
        MacOSLogger.initialize(storageProvider)

        // 3. Deploy bundled extensions on first launch (Phase 3.3)
        deployBundledExtensions()

        // 3b. Network clients retain the JVM's secure default certificate and
        // hostname validation. Extension-specific exceptions must be isolated
        // to the individual client; never mutate JVM-global TLS state.

        // 3c. Initialize UI Action Logger (verbose debugging for development)
        UIActionLogger.initialize(storageProvider.logsDirectory, verboseLevel = 2)

        // 4. Initialize preferences (JSON file-backed)
        val prefsFile = File(storageProvider.dataDirectory, "preferences.json")
        preferenceStore = MacOSPreferenceStore(prefsFile)

        // 4b. Run idempotent platform migrations before services read state.
        migrationManager = MacOSMigrationManager(
            preferences = preferenceStore,
            storageProvider = storageProvider,
            secretStore = MacOSKeychain(service = "anikku", account = "anikku-app"),
        )
        runBlocking {
            val result = migrationManager.migrate()
            if (!result.success) {
                MacOSLogger.getLogger<AnikkuApplication>()
                    .warn("macOS migration deferred at ${result.failedMigration}")
            }
        }

        // 5. Initialize database driver
        databaseDriver = MacOSDatabaseDriver(storageProvider)

        // 5. Initialize networking (Phase 3.1-3.2)
        networkHelper = MacOSNetworkHelper(storageProvider)
        cookieJar = networkHelper.cookieJar

        // 5.5. Start Koin BEFORE extension loading — extensions use injectLazy (Koin) to
        // resolve NetworkHelper and other dependencies during construction.
        startKoin {
            modules(
                platformModule(this@AnikkuApplication),
                domainModule(this@AnikkuApplication),
                appModule(this@AnikkuApplication),
            )
        }

        // 6. Initialize extension system (Phase 3.3)
        // IMPORTANT: startKoin() must run before this — MacOSExtensionManager.initExtensions()
        // calls MacOSExtensionLoader.loadExtensions() which instantiates extension source
        // classes. Those source classes use injectLazy<> delegates from AnimeHttpSource
        // which resolve via Koin's GlobalContext. Without Koin started first, they throw
        // "KoinApplication has not been started".
        extensionManager = MacOSExtensionManager(storageProvider, networkHelper)

        // 7. Initialize storage manager (Phase 2.1)
        storageManager = MacOSStorageManager(storageProvider)

        // 8. Initialize custom anime repo (Phase 2.2)
        customAnimeRepository = MacOSCustomAnimeRepository(storageProvider.dataDirectory)

        // 8b. Initialize library and history repos
        libraryRepository = LibraryRepository(storageProvider.dataDirectory)
        historyRepository = HistoryRepository(storageProvider.dataDirectory)

        // 8c. Initialize download repository (Phase 5.10)
        downloadRepository = DownloadRepository(storageProvider.dataDirectory)

        // 8d. Initialize backup manager
        backupManager = MacOSBackupManager(
            libraryRepository = libraryRepository,
            historyRepository = historyRepository,
            downloadRepository = downloadRepository,
            preferenceStore = preferenceStore,
            customAnimeRepository = customAnimeRepository,
        )
        libraryUpdateService = MacOSLibraryUpdateService(
            libraryRepository = libraryRepository,
            historyRepository = historyRepository,
            sourceResolver = extensionManager::getSource,
        )

        // 9. Initialize Phase 7: Advanced Features
        // 9a. Discord Rich Presence
        discordRPC = DiscordRPC(applicationScope)

        // 9a.2 Google Drive backups. Tokens remain exclusively in Keychain;
        // session restoration runs off the UI thread below.
        googleDriveService = MacOSGoogleDriveService(
            driveClient = GoogleDriveRestClient(networkHelper.client),
            backupManager = backupManager,
            backupsDirectory = storageProvider.backupsDirectory,
            secretStore = MacOSKeychain(service = "anikku-google-drive", account = "oauth"),
        )
        backgroundScheduler.runOnce("restore-google-drive-session") {
            googleDriveService.restoreSession()
        }
        syncYomiService = MacOSSyncYomiService(
            httpClient = networkHelper.client,
            backupManager = backupManager,
            cacheDirectory = storageProvider.cacheDirectory,
            secretStore = MacOSKeychain(service = "anikku-syncyomi", account = "sync"),
            preferenceStore = preferenceStore,
        )
        backgroundScheduler.runOnce("restore-syncyomi-configuration") {
            syncYomiService.restoreConfiguration()
        }

        // 9b. macOS Notifications
        notificationManager = MacOSNotificationManager()
        notificationManager.initialize()

        backgroundJobs = MacOSBackgroundJobs(
            scheduler = backgroundScheduler,
            backupManager = backupManager,
            automaticBackupsDirectory = File(storageProvider.backupsDirectory, "automatic"),
            libraryUpdateService = libraryUpdateService,
            googleDriveService = googleDriveService,
            notificationManager = notificationManager,
            preferenceStore = preferenceStore,
            syncYomiService = syncYomiService,
        )

        // 9c. Biometric Authentication (Touch ID + PIN fallback)
        biometricAuth = MacOSBiometricAuth(
            secretStore = MacOSKeychain(service = "anikku-security", account = "app-lock"),
        )

        // 9d. App Update Checker (GitHub API)
        appUpdateChecker = AppUpdateChecker(
            currentVersion = "1.0.0",
            repoOwner = "ErnestHysa",
            repoName = "anikku",
        )

        // 9e. Sparkle Updater (wraps AppUpdateChecker as fallback)
        sparkleUpdater = SparkleUpdater(
            appUpdateChecker = appUpdateChecker,
            notificationManager = notificationManager,
        )

        // Initialize Sparkle at startup — must call before any check methods.
        // Sparkle reads SUFeedURL from Info.plist (injected by patchInfoPlist).
        // If Sparkle framework is not bundled (dev builds), this is a no-op
        // and the AppUpdateChecker fallback handles update checks.
        sparkleUpdater.initialize()

        // Schedule silent background update check 30 seconds after startup
        backgroundScheduler.runOnce("startup-update-check") {
            kotlinx.coroutines.delay(30_000) // Wait 30s for app to fully initialize
            sparkleUpdater.checkForUpdatesSilently()
        }

        // 9f. Crash Reporting
        CrashReporter.initialize(
            storageProvider = storageProvider,
            version = "1.0.0",
        )

        // (startKoin moved to step 5.5 — must run before extension loading at step 6)

        MacOSLogger.getLogger<AnikkuApplication>()
            .info("Anikku macOS application initialized")
        CrashReporter.logEvent("App initialization complete")
    }

    /**
     * Deploy bundled extension JARs from the .app bundle to the user's extensions directory.
     *
     * On first launch (or any launch where the extensions directory is empty),
     * this copies pre-converted extension JARs from the application bundle's
     * Resources/libs/ directory so users have working extensions immediately.
     *
     * To avoid duplicate-source conflicts (which cause "Key already used" crashes
     * in BrowseTab), this method compares by [pkgName] from each JAR's
     * `META-INF/extension.json` — NOT by filename. If an extension with the same
     * [pkgName] already exists in the user's extensions directory (installed from
     * a repo or another bundle version), the bundled copy is skipped.
     */
    private fun deployBundledExtensions() {
        val bundledLibsDir = File("../Resources/libs")
        if (!bundledLibsDir.isDirectory) return

        val extensionsDir = storageProvider.extensionsDirectory
        val jarFiles = bundledLibsDir.listFiles()
            ?.filter { it.extension == "jar" && it.name.contains("tachiyomi") }
            ?: emptyList()

        if (jarFiles.isEmpty()) return

        // Collect pkgNames already present in the extensions directory
        val existingPkgNames = extensionsDir.listFiles()
            ?.filter { it.extension == "jar" }
            ?.mapNotNull { jar ->
                MacOSExtensionLoader.readMetadata(jar)?.pkgName
            }
            ?.toSet()
            ?: emptySet()

        // Copy each bundled JAR whose package name is not already installed
        var deployed = 0
        var skipped = 0
        for (jarFile in jarFiles) {
            val metadata = MacOSExtensionLoader.readMetadata(jarFile)
            val pkgName = metadata?.pkgName

            if (pkgName != null && pkgName in existingPkgNames) {
                // Extension with this package name is already installed — skip
                skipped++
                continue
            }

            val targetFile = File(extensionsDir, jarFile.name)
            if (!targetFile.exists()) {
                jarFile.copyTo(targetFile, overwrite = false)
                deployed++
            }
        }

        if (deployed > 0 || skipped > 0) {
            MacOSLogger.getLogger<AnikkuApplication>()
                .info("Deployed $deployed, skipped $skipped bundled extension(s) to ${extensionsDir.absolutePath}")
        }
    }

    /**
     * Called when the main window gains focus.
     * Equivalent to onStart() in Android lifecycle.
     * Triggers Discord RPC reconnection and sync operations.
     */
    fun onAppFocused(enableDiscord: Boolean = true) {
        // Phase 7.3: Discord Rich Presence — connect on app focus
        if (enableDiscord && discordRPC.isDiscordInstalled) {
            discordRPC.start()
        } else if (!enableDiscord) {
            discordRPC.stop()
        }
        backgroundJobs.onAppFocused()
    }

    /**
     * Called when the main window loses focus.
     * Equivalent to onStop() in Android lifecycle.
     * Pauses Discord RPC and saves sync state.
     */
    fun onAppBlurred() {
        // Phase 7.3: Discord Rich Presence — stop the background IPC task on
        // blur. The current activity is retained in memory and restored after
        // the next successful focus-triggered connection.
        discordRPC.stop()
    }

    /**
     * Called when the application is shutting down.
     * Cleans up all Phase 7 services and prints a summary of UI errors to the terminal.
     */
    @Synchronized
    fun onShutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) return

        // Stop new work first, then release resources in dependency order.
        backgroundScheduler.cancelAll()
        runCatching {
            org.koin.core.context.GlobalContext.get().get<app.anikku.macos.platform.download.MacOSDownloadManager>().close()
        }
        extensionManager.close()

        // Phase 3: Release Sparkle updater and persistent Chrome resources.
        sparkleUpdater.shutdown()
        ChromeCDPClient.shutdown()

        // Phase 7.3: Discord RPC
        discordRPC.stop()

        // Phase 7.6: Notifications
        notificationManager.shutdown()
        storageManager.close()
        applicationScope.cancel()

        CrashReporter.logEvent("App shutdown")
        runCatching { stopKoin() }

        // Print a terminal summary of every UI error captured this session.
        TerminalErrorLogger.printShutdownSummary()
    }

}
