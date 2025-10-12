package tachiyomi.domain.manga.model

import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy

typealias AnimeUpdate = MangaUpdate
data class MangaUpdate(
    val id: Long,
    val source: Long? = null,
    val favorite: Boolean? = null,
    val lastUpdate: Long? = null,
    val nextUpdate: Long? = null,
    val fetchInterval: Int? = null,
    val dateAdded: Long? = null,
    val viewerFlags: Long? = null,
    val chapterFlags: Long? = null,
    val coverLastModified: Long? = null,
    // AY -->
    val backgroundLastModified: Long? = null,
    // <-- AY
    val url: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long? = null,
    val thumbnailUrl: String? = null,
    // AY -->
    val backgroundUrl: String? = null,
    // <-- AY
    val updateStrategy: UpdateStrategy? = null,
    val initialized: Boolean? = null,
    val version: Long? = null,
    val notes: String? = null,
    // SY -->
    val filteredScanlators: List<String>? = null,
    // SY <--
    // AY -->
    val fetchType: FetchType? = null,
    val parentId: Long? = null,
    val seasonFlags: Long? = null,
    val seasonNumber: Double? = null,
    val seasonSourceOrder: Long? = null,
    // <-- AY
)

fun Manga.toMangaUpdate(): MangaUpdate {
    return MangaUpdate(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate,
        nextUpdate = nextUpdate,
        fetchInterval = fetchInterval,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        chapterFlags = chapterFlags,
        coverLastModified = coverLastModified,
        // AY -->
        backgroundLastModified = backgroundLastModified,
        // <-- AY
        url = url,
        // SY -->
        title = ogTitle,
        artist = ogArtist,
        author = ogAuthor,
        thumbnailUrl = ogThumbnailUrl,
        description = ogDescription,
        genre = ogGenre,
        status = ogStatus,
        // SY <--
        // AY -->
        backgroundUrl = backgroundUrl,
        // <-- AY
        updateStrategy = updateStrategy,
        initialized = initialized,
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
}
