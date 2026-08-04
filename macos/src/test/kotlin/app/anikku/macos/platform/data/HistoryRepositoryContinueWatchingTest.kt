package app.anikku.macos.platform.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HistoryRepositoryContinueWatchingTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repo(): HistoryRepository = HistoryRepository(tempDir.toFile())

    private fun entry(
        animeId: Long,
        episodeId: Long,
        seenAt: Long,
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double = 1.0,
    ) = HistoryRepository.HistoryEntry(
        animeId = animeId,
        episodeId = episodeId,
        animeTitle = "Anime $animeId",
        episodeName = "Episode $episodeId",
        episodeNumber = episodeNumber,
        seenAt = seenAt,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
    )

    @Test
    fun `excludes never-started and fully-finished episodes`() {
        val history = repo()
        history.add(entry(1L, 11L, 100L, lastSecondSeen = 0L, totalSeconds = 1200L))      // never started
        history.add(entry(2L, 21L, 200L, lastSecondSeen = 1200L, totalSeconds = 1200L))    // finished exactly
        history.add(entry(3L, 31L, 300L, lastSecondSeen = 400L, totalSeconds = 1200L))     // in progress

        val result = history.getContinueWatching()
        assertEquals(1, result.size)
        assertEquals(3L, result[0].animeId)
    }

    @Test
    fun `keeps in-progress episodes with unknown duration`() {
        val history = repo()
        history.add(entry(1L, 11L, 100L, lastSecondSeen = 60L, totalSeconds = 0L))

        val result = history.getContinueWatching()
        assertEquals(1, result.size)
        assertEquals(11L, result[0].episodeId)
    }

    @Test
    fun `dedupes by anime keeping the most recently watched episode`() {
        val history = repo()
        history.add(entry(1L, 11L, 100L, lastSecondSeen = 100L, totalSeconds = 1200L))
        history.add(entry(1L, 12L, 400L, lastSecondSeen = 700L, totalSeconds = 1200L)) // newer episode
        history.add(entry(2L, 21L, 300L, lastSecondSeen = 500L, totalSeconds = 1200L))

        val result = history.getContinueWatching()
        assertEquals(2, result.size)
        // Anime 1 resolves to the newest entry (episode 12).
        assertEquals(12L, result.first { it.animeId == 1L }.episodeId)
    }

    @Test
    fun `sorts most recent first and caps at limit`() {
        val history = repo()
        for (animeId in 1L..5L) {
            history.add(entry(animeId, animeId * 10 + 1, seenAt = animeId * 100, lastSecondSeen = 300L, totalSeconds = 1200L))
        }
        val result = history.getContinueWatching(limit = 2)
        assertEquals(2, result.size)
        assertEquals(5L, result[0].animeId)
        assertEquals(4L, result[1].animeId)
    }
}
