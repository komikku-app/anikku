package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ALAnimeMetadata(
    val data: ALAnimeMetadataData,
)

@Serializable
data class ALAnimeMetadataData(
    @SerialName("Media")
    val media: ALAnimeMetadataMedia,
)

@Serializable
data class ALAnimeMetadataMedia(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val staff: ALStaff,
    val studios: ALStudios,
)

@Serializable
data class ALStudios(
    val nodes: List<ALStudioNode>,
)

@Serializable
data class ALStudioNode(
    val name: String,
)

@Serializable
data class ALStaff(
    val edges: List<ALStaffEdge>,
)

@Serializable
data class ALStaffEdge(
    val role: String,
    val node: ALStaffNode,
)

@Serializable
data class ALStaffNode(
    val name: ALItemTitle,
)
