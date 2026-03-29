package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraktShowProgress(
    val aired: Int = 0,
    val completed: Int = 0,
    @SerialName("last_watched_at")
    val lastWatchedAt: String? = null,
)
