package eu.kanade.tachiyomi.util

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomBackground
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.image.LocalBackgroundManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.image.LocalEpisodeThumbnailManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.time.Instant

/**
 * Call before updating [Manga.thumbnailUrl] to ensure old cover can be cleared from cache
 */
fun Manga.prepUpdateCover(coverCache: CoverCache, remoteManga: SManga, refreshSameUrl: Boolean): Manga {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteManga.thumbnail_url ?: return this

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return this

    if (!refreshSameUrl && thumbnailUrl == newUrl) return this

    return when {
        isLocal() -> {
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
        hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
            this
        }
        else -> {
            coverCache.deleteFromCache(this, false)
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
    }
}

// AY -->

/**
 * Call before updating [Anime.background_url] to ensure old background can be cleared from cache
 */
fun Anime.prepUpdateBackground(
    backgroundCache: BackgroundCache,
    remoteAnime: SAnime,
    refreshSameUrl: Boolean,
): Anime {
    // Never refresh backgrounds if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteAnime.background_url ?: return this

    // Never refresh covers if the url is empty to avoid "losing" existing backgrounds
    if (newUrl.isEmpty()) return this

    if (!refreshSameUrl && backgroundUrl == newUrl) return this

    return when {
        isLocal() -> {
            this.copy(backgroundLastModified = Instant.now().toEpochMilli())
        }
        hasCustomBackground(backgroundCache) -> {
            backgroundCache.deleteFromCache(this, false)
            this
        }
        else -> {
            backgroundCache.deleteFromCache(this, false)
            this.copy(backgroundLastModified = Instant.now().toEpochMilli())
        }
    }
}
// <-- AY

fun Manga.removeCovers(coverCache: CoverCache = Injekt.get()): Manga {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

// AY -->
fun Anime.removeBackgrounds(backgroundCache: BackgroundCache): Anime {
    if (isLocal()) return this
    return if (backgroundCache.deleteFromCache(this, true) > 0) {
        copy(backgroundLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}
// <-- AY

suspend fun Manga.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateManga: UpdateManga = Injekt.get(),
    coverCache: CoverCache = Injekt.get(),
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}

// AY -->
suspend fun Anime.editBackground(
    backgroundManager: LocalBackgroundManager,
    stream: InputStream,
    updateAnime: UpdateManga = Injekt.get(),
    backgroundCache: BackgroundCache = Injekt.get(),
) {
    if (isLocal()) {
        backgroundManager.update(toSManga(), stream)
        updateAnime.awaitUpdateBackgroundLastModified(id)
    } else if (favorite) {
        backgroundCache.setCustomBackgroundToCache(this, stream)
        updateAnime.awaitUpdateBackgroundLastModified(id)
    }
}

fun SEpisode.editThumbnail(
    anime: Anime,
    thumbnailManager: LocalEpisodeThumbnailManager,
    stream: InputStream,
) {
    if (anime.isLocal()) {
        thumbnailManager.update(anime.toSManga(), this, stream)
    }
}
// <-- AY
