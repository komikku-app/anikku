package app.anikku.macos.platform.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * Central registry for errors that are surfaced to the user through UI pop-ins
 * (toasts, error banners, alert dialogs) in the macOS port.
 *
 * Every logged error is:
 * 1. Emitted to the terminal immediately so it can be copy-pasted.
 * 2. Stored with metadata (id, timestamp, message, source/extension, stack trace).
 * 3. Summarised on application shutdown.
 *
 * ## Usage
 *
 * ```kotlin
 * // From a screen showing an error toast
 * TerminalErrorLogger.logUiError(
 *     message = "Failed to fetch episodes: ...",
 *     source = sourceName,
 *     throwable = exception,
 *     location = "PlayerScreen.fetchEpisodes",
 * )
 * ```
 */
object TerminalErrorLogger {

    private val logger = KotlinLogging.logger {}

    /** Formatter for ISO-8601-like timestamps in the summary. */
    private val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /** Monotonically increasing error id. */
    private val nextId = AtomicLong(1L)

    /** Thread-safe list of errors captured this session. */
    private val _errors = Collections.synchronizedList(mutableListOf<LoggedUiError>())

    /** Maximum number of errors to keep in memory to avoid unbounded growth. */
    private const val MAX_RETAINED_ERRORS = 100

    /** Read-only view of captured errors. */
    val errors: List<LoggedUiError>
        get() = synchronized(_errors) { _errors.toList() }

    /** Total number of errors captured this session. */
    val errorCount: Int
        get() = _errors.size

    /**
     * Represents a single UI error that was shown to the user.
     *
     * @property id Unique sequential id for this error.
     * @property timestamp When the error was captured.
     * @property message The human-readable error message shown in the UI.
     * @property source Optional source/extension that caused the error.
     * @property throwable Optional underlying exception.
     * @property stackTrace Optional stack trace of the exception.
     * @property location Optional code location where the error was reported.
     */
    data class LoggedUiError(
        val id: Long,
        val timestamp: Instant,
        val message: String,
        val source: String?,
        val throwable: Throwable?,
        val stackTrace: String?,
        val location: String?,
    )

    /**
     * Log an error that is being shown to the user in a UI pop-in.
     *
     * The error is printed to the terminal immediately and stored for the
     * shutdown summary.
     *
     * @param message The error message shown in the UI.
     * @param source Optional source/extension that caused the error (e.g. extension name/id).
     * @param throwable Optional underlying exception.
     * @param location Optional code location (e.g. "PlayerScreen.loadEpisode").
     */
    @JvmOverloads
    fun logUiError(
        message: String,
        source: String? = null,
        throwable: Throwable? = null,
        location: String? = null,
    ) {
        val id = nextId.getAndIncrement()
        val stackTrace = throwable?.stackTraceToString()
        val error = LoggedUiError(
            id = id,
            timestamp = Instant.now(),
            message = message,
            source = source?.takeIf { it.isNotBlank() },
            throwable = throwable,
            stackTrace = stackTrace,
            location = location?.takeIf { it.isNotBlank() },
        )
        _errors.add(error)

        // Prevent unbounded memory growth in long-running sessions.
        if (_errors.size > MAX_RETAINED_ERRORS) {
            _errors.removeAt(0)
        }

        // Immediate terminal output so the user can copy-paste it.
        printErrorToTerminal(error)

        // Also route through the existing action/crash loggers for persistence.
        UIActionLogger.logError("UI_ERROR", formatForActionLog(error), throwable)
        CrashReporter.logError("UI_ERROR", formatForActionLog(error), throwable)
    }

    /**
     * Print a formatted shutdown summary of all captured UI errors.
     * This should be called once when the application is closing.
     *
     * The summary is written to both the terminal (for copy-paste) and
     * the crash log file (for persistence alongside crash reports).
     *
     * Repeated errors with the same message and source are grouped together and
     * shown with an occurrence count, so the summary stays readable even when the
     * same failure happens many times.
     */
    fun printShutdownSummary() {
        val snapshot = synchronized(_errors) { _errors.toList() }

        val header = buildString {
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════════════╗")
            appendLine("║                    ANIKKU MACOS — UI ERROR SUMMARY                           ║")
            appendLine("╠══════════════════════════════════════════════════════════════════════════════╣")
        }

        val footer = buildString {
            appendLine("╚══════════════════════════════════════════════════════════════════════════════╝")
        }

        val body = if (snapshot.isEmpty()) {
            buildString {
                appendLine("║ No UI errors were captured this session.                                     ║")
            }
        } else {
            val groups = groupErrors(snapshot)
            buildString {
                appendLine("║ Total UI errors captured: ${snapshot.size.toString().padEnd(54)} ║")
                appendLine("║ Unique error groups:      ${groups.size.toString().padEnd(54)} ║")
                appendLine("╠══════════════════════════════════════════════════════════════════════════════╣")
                groups.forEachIndexed { index, group ->
                    appendLine(formatErrorGroupForSummary(group, index + 1))
                    if (index < groups.lastIndex) {
                        appendLine("║ ─────────────────────────────────────────────────────────────────────────── ║")
                    }
                }
            }
        }

        val terminalOutput = header + body + footer
        println(terminalOutput)

        // Also persist the summary in the crash log file so it survives
        // terminal closure and can be reviewed alongside crash reports.
        CrashReporter.logBlock("UI_ERROR_SUMMARY", terminalOutput)

        logger.info { "UI error shutdown summary printed (count=${snapshot.size})" }
    }

