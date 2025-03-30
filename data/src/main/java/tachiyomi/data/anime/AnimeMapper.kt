package tachiyomi.data.anime

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.manga.model.Manga

object AnimeMapper {
    fun mapAnime(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        episodeFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        @Suppress("UNUSED_PARAMETER")
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): Manga = Manga(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0,
        nextUpdate = nextUpdate ?: 0,
        fetchInterval = calculateInterval.toInt(),
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        episodeFlags = episodeFlags,
        coverLastModified = coverLastModified,
        url = url,
        // SY -->
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogThumbnailUrl = thumbnailUrl,
        ogDescription = description,
        ogGenre = genre,
        ogStatus = status,
        // SY <--
        updateStrategy = updateStrategy,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
    )

    fun mapLibraryAnime(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        episodeFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        @Suppress("UNUSED_PARAMETER")
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        totalCount: Long,
        seenCount: Double,
        latestUpload: Long,
        episodeFetchedAt: Long,
        lastSeen: Long,
        bookmarkCount: Double,
        // AM (FILLERMARK) -->
        fillermarkCount: Double,
        // <-- AM (FILLERMARK)
        category: Long,
    ): LibraryAnime = LibraryAnime(
        manga = mapAnime(
            id,
            source,
            url,
            artist,
            author,
            description,
            genre,
            title,
            status,
            thumbnailUrl,
            favorite,
            lastUpdate,
            nextUpdate,
            initialized,
            viewerFlags,
            episodeFlags,
            coverLastModified,
            dateAdded,
            // SY -->
            null,
            // SY <--
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
        ),
        category = category,
        totalEpisodes = totalCount,
        seenCount = seenCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        // AM (FILLERMARK) -->
        fillermarkCount = fillermarkCount.toLong(),
        // <-- AM (FILLERMARK)
        latestUpload = latestUpload,
        episodeFetchedAt = episodeFetchedAt,
        lastSeen = lastSeen,
    )
}
