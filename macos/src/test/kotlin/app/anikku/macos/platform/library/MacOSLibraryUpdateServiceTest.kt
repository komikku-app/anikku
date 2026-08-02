package app.anikku.macos.platform.library

import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class MacOSLibraryUpdateServiceTest {

    @Test
    fun `real source scan persists metadata latest episode and unseen count`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        val history = HistoryRepository(tempDir.toFile())
        library.add(
            LibraryRepository.LibraryEntry(
                animeId = 10,
                title = "Old title",
                sourceId = 99,
                url = "/anime/10",
            ),
        )
        history.add(
            HistoryRepository.HistoryEntry(
                animeId = 10,
                episodeId = 101,
                episodeNumber = 1.0,
            ),
        )
        val source = FakeSource()
        val service = MacOSLibraryUpdateService(library, history) { if (it == 99L) source else null }

        val first = service.updateAll()

        assertEquals(1, first.scanned)
        assertEquals(1, first.updated)
        assertEquals(2, first.newlyDiscoveredEpisodes)
        assertTrue(first.failures.isEmpty())
        val updated = library.get(10)!!
        assertEquals("Updated title", updated.title)
        assertEquals("Updated description", updated.description)
        assertEquals(3.0, updated.latestEpisodeNumber)
        assertEquals("Episode 3", updated.latestEpisodeName)
        assertEquals(2, updated.unseenEpisodeCount)
        assertFalse(service.progress.value.running)

        val second = service.updateAll()
        assertEquals(0, second.newlyDiscoveredEpisodes, "An unchanged source must not re-notify old episodes")

        val restarted = LibraryRepository(tempDir.toFile()).get(10)!!
        assertEquals(3.0, restarted.latestEpisodeNumber)
        assertEquals(2, restarted.unseenEpisodeCount)
    }

    @Test
    fun `missing extension is isolated and reported`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(
            LibraryRepository.LibraryEntry(
                animeId = 20,
                title = "Unavailable",
                sourceId = 404,
                url = "/missing",
            ),
        )
        val service = MacOSLibraryUpdateService(library, HistoryRepository(tempDir.toFile())) { null }

        val result = service.updateAll()

        assertEquals(1, result.scanned)
        assertEquals(0, result.updated)
        assertTrue(result.failures.getValue(20).contains("not installed"))
        assertEquals("Unavailable", library.get(20)?.title)
    }

    private class FakeSource : AnimeSource {
        override val id: Long = 99
        override val name: String = "Test Source"

        override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime.apply {
            title = "Updated title"
            description = "Updated description"
            author = "Author"
            genre = "Action, Adventure"
            status = SAnime.ONGOING
            thumbnail_url = "https://example.test/poster.jpg"
        }

        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = (1..3).map { number ->
            SEpisode.create().apply {
                url = "/episode/$number"
                name = "Episode $number"
                episode_number = number.toFloat()
            }
        }

        override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
    }
}
