package tachiyomi.core.metadata.tachiyomi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EpisodeDetails(
    @SerialName("episode_number")
    val episodeNumber: Float,
    val name: String? = null,
    @SerialName("date_upload")
    val dateUpload: String? = null,
    val fillermark: Boolean = false,
    val scanlator: String? = null,
    val summary: String? = null,
    @SerialName("preview_url")
    val previewUrl: String? = null,
)
