package app.anikku.macos.platform.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TorrentAnimeGrouperTest {

    private fun rel(filename: String, magnet: String = "magnet:?xt=urn:btih:${filename.hashCode()}"): TorrentRelease =
        TorrentRelease(
            magnetUrl = magnet,
            pageUrl = "/view/${filename.hashCode()}",
            rawTitle = filename,
            parsed = NyaaTorrentParser.parse(filename),
            sizeSeeders = "Size: 1.2 GiB ▲12 ▼3 ⬇45",
        )

    @Test
    fun `groups same anime across sub groups and qualities into one group`() {
        val releases = listOf(
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
            rel("[Erai-raws] Death Note - 01 [720p][Multi]"),
            rel("[SubsPlease] Death Note - 02 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("Death Note", group.displayTitle)
        assertEquals(2, group.episodeCount)
        assertEquals(1, group.seasonCount)
        assertEquals(3, group.totalReleases)

        val season = group.seasons.first()
        assertEquals(1, season.season)
        assertEquals(listOf(1, 2), season.episodes.map { it.episode })

        // Best release of ep 1 = 1080p; the 720p one becomes an alternative.
        val ep1 = season.episodes.first()
        assertEquals("1080p", ep1.best.parsed.quality)
        assertEquals(listOf("720p"), ep1.alternatives.map { it.parsed.quality })
    }

    @Test
    fun `separates different anime`() {
        val releases = listOf(
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
            rel("[SubsPlease] Frieren - 01 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)
        assertEquals(2, groups.size)
        assertTrue(groups.map { it.displayTitle }.containsAll(listOf("Death Note", "Frieren")))
    }

    @Test
    fun `groups all seasons of one anime under one group`() {
        val releases = listOf(
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
            rel("[SubsPlease] Death Note - S2 - 01 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(2, group.seasonCount)
        assertEquals(2, group.episodeCount)
        assertEquals(listOf(1, 2), group.seasons.map { it.season })
    }

    @Test
    fun `orders episodes ascending regardless of input order`() {
        val releases = listOf(
            rel("[SubsPlease] Death Note - 03 (1080p) [ABC]"),
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
            rel("[SubsPlease] Death Note - 02 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)
        assertEquals(listOf(1, 2, 3), groups.first().seasons.first().episodes.map { it.episode })
    }

    @Test
    fun `separates batch releases from episode rows`() {
        val releases = listOf(
            rel("[Erai-raws] Death Note - 01-24 [720p]"),
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)
        val group = groups.first()

        assertEquals(1, group.batches.size)
        assertEquals(24, group.batches.first().parsed.episodeEnd)
        assertEquals(1, group.episodeCount) // only the single episode is a row
        assertEquals(2, group.totalReleases)
    }

    @Test
    fun `unparseable releases go to other`() {
        val releases = listOf(
            rel("Death Note - 01 (1080p) [ABC]"),
            rel("Weird Special Release No Episode"),
        )
        val groups = TorrentAnimeGrouper.group(releases)

        // The weird title is its own group with the release in "other".
        val weird = groups.firstOrNull { it.normalizedKey.contains("weirdspecial") }!!
        assertTrue(weird.other.isNotEmpty())
        assertEquals(1, weird.other.size)
    }

    @Test
    fun `picks highest quality then most seeders as best release`() {
        val releases = listOf(
            rel("[Group] Death Note - 01 [1080p]").copy(sizeSeeders = "Size: 1 GiB ▲5"),
            rel("[Group] Death Note - 01 [720p]").copy(sizeSeeders = "Size: 700 MiB ▲500"),
            rel("[Group] Death Note - 01 [2160p]").copy(sizeSeeders = "Size: 4 GiB ▲2"),
        )
        val groups = TorrentAnimeGrouper.group(releases)
        val ep1 = groups.first().seasons.first().episodes.first()
        assertEquals("2160p", ep1.best.parsed.quality)
        assertEquals(listOf("1080p", "720p"), ep1.alternatives.map { it.parsed.quality })
    }

    @Test
    fun `sorts groups by release count descending`() {
        val releases = listOf(
            rel("[SubsPlease] Death Note - 01 (1080p) [ABC]"),
            rel("[SubsPlease] Death Note - 02 (1080p) [ABC]"),
            rel("[SubsPlease] Death Note - 03 (1080p) [ABC]"),
            rel("[SubsPlease] Frieren - 01 (1080p) [ABC]"),
        )
        val groups = TorrentAnimeGrouper.group(releases)
        assertEquals("Death Note", groups.first().displayTitle)
        assertEquals(3, groups.first().totalReleases)
    }
}
