package app.anikku.macos.platform.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Comprehensive UI action and state logger.
 *
 * Logs ALL user interactions (clicks, navigation, screen transitions, state changes,
 * errors, extension loading, player events, network requests) to:
 * 1. SLF4J logger (console + rolling file via Logback)
 * 2. A dedicated JSON action log at `~/Library/Application Support/Anikku/logs/actions.log`
 *
 * ## Enabling
 * Called automatically at app startup. Set log level:
 * - Default: INFO (errors, state transitions, major actions)
 * - Verbose: DEBUG (all clicks, movements, minor state changes)
 * - Firehose: TRACE (every property change, every event)
 *
 * ## Usage
 * ```kotlin
 * UIActionLogger.logClick("EpisodeItem", "episode_5", "Play button")
 * UIActionLogger.logNavigation("BrowseTab", "ExtensionDetail")
 * UIActionLogger.logError("VideoLoad", "Failed to load URL", exception)
 * ```
 */
object UIActionLogger {

    private val logger = KotlinLogging.logger {}
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /** Dedicated action log file. */
    private var actionLogFile: File? = null

    /** Whether TRACE-level logging is enabled (for mouse positions etc.). */
    var logMousePositions: Boolean = false
        private set

    /**
     * Initialize the action logger.
     * Creates the action log file and sets verbosity level.
     *
     * @param logDir Directory for the action log file (typically storageProvider.logsDirectory).
     * @param verboseLevel 0=INFO, 1=DEBUG (includes navigation/clicks), 2=TRACE (includes everything)
     */
    fun initialize(logDir: File, verboseLevel: Int = 1) {
        logDir.mkdirs()
        actionLogFile = File(logDir, "actions.log")
        logMousePositions = verboseLevel >= 2

        // Write header
        val timestamp = LocalDateTime.now().format(formatter)
        appendToFile("""
            |========================================
            |[$timestamp] UI ACTION LOG STARTED (level=$verboseLevel)
            |OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}
            |Java: ${System.getProperty("java.version")}
            |========================================
        """.trimMargin())

        logger.info { "📋 UI Action Logger initialized (level=$verboseLevel, file=${actionLogFile?.absolutePath})" }
    }

    /** Log a user click/tap on a UI element. */
    fun logClick(
        screen: String,
        element: String,
        action: String = "click",
        details: String? = null,
    ) {
        val msg = "🖱️ CLICK [$screen] $element — $action${details?.let { ": $it" } ?: ""}"
        logger.info { msg }
        logToFile("CLICK", screen, mapOf("element" to element, "action" to action, "details" to details))
    }

    /** Log a navigation event between screens. */
    fun logNavigation(from: String, to: String, details: String? = null) {
        val msg = "🧭 NAVIGATE $from → $to${details?.let { " ($it)" } ?: ""}"
        logger.info { msg }
        logToFile("NAVIGATE", from, mapOf("to" to to, "details" to details))
    }

    /** Log a screen being opened. */
    fun logScreenOpen(screen: String, params: Map<String, Any?>? = null) {
        val paramStr = params?.let { p -> p.entries.joinToString(", ") { "${it.key}=${it.value}" } }
        val msg = "📺 SCREEN [$screen]${paramStr?.let { " [$it]" } ?: ""}"
        logger.info { msg }
        logToFile("SCREEN", screen, params?.mapValues { it.value.toString() })
    }

    /** Log a player state change (play, pause, seek, load, end, error). */
    fun logPlayerState(episode: String, state: String, details: String? = null) {
        val msg = "🎬 PLAYER [$episode] → $state${details?.let { " ($it)" } ?: ""}"
        logger.info { msg }
        logToFile("PLAYER", state, mapOf("episode" to episode, "details" to details))
    }

    /** Log a network request or response (URL, method, status code). */
    fun logNetwork(method: String, url: String, statusCode: Int? = null, durationMs: Long? = null) {
        val statusStr = statusCode?.let { " → $it" } ?: ""
        val durStr = durationMs?.let { " (${it}ms)" } ?: ""
        logger.debug { "🌐 $method $url$statusStr$durStr" }
        logToFile("NETWORK", "$method $url", mapOf(
            "method" to method,
            "url" to url,
            "statusCode" to statusCode?.toString(),
            "durationMs" to durationMs?.toString(),
        ))
    }

    /** Log an extension event (loading, fetching, parsing). */
    fun logExtension(extName: String, action: String, details: String? = null) {
        val msg = "🔌 EXTENSION [$extName] $action${details?.let { " ($it)" } ?: ""}"
        logger.info { msg }
        logToFile("EXTENSION", action, mapOf("extName" to extName, "details" to details))
    }

    /** Log a mouse position (only if TRACE logging enabled). */
    fun logMousePosition(x: Int, y: Int, screen: String) {
        if (!logMousePositions) return
        logger.trace { "🖱️ MOUSE [$screen] position=($x, $y)" }
        // Don't write mouse positions to the action log file (too verbose)
    }

    /** Log a generic debug message. */
    fun logDebug(tag: String, message: String) {
        logger.debug { "🔍 DEBUG [$tag] $message" }
        logToFile("DEBUG", tag, mapOf("message" to message))
    }

    /** Log an error with optional exception. */
    fun logError(tag: String, message: String, exception: Throwable? = null) {
        if (exception != null) {
            logger.error(exception) { "❌ ERROR [$tag] $message" }
        } else {
            logger.error { "❌ ERROR [$tag] $message" }
        }
        logToFile("ERROR", tag, mapOf("message" to message, "exception" to exception?.toString()))
        CrashReporter.logError(tag, message, exception)
    }

    /** Log a warning. */
    fun logWarning(tag: String, message: String) {
        logger.warn { "⚠️ WARN [$tag] $message" }
        logToFile("WARN", tag, mapOf("message" to message))
    }

    /** Log a video URL resolution attempt. */
    fun logVideoResolution(sourceId: Long?, episodeUrl: String?, result: String?) {
        val msg = "🎥 VIDEO_RESOLVE source=$sourceId url=$episodeUrl → $result"
        logger.info { msg }
        logToFile("VIDEO_RESOLVE", result ?: "null", mapOf(
            "sourceId" to sourceId?.toString(),
            "episodeUrl" to episodeUrl,
        ))
    }

    /** Log when resolving video URLs for each hoster. */
    fun logHosterVideo(hosterName: String, videoCount: Int, firstUrl: String?) {
        val msg = "🎥 HOSTER [$hosterName] → $videoCount videos, first=$firstUrl"
        logger.debug { msg }
        logToFile("HOSTER", hosterName, mapOf(
            "videoCount" to videoCount.toString(),
            "firstUrl" to firstUrl,
        ))
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun logToFile(type: String, target: String, data: Map<String, String?>?) {
        val file = actionLogFile ?: return
        val timestamp = LocalDateTime.now().format(formatter)
        val dataStr = data?.entries
            ?.filter { it.value != null }
            ?.joinToString(", ") { "${it.key}=${it.value}" }
            ?: ""
        val line = "[$timestamp] [$type] [$target] $dataStr"
        try {
            file.appendText(line + "\n")
        } catch (_: Exception) {
            // Cannot write to action log — safe to ignore
        }
    }

    private fun appendToFile(text: String) {
        val file = actionLogFile ?: return
        try {
            file.appendText(text + "\n")
        } catch (_: Exception) { }
    }
}
