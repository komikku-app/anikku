package tachiyomi.data.chapter

import tachiyomi.domain.chapter.model.Chapter

object ChapterMapper {
    fun mapChapter(
        id: Long,
        mangaId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        // AY -->
        fillermark: Boolean,
        // <-- AY
        lastPageRead: Long,
        totalSeconds: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        // AY -->
        summary: String?,
        previewUrl: String?,
        // <-- AY
    ): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        read = read,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastPageRead = lastPageRead,
        totalPages = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        // AY -->
        summary = summary,
        previewUrl = previewUrl,
        // <-- AY
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
