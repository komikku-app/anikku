package tachiyomi.data.chapter

import tachiyomi.domain.chapter.model.Chapter

object ChapterMapper {
    fun mapChapter(
        id: Long,
        animeId: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        // AM (FILLERMARK) -->
        fillermark: Boolean,
        // <-- AM (FILLERMARK)
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): Chapter = Chapter(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        // AM (FILLERMARK) -->
        fillermark = fillermark,
        // <-- AM (FILLERMARK)
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
