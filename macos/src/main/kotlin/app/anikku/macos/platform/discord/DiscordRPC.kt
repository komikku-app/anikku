package app.anikku.macos.platform.discord

import androidx.compose.runtime.compositionLocalOf
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.EOFException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Discord Rich Presence client for the local Discord desktop IPC socket.
 *
 * Discord IPC is a little-endian binary protocol over a Unix-domain socket. A
 * successful connection is only reported after Discord answers the handshake
 * with a READY frame. Discord is optional: failures remain isolated from app
 * startup and playback and are retried while this client is running.
 */
class DiscordRPC(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clientId: String = ANIKKU_DISCORD_APPLICATION_ID,
    private val socketCandidates: () -> List<Path> = ::defaultDiscordSocketCandidates,
    private val reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS,
) {

    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var socket: SocketChannel? = null
    private val requested = AtomicBoolean(false)
    private val writeLock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
        // The handshake version is a protocol-required field even though its
        // Kotlin model has a default value.
        encodeDefaults = true
        explicitNulls = true
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var currentPresence: DiscordPresence? = null
    private var nonceCounter: Int = 0

    /** Start connecting to the local Discord client. Safe to call repeatedly. */
    fun start() {
        requested.set(true)
        if (connectionJob?.isActive == true || reconnectJob?.isActive == true) return
        launchConnection()
    }

    /** Stop reconnecting, clear Rich Presence when possible, and close IPC. */
    fun stop() {
        requested.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        if (_connectionState.value == ConnectionState.CONNECTED) {
            runCatching { sendClearPresence() }
        }
        connectionJob?.cancel()
        connectionJob = null
        closeSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
        logger.info { "Discord RPC stopped" }
    }

    fun setPresence(
        details: String,
        state: String,
        largeImage: String = "anikku_logo",
        largeText: String = "Anikku",
        smallImage: String? = null,
        smallText: String? = null,
        startTimestamp: Long? = null,
        endTimestamp: Long? = null,
    ) {
        currentPresence = DiscordPresence(
            details = details,
            state = state,
            largeImage = largeImage,
            largeText = largeText,
            smallImage = smallImage,
            smallText = smallText,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
        )
        if (connectionState.value == ConnectionState.CONNECTED) {
            runCatching { sendPresence(currentPresence!!) }
                .onFailure { disconnectAfterWriteFailure(it) }
        }
    }

    fun clearPresence() {
        currentPresence = null
        if (connectionState.value == ConnectionState.CONNECTED) {
            runCatching { sendClearPresence() }
                .onFailure { disconnectAfterWriteFailure(it) }
        }
    }

    /**
     * Publish the currently playing episode without exposing source URLs or
     * account data. Timestamps let Discord render elapsed/remaining time while
     * playback is active; pausing intentionally removes them.
     */
    fun setPlaybackPresence(
        animeTitle: String,
        episodeLabel: String,
        positionSeconds: Double,
        durationSeconds: Double,
        isPaused: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val presence = playbackPresence(
            animeTitle = animeTitle,
            episodeLabel = episodeLabel,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            isPaused = isPaused,
            nowMillis = nowMillis,
        )
        setPresence(
            details = presence.details.orEmpty(),
            state = presence.state.orEmpty(),
            largeImage = presence.largeImage.orEmpty(),
            largeText = presence.largeText.orEmpty(),
            startTimestamp = presence.startTimestamp,
            endTimestamp = presence.endTimestamp,
        )
    }

    val isConnected: Boolean get() = connectionState.value == ConnectionState.CONNECTED

    private fun launchConnection() {
        if (!requested.get()) return
        _connectionState.value = ConnectionState.CONNECTING
        connectionJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { connectAndRead() }
            } catch (e: Exception) {
                if (requested.get()) logger.warn(e) { "Discord IPC connection ended" }
            } finally {
                closeSocket()
                _connectionState.value = ConnectionState.DISCONNECTED
                connectionJob = null
                scheduleReconnect()
            }
        }
    }

    private fun connectAndRead() {
        require(clientId.matches(Regex("[0-9]{17,20}"))) {
            "Discord application ID must be a 17-20 digit snowflake"
        }
        val socketPath = findDiscordSocket()
            ?: throw IllegalStateException("Discord IPC socket not found; is Discord desktop running?")

        logger.info { "Connecting to Discord IPC at $socketPath" }
        val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        socket = channel
        channel.connect(UnixDomainSocketAddress.of(socketPath))
        sendJsonFrame(channel, OP_HANDSHAKE, json.encodeToString(DiscordHandshake(client_id = clientId)))

        val ready = readFrame(channel)
        check(ready.opCode == OP_FRAME && ready.eventName() == "READY") {
            "Discord rejected IPC handshake (opcode=${ready.opCode}, event=${ready.eventName()})"
        }

        _connectionState.value = ConnectionState.CONNECTED
        logger.info { "Discord RPC connected" }
        currentPresence?.let(::sendPresence)

        while (requested.get() && channel.isOpen) {
            val frame = readFrame(channel)
            when (frame.opCode) {
                OP_CLOSE -> throw EOFException("Discord closed IPC: ${frame.payload}")
                OP_PING -> sendRawFrame(channel, OP_PONG, frame.payload)
                OP_FRAME -> if (frame.eventName() == "ERROR") {
                    logger.warn { "Discord IPC command error: ${frame.payload}" }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!requested.get() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(reconnectDelayMillis)
            reconnectJob = null
            if (isActive && requested.get()) launchConnection()
        }
    }

    private fun sendPresence(presence: DiscordPresence) {
        val activity = DiscordActivity(
            type = ACTIVITY_TYPE_WATCHING,
            details = presence.details,
            state = presence.state,
            assets = DiscordAssets(
                large_image = presence.largeImage,
                large_text = presence.largeText,
                small_image = presence.smallImage,
                small_text = presence.smallText,
            ),
            timestamps = if (presence.startTimestamp != null || presence.endTimestamp != null) {
                DiscordTimestamps(
                    start = presence.startTimestamp?.toDiscordSeconds(),
                    end = presence.endTimestamp?.toDiscordSeconds(),
                )
            } else {
                null
            },
            instance = false,
        )
        sendActivity(activity)
        logger.debug { "Sent Discord presence: ${presence.details}" }
    }

    private fun sendClearPresence() = sendActivity(null)

    private fun sendActivity(activity: DiscordActivity?) {
        nonceCounter++
        val frame = DiscordPresenceFrame(
            cmd = "SET_ACTIVITY",
            args = DiscordPresenceArgs(
                pid = ProcessHandle.current().pid().toInt(),
                activity = activity,
            ),
            nonce = "anikku_$nonceCounter",
        )
        val channel = socket ?: throw IllegalStateException("Discord IPC is not connected")
        sendJsonFrame(channel, OP_FRAME, json.encodeToString(frame))
    }

    private fun sendJsonFrame(channel: SocketChannel, opCode: Int, payload: String) {
        sendRawFrame(channel, opCode, payload)
    }

    private fun sendRawFrame(channel: SocketChannel, opCode: Int, payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_FRAME_SIZE) { "Discord IPC frame exceeds $MAX_FRAME_SIZE bytes" }
        val frame = ByteBuffer.allocate(HEADER_SIZE + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        frame.putInt(opCode)
        frame.putInt(bytes.size)
        frame.put(bytes)
        frame.flip()
        synchronized(writeLock) {
            while (frame.hasRemaining()) channel.write(frame)
        }
    }

    private fun readFrame(channel: SocketChannel): IpcFrame {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, header)
        header.flip()
        val opCode = header.int
        val length = header.int
        require(length in 0..MAX_FRAME_SIZE) { "Invalid Discord IPC frame length: $length" }
        val payload = ByteBuffer.allocate(length)
        readFully(channel, payload)
        payload.flip()
        return IpcFrame(opCode, StandardCharsets.UTF_8.decode(payload).toString())
    }

    private fun readFully(channel: SocketChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw EOFException("Discord IPC socket closed")
        }
    }

    private fun IpcFrame.eventName(): String? = runCatching {
        json.parseToJsonElement(payload).jsonObject["evt"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun findDiscordSocket(): Path? = socketCandidates().firstOrNull {
        Files.exists(it)
    }

    private fun disconnectAfterWriteFailure(error: Throwable) {
        logger.warn(error) { "Discord IPC write failed" }
        closeSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun closeSocket() {
        synchronized(writeLock) {
            runCatching { socket?.close() }
            socket = null
        }
    }

    val isDiscordInstalled: Boolean
        get() = listOf(
            "/Applications/Discord.app",
            "${System.getProperty("user.home")}/Applications/Discord.app",
        ).any { File(it).isDirectory }

    data class DiscordPresence(
        val details: String? = null,
        val state: String? = null,
        val largeImage: String? = "anikku_logo",
        val largeText: String? = "Anikku",
        val smallImage: String? = null,
        val smallText: String? = null,
        val startTimestamp: Long? = null,
        val endTimestamp: Long? = null,
    )

    private data class IpcFrame(val opCode: Int, val payload: String)

    companion object {
        private const val ANIKKU_DISCORD_APPLICATION_ID = "1173423931865170070"
        private const val DEFAULT_RECONNECT_DELAY_MILLIS = 30_000L
        private const val HEADER_SIZE = 8
        private const val MAX_FRAME_SIZE = 1024 * 1024
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2
        private const val OP_PING = 3
        private const val OP_PONG = 4
        private const val ACTIVITY_TYPE_WATCHING = 3
        private const val MAX_ACTIVITY_TEXT_LENGTH = 128

        private fun defaultDiscordSocketCandidates(): List<Path> {
            val environmentDirectories = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")
                .mapNotNull(System::getenv)
                .filter(String::isNotBlank)
                .map(Path::of)
                .plus(Path.of("/tmp"))
                .distinct()
            val standard = environmentDirectories.flatMap { directory ->
                (0..9).map { directory.resolve("discord-ipc-$it") }
            }
            val home = System.getProperty("user.home")
            val legacy = if (home.isNullOrBlank()) emptyList() else (0..9).flatMap { index ->
                val directory = Path.of(home, "Library", "Application Support", "discord")
                listOf(directory.resolve("discord-ipc-$index"), directory.resolve("ipc-$index"))
            }
            return standard + legacy
        }

        private fun Long.toDiscordSeconds(): Long = if (this >= 100_000_000_000L) this / 1_000L else this

        internal fun normalizeActivityText(value: String, fallback: String): String =
            value.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { fallback }
                .take(MAX_ACTIVITY_TEXT_LENGTH)

        internal fun playbackPresence(
            animeTitle: String,
            episodeLabel: String,
            positionSeconds: Double,
            durationSeconds: Double,
            isPaused: Boolean,
            nowMillis: Long,
        ): DiscordPresence {
            val details = normalizeActivityText("Watching $animeTitle", "Watching anime")
            val state = normalizeActivityText(
                if (isPaused) "$episodeLabel • Paused" else episodeLabel,
                if (isPaused) "Paused" else "Watching",
            )
            val validPosition = positionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val validDuration = durationSeconds.takeIf { it.isFinite() && it > validPosition }
            val startTimestamp = if (!isPaused) nowMillis - (validPosition * 1_000.0).toLong() else null
            val endTimestamp = if (!isPaused && validDuration != null) {
                startTimestamp!! + (validDuration * 1_000.0).toLong()
            } else {
                null
            }
            return DiscordPresence(
                details = details,
                state = state,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
            )
        }
    }
}

/** Optional app-level Discord client for Compose screens. */
val LocalDiscordRPC = compositionLocalOf<DiscordRPC?> { null }

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

@Serializable
private data class DiscordHandshake(
    val v: Int = 1,
    val client_id: String,
)

@Serializable
private data class DiscordPresenceFrame(
    val cmd: String,
    val args: DiscordPresenceArgs,
    val nonce: String,
)

@Serializable
private data class DiscordPresenceArgs(
    val pid: Int,
    val activity: DiscordActivity?,
)

@Serializable
private data class DiscordActivity(
    val type: Int,
    val details: String? = null,
    val state: String? = null,
    val assets: DiscordAssets? = null,
    val timestamps: DiscordTimestamps? = null,
    val instance: Boolean = false,
)

@Serializable
private data class DiscordAssets(
    val large_image: String? = null,
    val large_text: String? = null,
    val small_image: String? = null,
    val small_text: String? = null,
)

@Serializable
private data class DiscordTimestamps(
    val start: Long? = null,
    val end: Long? = null,
)
