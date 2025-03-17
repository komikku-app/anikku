package eu.kanade.presentation.browse.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingAnime
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MigrationItemResult(
    modifier: Modifier,
    migrationItem: MigratingAnime,
    result: MigratingAnime.SearchResult,
    getManga: suspend (MigratingAnime.SearchResult.Result) -> Anime?,
    getEpisodeInfo: suspend (MigratingAnime.SearchResult.Result) -> MigratingAnime.EpisodeInfo,
    getSourceName: (Anime) -> String,
    onMigrationItemClick: (Anime) -> Unit,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            MigratingAnime.SearchResult.Searching -> Box(
                modifier = Modifier
                    .widthIn(max = 150.dp)
                    .fillMaxSize()
                    .aspectRatio(AnimeCover.Book.ratio),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            MigratingAnime.SearchResult.NotFound -> Column(
                Modifier
                    .widthIn(max = 150.dp)
                    .fillMaxSize()
                    .padding(top = 4.dp),
            ) {
                Image(
                    painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(AnimeCover.Book.ratio)
                        .clip(MaterialTheme.shapes.extraSmall),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = stringResource(SYMR.strings.no_alternatives_found),
                    modifier = Modifier.padding(top = 4.dp, bottom = 1.dp, start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            is MigratingAnime.SearchResult.Result -> {
                val item by produceState<Triple<Anime, MigratingAnime.EpisodeInfo, String>?>(
                    initialValue = null,
                    migrationItem,
                    result,
                ) {
                    value = withIOContext {
                        val manga = getManga(result) ?: return@withIOContext null
                        Triple(
                            manga,
                            getEpisodeInfo(result),
                            getSourceName(manga),
                        )
                    }
                }
                if (item != null) {
                    val (manga, chapterInfo, source) = item!!
                    MigrationItem(
                        modifier = Modifier.fillMaxSize(),
                        manga = manga,
                        sourcesString = source,
                        episodeInfo = chapterInfo,
                        onClick = {
                            onMigrationItemClick(manga)
                        },
                    )
                }
            }
        }
    }
}
