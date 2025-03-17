package eu.kanade.presentation.updates
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.anime.components.DotSeparatorText
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.anime.components.EpisodeDownloadIndicator
import eu.kanade.presentation.anime.components.RatioSwitchToPanorama
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

internal fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "animeUpdates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
        ) {
            Text(
                text = stringResource(MR.strings.updates_last_update_info, relativeTimeSpanString(lastUpdated)),
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

internal fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    usePanoramaCover: Boolean,
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem, altPlayer: Boolean) -> Unit,
    onDownloadEpisode: (List<UpdatesItem>, EpisodeDownloadAction) -> Unit,
) {
    items(
        items = uiModels,
        contentType = {
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
            }
        },
        key = {
            when (it) {
                is UpdatesUiModel.Header -> "animeUpdatesHeader-${it.hashCode()}"
                is UpdatesUiModel.Item -> "animeUpdates-${it.item.update.animeId}-${it.item.update.episodeId}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                ListGroupHeader(
                    modifier = Modifier.animateItemFastScroll()
                        // KMK -->
                        .padding(top = MaterialTheme.padding.extraSmall),
                    // KMK <--
                    text = relativeDateText(item.date),
                )
            }
            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                UpdatesUiItem(
                    modifier = Modifier.animateItemFastScroll(),
                    update = updatesItem.update,
                    selected = updatesItem.selected,
                    watchProgress = updatesItem.update.lastSecondSeen
                        .takeIf { !updatesItem.update.seen && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.episode_progress,
                                formatProgress(it),
                                formatProgress(updatesItem.update.totalSeconds),
                            )
                        },
                    onLongClick = {
                        onUpdateSelected(updatesItem, !updatesItem.selected, true, true)
                    },
                    onClick = {
                        when {
                            selectionMode -> onUpdateSelected(
                                updatesItem,
                                !updatesItem.selected,
                                true,
                                false,
                            )
                            else -> onClickUpdate(updatesItem, false)
                        }
                    },
                    onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                    onDownloadEpisode = { action: EpisodeDownloadAction ->
                        onDownloadEpisode(listOf(updatesItem), action)
                    }.takeIf { !selectionMode },
                    downloadStateProvider = updatesItem.downloadStateProvider,
                    downloadProgressProvider = updatesItem.downloadProgressProvider,
                    // AM (FILE_SIZE) -->
                    updatesItem = updatesItem,
                    // <-- AM (FILE_SIZE)
                    usePanoramaCover = usePanoramaCover,
                )
            }
        }
    }
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    watchProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadEpisode: ((EpisodeDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    // AM (FILE_SIZE) -->
    updatesItem: UpdatesItem,
    // <-- AM (FILE_SIZE)
    usePanoramaCover: Boolean,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.seen) DISABLED_ALPHA else 1f

    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .padding(
                // KMK -->
                vertical = MaterialTheme.padding.extraSmall,
                // KMK <--
                horizontal = MaterialTheme.padding.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // KMK -->
        val mangaCover = update.coverData
        val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
        val bgColor = mangaCover.dominantCoverColors?.first?.let { Color(it) }
        val onBgColor = mangaCover.dominantCoverColors?.second
            if (usePanoramaCover && coverIsWide) {
                AnimeCover.Panorama(
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small)
                        .width(UpdateItemPanoramaWidth),
                    data = mangaCover,
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
                    modifier = Modifier
                        // KMK -->
                        .padding(top = MaterialTheme.padding.small)
                        .width(UpdateItemWidth),
                    // KMK <--
                    data = mangaCover,
                    onClick = onClickCover,
                    // KMK -->
                    bgColor = bgColor,
                    tint = onBgColor,
                    size = AnimeCover.Size.Medium,
                    onCoverLoaded = { _, result ->
                        val image = result.result.image
                        coverRatio.floatValue = image.height.toFloat() / image.width
                    },
                )
            }
        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.animeTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableIntStateOf(0) }
                if (!update.seen) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = stringResource(MR.strings.unread),
                        modifier = Modifier
                            .height(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (update.bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = update.episodeName,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier
                        .weight(weight = 1f, fill = false),
                )
                if (watchProgress != null) {
                    DotSeparatorText()
                    Text(
                        text = watchProgress,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
// AM (FILE_SIZE) -->
        var fileSizeAsync: Long? by remember { mutableStateOf(updatesItem.fileSize) }
        if (downloadStateProvider() == Download.State.DOWNLOADED &&
            storagePreferences.showEpisodeFileSize().get() &&
            fileSizeAsync == null
        ) {
            LaunchedEffect(update, Unit) {
                fileSizeAsync = withIOContext {
                    downloadProvider.getEpisodeFileSize(
                        update.episodeName,
                        null,
                        update.scanlator,
                        // AM (CUSTOM_INFORMATION) -->
                        update.ogAnimeTitle,
                        // <-- AM (CUSTOM_INFORMATION)
                        sourceManager.getOrStub(update.sourceId),
                    )
                }
                updatesItem.fileSize = fileSizeAsync
            }
        }
        // <-- AM (FILE_SIZE)

        EpisodeDownloadIndicator(
            enabled = onDownloadEpisode != null,
            modifier = Modifier.padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadEpisode?.invoke(it) },
            // AM (FILE_SIZE) -->
            fileSize = fileSizeAsync,
            // <-- AM (FILE_SIZE)
        )
    }
}

private fun formatProgress(milliseconds: Long): String {
    return if (milliseconds > 3600000L) {
        String.format(
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}

private val IndicatorSize = 18.dp
private val UpdateItemPanoramaWidth = 126.dp
private val UpdateItemWidth = 56.dp
// KMK <--

// AM (FILE_SIZE) -->
private val storagePreferences: StoragePreferences by injectLazy()
private val downloadProvider: DownloadProvider by injectLazy()
private val sourceManager: SourceManager by injectLazy()
// <-- AM (FILE_SIZE)
