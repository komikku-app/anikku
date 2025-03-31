package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaListItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseSourceList(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    entries: Int,
    topBarHeight: Int,
    contentPadding: PaddingValues,
    onAnimeClick: (Manga) -> Unit,
    onAnimeLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    // KMK <--
) {
    var containerHeight by remember { mutableIntStateOf(0) }
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
        modifier = Modifier
            .onGloballyPositioned { layoutCoordinates ->
                containerHeight = layoutCoordinates.size.height - topBarHeight
            },
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = mangaList.itemCount) { index ->
            val anime by mangaList[index]?.collectAsState() ?: return@items
            BrowseSourceListItem(
                manga = anime,
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeLongClick(anime) },
                entries = entries,
                containerHeight = containerHeight,
                // KMK -->
                isSelected = selection.fastAny { selected -> selected.id == anime.id },
                // KMK <--
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
internal fun BrowseSourceListItem(
    manga: Manga,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    entries: Int,
    containerHeight: Int,
    // KMK -->
    isSelected: Boolean = false,
    // KMK <--
) {
    MangaListItem(
        title = manga.title,
        coverData = MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            ogUrl = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        ),
        // KMK -->
        isSelected = isSelected,
        // KMK <--
        coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = manga.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
        entries = entries,
        containerHeight = containerHeight,
    )
}
