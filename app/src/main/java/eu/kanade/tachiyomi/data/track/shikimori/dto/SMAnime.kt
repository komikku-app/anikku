package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SMAnime(
    val id: Long,
    val name: String,
    val episodes: Long?,
    val image: SUAnimeCover,
    val score: Double,
    val url: String,
    val status: String,
    val kind: String,
    @SerialName("aired_on")
    val airedOn: String?,
) {
    fun toTrackSearch(trackId: Long): TrackSearch {
        return TrackSearch.create(trackId).apply {
            remote_id = this@SMAnime.id
            title = name
            total_episodes = episodes!!
            cover_url = ShikimoriApi.BASE_URL + image.preview
            summary = ""
            score = this@SMAnime.score
            tracking_url = ShikimoriApi.BASE_URL + url
            publishing_status = this@SMAnime.status
            publishing_type = kind
            start_date = airedOn ?: ""
        }
    }
}

@Serializable
data class SUAnimeCover(
    val preview: String,
)
