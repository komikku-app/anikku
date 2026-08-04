package app.anikku.macos.platform.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NewEpisodeRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repo() = NewEpisodeRepository(tempDir.toFile())

    private fun info(
        animeId: Long,
        title: String = "Anime $animeId",
        latest: Double = 5.0,
        count: Int = 2,
        name: String? = null,
        thumb: String? = null,
    ) = NewEpisodeInfo(
        animeId = animeId,
        title = title,
        thumbnailUrl = thumb,
        latestEpisodeNumber = latest,
        latestEpisodeName = name,
        episodeCount = count,
    )

    @Test
    fun `toFeedEntries expands a discovery into per-episode rows`() {
        val rows = info(animeId = 1, latest = 5.0, count = 2, name = "Episode 5").toFeedEntries()

        assertEquals(2, rows.size)
        assertEquals(4.0, rows[0].episodeNumber)
        assertEquals(5.0, rows[1].episodeNumber)
        // Only the latest row carries the episode name.
        assertEquals(null, rows[0].episodeName)
        assertEquals("Episode 5", rows[1].episodeName)
        assertEquals(1L, rows[0].animeId)
        assertEquals("Anime 1", rows[0].animeTitle)
    }

    @Test
    fun `toFeedEntries is empty for degenerate inputs`() {
        assertTrue(info(animeId = 1, count = 0).toFeedEntries().isEmpty())
        assertTrue(info(animeId = 1, latest = 0.0, count = 3).toFeedEntries().isEmpty())
    }

    @Test
    fun `addDiscovered dedupes by anime and episode`() {
        val feed = repo()
        val added = feed.addDiscovered(info(animeId = 1, latest = 5.0, count = 2).toFeedEntries())
        assertEquals(2, added)
        assertEquals(2, feed.count())

        // Re-adding the same episodes must be skipped.
        val reAdded = feed.addDiscovered(info(animeId = 1, latest = 5.0, count = 2).toFeedEntries())
        assertEquals(0, reAdded)
        assertEquals(2, feed.count())

        // A different anime's episodes still add.
        val addedOther = feed.addDiscovered(info(animeId = 2, latest = 3.0, count = 1).toFeedEntries())
        assertEquals(1, addedOther)
        assertEquals(3, feed.count())
    }

    @Test
    fun `addDiscovered bumps revision and persists across reload`() {
        val feed = repo()
        val start = feed.revision.value
        feed.addDiscovered(info(animeId = 1, latest = 5.0, count = 2).toFeedEntries())
        assertTrue(feed.revision.value > start)

        val reloaded = repo()
        assertEquals(2, reloaded.count())
        val rows = reloaded.getAll()
        assertEquals(2, rows.size)
        assertEquals(5.0, rows.first().episodeNumber) // newest discovered first
    }

    @Test
    fun `removeForAnime and clear update count and revision`() {
        val feed = repo()
        feed.addDiscovered(
            info(animeId = 1, latest = 5.0, count = 2).toFeedEntries() +
                info(animeId = 2, latest = 3.0, count = 1).toFeedEntries(),
        )
        assertEquals(3, feed.count())

        assertTrue(feed.removeForAnime(1))
        assertEquals(1, feed.count())
        assertFalse(feed.removeForAnime(999), "removing unknown anime must not bump revision")

        assertEquals(1, feed.clear())
        assertEquals(0, feed.count())
        assertEquals(0, feed.clear(), "clearing an empty feed is a no-op")
    }

    @Test
    fun `getAll sorts newest discoveries first`() {
        val feed = repo()
        val first = info(animeId = 1, latest = 5.0, count = 1).toFeedEntries().map {
            it.copy(discoveredAt = 100)
        }
        val second = info(animeId = 2, latest = 3.0, count = 1).toFeedEntries().map {
            it.copy(discoveredAt = 200)
        }
        feed.addDiscovered(first + second)

        assertEquals(2L, feed.getAll().first().animeId)
    }
}
