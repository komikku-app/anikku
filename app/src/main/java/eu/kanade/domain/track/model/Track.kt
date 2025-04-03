package eu.kanade.domain.track.model

import tachiyomi.domain.track.model.Track
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

fun Track.copyPersonalFrom(other: Track): Track {
    return this.copy(
        lastEpisodeSeen = other.lastEpisodeSeen,
        score = other.score,
        status = other.status,
        startDate = other.startDate,
        finishDate = other.finishDate,
    )
}

fun Track.toDbTrack(): DbTrack = DbTrack.create(trackerId).also {
    it.id = id
    it.anime_id = animeId
    it.remote_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_episode_seen = lastEpisodeSeen
    it.total_episodes = totalEpisodes
    it.status = status
    it.score = score
    it.tracking_url = remoteUrl
    it.started_watching_date = startDate
    it.finished_watching_date = finishDate
}

fun DbTrack.toDomainTrack(idRequired: Boolean = true): Track? {
    val trackId = id ?: if (!idRequired) -1 else return null
    return Track(
        id = trackId,
        animeId = anime_id,
        trackerId = tracker_id,
        remoteId = remote_id,
        libraryId = library_id,
        title = title,
        lastEpisodeSeen = last_episode_seen,
        totalEpisodes = total_episodes,
        status = status,
        score = score,
        remoteUrl = tracking_url,
        startDate = started_watching_date,
        finishDate = finished_watching_date,
    )
}
