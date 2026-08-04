package app.anikku.macos.ui.screens.library

import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibrarySortingTest {

    private fun entry(
        animeId: Long,
        title: String,
        categoryId: Long = 0L,
        addedAt: Long = 0L,
        lastUpdatedAt: Long = 0L,
        author: String? = null,
    ) = LibraryRepository.LibraryEntry(
        animeId = animeId,
        title = title,
        categoryId = categoryId,
        addedAt = addedAt,
        lastUpdatedAt = lastUpdatedAt,
        author = author,
    )

    private fun history(animeId: Long, seenAt: Long, lastSecondSeen: Long, totalSeconds: Long) =
        HistoryRepository.HistoryEntry(
            animeId = animeId,
            episodeId = animeId * 100 + 1,
            seenAt = seenAt,
            lastSecondSeen = lastSecondSeen,
            totalSeconds = totalSeconds,
        )

    private fun latestOf(vararg entries: HistoryRepository.HistoryEntry) =
        latestHistoryByAnime(entries.toList())

    @Test
    fun `last watched sort orders by most recent seenAt with unseen last`() {
        val entries = listOf(entry(1, "A"), entry(2, "B"), entry(3, "C"))
        val latest = latestOf(
            history(1, seenAt = 300, lastSecondSeen = 10, totalSeconds = 100),
            history(3, seenAt = 900, lastSecondSeen = 10, totalSeconds = 100),
        )

        val sorted = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.LastWatched, LibraryProgressFilter.All, latest,
        )

        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.animeId })
    }

    @Test
    fun `date added sort is newest first`() {
        val entries = listOf(
            entry(1, "Old", addedAt = 100),
            entry(2, "New", addedAt = 900),
            entry(3, "Middle", addedAt = 500),
        )

        val sorted = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.DateAdded, LibraryProgressFilter.All, emptyMap(),
        )

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.animeId })
    }

    @Test
    fun `progress sort puts started-most-advanced first and not-started last`() {
        val entries = listOf(entry(1, "A"), entry(2, "B"), entry(3, "C"))
        val latest = latestOf(
            history(1, seenAt = 1, lastSecondSeen = 90, totalSeconds = 100), // 90%
            history(2, seenAt = 2, lastSecondSeen = 10, totalSeconds = 100), // 10%
        )

        val sorted = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.Progress, LibraryProgressFilter.All, latest,
        )

        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.animeId })
    }

    @Test
    fun `in progress filter keeps only partially watched episodes`() {
        val entries = listOf(entry(1, "A"), entry(2, "B"), entry(3, "C"))
        val latest = latestOf(
            history(1, seenAt = 1, lastSecondSeen = 400, totalSeconds = 1200), // in progress
            history(2, seenAt = 2, lastSecondSeen = 1200, totalSeconds = 1200), // finished
            history(3, seenAt = 3, lastSecondSeen = 0, totalSeconds = 1200),    // never started
        )

        val filtered = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.Title, LibraryProgressFilter.InProgress, latest,
        )

        assertEquals(listOf(1L), filtered.map { it.animeId })
    }

    @Test
    fun `finished filter keeps only watched-to-end episodes`() {
        val entries = listOf(entry(1, "A"), entry(2, "B"), entry(3, "C"))
        val latest = latestOf(
            history(1, seenAt = 1, lastSecondSeen = 400, totalSeconds = 1200),
            history(2, seenAt = 2, lastSecondSeen = 1200, totalSeconds = 1200),
        )

        val filtered = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.Title, LibraryProgressFilter.Finished, latest,
        )

        assertEquals(listOf(2L), filtered.map { it.animeId })
    }

    @Test
    fun `not started filter keeps entries with no progress`() {
        val entries = listOf(entry(1, "A"), entry(2, "B"))
        val latest = latestOf(
            history(1, seenAt = 1, lastSecondSeen = 400, totalSeconds = 1200),
            history(2, seenAt = 2, lastSecondSeen = 0, totalSeconds = 1200),
        )

        val filtered = filterAndSortLibrary(
            entries, "", null, LibrarySortMode.Title, LibraryProgressFilter.NotStarted, latest,
        )

        assertEquals(listOf(2L), filtered.map { it.animeId })
    }

    @Test
    fun `search filters by title and author`() {
        val entries = listOf(
            entry(1, "Solo Leveling", author = "Chugong"),
            entry(2, "One Piece", author = "Oda"),
            entry(3, "Soloist", author = "Someone"),
        )

        val byTitle = filterAndSortLibrary(entries, "solo", null, LibrarySortMode.Title, LibraryProgressFilter.All, emptyMap())
        assertEquals(setOf(1L, 3L), byTitle.map { it.animeId }.toSet())

        val byAuthor = filterAndSortLibrary(entries, "oda", null, LibrarySortMode.Title, LibraryProgressFilter.All, emptyMap())
        assertEquals(listOf(2L), byAuthor.map { it.animeId })
    }

    @Test
    fun `category filter narrows to one category`() {
        val entries = listOf(
            entry(1, "A", categoryId = 1),
            entry(2, "B", categoryId = 2),
        )

        val filtered = filterAndSortLibrary(
            entries, "", categoryId = 2, LibrarySortMode.Title, LibraryProgressFilter.All, emptyMap(),
        )

        assertEquals(listOf(2L), filtered.map { it.animeId })
    }

    @Test
    fun `latestHistoryByAnime keeps the newest entry per anime`() {
        val latest = latestOf(
            history(1, seenAt = 100, lastSecondSeen = 10, totalSeconds = 100),
            history(1, seenAt = 500, lastSecondSeen = 50, totalSeconds = 100),
            history(2, seenAt = 300, lastSecondSeen = 20, totalSeconds = 100),
        )

        assertEquals(500L, latest[1L]?.seenAt)
        assertEquals(300L, latest[2L]?.seenAt)
        assertTrue(3L !in latest)
    }
}
