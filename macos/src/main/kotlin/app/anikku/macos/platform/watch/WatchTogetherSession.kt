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
    httpClient: OkHttpClient = defaultHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val localActionGuardMillis: Long = 800L,
    sessionName: String = System.getProperty("user.name", "Anikku"),
) : AutoCloseable {

    companion object {
        /**
         * The server's websocket read loop times out when a member sends
         * nothing (NanoHTTPD's socket SO_TIMEOUT) — OkHttp PINGs keep the
         * connection alive for members who are only watching.
         */
        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder().pingInterval(15, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    private val httpClient: OkHttpClient = httpClient

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
    /** The host's display name (null until the server announces it). */
    val hostName = MutableStateFlow<String?>(null)
    /**
     * Whether the host has locked room controls. While locked, play/pause/seek
     * from anyone but the host are dropped server-side; the UI shows a badge
     * and disables transport for guests.
     */
    val controlsLocked = MutableStateFlow(false)
    /**
     * Browser-join URL shown in the room dialog: the public tunnel link when
     * the room is hosted over the internet, the LAN URL otherwise.
     */
    val joinUrl = MutableStateFlow<String?>(null)
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

    /**
     * Public tunnel base (`https://<name>.trycloudflare.com`) the room is
     * hosted through, or null for a LAN-only room. Guests anywhere reach the
     * room server (sync + media) through this base.
     */
    @Volatile
    private var tunnelBase: String? = null

    /**
     * The tunnel owned by the CURRENT room, if any. One tunnel per room: the
     * caller starts it (spawning a fresh cloudflared process and URL) before
     * [startRoom], it is stored here, and [leave] drops it — so every new
     * room gets a new Cloudflare quick tunnel generation, and ending the
     * room kills it.
     */
    @Volatile
    private var roomTunnel: WatchTogetherTunnel? = null

    @Volatile
    private var sessionName: String = sessionName

    /** The name this member is known by in the room (may change via [rename]). */
    val name: String get() = sessionName

    /**
     * The host position captured by the FIRST sync received after joining —
     * the "you're here, not at 0:00" anchor. The player uses it as the start
     * position for the very first episode load as a guest.
     */
    @Volatile
    private var joinPosition = 0.0
    val joinStartPosition: Double get() = joinPosition

    /**
     * Change the display name shown to other members. Sent immediately if the
     * room is connected; the server re-broadcasts the member list.
     */
    fun rename(name: String) {
        val clean = name.trim().take(24)
        if (clean.isEmpty() || clean == sessionName) return
        sessionName = clean
        sendRaw(WtMessage.Hello(clean))
    }

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

    /**
     * Host: create a room for the current episode and join it as member 1.
     *
     * @param tunnelUrl public tunnel base (`https://…trycloudflare.com`) when
     *   the room should be reachable from the internet, or null for a
     *   LAN-only room (the default; same-network guests use UDP discovery).
     * @param tunnel the tunnel instance backing [tunnelUrl]. When a room is
     *   actually hosted through it, [leave] drops the tunnel so the next room
     *   starts a fresh generation. Pass null (or no tunnel) in tests that
     *   inject [tunnelUrl] directly.
     */
    fun startRoom(
        episode: WtMessage.Episode,
        media: MediaSpec,
        server: WatchTogetherServer,
        tunnelUrl: String? = null,
        tunnel: WatchTogetherTunnel? = null,
    ): Boolean {
        if (role.value != Role.NONE) return false
        val lanIp = LanAddresses.siteLocalIPv4() ?: "127.0.0.1"
        val base = tunnelUrl?.trimEnd('/')

        val handle = toHandle(media)
        val info = server.createRoom(episode, handle) ?: run {
            status.value = "Could not start the room server"
            // The caller may have already brought a tunnel up for this room —
            // don't leak it when the room itself cannot exist.
            tunnel?.stop()
            return false
        }

        // Guests reach the media through the room server — over the public
        // tunnel when one is up, otherwise on the host's LAN IP.
        val finalEpisode = if (handle != null) {
            episode.copy(
                mediaUrl = if (base != null) {
                    "$base/media/${info.code}/${handle.id}"
                } else {
                    "http://$lanIp:${server.actualPort}/media/${info.code}/${handle.id}"
                },
            )
        } else {
            episode
        }
        server.room(info.code)?.episode = finalEpisode

        this.server = server
        this.tunnelBase = base
        this.roomTunnel = if (base != null) tunnel else null
        role.value = Role.HOST
        roomCode.value = info.code
        joinUrl.value = if (base != null) {
            "$base/room/${info.code}"
        } else {
            "http://$lanIp:${server.actualPort}/room/${info.code}"
        }
        status.value = null

        // The host is member 1 — connect through the same client path as guests.
        connect("ws://127.0.0.1:${server.actualPort}/room/${info.code}")
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
        val base = tunnelBase?.trimEnd('/')
        val finalEpisode = if (handle != null) {
            episode.copy(
                mediaUrl = if (base != null) {
                    "$base/media/$code/${handle.id}"
                } else {
                    "http://$lanIp:${server.actualPort}/media/$code/${handle.id}"
                },
            )
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

    /**
     * Guest: join a room. Accepts either a shared room link (e.g.
     * `https://<tunnel>/room/<CODE>` — joins directly, works across the
     * internet) or a bare code (discovered on the LAN via UDP beacon).
     */
    fun joinRoom(input: String, discoveryTimeoutMs: Long = 5_000) {
        if (role.value != Role.NONE) return
        WtLinks.parse(input)?.let { link ->
            status.value = null
            joinRoomAt(link.host, link.port, link.code, useTls = link.secure)
            return
        }
        val code = input.trim()
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

    /**
     * Guest: join a room at an explicit host address (LAN discovery result,
     * or a shared-link target). [useTls] selects wss over ws (tunnel links).
     */
    fun joinRoomAt(host: String, port: Int, code: String, useTls: Boolean = false) {
        if (role.value != Role.NONE) return
        this.server = null
        this.tunnelBase = null
        roomClosedNotified = false
        hasSyncBaseline = false
        joinPosition = 0.0
        role.value = Role.GUEST
        roomCode.value = code
        status.value = null
        val scheme = if (useTls) "wss" else "ws"
        connect("$scheme://$host:$port/room/$code")
    }

    /** Leave the room (host: also closes it and stops the beacon + tunnel). */
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
        // The room's tunnel dies with it — the next room gets a fresh URL.
        roomTunnel?.stop()
        roomTunnel = null
        tunnelBase = null
        role.value = Role.NONE
        roomCode.value = null
        memberCount.value = 0
        memberNames.value = emptyList()
        hostName.value = null
        controlsLocked.value = false
        joinUrl.value = null
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

    /** Host: lock or unlock room controls (guests become watch-only). */
    fun lockControls(locked: Boolean) {
        if (role.value != Role.HOST) return
        controlsLocked.value = locked
        sendRaw(WtMessage.Lock(locked = locked, by = sessionName))
    }

    /** Host: remove a member from the room by display name. */
    fun kickMember(name: String) {
        if (role.value != Role.HOST) return
        sendRaw(WtMessage.Kick(name = name, by = sessionName))
    }

    /** Any member: change the room's playback speed (applies to everyone). */
    fun setRoomSpeed(rate: Double) {
        if (role.value == Role.NONE) return
        sendRaw(WtMessage.Speed(rate = rate.coerceIn(0.25, 4.0), by = sessionName))
    }

    /** Internal send — the host's periodic Sync must NOT count as a local action. */
    private fun sendRaw(message: WtMessage) {
        val socket = webSocket ?: return
        runCatching { socket.send(WtProtocol.encode(message)) }
    }

    /** A snapshot of the host's playback state, broadcast once per second. */
    data class SyncSnapshot(
        val pos: Double,
        val playing: Boolean,
        val rate: Double,
        /** Host's media length in seconds; guests use it as a timeline fallback. */
        val duration: Double,
    )

    /**
     * Host: start broadcasting the player's position once per second so
     * guests reconcile drift. [provider] is polled on the session's scope.
     */
    fun beginHostSync(provider: () -> SyncSnapshot) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && role.value == Role.HOST) {
                val snapshot = provider()
                sendRaw(
                    WtMessage.Sync(
                        pos = snapshot.pos,
                        playing = snapshot.playing,
                        rate = snapshot.rate,
                        duration = snapshot.duration,
                    ),
                )
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

    private fun connect(url: String) {
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
                    hostName.value = message.hostName
                }
                is WtMessage.Lock -> {
                    controlsLocked.value = message.locked
                    onControl?.invoke(message)
                }
                is WtMessage.Sync -> {
                    if (role.value == Role.NONE) return@onMessage
                    // The first sync after joining (replayed by the server on
                    // open) anchors the guest's start position, so their very
                    // first load can begin where the host is, not at 0:00.
                    if (role.value == Role.GUEST && !hasSyncBaseline) {
                        joinPosition = message.pos
                    }
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
                        hostName.value = null
                        controlsLocked.value = false
                        joinUrl.value = null
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
                hostName.value = null
                controlsLocked.value = false
                joinUrl.value = null
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
                hostName.value = null
                controlsLocked.value = false
                joinUrl.value = null
            }
        }
    }
}
