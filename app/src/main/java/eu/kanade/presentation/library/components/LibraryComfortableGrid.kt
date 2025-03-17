package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.library.model.LibraryAnime

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    // KMK -->
    usePanoramaCover: Boolean = false,
    // KMK <--
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "anime_library_comfortable_grid_item" },
        ) { libraryItem ->
            val anime = libraryItem.libraryAnime.anime
            AnimeComfortableGridItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAnime.id },
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    ogUrl = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                        // KMK -->
                        useLangIcon = libraryItem.useLangIcon,
                        // KMK <--
                    )
                    // KMK -->
                    SourceIconBadge(source = libraryItem.source)
                    // KMK <--
                },
                onLongClick = { onLongClick(libraryItem.libraryAnime) },
                onClick = { onClick(libraryItem.libraryAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
                    { onClickContinueWatching(libraryItem.libraryAnime) }
                } else {
                    null
                },
                // KMK -->
                usePanoramaCover = usePanoramaCover,
                // KMK <--
            )
        }
    }
}
