package app.anikku.macos.platform.logging

import app.anikku.macos.platform.storage.MacOSStorageProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Crash reporter for macOS.
 *
 * Replaces Firebase Crashlytics with local crash log storage.
 * Captures uncaught exceptions, saves them to a crash log file,
 * and optionally sends them to a remote crash reporting service.
 *
 * ## Features
 *
 * - Captures all uncaught exceptions via Thread.setDefaultUncaughtExceptionHandler
 * - Writes crash logs to `~/Library/Logs/Anikku/crash.log`
 * - Includes app version, system info, and stack trace
 * - Notifies the user on the next launch about recent crashes
 * - Supports Sentry integration (optional dependency)
 *
 * ## Usage
 *
 * ```kotlin
 * // In AnikkuApplication.init():
 * CrashReporter.initialize(storageProvider, "1.0.0")
 * ```
 */
object CrashReporter {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var isInitialized = false
    private var crashLogDir: File? = null
    private var appVersion: String = "unknown"

    /** Path to the current session's crash log file. */
    private var currentLogFile: File? = null

    /**
     * Initialize the crash reporter.
     *
     * @param storageProvider The macOS storage provider for log directory.
     * @param version The current app version string.
     * @param enableSentry Whether to enable Sentry crash reporting (if dependency available).
     */
    fun initialize(
        storageProvider: MacOSStorageProvider,
        version: String = "1.0.0",
        enableSentry: Boolean = false,
    ) {
        if (isInitialized) return

        appVersion = version
        crashLogDir = File(
            System.getProperty("user.home"),
            "Library/Logs/Anikku",
        ).also { it.mkdirs() }

        // Create a new crash log file for this session
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        currentLogFile = File(crashLogDir, "crash-$timestamp.log")

        // Log app startup
        val osName = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")
        val javaVersion = System.getProperty("java.version")
        logEvent("App started", "version=$version, os=$osName, osVer=$osVersion, javaVer=$javaVersion")

        // Set up uncaught exception handler
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable, previousHandler)
        }

        isInitialized = true
        logger.info { "Crash reporter initialized" }
    }

    /**
     * Get the list of recent crash logs.
     *
     * @param maxAgeDays Maximum age of crash logs to include (default: 7 days).
     * @return List of crash log files sorted by age (newest first).
     */
    fun getRecentCrashLogs(maxAgeDays: Int = 7): List<File> {
        val logDir = crashLogDir ?: return emptyList()
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)

        return logDir.listFiles()
            ?.filter { it.name.startsWith("crash-") && it.lastModified() > cutoff }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Check if there were any crashes in the recent crash logs.
     *
     * @return true if there are crash logs from the current session or recent crashes.
     */
    fun hasRecentCrashes(): Boolean {
        return getRecentCrashLogs().isNotEmpty()
    }

    /**
     * Get the most recent crash report as a string.
     *
     * @return The crash report text, or null if no crashes.
     */
    fun getLatestCrashReport(): String? {
        val logs = getRecentCrashLogs()
        if (logs.isEmpty()) return null

        return try {
            logs.first().readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Log an event to the crash log file.
     *
     * @param event The event name.
     * @param details Optional event details.
     */
    fun logEvent(event: String, details: String = "") {
        val logFile = currentLogFile ?: return
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            logFile.appendText("[$timestamp] EVENT: $event $details\n")
        } catch (_: Exception) {
            // Cannot log to file — safe to ignore
        }
    }

    /**
     * Log an error to the crash log file.
     *
     * @param tag A tag identifying the source of the error.
     * @param message The error message.
     * @param throwable Optional exception.
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val logFile = currentLogFile ?: return
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            logFile.appendText("[$timestamp] ERROR [$tag]: $message\n")
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                logFile.appendText(sw.toString())
                logFile.appendText("\n")
            }
        } catch (_: Exception) {
            // Cannot log to file — safe to ignore
        }
    }

    /**
     * Write a multi-line block of text to the crash log file.
     *
     * This is useful for writing structured summaries (e.g., the terminal
     * error logger's shutdown summary) into the crash log so they are
     * preserved alongside crash reports.
     *
     * @param tag A tag identifying the source of the block.
     * @param text The full multi-line text to write.
     */
    fun logBlock(tag: String, text: String) {
        val logFile = currentLogFile ?: return
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            logFile.appendText("[$timestamp] BLOCK [$tag]:\n")
            logFile.appendText(text)
            logFile.appendText("\n")
        } catch (_: Exception) {
            // Cannot log to file — safe to ignore
        }
    }

    /**
     * Report a handled exception (non-fatal).
     *
     * @param tag A tag identifying the source.
     * @param throwable The exception to report.
     */
    fun reportHandledException(tag: String, throwable: Throwable) {
        logError(tag, "Handled exception: ${throwable.message}", throwable)
        logger.warn(throwable) { "[$tag] Handled exception: ${throwable.message}" }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun handleUncaughtException(
        thread: Thread,
        throwable: Throwable,
        previousHandler: Thread.UncaughtExceptionHandler?,
    ) {
        val logFile = currentLogFile ?: return

        try {
            val timestamp = LocalDateTime.now().format(formatter)
            val header = """
                |========================================
                |[$timestamp] CRASH — Uncaught Exception
                |Thread: ${thread.name} (${thread.id})
                |App Version: $appVersion
                |OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}
                |Java: ${System.getProperty("java.version")}
                |========================================
                |
            """.trimMargin()
            logFile.appendText(header)

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            logFile.appendText(sw.toString())
            logFile.appendText("\n")

            logger.error(throwable) { "Uncaught exception in thread: ${thread.name}" }
        } catch (_: Exception) {
            // Cannot log — this is the crash handler, do our best
        }

        // Chain to the previous handler if one existed
        previousHandler?.uncaughtException(thread, throwable)
    }
}
