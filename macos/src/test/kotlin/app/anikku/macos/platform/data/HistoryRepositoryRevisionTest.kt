package app.anikku.macos.platform.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HistoryRepositoryRevisionTest {

    @Test
    fun `revision bumps on every mutation`(@TempDir tempDir: Path) {
        val repo = HistoryRepository(tempDir.toFile())
        val start = repo.revision.value

        repo.add(HistoryRepository.HistoryEntry(animeId = 1, episodeId = 11))
        assertTrue(repo.revision.value > start, "add must bump revision")

        val afterAdd = repo.revision.value
        repo.removeForEpisode(1, 11)
        assertTrue(repo.revision.value > afterAdd, "removeForEpisode must bump revision")

        val afterRemove = repo.revision.value
        repo.add(HistoryRepository.HistoryEntry(animeId = 2, episodeId = 21))
        repo.removeForAnime(2)
        assertTrue(repo.revision.value > afterRemove, "removeForAnime must bump revision")

        val afterAnimeRemove = repo.revision.value
        repo.clearAll()
        assertTrue(repo.revision.value > afterAnimeRemove, "clearAll must bump revision")

        val afterClear = repo.revision.value
        repo.replaceAll(listOf(HistoryRepository.HistoryEntry(animeId = 3, episodeId = 31)))
        assertTrue(repo.revision.value > afterClear, "replaceAll must bump revision")
    }

    @Test
    fun `no-op removals do not bump revision`(@TempDir tempDir: Path) {
        val repo = HistoryRepository(tempDir.toFile())
        val start = repo.revision.value

        repo.removeForEpisode(999, 1)
        repo.removeForAnime(999)

        assertTrue(repo.revision.value == start, "removing nothing must not bump revision")
    }
}
