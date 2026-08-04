package app.anikku.macos.platform.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NyaaTorrentParserTest {

    @Test
    fun `parses classic SubsPlease dash episode`() {
        val p = NyaaTorrentParser.parse("[SubsPlease] Death Note - 01 (1080p) [ABC123]")
        assertEquals("Death Note", p.title)
        assertEquals(1, p.episode)
        assertNull(p.season)
        assertEquals("1080p", p.quality)
        assertEquals("SubsPlease", p.group)
        assertFalse(p.batch)
        assertFalse(p.unparsed)
    }

    @Test
    fun `parses SxxExx season and episode`() {
        val p = NyaaTorrentParser.parse("Death Note S01E05 720p")
        assertEquals("Death Note", p.title)
        assertEquals(1, p.season)
        assertEquals(5, p.episode)
        assertEquals("720p", p.quality)
    }

    @Test
    fun `parses S2 dash episode with trailing multi tags`() {
        val p = NyaaTorrentParser.parse("[Erai-raws] Frieren - S2 - 05 [720p][Multiple Subtitle]")
        assertEquals("Frieren", p.title)
        assertEquals(2, p.season)
        assertEquals(5, p.episode)
        assertEquals("720p", p.quality)
        assertEquals("Erai-raws", p.group)
    }

    @Test
    fun `parses episode range as batch`() {
        val p = NyaaTorrentParser.parse("[Erai-raws] Death Note - 01-24 [720p]")
        assertEquals("Death Note", p.title)
        assertEquals(1, p.episode)
        assertEquals(24, p.episodeEnd)
        assertEquals("720p", p.quality)
        assertTrue(p.batch)
    }

    @Test
    fun `parses batch category tag and Complete Series words`() {
        val p = NyaaTorrentParser.parse("[Batch] Death Note - Complete Series [1080p]")
        assertEquals("Death Note", p.title)
        assertTrue(p.batch)
        assertNull(p.group) // "Batch" is a category tag, not a fan-sub group
        assertEquals("1080p", p.quality)
        assertNull(p.episode)
    }

    @Test
    fun `year in parens is not an episode`() {
        val p = NyaaTorrentParser.parse("Death Note (2006) - 01 [1080p]")
        assertEquals("Death Note", p.title)
        assertEquals(1, p.episode)
    }

    @Test
    fun `parses Episode word form`() {
        val p = NyaaTorrentParser.parse("Death Note Episode 12 1080p")
        assertEquals("Death Note", p.title)
        assertEquals(12, p.episode)
    }

    @Test
    fun `parses ordinal season`() {
        val p = NyaaTorrentParser.parse("Mushoku Tensei - 2nd Season - 05 [1080p]")
        assertEquals("Mushoku Tensei", p.title)
        assertEquals(2, p.season)
        assertEquals(5, p.episode)
    }

    @Test
    fun `parses four digit episode numbers`() {
        val p = NyaaTorrentParser.parse("One Piece - 1085 [1080p]")
        assertEquals("One Piece", p.title)
        assertEquals(1085, p.episode)
    }

    @Test
    fun `strips version suffix`() {
        val p = NyaaTorrentParser.parse("Death Note - 05v2 [720p]")
        assertEquals("Death Note", p.title)
        assertEquals(5, p.episode)
    }

    @Test
    fun `parses Season word form`() {
        val p = NyaaTorrentParser.parse("Boku no Hero Academia - Season 3 - 01 [1080p]")
        assertEquals("Boku no Hero Academia", p.title)
        assertEquals(3, p.season)
        assertEquals(1, p.episode)
    }

    @Test
    fun `falls back to raw title when nothing parses`() {
        val p = NyaaTorrentParser.parse("Some Weird Name No Episode Info")
        assertEquals("Some Weird Name No Episode Info", p.title)
        assertNull(p.episode)
        assertFalse(p.batch)
        assertNull(p.season)
    }

    @Test
    fun `parses tilde episode range as batch`() {
        // Real Nyaa batch naming: "01 ~ 37" with a tilde separator.
        val p = NyaaTorrentParser.parse("[Erai-raws] Death Note - 01 ~ 37 [720p][BATCH][Multiple Subtitle]")
        assertEquals("Death Note", p.title)
        assertEquals(1, p.episode)
        assertEquals(37, p.episodeEnd)
        assertTrue(p.batch)
    }

    @Test
    fun `strips file extension before episode parsing`() {
        val p = NyaaTorrentParser.parse("Death Note - 13 [Under9000].avi")
        assertEquals("Death Note", p.title)
        assertEquals(13, p.episode)
    }

    @Test
    fun `keeps only the first segment of multi-title rows`() {
        val p = NyaaTorrentParser.parse(
            "[ItachiUchiha] Death Note S01 [BD 720p][AV1 Opus][Multi-Audio] | Death Note S1 (Season 1) (2006) Multi Dual",
        )
        assertEquals("Death Note", p.title)
        assertEquals(1, p.season)
    }

    @Test
    fun `strips trailing fansub descriptors`() {
        val p = NyaaTorrentParser.parse("Death Note Episode 24 TV Fansubs")
        assertEquals("Death Note", p.title)
        assertEquals(24, p.episode)
    }

    @Test
    fun `normalizes case and keeps special chars out of comparisons`() {
        val a = NyaaTorrentParser.parse("Re:Zero - 01 [1080p]")
        val b = NyaaTorrentParser.parse("Re:Zero - 02 [720p]")
        assertEquals("Re:Zero", a.title)
        assertEquals("Re:Zero", b.title)
        // Both parse to the same normalized grouping key (punctuation stripped).
        assertEquals(
            app.anikku.macos.platform.library.AnimeSourceMatcher.normalizeTitle("ReZero"),
            app.anikku.macos.platform.library.AnimeSourceMatcher.normalizeTitle(a.title),
        )
        assertEquals(
            app.anikku.macos.platform.library.AnimeSourceMatcher.normalizeTitle(a.title),
            app.anikku.macos.platform.library.AnimeSourceMatcher.normalizeTitle(b.title),
        )
    }
}
