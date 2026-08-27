package app.anikku.macos.ui.screens.models

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Convert an [SAnime] from the source API into the local [AnimeModel] for rendering.
 *
 * Guards against [UninitializedPropertyAccessException] for `lateinit` properties
 * that some sources may forget to set (like [url] and [title]).
 * Uses the anime URL's hash as a stable ID for navigation.
 */
fun SAnime.toAnimeModel(sourceId: Long = 0L): AnimeModel {
    val safeUrl = try {
        url
    } catch (_: UninitializedPropertyAccessException) {
        ""
    }
    val safeTitle = try {
        title
    } catch (_: UninitializedPropertyAccessException) {
        ""
    }

    return AnimeModel(
        id = if (safeUrl.isNotBlank()) safeUrl.hashCode().toLong().let { if (it == 0L) 1L else it } else 1L,
        title = safeTitle,
        source = sourceId,
        author = this.author,
        artist = this.artist,
        description = this.description,
        genre = this.getGenres(),
        status = this.status,
        thumbnailUrl = this.thumbnail_url,
        url = safeUrl,
        favorite = false,
        coverLastModified = 0L,
    )
}

/**
 * Convert an [SEpisode] from the source API into the local [EpisodeModel] for rendering.
 *
 * Guards against [UninitializedPropertyAccessException] for `lateinit` properties
 * that some sources may forget to set.
 * Uses the episode URL's hash as a stable ID for navigation.
 */
fun SEpisode.toEpisodeModel(animeId: Long = 0L): EpisodeModel {
    val safeUrl = try {
        url
    } catch (_: UninitializedPropertyAccessException) {
        ""
    }
    val safeName = try {
        name
    } catch (_: UninitializedPropertyAccessException) {
        "Unknown"
    }

    return EpisodeModel(
        id = (safeUrl.hashCode()).toLong().let { if (it == 0L) 1L else it },
        animeId = animeId,
        name = safeName,
        episodeNumber = this.episode_number.toDouble(),
        url = safeUrl,
        seen = false,
        bookmark = false,
        dateUpload = this.date_upload,
        scanlator = this.scanlator,
    )
}
