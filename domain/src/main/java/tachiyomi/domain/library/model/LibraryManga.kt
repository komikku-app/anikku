package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val category: Long,
    val totalEpisodes: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    // AM (FILLERMARK) -->
    val fillermarkCount: Long,
    // <-- AM (FILLERMARK)
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = manga.id

    val unseenCount
        get() = totalEpisodes - seenCount

    val hasBookmarks
        get() = bookmarkCount > 0

    // AM (FILLERMARK) -->
    val hasFillermarks
        get() = fillermarkCount > 0
    // <-- AM (FILLERMARK)

    val hasStarted = seenCount > 0
}
