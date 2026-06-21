package aniyomi.domain.anime

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.LibraryAnime

data class SeasonAnime(
    val anime: Anime,
    val totalEpisodes: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Long,
    val fetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = anime.id

    val seen
        get() = totalEpisodes == seenCount

    val unseenCount
        get() = totalEpisodes - seenCount

    val hasStarted = seenCount > 0

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasFillermarks
        get() = fillermarkCount > 0

    fun toLibraryAnime(): LibraryAnime {
        return LibraryAnime(
            anime = anime,
            category = -1L,
            totalEpisodes = totalEpisodes,
            seenCount = seenCount,
            bookmarkCount = bookmarkCount,
            fillermarkCount = fillermarkCount,
            latestUpload = latestUpload,
            episodeFetchedAt = fetchedAt,
            lastSeen = lastSeen,
        )
    }
}
