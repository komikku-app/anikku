package app.anikku.macos.platform.watch

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * FULL INTERNET end-to-end test of Watch Together — the real deal, not
 * loopback-only.
 *
 * A genuine Cloudflare quick tunnel (the bundled cloudflared binary) fronts
 * the room server, and a guest joins through the PUBLIC wss link, exactly like
 * a friend on another network would. The traffic really leaves the machine,
 * crosses the public internet to Cloudflare's edge, and comes back through
 * the tunnel. Covered:
 *
 *   1. tunnel establishment (fresh URL)
 *   2. browser join page reachable over public https
 *   3. guest joins over wss via the share link
 *   4. control relay (pause) across the tunnel
 *   5. media proxied over https with Range + host header injection
 *   6. host leave → room_closed for the guest, tunnel dropped
 *   7. a new room generates a NEW tunnel URL
 *
 * Requires network access + the build-time cloudflared binary; run with the
 * nativeWatchTogetherE2ETest Gradle task (sets anikku.test.cloudflared.bin).
 */
class WatchTogetherInternetE2ETest {

    companion object {
        /**
         * A fresh trycloudflare hostname takes a few seconds to propagate. The
         * JVM otherwise caches the first (pre-propagation) resolution failure
         * for its negative TTL, so retries keep failing. Disable the cache so
         * every attempt re-resolves; the test polls until DNS is live.
         */
        @BeforeAll
        @JvmStatic
        fun disableJvmDnsCaching() {
            java.security.Security.setProperty("networkaddress.cache.ttl", "0")
            java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")
        }
    }

    private lateinit var server: WatchTogetherServer
    private var tunnel: WatchTogetherTunnel? = null
    private var host: WatchTogetherSession? = null
    private var guest: WatchTogetherSession? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @BeforeEach
    fun setUp() {
        server = WatchTogetherServer(preferredPort = 0)
        server.startServer()
    }

    @AfterEach
    fun tearDown() {
        guest?.close()
        host?.close()
        tunnel?.close()
        server.stopServer()
    }

