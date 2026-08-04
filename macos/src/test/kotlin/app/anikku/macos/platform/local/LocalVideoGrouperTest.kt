package app.anikku.macos.platform.local

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalVideoGrouperTest {

    private fun entry(
        title: String,
        season: Int = 1,
        episode: Int,
        path: String = "/tmp/${title}_E$episode.mkv",
    ) = LocalVideoEntry(
        animeId = app.anikku.macos.platform.library.AnimeSourceMatcher.normalizeTitle(title)
            .hashCode().toLong(),
        title = title,
        season = season,
        episode = episode,
        filePath = path,
        fileName = path.substringAfterLast('/'),
    )

    @Test
    fun `groups same anime into one group with ordered episodes`() {
        val groups = LocalVideoGrouper.group(
            listOf(
                entry("Death Note", episode = 3),
                entry("Death Note", episode = 1),
                entry("Death Note", episode = 2),
            ),
        )

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("Death Note", group.displayTitle)
        assertEquals(3, group.episodeCount)
        assertEquals(listOf(1, 2, 3), group.seasons.first().episodes.map { it.episode })
    }

    @Test
    fun `separates seasons into sub-groups`() {
        val groups = LocalVideoGrouper.group(
            listOf(
                entry("Frieren", episode = 1),
                entry("Frieren", season = 2, episode = 1),
            ),
        )

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(2, group.seasonCount)
        assertEquals(listOf(1, 2), group.seasons.map { it.season })
    }

    @Test
    fun `separates different anime`() {
        val groups = LocalVideoGrouper.group(
            listOf(
                entry("Death Note", episode = 1),
                entry("Frieren", episode = 1),
            ),
        )
        assertEquals(2, groups.size)
    }

    @Test
    fun `unparsed episodes go to other`() {
        val groups = LocalVideoGrouper.group(
            listOf(
                entry("Death Note", episode = 1),
                LocalVideoEntry(
                    animeId = 99L,
                    title = "Death Note Movie",
                    season = 1,
                    episode = 0,
                    filePath = "/tmp/movie.mkv",
                    fileName = "movie.mkv",
                ),
            ),
        )

        val group = groups.first { it.normalizedKey == "deathnotemovie" }
        assertEquals(1, group.other.size)
        assertEquals(0, group.episodeCount)
    }

    @Test
    fun `sorts groups by file count descending`() {
        val groups = LocalVideoGrouper.group(
            listOf(
                entry("Death Note", episode = 1),
                entry("Death Note", episode = 2),
                entry("Frieren", episode = 1),
            ),
        )
        assertEquals("Death Note", groups.first().displayTitle)
    }
}

class LocalFolderScannerTest {

    @TempDir
    lateinit var tempDir: java.nio.file.Path

    @Test
    fun `scans nested folders for video files`() {
        val show = Files.createDirectories(tempDir.resolve("Show S1"))
        Files.createDirectories(tempDir.resolve("nested/deep"))
        Files.write(tempDir.resolve("Death Note - 01 (1080p).mkv"), ByteArray(10))
        Files.write(tempDir.resolve("Death Note - 02 (720p).mkv"), ByteArray(20))
        Files.write(show.resolve("Frieren - S2 - 05 [1080p].mkv"), ByteArray(30))
        Files.write(tempDir.resolve("ignored.txt"), ByteArray(5))

        val entries = LocalFolderScanner.scan(tempDir.toFile())

        assertEquals(3, entries.size)
        val deathNote = entries.filter { it.title == "Death Note" }
        assertEquals(listOf(1, 2), deathNote.map { it.episode }.sorted())
        val frieren = entries.first { it.title == "Frieren" }
        assertEquals(2, frieren.season)
        assertEquals(5, frieren.episode)
    }

    @Test
    fun `skips unparseable filenames`() {
        Files.write(tempDir.resolve("random noise file.mkv"), ByteArray(10))
        assertTrue(LocalFolderScanner.scan(tempDir.toFile()).isEmpty())
    }

    @Test
    fun `respects max depth`() {
        val deep = Files.createDirectories(tempDir.resolve("a/b/c/d"))
        Files.write(deep.resolve("Death Note - 01.mkv"), ByteArray(10))

        assertEquals(0, LocalFolderScanner.scan(tempDir.toFile(), maxDepth = 2).size)
        assertEquals(1, LocalFolderScanner.scan(tempDir.toFile(), maxDepth = 5).size)
    }
}
