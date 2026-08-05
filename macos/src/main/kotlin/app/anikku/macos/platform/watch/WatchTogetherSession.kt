package app.anikku.macos.platform.watch

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * A Watch Together session — the client half of a room, used by both the host
 * (who created the room on the local [WatchTogetherServer] and connected to
 * it) and guests (who discovered the host and joined). One session per player.
 *
 * Control flow is peer-to-peer: any member's play/pause/seek is relayed by the
 * server to everyone else, and the host additionally broadcasts a [WtMessage.Sync]
 * position tick once per second so guests stay aligned. Callbacks (installed by
 * the player) receive incoming control messages and episode updates.
 */
class WatchTogetherSession(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val localActionGuardMillis: Long = 800L,
    sessionName: String = System.getProperty("user.name", "Anikku"),
) : AutoCloseable {

    enum class Role { NONE, HOST, GUEST }

    /** The media the host is watching — what guests will be offered. */
    sealed interface MediaSpec {
        data class Local(val file: File) : MediaSpec
        data class Url(val url: String, val headers: Map<String, String>? = null) : MediaSpec
        data object Magnet : MediaSpec
    }

    val role = MutableStateFlow(Role.NONE)
    val roomCode = MutableStateFlow<String?>(null)
    val memberCount = MutableStateFlow(0)
    /** Member display names, in join order (best-effort; empty when unknown). */
    val memberNames = MutableStateFlow<List<String>>(emptyList())
    /** Browser-join URL shown in the room dialog. */
    val lanUrl = MutableStateFlow<String?>(null)
    /** User-facing status/error text. */
    val status = MutableStateFlow<String?>(null)

    /** Incoming control messages (play/pause/seek/sync) from other members. */
    var onControl: ((WtMessage) -> Unit)? = null

    /** A member shared a new episode (guest auto-load path). */
    var onEpisode: ((WtMessage.Episode) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var server: WatchTogetherServer? = null
    private var beacon: WatchTogetherDiscovery.Beacon? = null
    private var syncJob: Job? = null
    private val sessionName: String = sessionName

    /**
     * When the user acted locally (play/pause/seek), their own relayed action
     * converges everyone — a stale host [WtMessage.Sync] arriving right after
     * would otherwise fight it (e.g. a pause gets undone by a sync sent before
     * the pause was processed). Sync positions are skipped within this window.
     */
    private val localActionGuardNanos = localActionGuardMillis * 1_000_000L
    @Volatile
    private var lastLocalActionNanos = 0L
    @Volatile
    private var hasSyncBaseline = false
    /** True once the server announced the room is closing — keep that status over connection teardown noise. */
    @Volatile
    private var roomClosedNotified = false

    /** Host: create a room for the current episode and join it as member 1. */
    fun startRoom(episode: WtMessage.Episode, media: MediaSpec, server: WatchTogetherServer): Boolean {
        if (role.value != Role.NONE) return false
        val lanIp = LanAddresses.siteLocalIPv4() ?: "127.0.0.1"

        val handle = toHandle(media)
        val info = server.createRoom(episode, handle) ?: run {
            status.value = "Could not start the room server"
            return false
        }

        // Guests reach the media through the room server on the host's LAN IP.
        val finalEpisode = if (handle != null) {
            episode.copy(mediaUrl = "http://$lanIp:${server.actualPort}/media/${info.code}/${handle.id}")
        } else {
            episode
        }
        server.room(info.code)?.episode = finalEpisode

        this.server = server
        role.value = Role.HOST
        roomCode.value = info.code
        lanUrl.value = "http://$lanIp:${server.actualPort}/room/${info.code}"
        status.value = null

        // The host is member 1 — connect through the same client path as guests.
        connect("127.0.0.1", server.actualPort, info.code)
        beacon = WatchTogetherDiscovery.advertise(
            code = info.code,
            tcpPort = server.actualPort,
            name = sessionName,
        )
        logger.info { "Watch Together: hosting room ${info.code} on port ${server.actualPort}" }
        return true
    }

    /** Host: swap the room's media when the episode/stream changes (navigation). */
    fun updateRoomMedia(episode: WtMessage.Episode, media: MediaSpec, server: WatchTogetherServer) {
        if (role.value != Role.HOST) return
        val code = roomCode.value ?: return
        val handle = toHandle(media)
        server.room(code)?.media = handle
        val lanIp = LanAddresses.siteLocalIPv4() ?: "127.0.0.1"
        val finalEpisode = if (handle != null) {
            episode.copy(mediaUrl = "http://$lanIp:${server.actualPort}/media/$code/${handle.id}")
        } else {
            episode
        }
        server.room(code)?.episode = finalEpisode
        sendControl(finalEpisode)
    }

    private fun toHandle(media: MediaSpec): WatchTogetherServer.MediaHandle? = when (media) {
        is MediaSpec.Local -> WatchTogetherServer.MediaHandle(
            id = UUID.randomUUID().toString().take(12),
            localFile = media.file,
        )
        is MediaSpec.Url -> WatchTogetherServer.MediaHandle(
            id = UUID.randomUUID().toString().take(12),
            upstreamUrl = media.url,
            upstreamHeaders = media.headers,
        )
        MediaSpec.Magnet -> null
    }

    /** Guest: discover the host on the LAN and join [code]. */
    fun joinRoom(code: String, discoveryTimeoutMs: Long = 5_000) {
        if (role.value != Role.NONE) return
        status.value = "Looking for room $code on the network…"
        scope.launch {
            val found = WatchTogetherDiscovery.findHost(code, discoveryTimeoutMs)
            if (found == null) {
                if (role.value == Role.NONE) {
                    status.value = "Could not find room $code — is the host on the same network?"
                }
                return@launch
            }
            val host = found.substringBeforeLast(':')
            val port = found.substringAfterLast(':').toIntOrNull() ?: return@launch
            status.value = null
            joinRoomAt(host, port, code)
        }
    }

    /** Guest: join a room at an explicit host address (fallback when discovery fails). */
    fun joinRoomAt(host: String, port: Int, code: String) {
        if (role.value != Role.NONE) return
        this.server = null
        roomClosedNotified = false
        hasSyncBaseline = false
        role.value = Role.GUEST
        roomCode.value = code
        status.value = null
        connect(host, port, code)
    }

    /** Leave the room (host: also closes it and stops the beacon). */
    fun leave() {
        syncJob?.cancel()
        syncJob = null
        beacon?.shutdown()
        beacon = null
        webSocket?.close(1000, "leaving")
        webSocket = null
        server?.let { server ->
            roomCode.value?.let { server.closeRoom(it) }
            this.server = null
        }
        role.value = Role.NONE
        roomCode.value = null
        memberCount.value = 0
        memberNames.value = emptyList()
        lanUrl.value = null
        roomClosedNotified = false
    }

    /**
     * Broadcast a user-initiated control message (play/pause/seek). Marks a
     * local action so a stale incoming [WtMessage.Sync] cannot fight it.
     */
    fun sendControl(message: WtMessage) {
        if (role.value == Role.NONE) return
        if (message is WtMessage.Play || message is WtMessage.Pause || message is WtMessage.Seek) {
            lastLocalActionNanos = System.nanoTime()
        }
        sendRaw(message)
    }

    /** Internal send — the host's periodic Sync must NOT count as a local action. */
    private fun sendRaw(message: WtMessage) {
        val socket = webSocket ?: return
        runCatching { socket.send(WtProtocol.encode(message)) }
    }

    /**
     * Host: start broadcasting the player's position once per second so
     * guests reconcile drift. [provider] is polled on the session's scope.
     */
    fun beginHostSync(provider: () -> Triple<Double, Boolean, Double>) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && role.value == Role.HOST) {
                val (pos, playing, rate) = provider()
                sendRaw(WtMessage.Sync(pos = pos, playing = playing, rate = rate))
                delay(1_000)
            }
        }
    }

    override fun close() {
        leave()
        scope.cancel()
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun connect(host: String, port: Int, code: String) {
        val url = "ws://$host:$port/room/$code"
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(WtProtocol.encode(WtMessage.Hello(sessionName)))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val message = WtProtocol.decode(text)) {
                is WtMessage.Episode -> {
                    if (role.value != Role.NONE) onEpisode?.invoke(message)
                }
                is WtMessage.Members -> {
                    memberCount.value = message.count
                    memberNames.value = message.names
                }
                is WtMessage.Sync -> {
                    if (role.value == Role.NONE) return@onMessage
                    val now = System.nanoTime()
                    val recentlyActedLocally = now - lastLocalActionNanos < localActionGuardNanos
                    if (hasSyncBaseline && recentlyActedLocally) {
                        // The local action was already relayed and will converge
                        // everyone; a sync snapshot from before it was processed
                        // must not override the user's just-made choice.
                        return@onMessage
                    }
                    hasSyncBaseline = true
                    onControl?.invoke(message)
                }
                is WtMessage.RoomClosed -> {
                    if (role.value != Role.NONE) {
                        logger.info { "Watch Together: ${message.reason}" }
                        roomClosedNotified = true
                        status.value = message.reason
                        role.value = Role.NONE
                        roomCode.value = null
                        memberCount.value = 0
                        memberNames.value = emptyList()
                        lanUrl.value = null
                        this@WatchTogetherSession.webSocket = null
                    }
                }
                is WtMessage.Hello -> Unit // reserved for a future member list
                else -> message?.let { onControl?.invoke(it) }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (role.value != Role.NONE) {
                logger.warn(t) { "Watch Together connection failed" }
                // A RoomClosed announcement is already the answer — don't let
                // the socket teardown that follows overwrite it.
                if (!roomClosedNotified) {
                    status.value = "Connection failed — is the host reachable?"
                }
                role.value = Role.NONE
                roomCode.value = null
                memberCount.value = 0
                memberNames.value = emptyList()
                lanUrl.value = null
                beacon?.shutdown()
                beacon = null
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (role.value != Role.NONE) {
                role.value = Role.NONE
                roomCode.value = null
                memberCount.value = 0
                memberNames.value = emptyList()
                lanUrl.value = null
            }
        }
    }
}
