package app.anikku.macos.platform.library

import app.anikku.macos.platform.data.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibraryAutoLinkServiceTest {

    @Test
    fun `autoLink attaches a high-confidence match to an unlinked entry`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(LibraryRepository.LibraryEntry(animeId = 10, title = "Solo Leveling"))
        val source = FakeCatalogueSource(
            id = 99L,
            name = "SourceA",
            animes = listOf(Triple("/solo", "Solo Leveling (TV)", "thumb")),
        )
        val service = LibraryAutoLinkService(library, AnimeSourceMatcher(sourcesProvider = { listOf(source) }))

        val result = service.autoLink()

        assertEquals(1, result.linked)
        val updated = library.get(10)!!
        assertEquals(99L, updated.sourceId)
        assertEquals("/solo", updated.url)
        assertEquals("thumb", updated.thumbnailUrl)
    }

    @Test
    fun `autoLink leaves already-linked entries untouched`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(
            LibraryRepository.LibraryEntry(animeId = 10, title = "Solo Leveling", sourceId = 5, url = "/existing"),
        )
        val source = FakeCatalogueSource(
            id = 99L,
            name = "SourceA",
            animes = listOf(Triple("/solo", "Solo Leveling", null)),
        )
        val service = LibraryAutoLinkService(library, AnimeSourceMatcher(sourcesProvider = { listOf(source) }))

        val result = service.autoLink()

        assertEquals(0, result.linked)
        val entry = library.get(10)!!
        assertEquals(5L, entry.sourceId)
        assertEquals("/existing", entry.url)
    }

    @Test
    fun `autoLink does not link when no source has a confident match`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(LibraryRepository.LibraryEntry(animeId = 10, title = "One Piece"))
        val source = FakeCatalogueSource(
            id = 99L,
            name = "SourceA",
            animes = listOf(Triple("/naruto", "Naruto", null)),
        )
        val service = LibraryAutoLinkService(library, AnimeSourceMatcher(sourcesProvider = { listOf(source) }))

        val result = service.autoLink()

        assertEquals(0, result.linked)
        val entry = library.get(10)!!
        assertEquals(0L, entry.sourceId)
        assertNull(entry.url)
    }

    @Test
    fun `autoLinkOne links a single entry and returns the match`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(LibraryRepository.LibraryEntry(animeId = 42, title = "Frieren"))
        val source = FakeCatalogueSource(
            id = 7L,
            name = "SourceB",
            animes = listOf(Triple("/frieren", "Frieren: Beyond Journey's End", null)),
        )
        val service = LibraryAutoLinkService(library, AnimeSourceMatcher(sourcesProvider = { listOf(source) }))

        val match = service.autoLinkOne(42, "Frieren")

        assertNotNull(match)
        assertEquals(7L, match!!.sourceId)
        assertEquals(7L, library.get(42)!!.sourceId)
        assertEquals("/frieren", library.get(42)!!.url)
    }

    @Test
    fun `autoLinkOne skips entries that are already linked`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        library.add(
            LibraryRepository.LibraryEntry(animeId = 42, title = "Frieren", sourceId = 3, url = "/known"),
        )
        val service = LibraryAutoLinkService(
            library,
            AnimeSourceMatcher(sourcesProvider = { listOf(FakeCatalogueSource(7L, "SourceB")) }),
        )

        val match = service.autoLinkOne(42, "Frieren")

        assertNull(match)
        assertEquals(3L, library.get(42)!!.sourceId)
    }

    @Test
    fun `empty library auto-links nothing`(@TempDir tempDir: Path) = runBlocking {
        val library = LibraryRepository(tempDir.toFile())
        val service = LibraryAutoLinkService(
            library,
            AnimeSourceMatcher(sourcesProvider = { emptyList() }),
        )

        val result = service.autoLink()

        assertEquals(0, result.attempted)
        assertEquals(0, result.linked)
    }
}
