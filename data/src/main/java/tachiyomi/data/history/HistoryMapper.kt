package tachiyomi.data.history

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

object HistoryMapper {
    fun mapHistory(
        id: Long,
        episodeId: Long,
        seenAt: Date?,
        watchDuration: Long,
    ): History = History(
        id = id,
        episodeId = episodeId,
        seenAt = seenAt,
        watchDuration = watchDuration,
    )

    fun mapHistoryWithRelations(
        historyId: Long,
        animeId: Long,
        episodeId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        episodeNumber: Double,
        seenAt: Date?,
        watchDuration: Long,
    ): HistoryWithRelations = HistoryWithRelations(
        id = historyId,
        episodeId = episodeId,
        animeId = animeId,
        // SY -->
        ogTitle = title,
        // SY <--
        episodeNumber = episodeNumber,
        seenAt = seenAt,
        watchDuration = watchDuration,
        coverData = MangaCover(
            mangaId = animeId,
            sourceId = sourceId,
            isMangaFavorite = isFavorite,
            ogUrl = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
