package tachiyomi.data.manga

import aniyomi.domain.anime.SeasonAnime
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.model.DeletableAnime

object MangaMapper {
    fun mapManga(
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
        chapterFlags: Long,
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
        notes: String,
        // AY -->
        fetchType: FetchType,
        parentId: Long?,
        seasonFlags: Long,
        seasonNumber: Double,
        seasonSourceOrder: Long,
        backgroundUrl: String?,
        backgroundLastModified: Long,
        // <-- AY
    ): Manga = Manga(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0,
        nextUpdate = nextUpdate ?: 0,
        fetchInterval = calculateInterval.toInt(),
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        // AY -->
        backgroundLastModified = backgroundLastModified,
        // <-- AY
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
        // AY -->
        backgroundUrl = backgroundUrl,
        // <-- AY
        updateStrategy = updateStrategy,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        notes = notes,
        // AY -->
        fetchType = fetchType,
        parentId = parentId,
        seasonFlags = seasonFlags,
        seasonNumber = seasonNumber,
        seasonSourceOrder = seasonSourceOrder,
        // <-- AY
    )

    fun mapLibraryManga(
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
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        // AY -->
        fetchType: FetchType,
        parentId: Long?,
        seasonFlags: Long,
        seasonNumber: Double,
        seasonSourceOrder: Long,
        backgroundUrl: String?,
        backgroundLastModified: Long,
        // <-- AY
        totalCount: Long,
        readCount: Double,
        latestUpload: Long,
        chapterFetchedAt: Long,
        lastRead: Long,
        bookmarkCount: Double,
        // KMK -->
        bookmarkedReadCount: Long,
        // KMK <--
        // AY -->
        fillermarkCount: Double,
        // <-- AY
        categories: String,
    ): LibraryManga = LibraryManga(
        manga = mapManga(
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
            chapterFlags,
            coverLastModified,
            dateAdded,
            // SY -->
            filteredScanlators,
            // SY <--
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            // AY -->
            fetchType,
            parentId,
            seasonFlags,
            seasonNumber,
            seasonSourceOrder,
            backgroundUrl,
            backgroundLastModified,
            // <-- AY
        ),
        categories = categories.split(",").map { it.toLong() },
        totalChapters = totalCount,
        readCount = readCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        // KMK -->
        bookmarkReadCount = bookmarkedReadCount,
        chapterFlags = chapterFlags,
        // KMK <--
        // AY -->
        fillermarkCount = fillermarkCount.toLong(),
        // <-- AY
        latestUpload = latestUpload,
        chapterFetchedAt = chapterFetchedAt,
        lastRead = lastRead,
    )

    fun mapMangaWithChapterCount(
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
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        filteredScanlators: String?,
        // SY <--
        updateStrategy: UpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        // AY -->
        fetchType: FetchType,
        parentId: Long?,
        seasonFlags: Long,
        seasonNumber: Double,
        seasonSourceOrder: Long,
        backgroundUrl: String?,
        backgroundLastModified: Long,
        // <-- AY
        totalCount: Long,
    ): MangaWithChapterCount = MangaWithChapterCount(
        manga = mapManga(
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
            chapterFlags,
            coverLastModified,
            dateAdded,
            // SY -->
            filteredScanlators,
            // SY <--
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            // AY -->
            fetchType,
            parentId,
            seasonFlags,
            seasonNumber,
            seasonSourceOrder,
            backgroundUrl,
            backgroundLastModified,
            // <-- AY
        ),
        chapterCount = totalCount,
    )

    // AY -->
    fun mapSeasonAnime(
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
        chapterFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        // SY -->
        filteredScanlators: String?,
        // SY <--
        updateStrategy: AnimeUpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        isSyncing: Long,
        notes: String,
        fetchType: FetchType,
        parentId: Long?,
        seasonFlags: Long,
        seasonNumber: Double,
        seasonSourceOrder: Long,
        backgroundUrl: String?,
        backgroundLastModified: Long,
        totalCount: Long,
        seenCount: Double,
        latestUpload: Long,
        fetchedAt: Long,
        lastSeen: Long,
        bookmarkCount: Double,
        // KMK -->
        bookmarkedSeenCount: Long,
        // KMK <--
        fillermarkCount: Double,
    ): SeasonAnime = SeasonAnime(
        anime = mapManga(
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
            chapterFlags,
            coverLastModified,
            dateAdded,
            filteredScanlators,
            updateStrategy,
            calculateInterval,
            lastModifiedAt,
            favoriteModifiedAt,
            version,
            isSyncing,
            notes,
            fetchType,
            parentId,
            seasonFlags,
            seasonNumber,
            seasonSourceOrder,
            backgroundUrl,
            backgroundLastModified,
        ),
        totalCount = totalCount,
        seenCount = seenCount.toLong(),
        bookmarkCount = bookmarkCount.toLong(),
        // KMK -->
        bookmarkSeenCount = bookmarkedSeenCount,
        episodeFlags = chapterFlags,
        // KMK <--
        fillermarkCount = fillermarkCount.toLong(),
        latestUpload = latestUpload,
        fetchedAt = fetchedAt,
        lastSeen = lastSeen,
    )

    fun mapDeletableAnime(
        id: Long,
        source: Long,
        fetchType: FetchType,
    ): DeletableAnime = DeletableAnime(
        animeId = id,
        sourceId = source,
        fetchType = fetchType,
    )
    // <-- AY
}
