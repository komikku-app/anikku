package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALSearchItem(
    val id: Long,
    val title: ALItemTitle,
    val coverImage: ItemCover,
    val description: String?,
    val format: String,
    val status: String?,
    val startDate: ALFuzzyDate,
    val episodes: Long?,
    val averageScore: Int?,
    val studios: ALStudios,
    val staff: ALStaff,
) {
    fun toALAnime(): ALAnime = ALAnime(
        remoteId = id,
        title = title.userPreferred,
        imageUrl = coverImage.large,
        description = description,
        format = format.replace("_", "-"),
        publishingStatus = status ?: "",
        startDateFuzzy = startDate.toEpochMilli(),
        totalEpisodes = episodes ?: 0,
        averageScore = averageScore ?: -1,
        studios = studios,
        staff = staff,
    )
}

@Serializable
data class ALItemTitle(
    val userPreferred: String,
)

@Serializable
data class ItemCover(
    val large: String,
)

@Serializable
data class ALStaff(
    val edges: List<ALEdge>,
)

@Serializable
data class ALEdge(
    val role: String,
    val id: Int,
    val node: ALStaffNode,
) {
    fun getAuthorName(): String? =
        if (role.contains("Creator", true) ||
            role.contains("Story", true) ||
            role.contains("Script", true) ||
            role.contains("Writer", true)
        ) {
            node.name()
        } else {
            null
        }
    fun getArtistName(): String? =
        if (role.contains("Producer", true) ||
            role.contains("Director", true) ||
            role.contains("Animation", true) ||
            role.contains("Art", true) ||
            role.contains("Design", true) ||
            role.contains("Music", true) ||
            role.contains("Song", true)
        ) {
            node.name()
        } else {
            null
        }
}

@Serializable
data class ALStaffNode(
    val name: ALStaffName,
)

@Serializable
data class ALStaffName(
    val userPreferred: String?,
    val native: String?,
    val full: String?,
) {
    operator fun invoke(): String? {
        return userPreferred ?: full ?: native
    }
}
