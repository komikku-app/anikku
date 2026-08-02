package app.anikku.macos.platform.update

import app.anikku.macos.platform.notification.MacOSNotificationManager
import app.anikku.macos.platform.notification.NotificationType
import com.sun.jna.Library
import com.sun.jna.Native
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * JNA interface to the Swift Sparkle helper dylib.
 *
 * Compiled from src/main/swift/SparkleHelper.swift via
 * scripts/build-sparkle-helper.sh.
 */
interface SparkleHelperLib : Library {
    /** Initialize Sparkle with an optional feed URL (null = read from Info.plist). */
    fun sparkle_init(feedURL: String?): Boolean

    /** Show the standard Sparkle update dialog. */
    fun sparkle_checkForUpdates()

    /** Silent background check with notification if update found. */
    fun sparkle_checkInBackground()

    /** Release Sparkle's native updater controller. */
    fun sparkle_shutdown()

    /** Return the configured feed URL, or null if not initialized. */
    fun sparkle_feedURL(): String?
}

/**
 * Sparkle 2 auto-updater integration for macOS.
 *
 * ## Architecture
 *
 * [Sparkle](https://sparkle-project.org/) is the standard macOS auto-update
 * framework. At app startup, we load the Swift helper dylib via JNA which
 * initializes Sparkle's SPUStandardUpdaterController.
 *
 * When Sparkle is available:
 * - Updates are checked automatically on a schedule (daily by default)
 * - "Check for Updates" shows the native Sparkle dialog
 * - Updates download in the background and install on app relaunch
 *
 * When Sparkle is NOT available (dev builds, missing framework):
 * - Falls back to [AppUpdateChecker] which queries the GitHub Releases API
 * - "Check for Updates" opens the browser to the download page
 *
 * ## Bundling
 *
 * Sparkle.framework and libSparkleHelper.dylib are bundled in the .app:
 * ```
 * Anikku.app/Contents/Frameworks/
 *   Sparkle.framework/
 *   libSparkleHelper.dylib
 * ```
 *
 * The Gradle build runs scripts/build-sparkle-helper.sh which:
 * 1. Downloads Sparkle 2.x from GitHub Releases
 * 2. Compiles the Swift helper against it
 * 3. Copies both to src/main/resources/dist/Frameworks/
 *
 * @param appUpdateChecker Fallback update checker when Sparkle is unavailable.
 * @param notificationManager Optional notification manager for showing update alerts.
 */
