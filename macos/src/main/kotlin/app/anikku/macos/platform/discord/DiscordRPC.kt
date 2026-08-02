package app.anikku.macos.platform.discord

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Discord Rich Presence client using the Discord IPC (local socket) method.
 *
 * On macOS, Discord exposes a Unix domain socket at a well-known path for IPC.
 * This client connects to that socket and sends presence updates using Discord's
 * RPC protocol (OAuth 2.0 + WebSocket frame protocol).
 *
 * This is a desktop-compatible rewrite of the Android DiscordWebSocket/DiscordRPC
 * implementation. The Android version communicates with Discord's WebSocket gateway;
 * the desktop version uses the local IPC socket which is simpler (no OAuth flow needed
 * for basic presence).
 *
 * ## Usage
 *
 * ```kotlin
 * val rpc = DiscordRPC(coroutineScope)
 * rpc.setPresence(
 *     details = "Watching Attack on Titan",
 *     state = "Episode 3 - A Dim Light Amid Despair",
 *     largeImage = "anikku_logo",
 *     largeText = "Anikku",
 * )
 * rpc.start()
 * ```
 */
class DiscordRPC(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clientId: String = "1345678901234567890", // Placeholder — set via preferences
) {

    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var ws: WebSocket? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentPresence: DiscordPresence? = null
    private var nonceCounter: Int = 0

    // OkHttp client for Unix socket connections
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for long-lived WebSocket
        .build()

    /**
     * Start the Discord RPC connection.
     * Attempts to connect to Discord's IPC socket and authenticate.
     */
    fun start() {
        if (connectionJob?.isActive == true) return

        _connectionState.value = ConnectionState.CONNECTING
        connectionJob = scope.launch {
            connect()
        }
    }

    /**
     * Stop the Discord RPC connection.
     */
    fun stop() {
        reconnectJob?.cancel()
        connectionJob?.cancel()
        ws?.close(1000, "App closing")
        ws = null
        _connectionState.value = ConnectionState.DISCONNECTED
        logger.info { "Discord RPC stopped" }
    }

    /**
     * Set the current presence information.
     *
     * @param details Top line of the presence (e.g., "Watching Attack on Titan")
     * @param state Second line (e.g., "Episode 3")
     * @param largeImage Key for the large image asset (registered on Discord Developer Portal)
     * @param largeText Tooltip text for the large image
     * @param smallImage Key for the small image asset
     * @param smallText Tooltip text for the small image
     * @param startTimestamp When playback started (epoch millis), null to hide
     * @param endTimestamp When playback ends (epoch millis), null to hide
     */
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
            sendPresence(currentPresence!!)
        }
    }

    /**
     * Clear the current presence (set to idle/empty state).
     */
    fun clearPresence() {
        currentPresence = null
        if (connectionState.value == ConnectionState.CONNECTED) {
            sendPresence(DiscordPresence())
        }
    }

    /**
     * Whether the RPC client is connected and active.
     */
    val isConnected: Boolean get() = connectionState.value == ConnectionState.CONNECTED

    // -------------------------------------------------------------------------
    // Internal: Connection Management
    // -------------------------------------------------------------------------

    private suspend fun connect() {
        // Find Discord IPC socket path
        val socketPath = findDiscordSocket() ?: run {
            logger.warn { "Discord IPC socket not found. Is Discord running?" }
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        logger.info { "Connecting to Discord IPC at $socketPath" }

        try {
            // Open WebSocket connection via raw Unix socket. The native transport
            // is not bundled yet; never report CONNECTED for a simulated socket.
            if (!connectToSocket(socketPath)) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            // Handshake (OP_HANDSHAKE = 0)
            sendFrame(0, DiscordHandshake(client_id = clientId))

            // Start a reconnection monitor
            startReconnectMonitor()
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to Discord IPC" }
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Connect to Discord's Unix socket.
     *
     * The macOS port currently has no native Unix-domain-socket transport.
     * Returning false is deliberate: an unavailable optional integration must
     * remain disconnected rather than advertising a fake connected state.
     */
    private fun connectToSocket(socketPath: String): Boolean {
        logger.warn {
            "Discord IPC is unavailable: native Unix-socket transport is not bundled " +
                "(socket=$socketPath)"
        }
        return false
    }

    private fun startReconnectMonitor() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (isActive) {
                delay(30_000) // Check every 30 seconds
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    logger.info { "Discord RPC disconnected, attempting reconnect..." }
                    start()
                }
            }
        }
    }

    private fun sendFrame(opCode: Int, data: Any) {
        // Frame format: [opCode: 4 bytes][payload: JSON string]
        val payload = json.encodeToString(data)
        // Send via WebSocket
        ws?.send(payload)
    }

    private fun sendPresence(presence: DiscordPresence) {
        nonceCounter++
        val frame = DiscordPresenceFrame(
            cmd = "SET_ACTIVITY",
            args = DiscordPresenceArgs(
                pid = ProcessHandle.current().pid().toInt(),
                activity = DiscordActivity(
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
                            start = presence.startTimestamp,
                            end = presence.endTimestamp,
                        )
                    } else null,
                    instance = false,
                ),
            ),
            nonce = "anikku_${nonceCounter}",
        )
        sendFrame(1, frame)
        logger.debug { "Sent Discord presence: ${presence.details}" }
    }

    /**
     * Find Discord's IPC socket path on macOS.
     *
     * Discord creates Unix domain sockets at:
     * `~/Library/Application Support/discord/ipc-{0,1,2,...}`
     */
    private fun findDiscordSocket(): String? {
        val homeDir = System.getProperty("user.home") ?: return null
        val discordDir = File(homeDir, "Library/Application Support/discord")

        if (!discordDir.isDirectory) {
            logger.debug { "Discord directory not found at $discordDir" }
            return null
        }

        // Try ipc-0 through ipc-9
        for (i in 0..9) {
            val socketFile = File(discordDir, "ipc-$i")
            if (socketFile.exists()) {
                return socketFile.absolutePath
            }
        }

        return null
    }

    /**
     * Check if Discord is installed by looking for the Discord.app in Applications.
     */
    val isDiscordInstalled: Boolean
        get() {
            val discordPaths = listOf(
                "/Applications/Discord.app",
                "${System.getProperty("user.home")}/Applications/Discord.app",
            )
            return discordPaths.any { File(it).isDirectory }
        }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

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
}

/**
 * Connection state for Discord RPC.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

// -------------------------------------------------------------------------
// Discord IPC Protocol Data Classes (use snake_case for JSON serialization)
// -------------------------------------------------------------------------

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
    val activity: DiscordActivity,
)

@Serializable
private data class DiscordActivity(
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
