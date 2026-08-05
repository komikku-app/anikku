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
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
        /**
         * The most recent host [WtMessage.Sync] seen in this room. Replayed to
         * late joiners (before the episode) so they start exactly where the
         * host is instead of at 0:00 until the next 1 Hz tick.
         */
        @Volatile
        var lastSync: WtMessage.Sync? = null
    }

    private val rooms = ConcurrentHashMap<String, Room>()
    private var isRunning = false

    /** The actual TCP port (falls back to an ephemeral port when the default is taken). */
    val actualPort: Int get() = listeningPort

    @Synchronized
    fun startServer(): Boolean {
        if (isRunning) return true
        return try {
            // NanoHTTPD applies this timeout as Socket.setSoTimeout on EVERY
            // accepted connection, and the websocket read loop only counts
            // INCOMING frames as activity — with the default 5s, any member
            // who goes quiet (a guest who's just watching) gets dropped after
            // 5 seconds. Clients heartbeat every ~15s, so 120s is a generous
            // dead-peer window without ever killing a healthy watcher.
            start(120_000, true)
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
            val message = WtProtocol.encode(WtMessage.RoomClosed("The host closed the room"))
            rooms.values.forEach { room ->
                room.members.forEach { member ->
                    runCatching { member.send(message) }
                    runCatching { member.close(CloseCode.NormalClosure, "server stopping", false) }
                }
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
        // Tell the remaining members before the sockets close, so guests see
        // "host closed the room" instead of a silent disconnect.
        val message = WtProtocol.encode(WtMessage.RoomClosed())
        room.members.forEach { member ->
            runCatching { member.send(message) }
        }
        // Give the announcement a moment to flush before the sockets close,
        // so guests see "host closed the room" instead of a connection failure.
        Thread.sleep(150)
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
                    serveWebSocketHandshake(session)
                }
            }
            uri == "/health" -> textResponse(Response.Status.OK, "ok", method)
            uri == "/hls.min.js" -> serveHlsJs(method)
            uri.startsWith("/room/") -> joinPage(uri, method)
            uri.startsWith("/media/") -> serveMedia(uri, session, method)
            else -> textResponse(Response.Status.NOT_FOUND, "Not found", method)
        }
    }

    /**
     * Serve the WebSocket handshake with a standards-compliant 101. NanoWSD's
     * built-in handshake writes `Content-Length: 0` on the 101, which RFC 7230
     * forbids — loopback clients ignore it, but Cloudflare's edge treats it as
     * end-of-response and drops tunneled (internet) room connections right
     * after the handshake. We write the 101 ourselves (no Content-Length),
     * then hand the socket to NanoWSD's frame reader via [adoptSocket].
     */
    private fun serveWebSocketHandshake(session: IHTTPSession): Response {
        val headers = session.headers
        val version = headers.entries.firstOrNull { it.key.equals("sec-websocket-version", ignoreCase = true) }?.value
        val key = headers.entries.firstOrNull { it.key.equals("sec-websocket-key", ignoreCase = true) }?.value
        if (version != "13") return textResponse(Response.Status.BAD_REQUEST, "Invalid Websocket-Version", session.method)
        if (key.isNullOrBlank()) return textResponse(Response.Status.BAD_REQUEST, "Missing Websocket-Key", session.method)
        val socket = openWebSocket(session)
        return object : Response(Response.Status.SWITCH_PROTOCOL, "text/plain", null, 0L) {
            override fun send(outputStream: OutputStream) {
                val header = buildString {
                    append("HTTP/1.1 101 Switching Protocols\r\n")
                    append("Date: ").append(gmtDate()).append("\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Accept: ").append(secWebSocketAcceptKey(key)).append("\r\n")
                    append("\r\n")
                }
                outputStream.write(header.toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
                adoptSocket(socket, outputStream)
            }
        }
    }

    /**
     * Replicate what NanoWSD's internal `WebSocket$1.send()` does after the
     * handshake response: adopt the socket's output stream, mark the socket
     * OPEN, notify `onOpen()`, and run the frame reader (which blocks until
     * the connection closes). Uses reflection because `out`, `state`,
     * `onOpen` and `readWebsocket` are private/package-private to NanoWSD.
     * Stable for nanohttpd-websocket 2.3.1 (pinned in build.gradle.kts);
     * upgrading the library fails loudly with a ReflectiveOperationException.
     */
    private fun adoptSocket(socket: NanoWSD.WebSocket, outputStream: OutputStream) {
        val wsClass = NanoWSD.WebSocket::class.java
        runCatching {
            wsClass.getDeclaredField("out").apply { isAccessible = true }.set(socket, outputStream)
            wsClass.getDeclaredField("state").apply { isAccessible = true }.set(socket, NanoWSD.State.OPEN)
            wsClass.getDeclaredMethod("onOpen").apply { isAccessible = true }.invoke(socket)
            wsClass.getDeclaredMethod("readWebsocket").apply { isAccessible = true }.invoke(socket)
        }.onFailure { e ->
            logger.warn(e) { "NanoWSD socket handoff failed — websocket session will not work" }
        }
    }

    /** RFC 6455 Sec-WebSocket-Accept: base64(SHA-1(key + GUID)). */
    private fun secWebSocketAcceptKey(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.ISO_8859_1))
        return java.util.Base64.getEncoder().encodeToString(sha1)
    }

    private fun gmtDate(): String =
        java.text.SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
            .format(java.util.Date())

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
            // Late joiners first get the current position/play state, THEN the
            // media — so the guest's player can jump straight to the host's
            // spot once the file loads instead of flashing from 0:00.
            room.lastSync?.let { sync ->
                runCatching { send(WtProtocol.encode(sync)) }
            }
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
                is WtMessage.Hello -> {
                    if (message.name.isNotBlank() && message.name != memberName) {
                        memberName = message.name
                        broadcastMembers()
                    }
                }
                is WtMessage.Episode -> {
                    room.episode = message // keep late joiners in sync
                    relay(text)
                }
                is WtMessage.Sync -> {
                    room.lastSync = message // replay to late joiners on open
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
            val names = room.members.map { it.memberName }.filter { it.isNotBlank() }
            val message = WtProtocol.encode(WtMessage.Members(room.members.size, names))
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
        if (media.id != id.substringBefore('?')) return textResponse(Response.Status.NOT_FOUND, "Media not found", method)

        // HLS playlists served through this route are rewritten so every
        // referenced segment/playlist/key is fetched back through the proxy
        // (?u=<upstream target>) — browsers can't reach the source's CDN
        // directly (no host headers, no CORS).
        val mediaPath = "/media/$code/${id.substringBefore('?')}"
        val upstream = session.parms?.get("u") ?: media.upstreamUrl
        val response = media.localFile?.let { serveLocalFile(it, session, method) }
            ?: upstream?.let { serveProxy(it, media.upstreamHeaders, session, method, mediaPath) }
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

    /**
     * Stream an upstream http(s) URL, passing the client's Range header
     * through. HLS playlists (m3u8) are rewritten in full so browsers can
     * actually play them: Android Chrome and most desktop browsers cannot
     * fetch the source's segments directly (cross-origin + missing host
     * headers), so every reference is redirected back through the proxy.
     */
    private fun serveProxy(
        upstreamUrl: String,
        upstreamHeaders: Map<String, String>?,
        session: IHTTPSession,
        method: Method,
        mediaPath: String,
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
            val bodyStream = body?.byteStream() ?: return textResponse(Response.Status.INTERNAL_ERROR, "No body", method)

            // Sniff the stream head for HLS (some sources serve playlists with
            // neither a m3u8 URL nor a proper content type). Pushback lets us
            // restore the prefix when the payload is NOT a playlist.
            val sniff = java.io.PushbackInputStream(bodyStream, 16)
            val prefix = ByteArray(16)
            val prefixLen = sniff.read(prefix)
            if (isHlsPlaylist(upstreamUrl, mime, prefix, prefixLen)) {
                val text = String(prefix, 0, maxOf(prefixLen, 0), StandardCharsets.UTF_8) +
                    sniff.readBytes().toString(StandardCharsets.UTF_8)
                upstream.close()
                val rewritten = rewriteHlsPlaylist(text, upstreamUrl, mediaPath)
                return newFixedLengthResponse(status, HLS_MIME, rewritten)
                    .also { it.addHeader("Access-Control-Allow-Origin", "*") }
            }
            if (prefixLen > 0) sniff.unread(prefix, 0, prefixLen)

            // Close the OkHttp response when NanoHTTPD finishes with the stream.
            val stream = object : FilterInputStream(sniff) {
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

    private fun isHlsPlaylist(upstreamUrl: String, mime: String, prefix: ByteArray, prefixLen: Int): Boolean {
        if (upstreamUrl.contains(".m3u8", ignoreCase = true)) return true
        if (mime.contains("mpegurl", ignoreCase = true)) return true
        // Some sources serve HLS without a m3u8 URL or proper content type —
        // sniff the "#EXTM3U" magic at the start of the payload.
        return prefixLen >= 7 && String(prefix, 0, 7, StandardCharsets.UTF_8) == "#EXTM3U"
    }

    /**
     * Rewrite an HLS playlist so every referenced resource (media playlists,
     * segments, keys, maps) is fetched through the room proxy instead of the
     * source CDN. Absolute and relative references are both supported; the
     * rewritten URL encodes the fully-resolved upstream target in `?u=`.
     */
    internal fun rewriteHlsPlaylist(playlist: String, playlistUrl: String, mediaPath: String): String {
        val base = java.net.URI(playlistUrl)
        return buildString {
            playlist.lineSequence().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXT-X-KEY:") || trimmed.startsWith("#EXT-X-MAP:") -> {
                        // Tags carrying URI="..." attributes (encryption keys, fMP4 maps).
                        append(HLS_TAG_URI.replace(trimmed) { match ->
                            val absolute = base.resolve(match.groupValues[1]).toString()
                            "\"${proxyMediaUrl(mediaPath, absolute)}\""
                        })
                    }
                    trimmed.isEmpty() || trimmed.startsWith("#") -> append(line)
                    else -> {
                        val absolute = base.resolve(trimmed).toString()
                        append(proxyMediaUrl(mediaPath, absolute))
                    }
                }
                append('\n')
            }
        }
    }

    /** `/media/<code>/<id>?u=<encoded upstream target>` — the proxy loop-back URL. */
    private fun proxyMediaUrl(mediaPath: String, target: String): String =
        "$mediaPath?u=${java.net.URLEncoder.encode(target, StandardCharsets.UTF_8)}"

    private fun headerValue(session: IHTTPSession, name: String): String? =
        session.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** Serve the bundled hls.js (browser HLS playback for the join page). */
    private fun serveHlsJs(method: Method): Response {
        val bytes = runCatching {
            javaClass.classLoader?.getResourceAsStream("hls.min.js")?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null || bytes.size == 0) return textResponse(Response.Status.NOT_FOUND, "Not found", method)
        val response = if (method == Method.HEAD) {
            newFixedLengthResponse(Response.Status.OK, "application/javascript", "")
        } else {
            newFixedLengthResponse(Response.Status.OK, "application/javascript", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        }
        response.addHeader("Content-Length", bytes.size.toString())
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Cache-Control", "public, max-age=86400")
        return response
    }

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

        /** HLS playlist content type (m3u8). */
        internal const val HLS_MIME = "application/vnd.apple.mpegurl"

        /** URI="..." attributes inside HLS tags (encryption keys, fMP4 maps). */
        private val HLS_TAG_URI = Regex("URI=\"([^\"]+)\"")

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
  html,body{height:100%;margin:0;background:#000;color:#eee;font-family:system-ui,-apple-system,sans-serif;overflow:hidden}
  #stage{height:100%;display:flex;flex-direction:column}
  video{width:100%;flex:1;background:#000;min-height:0}
  #bar{height:54px;display:flex;gap:8px;align-items:center;justify-content:center;padding:0 10px;font-size:14px;flex-wrap:nowrap}
  #bar button{background:#1c1c1e;color:#eee;border:1px solid #3a3a3c;border-radius:8px;padding:8px 12px;cursor:pointer;font-size:14px;min-width:44px;white-space:nowrap}
  #bar button:hover{background:#2c2c2e}
  #playBtn{min-width:56px}
  #scrub{flex:1;display:flex;align-items:center;gap:8px;min-width:0}
  input[type=range]{flex:1;accent-color:#6c5ce7;height:26px;min-width:0;margin:0}
  #time{font-variant-numeric:tabular-nums;white-space:nowrap;font-size:12px;opacity:.8}
  #stage:fullscreen,#stage:-webkit-full-screen{width:100%;height:100%;background:#000}
  #info{opacity:.7;font-size:12px;text-align:center;padding:4px 12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  #nameModal{position:fixed;inset:0;background:rgba(0,0,0,.74);display:none;align-items:center;justify-content:center;z-index:10}
  #nameCard{background:#161618;border:1px solid #2c2c2e;border-radius:14px;padding:20px;width:min(330px,88vw);position:relative;box-sizing:border-box}
  #nameCard h2{margin:0 0 4px;font-size:17px}
  #nameCard p{margin:0 0 14px;font-size:13px;opacity:.65;line-height:1.4}
  #nameCard input{width:100%;box-sizing:border-box;background:#1c1c1e;color:#eee;border:1px solid #3a3a3c;border-radius:8px;padding:10px;font-size:15px}
  #closeName{position:absolute;top:6px;right:10px;background:none;border:none;color:#999;font-size:18px;cursor:pointer;padding:6px;line-height:1}
  #saveName{width:100%;margin-top:12px;background:#6c5ce7;border:none;color:#fff;border-radius:8px;padding:10px;font-size:15px;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  #status{position:fixed;top:10px;left:50%;transform:translateX(-50%);background:#1c1c1e;border:1px solid #3a3a3c;padding:6px 14px;border-radius:999px;font-size:12px;z-index:5;display:none;max-width:90vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
</style>
</head>
<body>
<div id="stage">
  <video id="v" playsinline webkit-playsinline preload="auto"></video>
  <div id="bar">
    <button id="playBtn" onclick="togglePlay()">&#9654;</button>
    <button onclick="skip(-10)">-10</button>
    <button onclick="skip(10)">+10</button>
    <div id="scrub">
      <input type="range" id="seekBar" min="0" max="1" step="0.1" value="0" disabled oninput="onScrub()" onchange="onScrubEnd()">
      <span id="time">0:00</span>
    </div>
    <button id="fsBtn" onclick="toggleFullscreen()" title="Fullscreen">&#x26F6;</button>
  </div>
  <div id="info">Connecting&#8230;</div>
</div>
<div id="nameModal">
  <div id="nameCard">
    <button id="closeName" onclick="closeNamePrompt()" aria-label="Close">&#10005;</button>
    <h2>What should we call you?</h2>
    <p>Pick a name for this watch party — anything works, even emojis. Close to keep a random one.</p>
    <input id="nameInput" maxlength="20" autocomplete="off">
    <button id="saveName" onclick="saveName()"></button>
  </div>
</div>
<div id="status"></div>
<script src="/hls.min.js"></script>
<script>
var code = location.pathname.split('/').pop();
// The page is served over https through the Cloudflare tunnel — browsers block
// ws:// on an https page as mixed content, so mirror the page's scheme.
var ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/room/' + code);
var v = document.getElementById('v');
var info = document.getElementById('info');
var statusEl = document.getElementById('status');
var seekBar = document.getElementById('seekBar');
var timeEl = document.getElementById('time');
var playBtn = document.getElementById('playBtn');
var fsBtn = document.getElementById('fsBtn');

// Random name assigned immediately on load; the prompt offers to replace it.
var NAMES = ['Sakura','Neko','Senpai','Kami','Kaze','Hoshi','Tama','Rin','Yuki','Momo','Kuro','Aki','Kira','Sora','Hana'];
function randomName() { return NAMES[Math.floor(Math.random() * NAMES.length)] + (10 + Math.floor(Math.random() * 90)); }
var myName = randomName();

var hasBaseline = false;   // first sync applied (either the join replay or a live tick)
var lastUser = 0;          // ms of the last USER-initiated action (syncs back off for 800ms)
var pendingSeek = null;    // position to apply once the video has metadata
var mediaReady = false;    // loadedmetadata seen at least once
var fallbackDuration = 0;  // host-reported duration; survives unknown stream duration
var scrubbing = false;
var currentMedia = null;   // mediaUrl currently attached to the player
var hls = null;            // hls.js instance for HLS streams
var nativeRetriedHls = false;

// HLS (m3u8) can't play in a bare <video> on most browsers — route it through
// hls.js (MSE) instead. Segments are fetched through the room proxy (the
// playlist is rewritten server-side), so the host's headers + CORS apply.
function setVideoSource(url, kind) {
  if (hls) { hls.destroy(); hls = null; }
  nativeRetriedHls = false;
  var wantsHls = window.Hls && Hls.isSupported() &&
    (kind === 'hls' || /\.m3u8($|\?)/i.test(url) || /mpegurl/i.test(url));
  if (wantsHls) {
    hls = new Hls({ enableWorker: true, maxBufferLength: 30 });
    hls.loadSource(url);
    hls.attachMedia(v);
    hls.on(Hls.Events.ERROR, function (evt, data) {
      if (data && data.fatal) flashStatus('Stream error: ' + (data.details || 'unknown'));
    });
  } else {
    v.src = url;
  }
}

function send(m) { if (ws.readyState === 1) ws.send(JSON.stringify(m)); }
function userAction() { lastUser = Date.now(); }
// Heartbeat: the server drops sockets that send no frames for a while, and a
// watching guest is otherwise silent — a JSON ping every 15s keeps us alive.
setInterval(function () { send({type: 'ping'}); }, 15000);

function fmt(s) {
  s = Math.max(0, Math.floor(s || 0));
  var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  return (h ? h + ':' + ('0' + m).slice(-2) : m) + ':' + ('0' + sec).slice(-2);
}

function knownDuration() {
  if (isFinite(v.duration) && v.duration > 0) return v.duration;
  return fallbackDuration;
}

// Seek only when the video can take it; before metadata browsers silently
// drop currentTime assignments, so queue the latest target instead.
function applySeek(pos) {
  if (!isFinite(pos) || pos < 0) return;
  var dur = knownDuration();
  if (mediaReady && dur > 0) {
    v.currentTime = Math.min(pos, dur - 0.05);
  } else {
    pendingSeek = pos;
  }
}

// Some engines (notably WKWebView) discard a seek issued while the media is
// still coming up, so retry until the position actually sticks.
function applyPendingSeek() {
  if (pendingSeek == null) return;
  if (!mediaReady || knownDuration() <= 0) return;
  var target = pendingSeek;
  var tries = 0;
  (function trySeek() {
    var dur = knownDuration();
    v.currentTime = Math.min(target, dur - 0.05);
    setTimeout(function () {
      if (Math.abs(v.currentTime - target) > 0.3 && tries++ < 8) {
        trySeek(); // position was reset — push it again
      } else {
        pendingSeek = null;
        syncSeekBar();
      }
    }, 250);
  })();
}

function syncSeekBar() {
  var dur = knownDuration();
  if (dur > 0) {
    seekBar.max = dur;
    seekBar.disabled = false;
    if (!scrubbing) seekBar.value = v.currentTime;
    timeEl.textContent = fmt(v.currentTime) + ' / ' + fmt(dur);
  } else {
    seekBar.max = 1;
    seekBar.disabled = true;
    timeEl.textContent = fmt(v.currentTime);
  }
}

function togglePlay() {
  if (v.paused) {
    userAction(); send({type: 'play'});
    v.play().catch(function () {});
    applyPendingSeek();
  } else {
    userAction(); send({type: 'pause'}); v.pause();
  }
}

// Fullscreen — stage-level so the custom controls stay visible on phones;
// iOS Safari falls back to its native video player.
function toggleFullscreen() {
  var stage = document.getElementById('stage');
  if (!document.fullscreenElement && !document.webkitFullscreenElement) {
    if (stage.requestFullscreen) stage.requestFullscreen().catch(function () {});
    else if (stage.webkitRequestFullscreen) stage.webkitRequestFullscreen();
    else if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
  } else {
    if (document.exitFullscreen) document.exitFullscreen().catch(function () {});
    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
  }
}
function syncFsBtn() {
  var active = document.fullscreenElement || document.webkitFullscreenElement;
  fsBtn.innerHTML = active ? '&#10005;' : '&#x26F6;';
}
document.addEventListener('fullscreenchange', syncFsBtn);
document.addEventListener('webkitfullscreenchange', syncFsBtn);

function skip(delta) {
  var target = (v.currentTime || 0) + delta;
  var dur = knownDuration();
  if (dur > 0) target = Math.min(Math.max(0, target), dur - 0.05);
  userAction();
  applySeek(target);
  send({type: 'seek', pos: target});
  syncSeekBar();
}

function onScrub() {
  scrubbing = true;
  timeEl.textContent = fmt(parseFloat(seekBar.value)) + ' / ' + fmt(parseFloat(seekBar.max));
}
function onScrubEnd() {
  scrubbing = false;
  var pos = parseFloat(seekBar.value);
  userAction();
  applySeek(pos);
  send({type: 'seek', pos: pos});
  syncSeekBar();
}

function flashStatus(text) {
  statusEl.textContent = text;
  statusEl.style.display = 'block';
  clearTimeout(flashStatus._t);
  flashStatus._t = setTimeout(function () { statusEl.style.display = 'none'; }, 4000);
}

ws.onopen = function () {
  send({type: 'hello', name: myName});
  document.getElementById('nameInput').value = myName;
  document.getElementById('saveName').textContent = 'Join as ' + myName;
  document.getElementById('nameModal').style.display = 'flex';
  document.getElementById('nameInput').focus();
};
function closeNamePrompt() { document.getElementById('nameModal').style.display = 'none'; }
function saveName() {
  var name = document.getElementById('nameInput').value.trim().slice(0, 20);
  if (name) { myName = name; send({type: 'hello', name: myName}); }
  closeNamePrompt();
  flashStatus('Joining as ' + myName);
}
document.getElementById('nameInput').addEventListener('keydown', function (e) {
  if (e.key === 'Enter') { e.preventDefault(); saveName(); }
  if (e.key === 'Escape') { e.preventDefault(); closeNamePrompt(); }
});

ws.onmessage = function (e) {
  var m; try { m = JSON.parse(e.data); } catch (err) { return; }
  if (m.type === 'episode' && m.mediaUrl && currentMedia !== m.mediaUrl) {
    // New media: wait for its metadata before applying any position, and let
    // the next sync act as a fresh baseline for the new episode.
    pendingSeek = null;
    hasBaseline = false;
    currentMedia = m.mediaUrl;
    setVideoSource(m.mediaUrl, m.kind);
  } else if (m.type === 'episode' && !m.mediaUrl) {
    flashStatus('The host is playing a torrent — no stream to join here');
  }
  if (m.type === 'sync') {
    if (m.duration > 0) fallbackDuration = m.duration;
    // First sync is the baseline; afterwards a sync arriving right after the
    // user's own action must not fight it (their action was already sent).
    if (hasBaseline && Date.now() - lastUser < 800) return;
    hasBaseline = true;
    if (Math.abs((v.currentTime || 0) - m.pos) > 0.75) applySeek(m.pos);
    if (m.playing && v.paused) v.play().catch(function () {});
    if (!m.playing && !v.paused) v.pause();
    syncSeekBar();
  }
  if (m.type === 'play') { userAction(); v.play().catch(function () {}); }
  if (m.type === 'pause') { userAction(); v.pause(); }
  if (m.type === 'seek') { userAction(); applySeek(m.pos); }
  if (m.type === 'members') {
    info.textContent = (m.names && m.names.length ? m.names.join(', ') + ' \u00b7 ' : '') + m.count + ' watching \u00b7 room ' + code;
  }
  if (m.type === 'room_closed') flashStatus(m.reason || 'The host closed the room');
};

v.addEventListener('loadedmetadata', function () {
  mediaReady = true;
  applyPendingSeek();
  syncSeekBar();
});
v.addEventListener('canplay', applyPendingSeek);
v.addEventListener('durationchange', syncSeekBar);
v.addEventListener('timeupdate', function () { if (!scrubbing) syncSeekBar(); });
// Play/pause button icon follows the video. NOTE: no "echo user events" here
// — the custom controls send explicitly, and trusting media-event flags is
// unreliable across engines (some webviews mark programmatic play/pause/seek
// events as user-initiated, which would echo syncs back as a message storm).
v.addEventListener('play', function () { playBtn.innerHTML = '&#10074;&#10074;'; });
v.addEventListener('pause', function () { playBtn.innerHTML = '&#9654;'; });
v.addEventListener('error', function () {
  // Some sources deliver HLS without a m3u8-looking URL or content type —
  // retry once through hls.js before declaring the format unplayable.
  if (window.Hls && Hls.isSupported() && !hls && currentMedia && !nativeRetriedHls) {
    nativeRetriedHls = true;
    setVideoSource(currentMedia, 'hls');
    return;
  }
  flashStatus('Could not play this stream — the host may be playing a format browsers can\'t play (MKV/HEVC). Join from the Anikku app instead.');
});
ws.onclose = function () { info.textContent = 'Disconnected'; flashStatus('Disconnected'); };
</script>
</body>
</html>
""".trimIndent()
