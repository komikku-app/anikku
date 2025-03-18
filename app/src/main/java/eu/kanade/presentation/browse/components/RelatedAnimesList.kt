package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.browse.RelatedAnimeTitle
import eu.kanade.presentation.browse.RelatedAnimesLoadingItem
import eu.kanade.tachiyomi.ui.anime.RelatedAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun RelatedAnimesList(
    relatedAnimes: List<RelatedAnime>,
    getManga: @Composable (Anime) -> State<Anime>,
    contentPadding: PaddingValues,
    onMangaClick: (Anime) -> Unit,
    onMangaLongClick: (Anime) -> Unit,
    onKeywordClick: (String) -> Unit,
    onKeywordLongClick: (String) -> Unit,
    selection: List<Anime>,
) {
    FastScrollLazyColumn(
        // Using modifier instead of contentPadding so we can use stickyHeader
        modifier = Modifier.padding(contentPadding),
    ) {
        relatedAnimes.forEach { related ->
            val isLoading = related is RelatedAnime.Loading
            if (isLoading) {
                item(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX${related.hashCode()}#header") {
                    RelatedAnimeTitle(
                        title = stringResource(MR.strings.loading),
                        subtitle = null,
                        onClick = {},
                        onLongClick = null,
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.padding.small,
                                end = MaterialTheme.padding.medium,
                            )
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                item(key = "${related.hashCode()}#content") { RelatedAnimesLoadingItem() }
            } else {
                val relatedAnime = related as RelatedAnime.Success
                item(key = "${related.hashCode()}#divider") { HorizontalDivider() }
                stickyHeader(key = "$STICKY_HEADER_KEY_PREFIX${related.hashCode()}#header") {
                    RelatedAnimeTitle(
                        title = if (relatedAnime.keyword.isNotBlank()) {
                            stringResource(KMR.strings.related_mangas_more)
                        } else {
                            stringResource(KMR.strings.related_mangas_website_suggestions)
                        },
                        showArrow = relatedAnime.keyword.isNotBlank(),
                        subtitle = null,
                        onClick = {
                            if (relatedAnime.keyword.isNotBlank()) onKeywordClick(relatedAnime.keyword)
                        },
                        onLongClick = {
                            if (relatedAnime.keyword.isNotBlank()) onKeywordLongClick(relatedAnime.keyword)
                        },
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.padding.small,
                                end = MaterialTheme.padding.medium,
                            )
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                items(
                    key = { "related-list-${relatedAnime.mangaList[it].url.hashCode()}" },
                    count = relatedAnime.mangaList.size,
                ) { index ->
                    val manga by getManga(relatedAnime.mangaList[index])
                    BrowseSourceListItem(
                        anime = manga,
                        onClick = { onMangaClick(manga) },
                        onLongClick = { onMangaLongClick(manga) },
                        isSelected = selection.fastAny { selected -> selected.id == manga.id },
                    )
                }
            }
        }
    }
}
