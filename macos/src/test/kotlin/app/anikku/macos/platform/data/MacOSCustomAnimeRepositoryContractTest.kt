package app.anikku.macos.platform.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.repository.CustomAnimeRepository
import java.nio.file.Path

class MacOSCustomAnimeRepositoryContractTest {

    @Test
    fun `implements shared contract and persists every override`(@TempDir tempDir: Path) {
        val repository: CustomAnimeRepository = MacOSCustomAnimeRepository(tempDir.toFile())
        repository.set(
            CustomAnimeInfo(
                id = 42,
                title = "Custom title",
                author = "Author",
                artist = "Artist",
                thumbnailUrl = "https://example.test/custom.jpg",
                description = "Description",
                genre = listOf("Action", "Drama"),
                status = 2,
            ),
        )

        val restarted: CustomAnimeRepository = MacOSCustomAnimeRepository(tempDir.toFile())
        assertEquals("Custom title", restarted.get(42)?.title)
        assertEquals(listOf("Action", "Drama"), restarted.get(42)?.genre)
        assertEquals(2L, restarted.get(42)?.status)
    }

    @Test
    fun `blank title and zero status normalize and empty override removes entry`(@TempDir tempDir: Path) {
        val repository: CustomAnimeRepository = MacOSCustomAnimeRepository(tempDir.toFile())
        repository.set(CustomAnimeInfo(id = 7, title = "Title", status = 1))
        repository.set(CustomAnimeInfo(id = 7, title = " ", status = 0))

        assertNull(repository.get(7))
        assertTrue(tempDir.resolve("edits.json").toFile().readText().contains("\"animes\":[]"))
    }

    @Test
    fun `domain set replaces old fields while legacy overload merges partial edits`(@TempDir tempDir: Path) {
        val repository = MacOSCustomAnimeRepository(tempDir.toFile())
        repository.set(9, title = "First", author = "Author")
        repository.set(9, title = "Second")
        assertEquals("Author", repository.get(9)?.author)

        repository.set(CustomAnimeInfo(id = 9, title = "Replacement"))
        assertEquals("Replacement", repository.get(9)?.title)
        assertNull(repository.get(9)?.author)
    }
}
