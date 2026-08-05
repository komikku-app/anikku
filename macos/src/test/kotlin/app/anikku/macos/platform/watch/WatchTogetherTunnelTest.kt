package app.anikku.macos.platform.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * URL-parsing tests for the bundled cloudflared quick tunnel. No process is
 * spawned — [WatchTogetherTunnel.parseTunnelUrl] runs on real log lines.
 */
class WatchTogetherTunnelTest {

    @Test
    fun `extracts the tunnel url from the ready banner`() {
        val line = "Your quick Tunnel has been created! Visit it at (trycloudflare.com) https://anime-night-2026.trycloudflare.com"
        assertEquals(
            "https://anime-night-2026.trycloudflare.com",
            WatchTogetherTunnel.parseTunnelUrl(line),
        )
    }

    @Test
    fun `extracts the url from a connection-established log line`() {
        val line = "INF Registered tunnel connection conns=1 https://k3t7-watch-party.trycloudflare.com"
        assertEquals(
            "https://k3t7-watch-party.trycloudflare.com",
            WatchTogetherTunnel.parseTunnelUrl(line),
        )
    }

    @Test
    fun `extracts the url from the boxed banner cloudflared prints`() {
        val line = "INF |  Your quick Tunnel has been created! Visit it at (trycloudflare.com) https://abc-123-def.trycloudflare.com |"
        assertEquals(
            "https://abc-123-def.trycloudflare.com",
            WatchTogetherTunnel.parseTunnelUrl(line),
        )
    }

    @Test
    fun `returns null when no tunnel url is present`() {
        assertNull(WatchTogetherTunnel.parseTunnelUrl("INF Starting tunnel"))
        assertNull(WatchTogetherTunnel.parseTunnelUrl("ERR failed to connect to the edge"))
        assertNull(WatchTogetherTunnel.parseTunnelUrl(""))
    }
}
