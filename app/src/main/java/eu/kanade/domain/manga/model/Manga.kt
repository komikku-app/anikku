package eu.kanade.domain.manga.model

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.cache.BackgroundCache
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

// AY -->
val Manga.seasonDownloadedFilter: TriState
    get() {
        if (Injekt.get<BasePreferences>().downloadedOnly().get()) return TriState.ENABLED_IS
        return when (seasonDownloadedFilterRaw) {
            Manga.SEASON_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Manga.SEASON_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

fun Manga.seasonsFiltered(): Boolean {
    return seasonDownloadedFilter != TriState.DISABLED ||
        seasonUnseenFilter != TriState.DISABLED ||
        seasonStartedFilter != TriState.DISABLED ||
        seasonCompletedFilter != TriState.DISABLED ||
        seasonBookmarkedFilter != TriState.DISABLED ||
        seasonFillermarkedFilter != TriState.DISABLED
}
// <-- AY

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
    // SY <--
    it.thumbnail_url = ogThumbnailUrl
    // AY -->
    it.background_url = backgroundUrl
    it.fetch_type = fetchType
    it.season_number = seasonNumber
    // <-- AY
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
    // AY -->
    val backgroundUrl = other.background_url ?: backgroundUrl
    // <-- AY
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
        // AY -->
        backgroundUrl = backgroundUrl,
        // <-- AY
        updateStrategy = other.update_strategy,
        // AY -->
        fetchType = other.fetch_type,
        seasonNumber = other.season_number,
        // <-- AY
        initialized = other.initialized && initialized,
    )
}

fun Manga.hasCustomCover(coverCache: CoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}

// AY -->
fun Manga.hasCustomBackground(backgroundCache: BackgroundCache = Injekt.get()): Boolean {
    return backgroundCache.getCustomBackgroundFile(id).exists()
}
// <-- AY
