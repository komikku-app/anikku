package eu.kanade.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.anime.components.RatioSwitchToPanorama
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val HistoryItemHeight = 96.dp

@Composable
fun HistoryItem(
    history: HistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // KMK -->
    usePanoramaCover: Boolean,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    // KMK <--
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HistoryItemHeight)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK -->
        val animeCover = history.coverData
        val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
        val bgColor = animeCover.dominantCoverColors?.first?.let { Color(it) }
        val onBgColor = animeCover.dominantCoverColors?.second
        if (usePanoramaCover && coverIsWide) {
            AnimeCover.Panorama(
                modifier = Modifier.fillMaxHeight(),
                data = animeCover,
                onClick = onClickCover,
                // KMK -->
                bgColor = bgColor,
                tint = onBgColor,
                size = AnimeCover.Size.Medium,
                onCoverLoaded = { _, result ->
                    val image = result.result.image
                    coverRatio.floatValue = image.height.toFloat() / image.width
                },
                // KMK <--
            )
        } else {
            // KMK <--
            AnimeCover.Book(
                modifier = Modifier.fillMaxHeight(),
                data = animeCover,
                onClick = onClickCover,
                // KMK -->
                bgColor = bgColor,
                tint = onBgColor,
                size = AnimeCover.Size.Medium,
                onCoverLoaded = { _, result ->
                    val image = result.result.image
                    coverRatio.floatValue = image.height.toFloat() / image.width
                },
                // KMK <--
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            val textStyle = MaterialTheme.typography.bodyMedium
            Text(
                text = history.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = textStyle,
            )
            val seenAt = remember { history.seenAt?.toTimestampString() ?: "" }
            Text(
                text = if (history.episodeNumber > -1) {
                    stringResource(
                        MR.strings.recent_anime_time,
                        formatEpisodeNumber(history.episodeNumber),
                        seenAt,
                    )
                } else {
                    seenAt
                },
                modifier = Modifier.padding(top = 4.dp),
                style = textStyle,
            )
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HistoryItemPreviews(
    @PreviewParameter(HistoryWithRelationsProvider::class)
    historyWithRelations: HistoryWithRelations,
) {
    TachiyomiPreviewTheme {
        Surface {
            HistoryItem(
                history = historyWithRelations,
                onClickCover = {},
                onClickResume = {},
                onClickDelete = {},
                usePanoramaCover = false,
            )
        }
    }
}
