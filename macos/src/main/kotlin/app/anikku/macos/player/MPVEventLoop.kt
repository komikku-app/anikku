package app.anikku.macos.player

import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

/**
 * Dedicated coroutine-based event loop for processing mpv events.
 *
 * Polls mpv's event queue in a background coroutine and emits events
 * to subscribers via [events] Flow.
 *
 * ## Usage
 *
 * ```kotlin
 * val eventLoop = MPVEventLoop(mpvHandle)
 * eventLoop.start(scope)
 *
 * // Observe events
 * eventLoop.events.collect { event ->
 *     when (event.eventId) {
 *         MPVLib.MPV_EVENT_FILE_LOADED -> onFileLoaded()
 *         MPVLib.MPV_EVENT_PLAYBACK_RESTART -> onPlaybackStarted()
 *         MPVLib.MPV_EVENT_PROPERTY_CHANGE -> onPropertyChanged(event)
 *     }
 * }
 * ```
 *
 * ## Property observation
 *
 * Before starting the loop, register properties to observe:
 * ```kotlin
 * eventLoop.observeProperty("time-pos")
 * eventLoop.observeProperty("duration")
 * eventLoop.observeProperty("pause")
 * ```
 */
class MPVEventLoop(
    private val mpvHandle: Pointer,
    private val waitForEvent: (Pointer, Double) -> MPVEvent? = { handle, timeout ->
        MPVLib.waitEvent(handle, timeout)
    },
    private val observe: (Pointer, Long, String, Int) -> Int = { handle, id, name, format ->
        MPVLib.observeProperty(handle, id, name, format)
    },
    private val unobserve: (Pointer, Long) -> Int = { handle, id ->
        MPVLib.unobserveProperty(handle, id)
    },
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var eventJob: Job? = null

    private val _events = MutableSharedFlow<MPVEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _propertyChanges = MutableSharedFlow<PropertyChange>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** All mpv events emitted as a hot Flow. */
    val events: Flow<MPVEvent> = _events.asSharedFlow()

    /** Property changes (from observed properties) emitted as a hot Flow. */
    val propertyChanges: Flow<PropertyChange> = _propertyChanges.asSharedFlow()

    private val _errors = MutableSharedFlow<Throwable>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Recoverable polling/JNA failures observed while the loop continues. */
    val errors: Flow<Throwable> = _errors.asSharedFlow()

    /** Whether the event loop is currently running. */
    var isRunning: Boolean = false
        private set

    /**
     * Start the event loop. Observes the registered properties and
     * begins polling for events.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        eventJob = scope.launch {
            logger.info { "MPV event loop started" }
            var consecutiveFailures = 0
            while (isActive && isRunning) {
                try {
                    val event = waitForEvent(mpvHandle, 0.05) // 50ms timeout
                    consecutiveFailures = 0
                    if (event != null) {
                        processEvent(event)
                    }
                } catch (e: VirtualMachineError) {
                    // Don't swallow fatal JVM errors (OutOfMemoryError, etc.)
                    throw e
                } catch (e: Throwable) {
                    // Catch Throwable (not just Exception) because JNA can throw
                    // Error types (UnsatisfiedLinkError, etc.) when native functions
                    // return unexpected values.
                    if (isActive) {
                        _errors.tryEmit(e)
                        logger.warn(e) { "Error in mpv event loop" }
                        // A broken native binding must not turn into a hot CPU
                        // loop. Keep retrying, but with bounded backoff.
                        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(10)
                        delay((consecutiveFailures * 10L).coerceAtMost(100L))
                    }
                }
            }
            logger.info { "MPV event loop stopped" }
        }
    }

    /**
     * Stop the event loop and clean up.
     */
    fun stop() {
        isRunning = false
        val job = eventJob
        job?.cancel()
        // mpv_wait_event is a native call. Do not let the owner destroy the
        // handle until the polling coroutine has actually left that call.
        if (job != null) runBlocking { job.join() }
        eventJob = null
    }

    /**
     * Observe a property for changes.
     * @param name The mpv property name (e.g. "time-pos", "duration", "pause")
     * @param format The mpv format constant (default: FORMAT_DOUBLE)
     */
    fun observeProperty(name: String, format: Int = MPVLib.FORMAT_DOUBLE) {
        try {
            observe(mpvHandle, name.hashCode().toLong(), name, format)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to observe property: $name" }
        }
    }

    /**
     * Stop observing a property.
     */
    fun unobserveProperty(name: String) {
        try {
            unobserve(mpvHandle, name.hashCode().toLong())
        } catch (e: Exception) {
            // Safe to ignore
        }
    }

    private fun processEvent(event: MPVEvent) {
        _events.tryEmit(event)

        when (event.eventId) {
            MPVLib.MPV_EVENT_PROPERTY_CHANGE -> {
                processPropertyChange(event)
            }
            MPVLib.MPV_EVENT_SHUTDOWN -> {
                logger.info { "MPV shutdown event received" }
                isRunning = false
            }
            MPVLib.MPV_EVENT_LOG_MESSAGE -> {
                processLogMessage(event)
            }
        }
    }

    private fun processPropertyChange(event: MPVEvent) {
        // MPVEvent snapshots the transient mpv_event_property payload before it
        // is emitted to asynchronous consumers.
        val name = event.propertyName ?: return
        val format = event.propertyFormat ?: return
        _propertyChanges.tryEmit(PropertyChange(name, format, event.replyUserdata))
    }

    /**
     * Process an mpv log message event to capture internal mpv errors.
     * MPVEvent snapshots the transient mpv_event_log_message payload before
     * this event reaches asynchronous processing.
     */
    private fun processLogMessage(event: MPVEvent) {
        val prefix = event.logPrefix ?: "?"
        val level = event.logLevelName ?: "?"
        val text = event.logText ?: "?"
        val logLevel = event.logLevel ?: return

        // Only log render-related and error/warn messages to avoid noise.
        if (prefix.contains("libmpv") || prefix.contains("vo") || prefix.contains("render") ||
            logLevel <= MPVLib.LOG_LEVEL_WARN
        ) {
            logger.info { "🎬 MPV_LOG [$prefix/$level] $text" }
        }
    }
}

/**
 * Represents a property change event from mpv.
 */
data class PropertyChange(
    val name: String,
    val format: Int,
    val replyUserdata: Long,
)
