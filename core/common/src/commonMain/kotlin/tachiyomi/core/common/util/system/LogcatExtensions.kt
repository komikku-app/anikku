package tachiyomi.core.common.util.system

import java.util.logging.Level
import java.util.logging.Logger

enum class LogPriority(val level: Level) {
    VERBOSE(Level.FINEST),
    DEBUG(Level.FINE),
    INFO(Level.INFO),
    WARN(Level.WARNING),
    ERROR(Level.SEVERE),
    ASSERT(Level.SEVERE),
}

inline fun Any.logcat(
    priority: LogPriority = LogPriority.DEBUG,
    throwable: Throwable? = null,
    tag: String? = null,
    message: () -> String = { "" },
) {
    val loggerName = tag ?: this::class.qualifiedName ?: "Unknown"
    val logger = Logger.getLogger(loggerName)

    val msg = message()
    if (msg.isBlank() && throwable == null) return

    when {
        throwable != null -> logger.log(priority.level, msg, throwable)
        else -> logger.log(priority.level, msg)
    }
}
