package eu.kanade.tachiyomi.data.track.simkl.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SimklSyncWatched(
    val result: Boolean?,
    @SerialName("last_watched_at")
    val lastWatchedAt: String?,
    val list: String?,
)
