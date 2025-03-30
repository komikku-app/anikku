package eu.kanade.domain.manga.model

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// TODO: move these into the domain model
val Manga.downloadedFilter: TriState
    get() {
        if (forceDownloaded()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Manga.EPISODE_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Manga.EPISODE_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }
fun Manga.episodesFiltered(): Boolean {
    return unseenFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED ||
        // AM (FILLERMARK) -->
        fillermarkedFilter != TriState.DISABLED
    // <-- AM (FILLERMARK)
}
fun Manga.forceDownloaded(): Boolean {
    return favorite && Injekt.get<BasePreferences>().downloadedOnly().get()
}

fun Manga.toSAnime(): SManga = SManga.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Manga.copyFrom(other: SManga): Manga {
    // SY -->
    val author = other.author ?: ogAuthor
    val artist = other.artist ?: ogArtist
    val thumbnailUrl = other.thumbnail_url ?: ogThumbnailUrl
    val description = other.description ?: ogDescription
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        ogGenre
    }
    // SY <--
    return this.copy(
        // SY -->
        ogAuthor = author,
        ogArtist = artist,
        ogThumbnailUrl = thumbnailUrl,
        ogDescription = description,
        ogGenre = genres,
        // SY <--
        // SY -->
        ogStatus = other.status.toLong(),
        // SY <--
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun SManga.toDomainAnime(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        // SY -->
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogThumbnailUrl = thumbnail_url,
        ogDescription = description,
        ogGenre = getGenres(),
        ogStatus = status.toLong(),
        // SY <--
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
