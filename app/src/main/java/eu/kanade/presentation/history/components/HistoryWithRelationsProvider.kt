package eu.kanade.presentation.history.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

internal class HistoryWithRelationsProvider : PreviewParameterProvider<HistoryWithRelations> {

    private val simple = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        episodeNumber = 10.2,
        seenAt = Date(1697247357L),
        watchDuration = 123L,
        coverData = MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithoutReadAt = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        episodeNumber = 10.2,
        seenAt = null,
        watchDuration = 123L,
        coverData = MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    private val historyWithNegativeChapterNumber = HistoryWithRelations(
        id = 1L,
        episodeId = 2L,
        animeId = 3L,
        // SY -->
        ogTitle = "Test Title",
        // SY <--
        episodeNumber = -2.0,
        seenAt = Date(1697247357L),
        watchDuration = 123L,
        coverData = MangaCover(
            mangaId = 3L,
            sourceId = 4L,
            isMangaFavorite = false,
            ogUrl = "https://example.com/cover.png",
            lastModified = 5L,
        ),
    )

    override val values: Sequence<HistoryWithRelations>
        get() = sequenceOf(simple, historyWithoutReadAt, historyWithNegativeChapterNumber)
}
