package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.Serializable

@Serializable
data class TraktUserSettings(
    val user: TraktUser,
)

@Serializable
data class TraktUser(
    val username: String,
)