class SparkleUpdater(
    private val appUpdateChecker: AppUpdateChecker? = null,
    private val notificationManager: MacOSNotificationManager? = null,
) {

    /** The JNA-loaded Sparkle helper dylib, or null if unavailable. */
    private val sparkleLib: SparkleHelperLib? = loadSparkleHelper()

    /** True only after Sparkle accepted a validated HTTPS feed configuration. */
    @Volatile
    private var initialized = false

    /**
     * Whether we are running inside a packaged macOS .app bundle.
     *
     * When running via `./gradlew run` (dev mode), there is no .app bundle and
     * thus no Info.plist with SUFeedURL. Sparkle would fail to find the feed
     * URL and show a native error dialog. We detect this by checking if the
     * working directory or executable path contains ".app".
     */
    private val isPackagedApp: Boolean by lazy {
        val cwd = System.getProperty("user.dir", "")
        val classPath = System.getProperty("java.class.path", "")
        // When running from a packaged .app, the working directory is
        // Anikku.app/Contents/MacOS/ or Anikku.app/Contents/Resources/.
        // When running via Gradle (./gradlew run), it's the project root.
        cwd.contains(".app") || classPath.contains(".app")
    }

    /** Whether the Sparkle helper library is available and loaded. */
    val isAvailable: Boolean get() = sparkleLib != null && isPackagedApp && initialized

    // ---- Initialization ----

    /**
     * Initialize Sparkle at app startup.
     * Must be called once before any other Sparkle methods.
     *
     * In dev builds (running via `./gradlew run`), this is a no-op because
     * Sparkle requires a properly packaged .app bundle with Info.plist
     * containing SUFeedURL. The AppUpdateChecker fallback handles updates.
     *
     * @param feedURL The appcast feed URL. If null, Sparkle reads SUFeedURL
     *                from the app's Info.plist (configured by the packaging DSL).
     */
    fun initialize(feedURL: String? = null) {
        if (feedURL != null && !isHttpsUrl(feedURL)) {
            logger.error { "Refusing to initialize Sparkle with a non-HTTPS feed URL" }
            return
        }
        if (!isPackagedApp) {
            logger.info { "Not running from .app bundle — Sparkle initialization skipped (dev mode). " +
                "Using AppUpdateChecker fallback for update checks." }
            return
        }

        val lib = sparkleLib
        if (lib != null) {
            logger.info { "Initializing Sparkle (${if (feedURL == null) "feed from Info.plist" else "explicit feed configured"})" }
            try {
                val ok = lib.sparkle_init(feedURL)
                if (ok && isHttpsUrl(lib.sparkle_feedURL().orEmpty())) {
                    initialized = true
                    logger.info { "Sparkle initialized successfully with a validated HTTPS feed" }
                } else {
                    logger.warn { "Sparkle initialization returned false" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Sparkle initialization failed" }
            } catch (e: UnsatisfiedLinkError) {
                logger.error(e) { "Sparkle helper is missing a required symbol" }
            }
        } else {
            logger.info { "Sparkle not available — using AppUpdateChecker fallback" }
        }
    }

    // ---- Update Checks ----

    /**
     * Check for updates silently in the background.
     *
     * If Sparkle is available and running in a packaged .app, triggers a
     * background check that shows a notification when an update is found.
     * Otherwise, delegates to [AppUpdateChecker.checkForUpdate] and shows a
     * macOS notification when an update is available.
     */
    fun checkForUpdatesSilently() {
        val lib = sparkleLib
        if (lib != null && isPackagedApp && initialized) {
            logger.info { "Sparkle: starting background update check" }
            try {
                lib.sparkle_checkInBackground()
            } catch (e: Exception) {
                logger.error(e) { "Sparkle background check failed" }
                fallbackCheck()
            }
        } else {
            fallbackCheck()
        }
    }

    /**
     * Check for updates and display the update dialog.
     *
     * If Sparkle is available and running in a packaged .app, opens the
     * native Sparkle update dialog. Otherwise, delegates to
     * [AppUpdateChecker.checkAndPrompt] which opens the browser to the
     * download page.
     *
     * @return true if the check was initiated.
     */
    fun checkForUpdatesWithUI(): Boolean {
        val lib = sparkleLib
        if (lib != null && isPackagedApp && initialized) {
            logger.info { "Sparkle: showing update dialog" }
            try {
                lib.sparkle_checkForUpdates()
            } catch (e: Exception) {
                logger.error(e) { "Sparkle UI check failed" }
                return appUpdateChecker?.let {
                    it.checkAndPrompt()
                    true
                } ?: false
            }
            return true
        }

        logger.info { "Sparkle not available in dev mode — using AppUpdateChecker fallback" }
        if (appUpdateChecker != null) {
            appUpdateChecker.checkAndPrompt()
            return true
        } else {
            logger.warn { "No update checker available — cannot check for updates" }
            return false
        }
    }

    /** Release native updater resources when the application shuts down. */
    fun shutdown() {
        val shouldShutdownNativeHelper = initialized && isPackagedApp
        initialized = false
        if (!shouldShutdownNativeHelper) return
        try {
            sparkleLib?.sparkle_shutdown()
        } catch (e: Exception) {
            logger.warn(e) { "Sparkle shutdown failed" }
        } catch (e: UnsatisfiedLinkError) {
            // A helper built before the lifecycle symbol was added may still be
            // present in a development bundle; shutdown must remain best-effort.
            logger.warn(e) { "Sparkle helper does not support shutdown" }
        }
    }

    /**
     * Get the configured Sparkle feed URL.
     */
    fun getFeedURL(): String? {
        val lib = sparkleLib
        if (lib != null) {
            return try {
                lib.sparkle_feedURL()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to read Sparkle feed URL" }
                null
            }
        }
        return "https://raw.githubusercontent.com/ErnestHysa/anikku/master/macos/src/main/resources/Sparkle/appcast.xml"
    }

    // ---- Private helpers ----

    /**
     * Fallback update check using the GitHub API.
     */
    private fun isHttpsUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    } catch (_: Exception) { false }

    private fun fallbackCheck() {
        logger.info { "Using AppUpdateChecker fallback for update check" }
        appUpdateChecker?.checkForUpdate { result ->
            when (result) {
                is app.anikku.macos.platform.update.UpdateCheckResult.Available -> {
                    val update = result.update
                    logger.info { "Update available: ${update.versionName}" }
                    notificationManager?.showNotification(
                        title = "Update Available",
                        message = "Anikku ${update.versionName} is ready to download",
                        type = NotificationType.INFO,
                        onClick = { appUpdateChecker.openReleasePage(update) },
                    )
                }
                app.anikku.macos.platform.update.UpdateCheckResult.NoUpdate ->
                    logger.info { "App is up to date" }
                app.anikku.macos.platform.update.UpdateCheckResult.SparkleDialogOpened ->
                    logger.debug { "Sparkle update dialog is already open" }
                is app.anikku.macos.platform.update.UpdateCheckResult.Failed ->
                    logger.warn { "Fallback update check failed: ${result.reason}" }
            }
        }
    }

    companion object {
        /**
         * Attempt to load the Sparkle helper dylib via JNA.
         *
         * Search order:
         * 1. Anikku.app/Contents/Frameworks/libSparkleHelper.dylib (packaged)
         * 2. build/sparkle/libSparkleHelper.dylib (development)
         * 3. System library path
         */
        private fun loadSparkleHelper(): SparkleHelperLib? {
            val libName = "SparkleHelper"

            // Packaged builds expose the helper through java.library.path.
            return try {
                Native.load(libName, SparkleHelperLib::class.java).also {
                    logger.info { "Sparkle helper dylib loaded via JNA" }
                }
            } catch (e: UnsatisfiedLinkError) {
                // Try explicit path for development (build/sparkle/)
                try {
                    val devPath = findDevDylibPath()
                    if (devPath != null) {
                        Native.load(devPath, SparkleHelperLib::class.java).also {
                            logger.info { "Sparkle helper dylib loaded from: $devPath" }
                        }
                    } else {
                        logger.info { "Sparkle helper dylib not available — updates via GitHub API" }
                        null
                    }
                } catch (e2: UnsatisfiedLinkError) {
                    logger.info { "Sparkle helper dylib not available — updates via GitHub API" }
                    null
                }
            }
        }

        /**
         * Find the development build path for the Sparkle helper dylib.
         */
        private fun findDevDylibPath(): String? {
            val cwd = System.getProperty("user.dir", ".")
            val packagedResources = System.getProperty("compose.application.resources.dir")
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
            val candidates = listOfNotNull(
                packagedResources?.resolve("Frameworks/libSparkleHelper.dylib"),
                File(cwd, "build/sparkle/libSparkleHelper.dylib"),
                File(cwd, "macos/build/sparkle/libSparkleHelper.dylib"),
                File(cwd, "../build/sparkle/libSparkleHelper.dylib"),
                File(cwd, "src/main/resources/dist/Frameworks/libSparkleHelper.dylib"),
                File(cwd, "macos/src/main/resources/dist/Frameworks/libSparkleHelper.dylib"),
            )
            return candidates.firstOrNull(File::isFile)?.absolutePath
        }
    }
}
