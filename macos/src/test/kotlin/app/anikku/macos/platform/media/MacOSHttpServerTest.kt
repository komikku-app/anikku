package app.anikku.macos.platform.media

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * Deterministic integration tests for the localhost media server.
 *
 * The fixture is deliberately small, but requests go through a real NanoHTTPD
 * socket so status codes, headers, URL decoding, range handling, and stream
 * closure are tested together.
 */
class MacOSHttpServerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mediaDir: File
    private lateinit var mediaFile: File
    private lateinit var server: MacOSHttpServer
    private val fixtureBytes = ByteArray(300) { index -> (index % 251).toByte() }

    @BeforeEach
    fun setUp() {
        mediaDir = tempDir.resolve("videos").toFile().apply { mkdirs() }
        mediaFile = File(mediaDir, "episode one.mp4").apply { writeBytes(fixtureBytes) }
        server = MacOSHttpServer(mediaDir)
        server.startServer()
        assertTrue(server.isRunning)
        assertTrue(server.actualPort > 0)
    }

    @AfterEach
    fun tearDown() {
        server.stopServer()
    }

    @Test
    fun `server binds loopback and builds encoded file URL`() {
        val url = server.getStreamUrl(mediaFile)

        assertNotNull(url)
        assertTrue(url!!.startsWith("http://127.0.0.1:${server.actualPort}/stream/"))
        assertTrue(url.endsWith("episode%20one.mp4"))
    }

    @Test
    fun `complete GET returns file bytes and media headers`() {
        val response = request(server.getStreamUrl(mediaFile)!!)

        assertEquals(200, response.status)
        assertArrayEquals(fixtureBytes, response.body)
        assertEquals("video/mp4", response.header("Content-Type"))
        assertEquals(fixtureBytes.size.toString(), response.header("Content-Length"))
        assertEquals("bytes", response.header("Accept-Ranges"))
    }

    @Test
    fun `HEAD returns headers without loading a response body`() {
        val response = request(server.getStreamUrl(mediaFile)!!, method = "HEAD")

        assertEquals(200, response.status)
        assertEquals(0, response.body.size)
        assertEquals("video/mp4", response.header("Content-Type"))
        assertEquals(fixtureBytes.size.toString(), response.header("Content-Length"))
        assertEquals("bytes", response.header("Accept-Ranges"))
    }

    @Test
    fun `empty file returns a successful zero-length response`() {
        val emptyFile = File(mediaDir, "empty.mp4").apply { createNewFile() }

        val response = request(server.getStreamUrl(emptyFile)!!)

        assertEquals(200, response.status)
        assertEquals(0, response.body.size)
        assertEquals("0", response.header("Content-Length"))
        assertEquals("bytes", response.header("Accept-Ranges"))
    }

    @Test
    fun `explicit range returns 206 and exact bytes`() {
        val response = request(server.getStreamUrl(mediaFile)!!, range = "bytes=0-99")

        assertEquals(206, response.status)
        assertArrayEquals(fixtureBytes.copyOfRange(0, 100), response.body)
        assertEquals("bytes 0-99/${fixtureBytes.size}", response.header("Content-Range"))
        assertEquals("100", response.header("Content-Length"))
    }

    @Test
    fun `open ended range returns remaining bytes`() {
        val response = request(server.getStreamUrl(mediaFile)!!, range = "bytes=100-")

        assertEquals(206, response.status)
        assertArrayEquals(fixtureBytes.copyOfRange(100, fixtureBytes.size), response.body)
        assertEquals("bytes 100-299/${fixtureBytes.size}", response.header("Content-Range"))
        assertEquals("200", response.header("Content-Length"))
    }

    @Test
    fun `suffix range returns final bytes`() {
        val response = request(server.getStreamUrl(mediaFile)!!, range = "bytes=-100")

        assertEquals(206, response.status)
        assertArrayEquals(fixtureBytes.copyOfRange(200, 300), response.body)
        assertEquals("bytes 200-299/${fixtureBytes.size}", response.header("Content-Range"))
        assertEquals("100", response.header("Content-Length"))
    }

    @Test
    fun `range end is clamped to file length`() {
        val response = request(server.getStreamUrl(mediaFile)!!, range = "bytes=250-999")

        assertEquals(206, response.status)
        assertArrayEquals(fixtureBytes.copyOfRange(250, 300), response.body)
        assertEquals("bytes 250-299/${fixtureBytes.size}", response.header("Content-Range"))
    }

    @Test
    fun `invalid reversed and out of range requests return 416`() {
        listOf("bytes=100-50", "bytes=300-", "bytes=-0", "items=0-10").forEach { range ->
            val response = request(server.getStreamUrl(mediaFile)!!, range = range)
            assertEquals(416, response.status, "range=$range")
            assertEquals("bytes */${fixtureBytes.size}", response.header("Content-Range"))
            assertEquals("bytes", response.header("Accept-Ranges"))
        }
    }

    @Test
    fun `missing files directories traversal and absolute paths are rejected`() {
        val outside = File(tempDir.toFile(), "outside.mp4").apply { writeBytes(fixtureBytes) }
        val missing = request("http://127.0.0.1:${server.actualPort}/stream/missing.mp4")
        val directory = request("http://127.0.0.1:${server.actualPort}/stream/videos")
        val traversal = request("http://127.0.0.1:${server.actualPort}/stream/%2e%2e/outside.mp4")
        val absolute = request(
            "http://127.0.0.1:${server.actualPort}/stream/${outside.absolutePath.replace("/", "%2F")}",
        )

        assertEquals(404, missing.status)
        assertEquals(404, directory.status)
        assertEquals(404, traversal.status)
        assertEquals(404, absolute.status)
    }

    @Test
    fun `symlink escaping media root is rejected`() {
        val outside = tempDir.resolve("outside.mp4").apply { Files.write(this, fixtureBytes) }
        val link = mediaDir.toPath().resolve("linked.mp4")
        Files.createSymbolicLink(link, outside)

        val response = request("http://127.0.0.1:${server.actualPort}/stream/linked.mp4")

        assertEquals(404, response.status)
    }

    @Test
    fun `unsupported methods are rejected and health endpoint remains available`() {
        val post = request(server.getStreamUrl(mediaFile)!!, method = "POST")
        val health = request("http://127.0.0.1:${server.actualPort}/health")

        assertEquals(405, post.status)
        assertEquals("GET, HEAD", post.header("Allow"))
        assertEquals(200, health.status)
        assertTrue(health.body.toString(Charsets.UTF_8).contains("OK"))
    }

    @Test
    fun `stop shuts down server and repeated stop is safe`() {
        val url = server.getStreamUrl(mediaFile)!!
        assertEquals(200, request(url).status)

        server.stopServer()
        server.stopServer()
        assertFalse(server.isRunning)

        assertThrowsConnectionFailure(url)
    }

    @Test
    fun `mime type mapping covers common and unknown extensions`() {
        assertEquals("video/mp4", server.getMimeType("MP4"))
        assertEquals("video/x-matroska", server.getMimeType("mkv"))
        assertEquals("video/webm", server.getMimeType("webm"))
        assertEquals("video/mp2t", server.getMimeType("ts"))
        assertEquals("application/octet-stream", server.getMimeType("unknown"))
    }

    private fun request(url: String, method: String = "GET", range: String? = null): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        if (range != null) connection.setRequestProperty("Range", range)

        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { (name, _) -> name!! }
                .mapValues { (_, values) -> values.firstOrNull() }
            HttpResponse(status, body, headers)
        } finally {
            connection.disconnect()
        }
    }

    private fun assertThrowsConnectionFailure(url: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        try {
            val status = connection.responseCode
            assertTrue(status >= 400, "A stopped server must not return a successful response")
        } catch (_: Exception) {
            // Connection refused is the expected outcome after shutdown.
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val status: Int,
        val body: ByteArray,
        private val headers: Map<String, String?>,
    ) {
        fun header(name: String): String? = headers.entries
            .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value
    }
}
