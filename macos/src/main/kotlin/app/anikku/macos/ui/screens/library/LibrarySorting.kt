package app.anikku.macos.ui.screens.library

import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository

/** Sort orders for the library grid/list. */
enum class LibrarySortMode {
    Title, Status, LastUpdated, LastWatched, DateAdded, Progress,
}

/** Watch-progress quick filters for the library. */
enum class LibraryProgressFilter {
    All, InProgress, NotStarted, Finished,
}

/**
 * Latest history entry per anime, keyed by animeId. Drives both the
 * "Last Watched" sort and the progress filter.
 */
fun latestHistoryByAnime(history: List<HistoryRepository.HistoryEntry>): Map<Long, HistoryRepository.HistoryEntry> =
    history.groupBy { it.animeId }.mapValues { (_, list) -> list.maxByOrNull { it.seenAt }!! }

/**
 * Progress-filter match. "In progress" = actually advanced into the episode but
 * not finished; "Finished" = watched to (near) the end of a known-length
 * episode; "Not started" = no history or never advanced.
 */
fun LibraryProgressFilter.matches(
    entry: LibraryRepository.LibraryEntry,
    latest: HistoryRepository.HistoryEntry?,
): Boolean = when (this) {
    LibraryProgressFilter.All -> true
    LibraryProgressFilter.InProgress -> latest != null && latest.lastSecondSeen > 0 &&
        (latest.totalSeconds <= 0 || latest.lastSecondSeen < latest.totalSeconds - 5)
    LibraryProgressFilter.NotStarted -> latest == null || latest.lastSecondSeen <= 0
    LibraryProgressFilter.Finished -> latest != null && latest.totalSeconds > 0 &&
        latest.lastSecondSeen >= latest.totalSeconds - 5
}

/**
 * Pure filter + sort pipeline for the library. Search matches title, author,
 * and genres; [categoryId] null = all categories. Pure so it is unit-testable.
 */
fun filterAndSortLibrary(
    entries: List<LibraryRepository.LibraryEntry>,
    query: String,
    categoryId: Long?,
    sortMode: LibrarySortMode,
    progressFilter: LibraryProgressFilter,
    latestByAnime: Map<Long, HistoryRepository.HistoryEntry>,
): List<LibraryRepository.LibraryEntry> {
    val q = query.trim()
    var filtered = entries

    if (categoryId != null) {
        filtered = filtered.filter { it.categoryId == categoryId }
    }

    if (q.isNotEmpty()) {
        filtered = filtered.filter { entry ->
            entry.title.contains(q, ignoreCase = true) ||
                entry.author?.contains(q, ignoreCase = true) == true ||
                entry.genre?.any { it.contains(q, ignoreCase = true) } == true
        }
    }

    filtered = filtered.filter { progressFilter.matches(it, latestByAnime[it.animeId]) }

    return when (sortMode) {
        LibrarySortMode.Title -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortMode.Status -> filtered.sortedBy { it.status }
        LibrarySortMode.LastUpdated -> filtered.sortedByDescending { it.lastUpdatedAt }
        LibrarySortMode.LastWatched -> filtered.sortedByDescending { latestByAnime[it.animeId]?.seenAt ?: 0L }
        LibrarySortMode.DateAdded -> filtered.sortedByDescending { it.addedAt }
        LibrarySortMode.Progress -> filtered.sortedByDescending { entry ->
            val latest = latestByAnime[entry.animeId]
            when {
                latest == null || latest.totalSeconds <= 0 -> -1f // not started last
                else -> (latest.lastSecondSeen.toFloat() / latest.totalSeconds).coerceIn(0f, 1f)
            }
        }
    }
}
