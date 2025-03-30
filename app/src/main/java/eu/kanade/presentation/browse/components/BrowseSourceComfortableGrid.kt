package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.library.components.AnimeComfortableGridItem
import eu.kanade.presentation.library.components.CommonAnimeItemDefaults
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseSourceComfortableGrid(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    onAnimeClick: (Manga) -> Unit,
    onAnimeLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    usePanoramaCover: Boolean = false,
    // KMK <--
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridHorizontalSpacer),
    ) {
        if (mangaList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = mangaList.itemCount) { index ->
            val anime by mangaList[index]?.collectAsState() ?: return@items
            BrowseSourceComfortableGridItem(
                manga = anime,
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeLongClick(anime) },
                // KMK -->
                isSelected = selection.fastAny { selected -> selected.id == anime.id },
                usePanoramaCover = usePanoramaCover,
                // KMK <--
            )
        }

        if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
internal fun BrowseSourceComfortableGridItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    // KMK -->
    isSelected: Boolean = false,
    usePanoramaCover: Boolean,
    // KMK <--
) {
    AnimeComfortableGridItem(
        title = manga.title,
        coverData = MangaCover(
            animeId = manga.id,
            sourceId = manga.source,
            isAnimeFavorite = manga.favorite,
            ogUrl = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        // KMK -->
        isSelected = isSelected,
        usePanoramaCover = usePanoramaCover,
        fitToPanoramaCover = true,
        // KMK <--
        coverAlpha = if (manga.favorite) CommonAnimeItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        coverBadgeStart = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
    )
}
