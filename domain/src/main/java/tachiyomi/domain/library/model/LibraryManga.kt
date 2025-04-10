package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val category: Long,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    // AM (FILLERMARK) -->
    val fillermarkCount: Long,
    // <-- AM (FILLERMARK)
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
) {
    val id: Long = manga.id

    val unreadCount
        get() = totalChapters - readCount

    val hasBookmarks
        get() = bookmarkCount > 0

    // AM (FILLERMARK) -->
    val hasFillermarks
        get() = fillermarkCount > 0
    // <-- AM (FILLERMARK)

    val hasStarted = readCount > 0
}
