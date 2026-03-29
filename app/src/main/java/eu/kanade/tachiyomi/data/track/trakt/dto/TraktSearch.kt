package eu.kanade.tachiyomi.data.track.trakt.dto

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraktSearchResult(
    val type: String,
    val show: TraktShow? = null,
    val movie: TraktMovie? = null,
) {
    fun toTrackSearch(): TrackSearch? = when (type) {
        "show" -> show?.toTrackSearch()
        "movie" -> movie?.toTrackSearch()
        else -> null
    }
}

@Serializable
data class TraktShow(
    val title: String,
    val year: Int? = null,
    val ids: TraktShowIds,
    val overview: String? = null,
    val status: String? = null,
    @SerialName("aired_episodes")
    val airedEpisodes: Int? = null,
) {
    fun toTrackSearch(): TrackSearch {
        return TrackSearch.create(TrackerManager.TRAKT).apply {
            remote_id = ids.trakt
            title = this@TraktShow.title
            total_episodes = airedEpisodes?.toLong() ?: 0L
            cover_url = ""
            summary = overview ?: ""
            tracking_url = "https://trakt.tv/shows/${ids.slug}"
            publishing_status = status ?: ""
            publishing_type = "tv"
            start_date = year?.toString() ?: ""
        }
    }
}

@Serializable
data class TraktMovie(
    val title: String,
    val year: Int? = null,
    val ids: TraktMovieIds,
    val overview: String? = null,
    val status: String? = null,
) {
    fun toTrackSearch(): TrackSearch {
        return TrackSearch.create(TrackerManager.TRAKT).apply {
            remote_id = ids.trakt
            title = this@TraktMovie.title
            total_episodes = 1L
            cover_url = ""
            summary = overview ?: ""
            tracking_url = "https://trakt.tv/movies/${ids.slug}"
            publishing_status = status ?: ""
            publishing_type = "movie"
            start_date = year?.toString() ?: ""
        }
    }
}

@Serializable
data class TraktShowIds(
    val trakt: Long,
    val slug: String,
    val tvdb: Long? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
)

@Serializable
data class TraktMovieIds(
    val trakt: Long,
    val slug: String,
    val imdb: String? = null,
    val tmdb: Long? = null,
)
