package eu.kanade.tachiyomi.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import androidx.core.net.toUri
import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.net.URLConnection

class LocalHttpServer(
    port: Int,
    private val contentResolver: ContentResolver,
) : NanoHTTPD(port) {

    @SuppressLint("Recycle")
    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        val uriParam = params["uri"]?.get(0) ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "text/plain",
            "Missing uri parameter",
        )

        val uri = try {
            uriParam.toUri()
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid URI")
        }

        val mimeType = URLConnection.guessContentTypeFromName(uri.toString()) ?: "application/octet-stream"

        // Open the file like an inputstream and obtain its size
        val assetFileDescriptor = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }

        val fileLength = assetFileDescriptor?.length ?: -1L

        // Verify if the "Range" header is included
        val rangeHeader = session.headers["range"]
        if (rangeHeader != null && fileLength > 0) {
            try {
                // Expect format "bytes=start-end"
                val range = rangeHeader.replace("bytes=", "").split("-")
                val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
                // If the end is not specified, we use the file size - 1
                val end = range.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
                val length = end - start + 1

                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.skip(start)

                // Reply with partial content
                val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, length)
                response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                response.addHeader("Accept-Ranges", "bytes")
                return response
            } catch (e: Exception) {
                // In case of error, the full file is sent
                logcat(LogPriority.ERROR, e) { "Error processing Range header" }
            }
        }

        // Without Range Header, send the full file
        val inputStream = contentResolver.openInputStream(uri)
        return if (inputStream != null) {
            val response = newChunkedResponse(Response.Status.OK, mimeType, inputStream)
            response.addHeader("Accept-Ranges", "bytes")
            response
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }
    }
}

object LocalHttpServerHolder {
    const val PORT = 8181
}
