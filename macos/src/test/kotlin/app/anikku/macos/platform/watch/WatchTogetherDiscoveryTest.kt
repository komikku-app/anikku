package app.anikku.macos.platform.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket

class WatchTogetherDiscoveryTest {

    @Test
    fun `guest finds a host advertising the same room code`() {
        val listenPort = ServerSocket(0).use { it.localPort }
        val beacon = WatchTogetherDiscovery.advertise(
            code = "ABC123",
            tcpPort = 18234,
            name = "Ernest",
            target = InetAddress.getByName("127.0.0.1"),
            targetPort = listenPort,
        )
        try {
            val found = WatchTogetherDiscovery.findHost("ABC123", timeoutMs = 5_000, port = listenPort)
            assertEquals("127.0.0.1:18234", found)
        } finally {
            beacon.shutdown()
        }
    }

    @Test
    fun `does not match a different room code`() {
        val listenPort = ServerSocket(0).use { it.localPort }
        val beacon = WatchTogetherDiscovery.advertise(
            code = "ABC123",
            tcpPort = 18234,
            name = "Ernest",
            target = InetAddress.getByName("127.0.0.1"),
            targetPort = listenPort,
        )
        try {
            assertNull(WatchTogetherDiscovery.findHost("ZZZ999", timeoutMs = 1_200, port = listenPort))
        } finally {
            beacon.shutdown()
        }
    }

    @Test
    fun `advertisement parsing rejects garbage`() {
        assertNull(WatchTogetherDiscovery.parseAdvertisement("junk"))
        assertNull(WatchTogetherDiscovery.parseAdvertisement("ANIKKU1|ABC123|notaport|Ernest"))
        assertNull(WatchTogetherDiscovery.parseAdvertisement("OTHER|ABC123|18234|Ernest"))
        val parsed = WatchTogetherDiscovery.parseAdvertisement("ANIKKU1|ABC123|18234|Ernest")
        assertTrue(parsed != null)
        assertEquals("ABC123", parsed!!.code)
        assertEquals(18234, parsed.tcpPort)
    }
}
