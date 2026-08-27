package app.anikku.macos.platform.auth

import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TrackerLibrarySyncServiceTest {

    private fun malEntry(
        id: Long,
        title: String,
        status: String = "watching",
        progress: Int = 0,
        total: Int? = null,
    ) = MalLibraryEntry(malId = id, title = title, status = status, progress = progress, totalEpisodes = total)

    private fun kitsuEntry(
        id: Long,
        title: String,
        status: String = "current",
        progress: Int = 0,
        total: Int? = null,
        libraryEntryId: String = "le-$id",
    ) = KitsuLibraryEntry(
        kitsuId = id,
        libraryEntryId = libraryEntryId,
        title = title,
        status = status,
        progress = progress,
        totalEpisodes = total,
    )

    private fun service(tempDir: Path): TrackerLibrarySyncService =
        fixture(tempDir).service

    /** Shared repos: the service and the test mutate the same instances. */
    private class Fixture(
        val service: TrackerLibrarySyncService,
        val library: LibraryRepository,
        val history: HistoryRepository,
    )

    private fun fixture(tempDir: Path): Fixture {
        val dataDir = tempDir.toFile()
        val tokenStore = TrackerTokenStore(MacOSPreferenceStore(File(dataDir, "preferences.json")))
        val manager = TrackerManager(
            oauthManager = TrackerOAuthManager(OkHttpClient()),
            tokenStore = tokenStore,
            httpClient = OkHttpClient(),
        )
        val library = LibraryRepository(dataDir)
        val history = HistoryRepository(dataDir)
        return Fixture(
            service = TrackerLibrarySyncService(manager, library, history),
            library = library,
            history = history,
        )
    }

    private fun watchingCategoryId(): Long = 1L // "Watching" preset category

    // ----------------------------------------------------------------------
    // Pull (MAL)
    // ----------------------------------------------------------------------

    @Test
    fun `MAL pull imports new entries with category mapping`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)

        val (imported, updated) = fx.service.pullMal(listOf(malEntry(1535, "Death Note", status = "watching", progress = 10)))

        assertEquals(1, imported)
        assertEquals(0, updated)
        val entry = fx.library.get(1535)!!
        assertEquals("Death Note", entry.title)
        assertEquals(1535L, entry.malId)
        assertEquals(watchingCategoryId(), entry.categoryId)
        assertNull(entry.anilistId)
    }

    @Test
    fun `MAL pull backfills malId on a title-matched existing entry`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        // Entry already imported from AniList (anilistId set, no malId).
        fx.library.add(
            LibraryRepository.LibraryEntry(animeId = 777, title = "Death Note", anilistId = 777),
        )

        val (imported, updated) = fx.service.pullMal(listOf(malEntry(1535, "Death Note")))

        assertEquals(0, imported)
        assertEquals(1, updated)
        val entry = fx.library.get(777)!!
        assertEquals(777L, entry.animeId, "animeId must stay stable across trackers")
        assertEquals(1535L, entry.malId, "malId attached to the existing entry")
    }

    @Test
    fun `MAL pull leaves already-linked entries untouched`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        fx.library.add(
            LibraryRepository.LibraryEntry(animeId = 1535, title = "Death Note", malId = 1535),
        )

        val (imported, updated) = fx.service.pullMal(listOf(malEntry(1535, "Death Note")))

        assertEquals(0, imported)
        assertEquals(0, updated)
    }

    // ----------------------------------------------------------------------
    // Push plan (MAL)
    // ----------------------------------------------------------------------

    @Test
    fun `MAL push plan takes max of local and remote progress, capped by total`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        fx.library.add(LibraryRepository.LibraryEntry(animeId = 5, title = "One Piece", malId = 5))
        // User finished episodes 1-3 (>=80% watched).
        fx.history.add(
            HistoryRepository.HistoryEntry(
                animeId = 5, episodeId = 51, episodeNumber = 1.0,
                lastSecondSeen = 100, totalSeconds = 100,
            ),
        )
        fx.history.add(
            HistoryRepository.HistoryEntry(
                animeId = 5, episodeId = 52, episodeNumber = 2.0,
                lastSecondSeen = 100, totalSeconds = 100,
            ),
        )
        fx.history.add(
            HistoryRepository.HistoryEntry(
                animeId = 5, episodeId = 53, episodeNumber = 3.0,
                lastSecondSeen = 100, totalSeconds = 100,
            ),
        )

        // Remote progress 2 < local 3 → pushes 3.
        val plans = fx.service.planMalPush(listOf(malEntry(5, "One Piece", progress = 2, total = 12)))
        assertEquals(listOf(PushPlan(5, 3, null)), plans)

        // Remote progress 10 > local 3, capped at total 12 → pushes 10.
        val plans2 = fx.service.planMalPush(listOf(malEntry(5, "One Piece", progress = 10, total = 12)))
        assertEquals(listOf(PushPlan(5, 10, null)), plans2)
    }

    @Test
    fun `MAL push plan maps library category to list status`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        fx.library.add(
            LibraryRepository.LibraryEntry(animeId = 5, title = "Done", malId = 5, categoryId = 2L), // "Completed"
        )

        val plans = fx.service.planMalPush(listOf(malEntry(5, "Done", status = "watching", progress = 0, total = 12)))

        assertEquals(1, plans.size)
        assertEquals("completed", plans[0].status)
    }

    @Test
    fun `MAL push plan emits no status when it already matches`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        fx.library.add(
            LibraryRepository.LibraryEntry(animeId = 5, title = "Watching", malId = 5, categoryId = 1L),
        )

        val plans = fx.service.planMalPush(listOf(malEntry(5, "Watching", status = "watching", progress = 0, total = 12)))

        assertEquals(1, plans.size)
        assertNull(plans[0].status, "matching status should not be re-pushed")
    }

    // ----------------------------------------------------------------------
    // Pull + push (Kitsu)
    // ----------------------------------------------------------------------

    @Test
    fun `Kitsu pull imports with kitsuId and category mapping`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)

        val (imported, updated) = fx.service.pullKitsu(listOf(kitsuEntry(40852, "Jujutsu Kaisen", status = "planned")))

        assertEquals(1, imported)
        val entry = fx.library.get(40852)!!
        assertEquals(40852L, entry.kitsuId)
        assertEquals(4L, entry.categoryId) // "Plan to Watch" preset
    }

    @Test
    fun `Kitsu push plan uses the kitsu anime id`(@TempDir tempDir: Path) = runBlocking {
        val fx = fixture(tempDir)
        fx.library.add(
            LibraryRepository.LibraryEntry(animeId = 40852, title = "Jujutsu Kaisen", kitsuId = 40852, categoryId = 1L),
        )

        val plans = fx.service.planKitsuPush(listOf(kitsuEntry(40852, "Jujutsu Kaisen", progress = 4, total = 24)))

        assertEquals(1, plans.size)
        assertEquals(40852L, plans[0].remoteId)
        assertEquals(4, plans[0].progress)
        assertNull(plans[0].status) // category "Watching" == kitsu "current"? no — plan maps Watching→current vs remote "current" → null
    }

    // ----------------------------------------------------------------------
    // Status / category mapping
    // ----------------------------------------------------------------------

    @Test
    fun `category mapping helpers translate tracker statuses`() {
        val categories = listOf(
            CategoryEntry(id = 0L, name = "Default"),
            CategoryEntry(id = 1L, name = "Watching"),
            CategoryEntry(id = 2L, name = "Completed"),
            CategoryEntry(id = 4L, name = "Plan to Watch"),
        )

        assertEquals(1L, TrackerLibrarySyncService.malCategoryForStatus("watching", categories))
        assertEquals(2L, TrackerLibrarySyncService.malCategoryForStatus("completed", categories))
        assertEquals(CATEGORY_DEFAULT_ID, TrackerLibrarySyncService.malCategoryForStatus("on_hold", categories))

        assertEquals(1L, TrackerLibrarySyncService.kitsuCategoryForStatus("current", categories))
        assertEquals(4L, TrackerLibrarySyncService.kitsuCategoryForStatus("planned", categories))
        assertEquals(CATEGORY_DEFAULT_ID, TrackerLibrarySyncService.kitsuCategoryForStatus("on_hold", categories))

        assertEquals("watching", TrackerLibrarySyncService.malStatusForCategory("Watching", "completed"))
        assertEquals("planned", TrackerLibrarySyncService.kitsuStatusForCategory("Plan to Watch", "current"))
        assertNull(TrackerLibrarySyncService.malStatusForCategory("Watching", "watching"))
        assertNull(TrackerLibrarySyncService.kitsuStatusForCategory("Completed", "completed"))
        assertNull(TrackerLibrarySyncService.malStatusForCategory("Unknown Category", "watching"))
    }

    @Test
    fun `unknown tracker returns an error outcome`(@TempDir tempDir: Path) = runBlocking {
        val result = service(tempDir).syncNow("shikimori")
        assertTrue(result.errors.isNotEmpty())
    }
}
