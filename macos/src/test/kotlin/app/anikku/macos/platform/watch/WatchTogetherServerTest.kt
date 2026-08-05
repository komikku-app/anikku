package app.anikku.macos.platform.watch

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WatchTogetherServerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var server: WatchTogetherServer
    private val client = OkHttpClient()

    @BeforeEach
    fun setUp() {
        server = WatchTogetherServer(preferredPort = 0)
        server.startServer()
    }

    @AfterEach
    fun tearDown() {
        server.stopServer()
    }

    @Test
    fun `joins relay messages and track membership`() {
        val info = server.createRoom(episode(), null)!!
        val host = connect(info.code)
        host.awaitOpen()
        val guest = connect(info.code)
        guest.awaitOpen()
        host.awaitMembers(2)
        guest.awaitMembers(2)

        // Every member receives the stored episode on open — drain it first.
        assertEquals(episode(), host.awaitMessage())
        assertEquals(episode(), guest.awaitMessage())

        guest.send(WtProtocol.encode(WtMessage.Play()))
        assertEquals(WtMessage.Play(), host.awaitMessage())
        host.send(WtProtocol.encode(WtMessage.Seek(99.0)))
        assertEquals(WtMessage.Seek(99.0), guest.awaitMessage())
        // Senders do not receive their own messages.
        assertEquals(null, guest.pollMessage(300))
    }

    @Test
    fun `late joiner immediately receives the stored episode`() {
        val info = server.createRoom(episode(mediaUrl = "http://192.168.1.10:18234/media/ABC123/m1"), null)!!
        val guest = connect(info.code)
        guest.awaitOpen()
        assertEquals(
            WtMessage.Episode(title = "Frieren", name = "Ep 3", number = 3.0, mediaUrl = "http://192.168.1.10:18234/media/ABC123/m1", kind = "direct", duration = 1440.0),
            guest.awaitMessage(),
        )
    }

    @Test
    fun `unknown room code is rejected at the websocket handshake`() {
        val socket = connect("NOPE42")
        socket.awaitFailure()
    }

    @Test
    fun `browser join page is served for existing rooms only`() {
        val info = server.createRoom(episode(), null)!!
        val ok = get("http://127.0.0.1:${server.actualPort}/room/${info.code}")
        assertEquals(200, ok.first)
        assertTrue(ok.second.contains("Watch Together"))

        val missing = get("http://127.0.0.1:${server.actualPort}/room/ZZZ999")
        assertEquals(404, missing.first)
    }

    @Test
    fun `serves a local media file with byte ranges`() {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val file = File(tempDir, "episode.mkv").apply { writeBytes(payload) }
        val info = server.createRoom(episode(), WatchTogetherServer.MediaHandle(id = "m1", localFile = file))!!

        // Full fetch.
        val full = get("http://127.0.0.1:${server.actualPort}/media/${info.code}/m1")
        assertEquals(200, full.first)
        assertArrayEquals(payload, full.third)

        // Range fetch — 206 with the requested window only.
        val range = getRange("http://127.0.0.1:${server.actualPort}/media/${info.code}/m1", "bytes=100-199")
        assertEquals(206, range.first)
        assertEquals("bytes 100-199/${payload.size}", range.second)
        assertArrayEquals(payload.copyOfRange(100, 200), range.third)

        // Unknown media id.
        assertEquals(404, get("http://127.0.0.1:${server.actualPort}/media/${info.code}/nope").first)
    }

    @Test
    fun `proxies an upstream url with range and header passthrough`() {
        val payload = "proxied-video-bytes-0123456789"
        val upstream = MockWebServer()
        upstream.start(0)
        try {
            upstream.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 10-19/29").setBody("0123456789"))
            val info = server.createRoom(
                episode(),
                WatchTogetherServer.MediaHandle(
                    id = "m2",
                    upstreamUrl = upstream.url("/stream.mp4").toString(),
                    upstreamHeaders = mapOf("Referer" to "https://source.example/"),
                ),
            )!!

            val range = getRange("http://127.0.0.1:${server.actualPort}/media/${info.code}/m2", "bytes=10-19")
            assertEquals(206, range.first)
            assertEquals("bytes 10-19/29", range.second)
            assertEquals("0123456789", range.third.toString(Charsets.UTF_8))

            val upstreamRequest = upstream.takeRequest()
            assertEquals("bytes=10-19", upstreamRequest.getHeader("Range"))
            assertEquals("https://source.example/", upstreamRequest.getHeader("Referer"))
        } finally {
            upstream.shutdown()
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun episode(mediaUrl: String? = null) = WtMessage.Episode(
        title = "Frieren",
        name = "Ep 3",
        number = 3.0,
        mediaUrl = mediaUrl,
        kind = "direct",
        duration = 1440.0,
    )

    private fun connect(code: String): TestSocket {
        val socket = TestSocket()
        client.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:${server.actualPort}/room/$code").build(),
            socket,
        )
        return socket
    }

    private fun get(url: String): Triple<Int, String, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            Triple(connection.responseCode, bytes.toString(Charsets.UTF_8), bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun getRange(url: String, range: String): Triple<Int, String, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", range)
        return try {
            val bytes = connection.inputStream.use { it.readBytes() }
            Triple(connection.responseCode, connection.getHeaderField("Content-Range") ?: "", bytes)
        } finally {
            connection.disconnect()
        }
    }

    private class TestSocket : WebSocketListener() {
        private val opened = CountDownLatch(1)
        private val failed = CountDownLatch(1)
        private val messages = LinkedBlockingQueue<WtMessage>()
        private val members = AtomicInteger(-1)
        private val failureMessage = AtomicReference<String?>(null)
        @Volatile
        private var socket: WebSocket? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            webSocket.send(WtProtocol.encode(WtMessage.Hello("test")))
            opened.countDown()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            WtProtocol.decode(text)?.let { message ->
                if (message is WtMessage.Members) {
                    members.set(message.count)
                } else {
                    messages.add(message)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            failureMessage.set(response?.code?.toString() ?: t.message)
            failed.countDown()
        }

        fun awaitOpen() {
            assertTrue(opened.await(5, TimeUnit.SECONDS), "websocket did not open")
        }

        fun awaitFailure() {
            assertTrue(failed.await(5, TimeUnit.SECONDS), "websocket should have failed")
            assertNotNull(failureMessage.get())
        }

        fun send(text: String) {
            assertTrue(socket!!.send(text))
        }

        fun awaitMessage(): WtMessage =
            messages.poll(5, TimeUnit.SECONDS) ?: fail("Timed out waiting for a message")

        fun pollMessage(timeoutMs: Long): WtMessage? = messages.poll(timeoutMs, TimeUnit.MILLISECONDS)

        fun awaitMembers(expected: Int): Int {
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                if (members.get() == expected) return expected
                Thread.sleep(25)
            }
            return fail("Timed out waiting for $expected members (last=${members.get()})")
        }
    }
}
