package app.anikku.macos.platform.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class WatchTogetherProtocolTest {

    @Test
    fun `round-trips every message type`() {
        val messages = listOf(
            WtMessage.Hello("Ernest"),
            WtMessage.Play(),
            WtMessage.Pause(),
            WtMessage.Seek(pos = 42.5),
            WtMessage.Sync(pos = 123.4, playing = true, rate = 1.25),
            WtMessage.Episode(
                title = "Frieren",
                name = "Ep 3 — Bitter End",
                number = 3.0,
                mediaUrl = "http://192.168.1.10:18234/media/ABCDEF/id123",
                kind = "direct",
                duration = 1440.0,
            ),
            WtMessage.Members(count = 3, names = listOf("Ernest", "Bob")),
            WtMessage.RoomClosed(reason = "The host closed the room"),
            WtMessage.Chat(text = "hello room", by = "Ernest", ts = 1234L),
        )

        for (message in messages) {
            val decoded = WtProtocol.decode(WtProtocol.encode(message))
            assertEquals(message, decoded)
        }
    }

    @Test
    fun `ignores unknown fields and malformed payloads`() {
        val encoded = """{"type":"sync","pos":10.0,"playing":true,"rate":1.0,"unexpected":9}"""
        assertEquals(WtMessage.Sync(10.0, true, 1.0), WtProtocol.decode(encoded))

        assertNull(WtProtocol.decode("not json at all"))
        assertNull(WtProtocol.decode("""{"type":"nope"}"""))
    }

    @Test
    fun `chat with an attached image round-trips`() {
        val message = WtMessage.Chat(
            text = "check this",
            by = "Ernest",
            ts = 999L,
            image = "data:image/png;base64,iVBORw0KGgo=",
            name = "shot.png",
        )
        assertEquals(message, WtProtocol.decode(WtProtocol.encode(message)))
    }

    @Test
    fun `wtImageDataUrl builds a data url for a real image and refuses oversize or missing files`() {
        val dir = java.nio.file.Files.createTempDirectory("wtimg").toFile()
        try {
            val png = File(dir, "shot.png").apply { writeBytes(ByteArray(64) { it.toByte() }) }
            val url = wtImageDataUrl(png)
            assertNotNull(url)
            assertTrue(url!!.startsWith("data:image/png;base64,"))

            val gif = File(dir, "clip.gif").apply { writeBytes(ByteArray(64) { 1 }) }
            assertTrue(wtImageDataUrl(gif)!!.startsWith("data:image/gif;base64,"))

            // A ~5 MB capture fits the 10 MB chat budget.
            val medium = File(dir, "bigish.png").apply { writeBytes(ByteArray(5_000_000)) }
            assertNotNull(wtImageDataUrl(medium))

            // Files over the decoded-size budget (~10 MB) are refused.
            val big = File(dir, "huge.png").apply { writeBytes(ByteArray(11_000_000)) }
            assertNull(wtImageDataUrl(big))

            assertNull(wtImageDataUrl(File(dir, "missing.png")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `room codes are six characters from the unambiguous alphabet`() {
        repeat(200) {
            val code = WtCodes.newCode()
            assertEquals(6, code.length)
            assertTrue(WtCodes.isValid(code))
        }
        assertTrue(!WtCodes.isValid("ABC1234"))
        assertTrue(!WtCodes.isValid("ABCDE0")) // 0 excluded
        assertTrue(!WtCodes.isValid("ABCDEI")) // I excluded
    }

    // -----------------------------------------------------------------------
    // WtLinks — shared room links (internet joins)
    // -----------------------------------------------------------------------

    @Test
    fun `parses a shared tunnel link to a TLS join target`() {
        val target = WtLinks.parse("https://anime-night-2026.trycloudflare.com/room/ABC234")
        assertEquals(
            WtLinks.JoinTarget(secure = true, host = "anime-night-2026.trycloudflare.com", port = 443, code = "ABC234"),
            target,
        )
        assertTrue(WtLinks.isJoinable("https://anime-night-2026.trycloudflare.com/room/ABC234"))
    }

    @Test
    fun `parses plain http links with explicit ports as ws joins`() {
        val target = WtLinks.parse("http://192.168.1.10:18234/room/abc234")
        assertEquals(
            WtLinks.JoinTarget(secure = false, host = "192.168.1.10", port = 18234, code = "ABC234"),
            target,
        )
    }

    @Test
    fun `accepts wss and ws schemes and a trailing slash`() {
        val wss = WtLinks.parse("wss://host.example/room/ABC234/")
        assertTrue(wss != null && wss.secure && wss.port == 443)
        val ws = WtLinks.parse("ws://host.example/room/ABC234")
        assertTrue(ws != null && !ws.secure && ws.port == 80)
        assertEquals(8080, WtLinks.parse("ws://host.example:8080/room/ABC234")?.port)
    }

    @Test
    fun `rejects malformed links and invalid codes`() {
        assertNull(WtLinks.parse("https://host.example/room/ABC23")) // too short
        assertNull(WtLinks.parse("https://host.example/room/ABC23I")) // I excluded
        assertNull(WtLinks.parse("https://host.example/room/ABCDE0")) // 0 excluded
        assertNull(WtLinks.parse("https://host.example/room/ABC234/extra"))
        assertNull(WtLinks.parse("https://host.example/room/ABC234?x=1"))
        assertNull(WtLinks.parse("not a link"))
        assertNull(WtLinks.parse(""))
    }

    @Test
    fun `a bare code is not a link - it means LAN discovery`() {
        assertNull(WtLinks.parse("ABC234"))
        assertTrue(!WtLinks.isJoinable("ABC234"))
    }
}