    /**
     * Clear all captured errors. Mainly useful for tests.
     */
    fun clear() {
        _errors.clear()
        nextId.set(1L)
    }

    // -------------------------------------------------------------------------
    // Internal formatting
    // -------------------------------------------------------------------------

    private fun printErrorToTerminal(error: LoggedUiError) {
        val lines = mutableListOf<String>()
        lines.add("╔══════════════════════════════════════════════════════════════════════════════╗")
        lines.add("║ UI ERROR #${error.id}".padEnd(78) + " ║")
        lines.add("╠══════════════════════════════════════════════════════════════════════════════╣")
        lines.add("║ Timestamp: ${formatTimestamp(error.timestamp)}".padEnd(78) + " ║")
        lines.add("║ Message:   ${error.message}".padEnd(78) + " ║")
        error.source?.let { lines.add("║ Source:    $it".padEnd(78) + " ║") }
        error.location?.let { lines.add("║ Location:  $it".padEnd(78) + " ║") }
        error.throwable?.let { lines.add("║ Exception: ${it.javaClass.simpleName}: ${it.message}".padEnd(78) + " ║") }
        lines.add("╚══════════════════════════════════════════════════════════════════════════════╝")

        // Print as a single block to avoid interleaving with other logs.
        println(lines.joinToString("\n"))

        // Also print the full stack trace if available, so it can be copy-pasted.
        error.stackTrace?.let { println(it) }
    }

    private fun formatErrorForSummary(error: LoggedUiError, displayIndex: Int): String {
        return buildString {
            appendLine("║ #${displayIndex} — UI Error #${error.id}".padEnd(78) + " ║")
            appendLine("║   Timestamp: ${formatTimestamp(error.timestamp)}".padEnd(78) + " ║")
            appendLine("║   Issue:     ${error.message}".padEnd(78) + " ║")
            error.source?.let {
                appendLine("║   Source:    $it".padEnd(78) + " ║")
            } ?: appendLine("║   Source:    <unknown>".padEnd(78) + " ║")
            error.location?.let {
                appendLine("║   Location:  $it".padEnd(78) + " ║")
            }
            error.throwable?.let {
                appendLine("║   Exception: ${it.javaClass.simpleName}: ${it.message}".padEnd(78) + " ║")
            }
        }
    }

    /**
     * Group repeated errors by message and source so the shutdown summary
     * doesn't flood the terminal with identical entries.
     */
    private fun groupErrors(errors: List<LoggedUiError>): List<ErrorGroup> {
        return errors
            .groupBy { Pair(it.message, it.source) }
            .values
            .map { group ->
                ErrorGroup(
                    representative = group.first(),
                    count = group.size,
                    firstTimestamp = group.minOf { it.timestamp },
                    lastTimestamp = group.maxOf { it.timestamp },
                )
            }
            .sortedBy { it.representative.id }
    }

    private fun formatErrorGroupForSummary(group: ErrorGroup, displayIndex: Int): String {
        val error = group.representative
        return buildString {
            appendLine("║ #${displayIndex} — UI Error #${error.id}".padEnd(78) + " ║")
            appendLine("║   Occurrences: ${group.count}".padEnd(78) + " ║")
            appendLine("║   First seen:  ${formatTimestamp(group.firstTimestamp)}".padEnd(78) + " ║")
            appendLine("║   Last seen:   ${formatTimestamp(group.lastTimestamp)}".padEnd(78) + " ║")
            appendLine("║   Issue:       ${error.message}".padEnd(78) + " ║")
            error.source?.let {
                appendLine("║   Source:      $it".padEnd(78) + " ║")
            } ?: appendLine("║   Source:      <unknown>".padEnd(78) + " ║")
            error.location?.let {
                appendLine("║   Location:    $it".padEnd(78) + " ║")
            }
            error.throwable?.let {
                appendLine("║   Exception:   ${it.javaClass.simpleName}: ${it.message}".padEnd(78) + " ║")
            }
        }
    }

    /**
     * A group of identical UI errors (same message and source).
     */
    private data class ErrorGroup(
        val representative: LoggedUiError,
        val count: Int,
        val firstTimestamp: Instant,
        val lastTimestamp: Instant,
    )

    private fun formatForActionLog(error: LoggedUiError): String {
        return buildString {
            append("UI_ERROR #${error.id}: ${error.message}")
            error.source?.let { append(" | source=$it") }
            error.location?.let { append(" | location=$it") }
        }
    }

    private fun formatTimestamp(instant: Instant): String {
        return formatter.format(instant)
    }

}
