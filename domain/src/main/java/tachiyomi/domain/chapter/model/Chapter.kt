package tachiyomi.domain.chapter.model

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val read: Boolean,
    val bookmark: Boolean,
    // AY -->
    val fillermark: Boolean,
    // <-- AY
    val lastPageRead: Long,
    val totalPages: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Double,
    val scanlator: String?,
    // AY -->
    val summary: String?,
    val previewUrl: String?,
    // <-- AY
    val lastModifiedAt: Long,
    val version: Long,
) {
    val isRecognizedNumber: Boolean
        get() = chapterNumber >= 0f

    fun copyFrom(other: Chapter): Chapter {
        return copy(
            name = other.name,
            url = other.url,
            dateUpload = other.dateUpload,
            chapterNumber = other.chapterNumber,
            // AY -->
            fillermark = other.fillermark,
            // <-- AY
            scanlator = other.scanlator?.ifBlank { null },
            // AY -->
            summary = other.summary?.ifBlank { null },
            previewUrl = other.previewUrl?.ifBlank { null },
            // <-- AY
        )
    }

    companion object {
        fun create() = Chapter(
            id = -1,
            mangaId = -1,
            read = false,
            bookmark = false,
            // AY -->
            fillermark = false,
            // <-- AY
            lastPageRead = 0,
            totalPages = 0,
            dateFetch = 0,
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = -1,
            chapterNumber = -1.0,
            scanlator = null,
            // AY -->
            summary = null,
            previewUrl = null,
            // <-- AY
            lastModifiedAt = 0,
            version = 1,
        )
    }

    val animeId = mangaId
    val episodeNumber = chapterNumber
    val seen = read
    val lastSecondSeen = lastPageRead
    val totalSeconds = totalPages
}
