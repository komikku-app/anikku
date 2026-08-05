package app.anikku.macos.platform.watch

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Per-room tunnel lifecycle: a fake `cloudflared` script stands in for the
 * real binary (it prints a quick-tunnel URL derived from its PID, so every
 * spawn is a new "generation"). Verifies that a room owns its tunnel and
 * [WatchTogetherSession.leave] drops it.
 */
class WatchTogetherTunnelLifecycleTest {

    private lateinit var server: WatchTogetherServer
    private var tunnel: WatchTogetherTunnel? = null
    private var host: WatchTogetherSession? = null

    @BeforeEach
    fun setUp() {
        server = WatchTogetherServer(preferredPort = 0)
        server.startServer()
    }

    @AfterEach
    fun tearDown() {
        host?.close()
        tunnel?.close()
        server.stopServer()
    }

    /** A fake cloudflared that reports a PID-unique tunnel URL and stays alive. */
    private fun fakeCloudflared(dir: File): File {
        val script = File(dir, "cloudflared")
        script.writeText(
            """
            #!/bin/sh
            echo "Your quick Tunnel has been created! Visit it at (trycloudflare.com) https://fake-tunnel-$$.trycloudflare.com"
            trap 'exit 0' TERM
            while true; do sleep 1; done
            """.trimIndent() + "\n",
        )
        check(script.setExecutable(true)) { "Unable to make fake cloudflared executable" }
        return script
    }

    @Test
    fun `the room tunnel is dropped when the host leaves`() = runBlocking {
        val dir = Files.createTempDirectory("wt-tunnel-").toFile()
        val t = WatchTogetherTunnel(binary = fakeCloudflared(dir))
        tunnel = t
        val h = WatchTogetherSession(httpClient = okhttp3.OkHttpClient(), sessionName = "Host")
        host = h

        val url = t.start(server.actualPort)
            ?: fail("fake tunnel did not establish")
        assertTrue(t.isRunning)
        assertTrue(url.startsWith("https://fake-tunnel-"))

        assertTrue(
            h.startRoom(
                episode(),
                WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"),
                server,
                tunnelUrl = url,
                tunnel = t,
            ),
        )
        assertEquals("$url/room/${h.roomCode.value}", h.joinUrl.value)

        h.leave()
        assertFalse(t.isRunning, "the room's tunnel must be dropped when the host leaves")
        assertEquals(null, t.url.value)
    }

    @Test
    fun `each new room gets a fresh tunnel generation`() = runBlocking {
        val dir = Files.createTempDirectory("wt-tunnel-").toFile()
        val t = WatchTogetherTunnel(binary = fakeCloudflared(dir))
        tunnel = t
        val h = WatchTogetherSession(httpClient = okhttp3.OkHttpClient(), sessionName = "Host")
        host = h

        val first = t.start(server.actualPort) ?: fail("first tunnel did not establish")
        assertTrue(
            h.startRoom(
                episode(),
                WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"),
                server,
                tunnelUrl = first,
                tunnel = t,
            ),
        )
        val firstUrl = t.url.value!!
        h.leave()
        assertFalse(t.isRunning)

        // Same episode, same host — a new room must generate a NEW tunnel.
        val second = t.start(server.actualPort) ?: fail("second tunnel did not establish")
        assertNotEquals(first, second, "a new room must not reuse the previous tunnel URL")
        assertTrue(
            h.startRoom(
                episode(),
                WatchTogetherSession.MediaSpec.Url("http://example.com/v.mp4"),
                server,
                tunnelUrl = second,
                tunnel = t,
            ),
        )
        assertTrue(t.isRunning)
        h.leave()
    }

    private fun episode() = WtMessage.Episode(
        title = "Frieren",
        name = "Ep 3",
        number = 3.0,
        kind = "direct",
        duration = 1440.0,
    )
}
