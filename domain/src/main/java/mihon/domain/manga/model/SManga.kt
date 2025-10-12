package mihon.domain.manga.model

import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga

fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        // SY -->
        ogTitle = title,
        ogArtist = artist,
        ogAuthor = author,
        ogDescription = description,
        ogGenre = getGenres(),
        ogStatus = status.toLong(),
        ogThumbnailUrl = thumbnail_url,
        // SY <--
        // AY -->
        backgroundUrl = background_url,
        // <-- AY
        updateStrategy = update_strategy,
        // AY -->
        fetchType = fetch_type,
        seasonNumber = season_number,
        // <-- AY
        initialized = initialized,
        source = sourceId,
    )
}