    @Test
    fun `internet room works end to end through a real cloudflare tunnel`() = runBlocking {
        val binary = System.getProperty("anikku.test.cloudflared.bin")?.let(::File)
        assumeTrue(binary?.isFile == true, "Run the nativeWatchTogetherE2ETest Gradle task to provision the helper")

        // A local "source" that serves episode bytes only when the host's
        // header is present — proves header injection + Range passthrough
        // survive the trip through the tunnel.
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val upstream = MockWebServer()
        upstream.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("X-Token") != "room-secret") {
                    return MockResponse().setResponseCode(403).setBody("forbidden")
                }
                val range = request.getHeader("Range")
                if (range != null && range.startsWith("bytes=")) {
                    val window = range.removePrefix("bytes=")
                    val start = window.substringBefore('-').toInt()
                    val end = window.substringAfter('-').toInt()
                    val slice = payload.copyOfRange(start, end + 1)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setHeader("Accept-Ranges", "bytes")
                        .setBody(Buffer().write(slice))
                }
                return MockResponse().setBody(Buffer().write(payload))
            }
        }
        upstream.start()
        try {
            // ---- 1. Host: start the room over the internet (fresh tunnel) ----
            val t = WatchTogetherTunnel(binary = binary!!)
            tunnel = t
            val h = WatchTogetherSession(httpClient = client, sessionName = "Host")
            host = h
            val hostCalls = CopyOnWriteArrayList<String>()
            h.onControl = { message ->
                when (message) {
                    is WtMessage.Pause -> hostCalls += "pause"
                    is WtMessage.Play -> hostCalls += "play"
                    is WtMessage.Seek -> hostCalls += "seek:${message.pos}"
                    else -> Unit
                }
            }
            var pushed: WtMessage.Episode? = null

            val g = WatchTogetherSession(httpClient = client, sessionName = "Guest")
            guest = g
            g.onEpisode = { pushed = it }

            // Cloudflare quick tunnels are created on demand and have no
            // uptime guarantee: under repeated use the edge can stall URL
            // assignment or DNS publication for a minute+. Retry a few times
            // before declaring failure.
            val publicBase = establishTunnel(t, server.actualPort)
            assertTrue(publicBase.startsWith("https://"), "expected an https tunnel URL, got $publicBase")
            assertTrue(publicBase.contains("trycloudflare.com"), "expected a trycloudflare URL, got $publicBase")

            assertTrue(
                h.startRoom(
                    episode(title = "Frieren"),
                    WatchTogetherSession.MediaSpec.Url(
                        upstream.url("/episode.mp4").toString(),
                        mapOf("X-Token" to "room-secret"),
                    ),
                    server,
                    tunnelUrl = publicBase,
                    tunnel = t,
                ),
            )
            val code = h.roomCode.value ?: fail("host got no room code")
            assertEquals("$publicBase/room/$code", h.joinUrl.value)
            assertTrue(t.isRunning)

            // ---- 2. Browser join page reachable over public https ----
            awaitHttp("$publicBase/room/$code", expectedContains = "Watch Together")

            // ---- 3. Guest joins through the PUBLIC link (real internet trip) ----
            g.joinRoom("$publicBase/room/$code")
            awaitState("host sees the guest") { h.memberCount.value == 2 }

            // ---- 4. Control relay across the tunnel ----
            g.sendControl(WtMessage.Pause())
            awaitState("host received the guest's pause") { hostCalls.contains("pause") }

            // ---- 5. Episode + media over the tunnel (Range + headers) ----
            val mediaUrl = awaitState("guest received the episode") { pushed?.mediaUrl }
            assertNotNull(mediaUrl)
            assertTrue(mediaUrl!!.startsWith(publicBase), "media must be served through the tunnel, got $mediaUrl")
            val range = fetch(mediaUrl, range = "bytes=0-99")
            assertEquals(206, range.code, "expected a 206 range response through the tunnel")
            assertArrayEquals(payload.copyOfRange(0, 100), range.bytes, "media bytes must survive the tunnel round trip")

            // ---- 6. Host leaves: guest gets room_closed, tunnel is dropped ----
            h.leave()
            awaitState("guest role resets after host leaves") { g.role.value == WatchTogetherSession.Role.NONE }
            assertEquals("The host closed the room", g.status.value)
            assertFalse(t.isRunning, "the tunnel must be dropped when the room ends")

            // ---- 7. A new room gets a FRESH tunnel generation ----
            val second = establishTunnel(t, server.actualPort)
            assertNotEquals(publicBase, second, "each room must get a new tunnel generation")
            assertTrue(t.isRunning)
        } finally {
            upstream.shutdown()
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun episode(title: String = "Frieren") = WtMessage.Episode(
        title = title,
        name = "Ep 3",
        number = 3.0,
        kind = "direct",
        duration = 1440.0,
    )

    /** Poll [block] until non-null or [timeoutMs] elapses; returns the value. */
    private fun <T> awaitState(
        description: String,
        timeoutMs: Long = 45_000,
        block: () -> T?,
    ): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            block()?.let { return it }
            Thread.sleep(300)
        }
        throw AssertionError("Timed out waiting for: $description")
    }

    /** Wait until [host] resolves (fresh tunnel hostnames propagate slowly). */
    private fun awaitDns(host: String, timeoutMs: Long = 60_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = "no attempt"
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.InetAddress.getAllByName(host)
                return
            } catch (e: java.net.UnknownHostException) {
                last = e.message.orEmpty()
            }
            Thread.sleep(300)
        }
        throw AssertionError("Timed out waiting for DNS: $host (last: $last)")
    }

    /**
     * Start the tunnel AND wait until its hostname actually resolves, retrying
     * from scratch. Cloudflare quick tunnels have no uptime guarantee: under
     * repeated creation the edge can stall URL assignment or DNS publication,
     * so one fresh attempt usually succeeds even when the previous one didn't.
     */
    private suspend fun establishTunnel(t: WatchTogetherTunnel, port: Int): String {
        var lastFailure = "no attempt"
        repeat(3) { attempt ->
            val url = t.start(port, timeoutSeconds = 60)
            if (url != null) {
                val host = java.net.URI(url).host
                if (host != null) {
                    try {
                        awaitDns(host, timeoutMs = 45_000)
                        return url
                    } catch (e: AssertionError) {
                        lastFailure = "URL assigned but DNS never appeared (attempt ${attempt + 1})"
                    }
                } else {
                    lastFailure = "URL had no host (attempt ${attempt + 1})"
                }
                t.stop()
            } else {
                lastFailure = "cloudflared did not assign a URL (attempt ${attempt + 1})"
            }
            delay(10_000)
        }
        throw AssertionError(
            "Tunnel never became reachable after 3 attempts: $lastFailure. " +
                "Cloudflare quick tunnels are created on demand with no uptime guarantee and can " +
                "be rate-limited under repeated creation — wait a few minutes and rerun.",
        )
    }

    /**
     * Fetch [url] over https until it responds with [expectedContains] in the
     * body. A fresh trycloudflare hostname can take a few seconds to route
     * through Cloudflare's edge, so transient failures are retried.
     */
    private fun awaitHttp(url: String, expectedContains: String, timeoutMs: Long = 60_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastAttempt = "no attempt"
        while (System.currentTimeMillis() < deadline) {
            try {
                client.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").build())
                    .execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        lastAttempt = "code=${response.code} body[0..80]=${body.take(80)}"
                        if (response.code == 200 && body.contains(expectedContains)) return
                    }
            } catch (e: IOException) {
                // hostname not routing yet — retry
                lastAttempt = "IOException(${e.javaClass.simpleName}): ${e.message} :: ${e.stackTrace.firstOrNull()?.toString()}"
            }
            Thread.sleep(500)
        }
        throw AssertionError("Timed out fetching $url (expected '$expectedContains'); last attempt: $lastAttempt")
    }

    private data class HttpResponse(val code: Int, val bytes: ByteArray)

    private fun fetch(url: String, range: String? = null): HttpResponse {
        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (range != null) builder.header("Range", range)
        client.newCall(builder.build()).execute().use { response ->
            return HttpResponse(response.code, response.body?.bytes() ?: ByteArray(0))
        }
    }
}
