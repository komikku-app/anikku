package app.anikku.macos.platform.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
