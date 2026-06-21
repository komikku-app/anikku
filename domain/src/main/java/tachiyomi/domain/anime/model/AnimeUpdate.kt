package tachiyomi.domain.anime.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType

data class AnimeUpdate(
    val id: Long,
    val source: Long? = null,
    val favorite: Boolean? = null,
    val lastUpdate: Long? = null,
    val nextUpdate: Long? = null,
    val fetchInterval: Int? = null,
    val dateAdded: Long? = null,
    val viewerFlags: Long? = null,
    val episodeFlags: Long? = null,
    val coverLastModified: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long? = null,
    val thumbnailUrl: String? = null,
    val updateStrategy: UpdateStrategy? = null,
    val initialized: Boolean? = null,
    val version: Long? = null,
    val fetchType: FetchType? = null,
    val parentId: Long? = null,
    val seasonFlags: Long? = null,
    val seasonNumber: Double? = null,
    val seasonSourceOrder: Long? = null,
    // SY -->
    val filteredScanlators: List<String>? = null,
    // SY <--
)

fun Anime.toAnimeUpdate(): AnimeUpdate {
    return AnimeUpdate(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate,
        nextUpdate = nextUpdate,
        fetchInterval = fetchInterval,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        episodeFlags = episodeFlags,
        coverLastModified = coverLastModified,
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
        updateStrategy = updateStrategy,
        initialized = initialized,
        version = version,
        fetchType = fetchType,
        parentId = parentId,
        seasonFlags = seasonFlags,
        seasonNumber = seasonNumber,
        seasonSourceOrder = seasonSourceOrder,
    )
}
