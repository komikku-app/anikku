package app.anikku.macos.platform.watch

import androidx.compose.runtime.compositionLocalOf
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

/**
 * LAN "Watch Together" room server (WebSocket sync + media serving).
 *
 * A [NanoWSD] subclass bound to 0.0.0.0 (unlike the loopback-only
 * [app.anikku.macos.platform.media.MacOSHttpServer]) so friends on the same
 * network can join. Routes:
 *
 * - `/room/<code>` — websocket handshake (join/leave/message relay).
 * - `/room/<code>` — HTTP GET: minimal browser join page.
 * - `/media/<code>/<id>` — HTTP GET/HEAD: serves the host's current media
 *   (local file directly, http(s) upstream proxied with Range passthrough).
 *
 * The server is peer-relay only: every message is broadcast to the other
 * members verbatim; [WtMessage.Episode] is additionally stored so members
 * joining later receive the current media immediately. Room codes are
 * 6-character and unguessable-ish (31^6 ≈ 2^29) — LAN trust model.
 */
class WatchTogetherServer(
    preferredPort: Int = DEFAULT_PORT,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : NanoWSD("0.0.0.0", choosePort(preferredPort)) {

    /** A playable media source registered with a room. Exactly one of the URL fields is set. */
    data class MediaHandle(
        val id: String,
        val localFile: File? = null,
        val upstreamUrl: String? = null,
        val upstreamHeaders: Map<String, String>? = null,
    )

    data class RoomInfo(val code: String, val port: Int)

    inner class Room(val code: String) {
        val members = CopyOnWriteArrayList<RoomSocket>()
        @Volatile
        var episode: WtMessage.Episode? = null
        @Volatile
        var media: MediaHandle? = null
    }

    private val rooms = ConcurrentHashMap<String, Room>()
    private var isRunning = false

    /** The actual TCP port (falls back to an ephemeral port when the default is taken). */
    val actualPort: Int get() = listeningPort

    @Synchronized
    fun startServer(): Boolean {
        if (isRunning) return true
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            isRunning = true
            logger.info { "Watch Together server listening on port $actualPort" }
            true
        } catch (e: Exception) {
            isRunning = false
            logger.error(e) { "Failed to start Watch Together server" }
            false
        }
    }

    @Synchronized
    fun stopServer() {
        if (!isRunning) return
        try {
            rooms.values.forEach { room ->
                room.members.forEach { member -> runCatching { member.close(CloseCode.NormalClosure, "server stopping", false) } }
            }
            rooms.clear()
            stop()
            logger.info { "Watch Together server stopped" }
        } catch (e: Exception) {
            logger.warn(e) { "Error stopping Watch Together server" }
        } finally {
            isRunning = false
        }
    }

    /** Create a room bound to the given episode/media; the host joins it as member 1. */
    fun createRoom(episode: WtMessage.Episode, media: MediaHandle?): RoomInfo? {
        if (!startServer()) return null
        val code = generateUniqueCode()
        val room = Room(code)
        room.episode = episode
        room.media = media
        rooms[code] = room
        logger.info { "Watch Together room $code created (${episode.title} ${episode.name})" }
        return RoomInfo(code, actualPort)
    }

    fun closeRoom(code: String) {
        val room = rooms.remove(code) ?: return
        room.members.forEach { member ->
            runCatching { member.close(CloseCode.NormalClosure, "room closed", false) }
        }
        logger.info { "Watch Together room $code closed" }
    }

    fun hasRoom(code: String): Boolean = rooms.containsKey(code)

    internal fun room(code: String): Room? = rooms[code]

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        return when {
            isWebsocketRequested(session) -> {
                // Reject unknown rooms BEFORE the handshake — NanoWSD's serve
                // dereferences the socket returned by openWebSocket without a
                // null check, so a missing room must 404 here instead.
                val code = uri.removePrefix("/room/").trim('/')
                if (!rooms.containsKey(code)) {
                    textResponse(Response.Status.NOT_FOUND, "Room not found", method)
                } else {
                    super.serve(session)
                }
            }
            uri == "/health" -> textResponse(Response.Status.OK, "ok", method)
            uri.startsWith("/room/") -> joinPage(uri, method)
            uri.startsWith("/media/") -> serveMedia(uri, session, method)
            else -> textResponse(Response.Status.NOT_FOUND, "Not found", method)
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val code = handshake.uri.removePrefix("/room/").trim('/')
        // serve() pre-checks existence; a race (room closed mid-handshake) gets
        // a throwaway room rather than a null dereference in NanoWSD.
        val room = rooms[code] ?: Room(code)
        return RoomSocket(handshake, room)
    }

    // -----------------------------------------------------------------------
    // Room socket — relay messages, track membership
    // -----------------------------------------------------------------------

    inner class RoomSocket(
        handshake: IHTTPSession,
        private val room: Room,
    ) : WebSocket(handshake) {

        @Volatile
        var memberName: String = ""

        override fun onOpen() {
            room.members.add(this)
            // Late joiners immediately receive the current media.
            room.episode?.let { episode ->
                runCatching { send(WtProtocol.encode(episode)) }
            }
            broadcastMembers()
        }

        override fun onClose(code: CloseCode, reason: String?, initiatedByRemote: Boolean) {
            room.members.remove(this)
            broadcastMembers()
        }

        override fun onMessage(frame: WebSocketFrame) {
            val text = runCatching { frame.textPayload }.getOrNull() ?: return
            val message = WtProtocol.decode(text) ?: return
            when (message) {
                // Join-time metadata — recorded but not relayed to others.
                is WtMessage.Hello -> memberName = message.name
                is WtMessage.Episode -> {
                    room.episode = message // keep late joiners in sync
                    relay(text)
                }
                else -> relay(text)
            }
        }

        override fun onPong(pong: WebSocketFrame) = Unit

        override fun onException(exception: IOException) {
            logger.debug(exception) { "Room socket exception (${room.code})" }
        }

        /** Forward a raw message to every other member of the room. */
        private fun relay(text: String) {
            room.members.forEach { member ->
                if (member !== this) {
                    runCatching { member.send(text) }
                }
            }
        }

        private fun broadcastMembers() {
            val count = room.members.size
            val message = WtProtocol.encode(WtMessage.Members(count))
            room.members.forEach { member ->
                runCatching { member.send(message) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Media serving — local files and http(s) proxy with Range passthrough
    // -----------------------------------------------------------------------

    private fun serveMedia(uri: String, session: IHTTPSession, method: Method): Response {
        val parts = uri.removePrefix("/media/").split("/")
        if (parts.size != 2) return textResponse(Response.Status.NOT_FOUND, "Not found", method)
        val (code, id) = parts
        val media = rooms[code]?.media ?: return textResponse(Response.Status.NOT_FOUND, "Media not found", method)
        if (media.id != id) return textResponse(Response.Status.NOT_FOUND, "Media not found", method)

        val response = media.localFile?.let { serveLocalFile(it, session, method) }
            ?: media.upstreamUrl?.let { serveProxy(it, media.upstreamHeaders, session, method) }
            ?: textResponse(Response.Status.NOT_FOUND, "Media not found", method)
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        return response
    }

    private fun serveLocalFile(file: File, session: IHTTPSession, method: Method): Response {
        if (!file.isFile) return textResponse(Response.Status.NOT_FOUND, "File not found", method)
        val fileLength = file.length()
        val range = parseRange(headerValue(session, "range"), fileLength)
        if (range == RangeResult.Invalid) return rangeNotSatisfiable(fileLength)

        val selected = range as? RangeResult.Valid
        val start = selected?.start ?: 0L
        val end = selected?.end ?: (fileLength - 1L)
        val contentLength = if (fileLength == 0L) 0L else end - start + 1L
        val status = if (selected == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT

        val response = if (method == Method.HEAD || contentLength == 0L) {
            newFixedLengthResponse(status, mimeType(file.extension), "")
        } else {
            try {
                val stream = FileInputStream(file)
                if (!skipFully(stream, start)) {
                    stream.close()
                    return textResponse(Response.Status.NOT_FOUND, "File is no longer available", method)
                }
                newFixedLengthResponse(status, mimeType(file.extension), stream, contentLength)
            } catch (e: IOException) {
                logger.warn(e) { "Unable to open room media file: ${file.name}" }
                return textResponse(Response.Status.NOT_FOUND, "File is no longer available", method)
            }
        }
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", contentLength.toString())
        if (selected != null) {
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
        }
        response.addHeader("Content-Disposition", "inline; filename=\"${file.name.replace(Regex("[\\r\\n\"]"), "_")}\"")
        return response
    }

    /** Stream an upstream http(s) URL, passing the client's Range header through. */
    private fun serveProxy(
        upstreamUrl: String,
        upstreamHeaders: Map<String, String>?,
        session: IHTTPSession,
        method: Method,
    ): Response {
        return try {
            val request = Request.Builder().url(upstreamUrl).apply {
                upstreamHeaders?.forEach { (name, value) -> runCatching { header(name, value) } }
                headerValue(session, "range")?.let { header("Range", it) }
            }.build()
            val upstream = httpClient.newCall(request).execute()
            val body = upstream.body
            val status = Response.Status.lookup(upstream.code) ?: Response.Status.BAD_REQUEST
            val mime = body?.contentType()?.toString() ?: "application/octet-stream"
            val length = body?.contentLength() ?: -1L

            if (method == Method.HEAD) {
                upstream.close()
                return newFixedLengthResponse(status, mime, "")
            }

            // Close the OkHttp response when NanoHTTPD finishes with the stream.
            val stream = object : FilterInputStream(body!!.byteStream()) {
                override fun close() {
                    runCatching { super.close() }
                    runCatching { upstream.close() }
                }
            }
            val response = if (length >= 0L) {
                newFixedLengthResponse(status, mime, stream, length)
            } else {
                newChunkedResponse(status, mime, stream)
            }
            if (upstream.code == 206) {
                response.addHeader("Content-Range", upstream.header("Content-Range") ?: "")
            }
            if (upstream.code == 416) {
                response.addHeader("Content-Range", upstream.header("Content-Range") ?: "bytes */0")
            }
            response.addHeader("Accept-Ranges", "bytes")
            response
        } catch (e: Exception) {
            logger.warn(e) { "Room media proxy failed for $upstreamUrl" }
            textResponse(Response.Status.INTERNAL_ERROR, "Upstream unavailable", method)
        }
    }

    private fun headerValue(session: IHTTPSession, name: String): String? =
        session.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun rangeNotSatisfiable(fileLength: Long): Response =
        newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain; charset=utf-8", "")
            .also {
                it.addHeader("Accept-Ranges", "bytes")
                it.addHeader("Content-Range", "bytes */$fileLength")
                it.addHeader("Content-Length", "0")
            }

    private fun skipFully(stream: FileInputStream, target: Long): Boolean {
        var remaining = target
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (stream.read() == -1) return false
            remaining--
        }
        return true
    }

    private sealed interface RangeResult {
        data class Valid(val start: Long, val end: Long) : RangeResult
        data object Invalid : RangeResult
    }

    private fun parseRange(header: String?, fileLength: Long): RangeResult? {
        if (header.isNullOrBlank()) return null
        if (fileLength <= 0L || !header.startsWith("bytes=")) return RangeResult.Invalid
        val value = header.removePrefix("bytes=").trim()
        if (value.contains(",")) return RangeResult.Invalid
        val separator = value.indexOf('-')
        if (separator < 0) return RangeResult.Invalid
        val startText = value.substring(0, separator).trim()
        val endText = value.substring(separator + 1).trim()
        return try {
            when {
                startText.isEmpty() -> {
                    val suffixLength = endText.toLongOrNull() ?: return RangeResult.Invalid
                    if (suffixLength <= 0L) return RangeResult.Invalid
                    val start = (fileLength - suffixLength).coerceAtLeast(0L)
                    RangeResult.Valid(start, fileLength - 1L)
                }
                else -> {
                    val start = startText.toLongOrNull() ?: return RangeResult.Invalid
                    if (start < 0L || start >= fileLength) return RangeResult.Invalid
                    val end = if (endText.isEmpty()) fileLength - 1L
                    else {
                        val requestedEnd = endText.toLongOrNull() ?: return RangeResult.Invalid
                        if (requestedEnd < start) return RangeResult.Invalid
                        requestedEnd.coerceAtMost(fileLength - 1L)
                    }
                    RangeResult.Valid(start, end)
                }
            }
        } catch (_: ArithmeticException) {
            RangeResult.Invalid
        }
    }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-wmv"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "ogv" -> "video/ogg"
        else -> "application/octet-stream"
    }

    // -----------------------------------------------------------------------
    // Browser join page
    // -----------------------------------------------------------------------

    private fun joinPage(uri: String, method: Method): Response {
        val code = uri.removePrefix("/room/").trim('/')
        if (!rooms.containsKey(code) || !WtCodes.isValid(code)) {
            return textResponse(Response.Status.NOT_FOUND, "Room not found", method)
        }
        val page = JOIN_PAGE.replace("__ROOM_CODE__", code)
        return newFixedLengthResponse(
            if (method == Method.HEAD) Response.Status.OK else Response.Status.OK,
            "text/html; charset=utf-8",
            if (method == Method.HEAD) "" else page,
        )
    }

    private fun textResponse(status: Response.Status, body: String, method: Method): Response {
        val response = if (method == Method.HEAD) {
            newFixedLengthResponse(status, "text/plain; charset=utf-8", "")
        } else {
            newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
        }
        response.addHeader("Content-Length", body.toByteArray(StandardCharsets.UTF_8).size.toString())
        return response
    }

    private fun generateUniqueCode(): String {
        while (true) {
            val code = WtCodes.newCode()
            if (!rooms.containsKey(code)) return code
        }
    }

    companion object {
        const val DEFAULT_PORT = 18234

        /**
         * Prefer the well-known port, falling back to an ephemeral one when
         * it's already taken. The beacon advertises the actual port.
         */
        fun choosePort(preferred: Int): Int =
            runCatching { ServerSocket(preferred).use { it.localPort } }
                .getOrElse { ServerSocket(0).use { it.localPort } }
    }
}

/** CompositionLocal for the app-wide [WatchTogetherServer] (null when unavailable). */
val LocalWatchTogetherServer = compositionLocalOf<WatchTogetherServer?> { null }

/** Minimal browser guest page — websocket sync + native <video> playback. */
private val JOIN_PAGE = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Anikku — Watch Together</title>
<style>
  html,body{height:100%;margin:0;background:#000;color:#eee;font-family:system-ui,-apple-system,sans-serif}
  video{width:100%;height:calc(100% - 52px);background:#000}
  #bar{height:52px;display:flex;gap:10px;align-items:center;justify-content:center;font-size:14px}
  button{background:#1c1c1e;color:#eee;border:1px solid #3a3a3c;border-radius:8px;padding:8px 20px;cursor:pointer;font-size:14px}
  button:hover{background:#2c2c2e}
  #info{opacity:.7}
</style>
</head>
<body>
<video id="v" controls playsinline></video>
<div id="bar">
  <button onclick="send({type:'play'})">&#9654; Play</button>
  <button onclick="send({type:'pause'})">&#10074;&#10074; Pause</button>
  <span id="info">Connecting&#8230;</span>
</div>
<script>
var code = location.pathname.split('/').pop();
var ws = new WebSocket('ws://' + location.host + '/room/' + code);
var v = document.getElementById('v');
var info = document.getElementById('info');
function send(m) { if (ws.readyState === 1) ws.send(JSON.stringify(m)); }
ws.onopen = function () { send({type:'hello', name:'Browser'}); };
ws.onmessage = function (e) {
  var m; try { m = JSON.parse(e.data); } catch (err) { return; }
  if (m.type === 'episode' && m.mediaUrl && v.src !== m.mediaUrl) v.src = m.mediaUrl;
  if (m.type === 'play') v.play();
  if (m.type === 'pause') v.pause();
  if (m.type === 'seek') v.currentTime = m.pos;
  if (m.type === 'sync') {
    if (Math.abs(v.currentTime - m.pos) > 1) v.currentTime = m.pos;
    if (m.playing && v.paused) v.play();
    if (!m.playing && !v.paused) v.pause();
  }
  if (m.type === 'members') info.textContent = m.count + ' watching &middot; room ' + code;
};
// Only echo USER-initiated events (e.isTrusted) — programmatic changes from
// sync would otherwise echo back and loop.
v.addEventListener('play', function (e) { if (e.isTrusted) send({type:'play'}); });
v.addEventListener('pause', function (e) { if (e.isTrusted) send({type:'pause'}); });
v.addEventListener('seeked', function (e) { if (e.isTrusted) send({type:'seek', pos: v.currentTime}); });
ws.onclose = function () { info.textContent = 'Disconnected'; };
</script>
</body>
</html>
""".trimIndent()
