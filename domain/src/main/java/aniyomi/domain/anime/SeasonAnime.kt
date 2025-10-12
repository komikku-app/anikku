package aniyomi.domain.anime

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.LibraryManga
import kotlin.Long

data class SeasonAnime(
    val anime: Anime,
    val totalCount: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    // KMK -->
    val bookmarkSeenCount: Long,
    val episodeFlags: Long,
    // KMK <--
    val fillermarkCount: Long,
    val latestUpload: Long,
    val fetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = anime.id

    val seen
        get() = totalCount == seenCount

    val unseenCount
        get() = totalCount - seenCount

    val hasStarted = seenCount > 0

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasFillermarks
        get() = fillermarkCount > 0

    fun toLibraryAnime(): LibraryManga {
        return LibraryManga(
            manga = anime,
            categories = emptyList(),
            totalChapters = totalCount,
            readCount = seenCount,
            bookmarkCount = bookmarkCount,
            // KMK -->
            bookmarkReadCount = bookmarkSeenCount,
            chapterFlags = episodeFlags,
            // KMK <--
            fillermarkCount = fillermarkCount,
            latestUpload = latestUpload,
            chapterFetchedAt = fetchedAt,
            lastRead = lastSeen,
        )
    }
}
