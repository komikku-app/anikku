// AY -->
package eu.kanade.presentation.anime.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import aniyomi.domain.anime.SeasonAnime
import aniyomi.domain.anime.SeasonDisplayMode
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.tachiyomi.ui.anime.AnimeSeasonItem
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.library.components.MangaComfortableGridItem as AnimeComfortableGridItem
import eu.kanade.presentation.library.components.MangaCompactGridItem as AnimeCompactGridItem
import eu.kanade.presentation.library.components.MangaListItem as AnimeListItem
import eu.kanade.presentation.library.components.UnreadBadge as UnseenBadge
import eu.kanade.presentation.util.formatChapterNumber as formatEpisodeNumber
import tachiyomi.domain.manga.model.MangaCover as AnimeCover

@Composable
fun AnimeSeasonListItem(
    anime: Anime,
    item: AnimeSeasonItem,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    modifier: Modifier = Modifier,
    // KMK -->
    usePanoramaCover: Boolean = false,
    // KMK <--
) {
    val itemAnime = item.seasonAnime.anime
    val title = if (anime.seasonDisplayMode == Anime.SEASON_DISPLAY_MODE_NUMBER) {
        stringResource(
            AYMR.strings.display_mode_season,
            formatEpisodeNumber(itemAnime.seasonNumber),
        )
    } else {
        itemAnime.title
    }

    when (anime.seasonDisplayGridMode) {
        SeasonDisplayMode.ComfortableGrid -> {
            AnimeComfortableGridItem(
                title = title,
                coverData = AnimeCover(
                    mangaId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isMangaFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = item.downloadCount)
                    UnseenBadge(count = item.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueReading = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
                // KMK -->
                usePanoramaCover = usePanoramaCover,
                // KMK <--
            )
        }
        SeasonDisplayMode.CompactGrid, SeasonDisplayMode.CoverOnlyGrid -> {
            AnimeCompactGridItem(
                title = title.takeIf { anime.seasonDisplayGridMode is SeasonDisplayMode.CompactGrid },
                coverData = AnimeCover(
                    mangaId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isMangaFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = item.downloadCount)
                    UnseenBadge(count = item.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueReading = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
            )
        }
        SeasonDisplayMode.List -> {
            AnimeListItem(
                title = title,
                coverData = AnimeCover(
                    mangaId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isMangaFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = item.downloadCount)
                    UnseenBadge(count = item.unseenCount)
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueReading = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
                entries = anime.seasonDisplayGridSize,
                containerHeight = containerHeight,
                modifier = modifier,
            )
        }
    }
}
// <-- AY
