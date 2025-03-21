package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuAnimeMetadata(
    val data: KitsuAnimeMetadataData,
)

@Serializable
data class KitsuAnimeMetadataData(
    val findLibraryEntryById: KitsuAnimeMetadataById,
)

@Serializable
data class KitsuAnimeMetadataById(
    val media: KitsuAnimeMetadataMedia,
)

@Serializable
data class KitsuAnimeMetadataMedia(
    val id: String,
    val titles: KitsuAnimeTitle,
    val posterImage: KitsuAnimeCover,
    val description: KitsuAnimeDescription,
    val staff: KitsuAnimeStaff,
)

@Serializable
data class KitsuAnimeTitle(
    val preferred: String,
)

@Serializable
data class KitsuAnimeCover(
    val original: KitsuAnimeCoverUrl,
)

@Serializable
data class KitsuAnimeCoverUrl(
    val url: String,
)

@Serializable
data class KitsuAnimeDescription(
    val en: String?,
)

@Serializable
data class KitsuAnimeStaff(
    val nodes: List<KitsuAnimeStaffNode>,
)

@Serializable
data class KitsuAnimeStaffNode(
    val role: String,
    val person: KitsuAnimeStaffPerson,
)

@Serializable
data class KitsuAnimeStaffPerson(
    val name: String,
)
