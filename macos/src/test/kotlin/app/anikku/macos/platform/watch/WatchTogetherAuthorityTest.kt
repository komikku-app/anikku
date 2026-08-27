package app.anikku.macos.platform.watch

import app.anikku.macos.platform.MacOSDeepLinkHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Protocol coverage for the host-authority upgrade: lock/kick/speed messages,
 * the magnet deep link in [WtMessage.Episode], and the anikku:// parser.
 */
class WatchTogetherAuthorityTest {

    @Test
    fun `lock message round-trips`() {
        val message = WtMessage.Lock(locked = true, by = "Host")
        val decoded = WtProtocol.decode(WtProtocol.encode(message))
        assertEquals(WtMessage.Lock(locked = true, by = "Host"), decoded)
    }

    @Test
    fun `kick message round-trips`() {
        val message = WtMessage.Kick(name = "Neko42", by = "Host")
        assertEquals(WtMessage.Kick(name = "Neko42", by = "Host"), WtProtocol.decode(WtProtocol.encode(message)))
    }

    @Test
    fun `speed message round-trips`() {
        val message = WtMessage.Speed(rate = 1.5, by = "Guest")
        assertEquals(WtMessage.Speed(rate = 1.5, by = "Guest"), WtProtocol.decode(WtProtocol.encode(message)))
    }

    @Test
    fun `episode carries magnet deep link`() {
        val episode = WtMessage.Episode(
            title = "Anime",
            kind = "magnet",
            appDeepLink = "anikku://watch?animeId=1&episodeId=2",
        )
        val decoded = WtProtocol.decode(WtProtocol.encode(episode)) as WtMessage.Episode
        assertEquals("anikku://watch?animeId=1&episodeId=2", decoded.appDeepLink)
    }

    @Test
    fun `members carries host name`() {
        val members = WtMessage.Members(count = 2, names = listOf("Host", "Neko"), hostName = "Host")
        val decoded = WtProtocol.decode(WtProtocol.encode(members)) as WtMessage.Members
        assertEquals("Host", decoded.hostName)
    }

    @Test
    fun `parseWatchLink extracts full target`() {
        val target = MacOSDeepLinkHandler.parseWatchLink(
            "anikku://watch?animeId=42&episodeId=7&sourceId=3&episodeUrl=" +
                "https%3A%2F%2Fexample.com%2Fep%2F1&animeTitle=My%20Anime&episodeName=EP1&episodeNumber=1.5",
        )
        assertNotNull(target)
        assertEquals(42L, target!!.animeId)
        assertEquals(7L, target.episodeId)
        assertEquals(3L, target.sourceId)
        assertEquals("https://example.com/ep/1", target.episodeUrl)
        assertEquals("My Anime", target.animeTitle)
        assertEquals("EP1", target.episodeName)
        assertEquals(1.5, target.episodeNumber)
    }

    @Test
    fun `parseWatchLink rejects non-watch and malformed links`() {
        assertNull(MacOSDeepLinkHandler.parseWatchLink("anikku://join?code=ABC123"))
        assertNull(MacOSDeepLinkHandler.parseWatchLink("https://example.com/room/ABC123"))
        assertNull(MacOSDeepLinkHandler.parseWatchLink("anikku://watch?animeId=abc&episodeId=1"))
        assertNull(MacOSDeepLinkHandler.parseWatchLink("anikku://watch?animeId=1"))
        assertNull(MacOSDeepLinkHandler.parseWatchLink(""))
    }
}
