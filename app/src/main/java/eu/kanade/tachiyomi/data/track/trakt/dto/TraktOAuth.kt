package eu.kanade.tachiyomi.data.track.trakt.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TraktOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    val scope: String = "public",
)

fun TraktOAuth.isExpired(): Boolean {
    return System.currentTimeMillis() / 1000 >= createdAt + expiresIn - 60
}
