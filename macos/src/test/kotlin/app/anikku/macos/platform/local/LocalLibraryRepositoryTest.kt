package app.anikku.macos.platform.local

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalLibraryRepositoryTest {

    @TempDir
    lateinit var tempDir: java.nio.file.Path

    private fun entry(
        animeId: Long = 1L,
        title: String = "Death Note",
        season: Int = 1,
        episode: Int = 1,
        filePath: String = "/tmp/Death Note - $episode.mkv",
    ) = LocalVideoEntry(
        animeId = animeId,
        title = title,
        season = season,
        episode = episode,
        filePath = filePath,
        fileName = filePath.substringAfterLast('/'),
    )

    @Test
    fun `persists entries across reloads`() {
        val repo = LocalLibraryRepository(tempDir.toFile())
        repo.add(listOf(entry(episode = 1), entry(episode = 2)))

        val reloaded = LocalLibraryRepository(tempDir.toFile())
        assertEquals(2, reloaded.getAll().size)
        assertEquals(listOf(1, 2), reloaded.getAll().map { it.episode }.sorted())
    }

    @Test
    fun `dedupes by file path on add`() {
        val repo = LocalLibraryRepository(tempDir.toFile())
        repo.add(listOf(entry(episode = 1)))
        repo.add(listOf(entry(episode = 1), entry(episode = 2)))

        assertEquals(2, repo.getAll().size)
    }

    @Test
    fun `revision increments on mutation only`() {
        val repo = LocalLibraryRepository(tempDir.toFile())
        val start = repo.revision.value

        repo.add(listOf(entry()))
        assertEquals(start + 1, repo.revision.value)

        repo.add(listOf(entry())) // duplicate path — no-op
        assertEquals(start + 1, repo.revision.value)
    }

    @Test
    fun `removeAnime removes all files of one anime`() {
        val repo = LocalLibraryRepository(tempDir.toFile())
        repo.add(listOf(entry(animeId = 1, episode = 1), entry(animeId = 1, episode = 2), entry(animeId = 2, title = "Frieren")))

        repo.removeAnime(1)

        assertEquals(1, repo.getAll().size)
        assertTrue(repo.getAll().all { it.animeId == 2L })
    }

    @Test
    fun `remove removes a single file path`() {
        val repo = LocalLibraryRepository(tempDir.toFile())
        repo.add(listOf(entry(episode = 1), entry(episode = 2)))

        repo.remove("/tmp/Death Note - 1.mkv")

        assertEquals(1, repo.getAll().size)
        assertEquals(2, repo.getAll().first().episode)
    }

    @Test
    fun `corrupted store degrades to empty without crashing`() {
        val dir = tempDir.toFile()
        dir.resolve("local_library.json").writeText("not json at all")
        val repo = LocalLibraryRepository(dir)
        assertTrue(repo.getAll().isEmpty())
    }
}
