package app.anikku.macos.platform.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `getLatestForEpisodeNumber finds resume position when episode id changed`() {
        val history = repo()
        history.add(entry(1L, 11L, seenAt = 100L, lastSecondSeen = 60L, totalSeconds = 1200L))
        // Same anime + episode number but a different (newer) episode id — e.g.
        // the source changed its episode URL between sessions.
        history.add(
            HistoryRepository.HistoryEntry(
                animeId = 1L,
                episodeId = 999L,
                animeTitle = "Anime 1",
                episodeName = "Episode 1",
                episodeNumber = 1.0,
                seenAt = 200L,
                lastSecondSeen = 400L,
                totalSeconds = 1200L,
            ),
        )

        val byId = history.getForEpisode(1L, 11L)
        assertEquals(60L, byId?.lastSecondSeen)

        // The number fallback resolves the newest entry regardless of which
        // hashed episode id it was stored under.
        val byNumber = history.getLatestForEpisodeNumber(1L, 1.0)
        assertEquals(999L, byNumber?.episodeId)
        assertEquals(400L, byNumber?.lastSecondSeen)
    }

    @Test
    fun `removeForEpisode and removeForAnime delete history`() {
        val history = repo()
        history.add(entry(1L, 11L, seenAt = 100L, lastSecondSeen = 60L, totalSeconds = 1200L))
        history.add(entry(1L, 12L, seenAt = 200L, lastSecondSeen = 30L, totalSeconds = 1200L))
        history.add(entry(2L, 21L, seenAt = 300L, lastSecondSeen = 90L, totalSeconds = 1200L))

        history.removeForEpisode(1L, 11L)
        assertEquals(2, history.count())
        assertNull(history.getForEpisode(1L, 11L))

        history.removeForAnime(1L)
        assertEquals(1, history.count())
        assertEquals(2L, history.getAll().single().animeId)
    }
}
