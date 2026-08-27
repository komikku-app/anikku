package app.anikku.macos.platform.logging

import app.anikku.macos.platform.storage.MacOSStorageProvider
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.slf4j.LoggerFactory

/**
 * macOS logger configuration using SLF4J + Logback.
 * Matches the existing Android logging levels (EHLogLevel) where applicable.
 *
 * Two appenders:
 * - Console: logs to stdout
 * - Rolling file: logs to ~/Library/Application Support/Anikku/logs/anikku.log
 */
object MacOSLogger {

    fun initialize(storageProvider: MacOSStorageProvider, verboseLevel: Int = 2) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext

        // Console encoder — includes file/line info at higher verbosity levels
        val consolePattern = if (verboseLevel >= 1) {
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36}:%line - %msg%n"
        } else {
            "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
        }

        val encoder = PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = consolePattern
        encoder.start()

        // File encoder — always includes file/line info
        val fileEncoder = PatternLayoutEncoder()
        fileEncoder.context = context
        fileEncoder.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36}:%line - %msg%n"
        fileEncoder.start()

        // Console appender
        val consoleAppender = ConsoleAppender<ILoggingEvent>()
        consoleAppender.context = context
        consoleAppender.encoder = encoder
        consoleAppender.start()

        // File appender
        storageProvider.logsDirectory.mkdirs()
        val fileAppender = RollingFileAppender<ILoggingEvent>()
        fileAppender.context = context
        fileAppender.file = "${storageProvider.logsDirectory.absolutePath}/anikku.log"
        fileAppender.encoder = fileEncoder

        val rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>()
        rollingPolicy.context = context
        rollingPolicy.fileNamePattern = "${storageProvider.logsDirectory.absolutePath}/anikku.%d{yyyy-MM-dd}.log"
        rollingPolicy.maxHistory = 30  // Keep 30 days of logs for debugging
        rollingPolicy.setParent(fileAppender)
        rollingPolicy.start()

        fileAppender.rollingPolicy = rollingPolicy
        fileAppender.start()

        // Configure root logger
        val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        rootLogger.level = if (verboseLevel >= 2) Level.DEBUG else Level.INFO
        rootLogger.addAppender(consoleAppender)
        rootLogger.addAppender(fileAppender)

        // Configure app-specific logger (all anikku packages at DEBUG)
        val appLogger = context.getLogger("app.anikku") as ch.qos.logback.classic.Logger
        appLogger.level = when {
            verboseLevel >= 2 -> Level.DEBUG
            verboseLevel >= 1 -> Level.INFO
            else -> Level.WARN
        }
        appLogger.isAdditive = false
        appLogger.addAppender(consoleAppender)
        appLogger.addAppender(fileAppender)

        // Also enable debug logging for extension-related packages (for root cause analysis)
        val extensionLogger = context.getLogger("eu.kanade") as ch.qos.logback.classic.Logger
        extensionLogger.level = Level.DEBUG
        extensionLogger.isAdditive = true

        val initLogger = LoggerFactory.getLogger(MacOSLogger::class.java)
        initLogger.debug("=== MacOSLogger initialized (verboseLevel={}, logDir={}) ===", verboseLevel, storageProvider.logsDirectory.absolutePath)
        initLogger.info("📝 LOGGING: Console+File enabled at level={}. Log file: {}", appLogger.level, "${storageProvider.logsDirectory.absolutePath}/anikku.log")
    }

    fun <T : Any> getLogger(clazz: Class<T>): org.slf4j.Logger {
        return LoggerFactory.getLogger(clazz)
    }

    inline fun <reified T : Any> getLogger(): org.slf4j.Logger {
        return LoggerFactory.getLogger(T::class.java)
    }
}
