package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.Serializable

@Serializable
data class SMUserListEntry(
    val id: Long,
    val episodes: Double,
    val score: Int,
    val status: String,
) {
    fun toAnimeTrack(trackId: Long, anime: SMAnime): Track {
        return Track.create(trackId).apply {
            title = anime.name
            remote_id = this@SMUserListEntry.id
            total_episodes = anime.episodes!!
            library_id = this@SMUserListEntry.id
            last_episode_seen = this@SMUserListEntry.episodes
            score = this@SMUserListEntry.score.toDouble()
            status = toTrackStatus(this@SMUserListEntry.status)
            tracking_url = ShikimoriApi.BASE_URL + anime.url
        }
    }
}
