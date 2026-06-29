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
        if (Injekt.get<BasePreferences>().downloadedOnly().get()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Manga.EPISODE_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Manga.EPISODE_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }
fun Manga.chaptersFiltered(): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED ||
        // AY -->
        fillermarkedFilter != TriState.DISABLED
    // <-- AY
}

fun Manga.toSManga(): SManga = SManga.create().also {
    it.url = url
    // SY -->
    it.title = ogTitle
    it.artist = ogArtist
    it.author = ogAuthor
    it.description = ogDescription
    it.genre = ogGenre.orEmpty().joinToString()
    it.status = ogStatus.toInt()
    it.thumbnail_url = ogThumbnailUrl
    // SY <--
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
        ogStatus = other.status.toLong(),
        // SY <--
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
