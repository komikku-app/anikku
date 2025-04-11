package tachiyomi.domain.chapter.model

data class ChapterUpdate(
    val id: Long,
    val mangaId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    // AM (FILLERMARK) -->
    val fillermark: Boolean? = null,
    // AM (FILLERMARK) <--
    val lastPageRead: Long? = null,
    val totalPages: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val version: Long? = null,
)

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(
        id,
        mangaId,
        read,
        bookmark,
        // AM (FILLERMARK) -->
        fillermark,
        // AM (FILLERMARK) <--
        lastPageRead,
        totalPages,
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        chapterNumber,
        scanlator,
        version,
    )
}
