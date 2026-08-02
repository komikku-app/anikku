package app.anikku.macos.platform.media

import fi.iki.elonen.NanoHTTPD
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Embedded HTTP server that serves local video files to mpv.
 *
 * The server intentionally binds only to loopback. It exposes files from
 * [downloadsDir] through the `/stream/<filename>` route and supports byte
 * ranges so mpv can seek without loading the whole video into memory.
 *
 * An HTTP server is used because mpv cannot reliably load local files via
 * `file://` on every supported macOS configuration.
 */
class MacOSHttpServer(
    private val downloadsDir: File,
    private val port: Int = 0, // 0 = auto-assign
) : NanoHTTPD("127.0.0.1", if (port > 0) port else 0) {

    /** Returns the actual port the server is listening on. */
    val actualPort: Int get() = listeningPort

    /** Whether the server is currently running. */
    var isRunning: Boolean = false
        private set

    private val canonicalDownloadsDir: File
        get() = downloadsDir.canonicalFile

    /** Start the HTTP server on a daemon background thread. */
    @Synchronized
    fun startServer() {
        if (isRunning) return
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            isRunning = true
            logger.info { "Local HTTP server started on loopback port $actualPort" }
        } catch (e: Exception) {
            isRunning = false
            logger.error(e) { "Failed to start local HTTP server" }
        }
    }

    /** Stop the HTTP server. Calling this repeatedly is safe. */
    @Synchronized
    fun stopServer() {
        if (!isRunning) return
        try {
            stop()
            logger.info { "Local HTTP server stopped" }
        } catch (e: Exception) {
            logger.warn(e) { "Error stopping HTTP server" }
        } finally {
            isRunning = false
        }
    }

    /**
     * Get a streamable HTTP URL for a local file.
     *
     * The file must be a regular file inside [downloadsDir]. The filename is
     * encoded as a single URL path segment; the server decodes it before
     * applying its canonical-path check.
     */
    fun getStreamUrl(file: File): String? {
        if (!isRunning || !isSafeMediaFile(file)) return null
        val encodedName = URLEncoder.encode(file.name, StandardCharsets.UTF_8).replace("+", "%20")
        return "http://127.0.0.1:$actualPort/stream/$encodedName"
    }

    /**
     * Download-ID URLs are intentionally disabled until a repository-backed
     * ID-to-file mapping exists. Returning null prevents callers from
     * advertising a route that cannot resolve an ID safely.
     */
    fun getStreamUrl(downloadId: Long): String? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (uri == "/health" || uri == "/") {
            return textResponse(Response.Status.OK, "Anikku local media server — OK", method)
        }

        if (uri.startsWith("/download/")) {
            return textResponse(
                Response.Status.NOT_FOUND,
                "Download-ID streaming is unavailable",
                method,
            )
        }

        if (!uri.startsWith("/stream/")) {
            return textResponse(Response.Status.NOT_FOUND, "Not found", method)
        }

        if (method != Method.GET && method != Method.HEAD) {
            return textResponse(Response.Status.METHOD_NOT_ALLOWED, "Method not allowed", method)
                .also { it.addHeader("Allow", "GET, HEAD") }
        }

        return serveFileStream(uri.removePrefix("/stream/"), session, method)
    }

    private fun serveFileStream(fileName: String, session: IHTTPSession, method: Method): Response {
        val file = findFile(fileName)
            ?: return textResponse(Response.Status.NOT_FOUND, "File not found", method)

        val fileLength = file.length()
        val rangeHeader = session.headers.entries
            .firstOrNull { (name, _) -> name.equals("range", ignoreCase = true) }
            ?.value
        val range = parseRange(rangeHeader, fileLength)

        if (range is RangeResult.Invalid) {
            return rangeNotSatisfiable(fileLength)
        }

        val selectedRange = range as? RangeResult.Valid
        val start = selectedRange?.start ?: 0L
        val end = selectedRange?.end ?: (fileLength - 1L)
        val contentLength = if (fileLength == 0L) 0L else end - start + 1L
        val status = if (selectedRange == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT

        val response = if (method == Method.HEAD || contentLength == 0L) {
            newFixedLengthResponse(status, getMimeType(file.extension), "")
        } else {
            try {
                val stream = FileInputStream(file)
                if (!skipFully(stream, start)) {
                    stream.close()
                    return textResponse(Response.Status.NOT_FOUND, "File is no longer available", method)
                }
                newFixedLengthResponse(status, getMimeType(file.extension), stream, contentLength)
            } catch (e: IOException) {
                logger.warn(e) { "Unable to open local media file: ${file.name}" }
                return textResponse(Response.Status.NOT_FOUND, "File is no longer available", method)
            }
        }

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", contentLength.toString())
        if (selectedRange != null) {
            response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
        }
        response.addHeader("Content-Disposition", "inline; filename=\"${safeHeaderFileName(file.name)}\"")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        return response
    }

    private fun findFile(fileName: String): File? {
        if (fileName.isBlank() || fileName == "." || fileName == "..") return null

        val candidate = File(canonicalDownloadsDir, fileName)
        val canonicalRoot = canonicalDownloadsDir
        val canonicalCandidate = try {
            candidate.canonicalFile
        } catch (_: IOException) {
            return null
        }
        val rootPrefix = canonicalRoot.path + File.separator

        if (!canonicalCandidate.path.startsWith(rootPrefix) || !canonicalCandidate.isFile) {
            return null
        }
        return canonicalCandidate
    }

    private fun isSafeMediaFile(file: File): Boolean {
        if (!file.isFile) return false
        val root = try { canonicalDownloadsDir } catch (_: IOException) { return false }
        val candidate = try { file.canonicalFile } catch (_: IOException) { return false }
        return candidate.path.startsWith(root.path + File.separator)
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

    private fun rangeNotSatisfiable(fileLength: Long): Response {
        return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain; charset=utf-8", "")
            .also {
                it.addHeader("Accept-Ranges", "bytes")
                it.addHeader("Content-Range", "bytes */$fileLength")
                it.addHeader("Content-Length", "0")
            }
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

    private fun safeHeaderFileName(name: String): String = name.replace(Regex("[\\r\\n\"]"), "_")

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
                    val end = if (endText.isEmpty()) {
                        fileLength - 1L
                    } else {
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

    /** Get MIME type for common video file extensions. */
    internal fun getMimeType(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "mpg", "mpeg" -> "video/mpeg"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "ogv" -> "video/ogg"
        else -> "application/octet-stream"
    }
}
