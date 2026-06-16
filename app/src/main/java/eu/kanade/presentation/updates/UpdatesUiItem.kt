package eu.kanade.presentation.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadIndicator
import eu.kanade.presentation.manga.components.DotSeparatorText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.MangaCoverHide
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.manga.components.getSwipeAction
import eu.kanade.presentation.manga.components.swipeActionThreshold
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.UpdateSelectionOptions
import eu.kanade.tachiyomi.ui.updates.groupByDateAndManga
import exh.debug.DebugToggles
import me.saket.swipe.SwipeableActionsBox
import mihon.feature.upcoming.DateHeading
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.ank.AMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

internal fun LazyListScope.updatesLastUpdatedItem(
    lastUpdated: Long,
) {
    item(key = "updates-lastUpdated") {
        Box(
            modifier = Modifier
                .animateItemFastScroll()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
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
    // KMK -->
    expandedState: Set<String>,
    collapseToggle: (key: String) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
    selectionMode: Boolean,
    onUpdateSelected: (UpdatesItem, /* KMK --> */ UpdateSelectionOptions /* KMK <-- */) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem, altPlayer: Boolean) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    // KMK -->
    updateSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    updateSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onUpdateSwipe: (UpdatesItem, LibraryPreferences.ChapterSwipeAction) -> Unit,
    // KMK <--
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
                is UpdatesUiModel.Header -> "updatesHeader-${it.hashCode()}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
            }
        },
    ) { item ->
        when (item) {
            is UpdatesUiModel.Header -> {
                // KMK -->
                DateHeading(
                    modifier = Modifier.animateItemFastScroll()
                        .padding(top = MaterialTheme.padding.extraSmall),
                    date = item.date,
                    mangaCount = item.mangaCount,
                )
                // KMK <--
            }
            is UpdatesUiModel.Item -> {
                val updatesItem = item.item
                // KMK -->
                val isLeader = item is UpdatesUiModel.Leader
                val isExpanded = expandedState.contains(updatesItem.update.groupByDateAndManga())

                AnimatedVisibility(
                    visible = isLeader || isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // KMK <--
                    UpdatesUiItem(
                        modifier = Modifier.animateItemFastScroll(),
                        update = updatesItem.update,
                        selected = updatesItem.selected,
                        readProgress = updatesItem.update.lastPageRead
                            .takeIf { !updatesItem.update.read && it > 0L }
                            ?.let {
                                stringResource(
                                    AYMR.strings.episode_progress,
                                    formatProgress(it),
                                    formatProgress(updatesItem.update.totalPages),
                                )
                            },
                        onLongClick = {
                            onUpdateSelected(
                                updatesItem,
                                // KMK -->
                                UpdateSelectionOptions(
                                    selected = !updatesItem.selected,
                                    userSelected = true,
                                    fromLongPress = true,
                                    isGroup = isLeader && item.isExpandable,
                                    isExpanded = isExpanded,
                                ),
                                // KMK <--
                            )
                        },
                        onClick = {
                            when {
                                selectionMode -> onUpdateSelected(
                                    updatesItem,
                                    // KMK -->
                                    UpdateSelectionOptions(
                                        selected = !updatesItem.selected,
                                        userSelected = true,
                                        fromLongPress = false,
                                        isGroup = isLeader && item.isExpandable,
                                        isExpanded = isExpanded,
                                    ),
                                    // KMK <--
                                )
                                else -> onClickUpdate(updatesItem, false)
                            }
                        },
                        onClickCover = { onClickCover(updatesItem) }.takeIf { !selectionMode },
                        onDownloadChapter = { action: ChapterDownloadAction ->
                            onDownloadChapter(listOf(updatesItem), action)
                        }.takeIf { !selectionMode },
                        downloadStateProvider = updatesItem.downloadStateProvider,
                        downloadProgressProvider = updatesItem.downloadProgressProvider,
                        // AM (FILE_SIZE) -->
                        updatesItem = updatesItem,
                        // <-- AM (FILE_SIZE)
                        // KMK -->
                        updateSwipeStartAction = updateSwipeStartAction,
                        updateSwipeEndAction = updateSwipeEndAction,
                        onUpdateSwipe = {
                            onUpdateSwipe(updatesItem, it)
                        },
                        isLeader = isLeader,
                        isExpandable = item.isExpandable,
                        expanded = isExpanded,
                        collapseToggle = collapseToggle,
                        usePanoramaCover = usePanoramaCover,
                        // KMK <--
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    // Download Indicator
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    // AM (FILE_SIZE) -->
    updatesItem: UpdatesItem,
    // <-- AM (FILE_SIZE)
    // KMK -->
    updateSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    updateSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onUpdateSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    isLeader: Boolean,
    isExpandable: Boolean,
    expanded: Boolean,
    collapseToggle: (key: String) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
    modifier: Modifier = Modifier,
    // KMK -->
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    // KMK <--
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = if (update.read) DISABLED_ALPHA else 1f

    // KMK -->
    val fillermarkPainter = rememberVectorPainter(
        if (!update.fillermark) {
            ImageVector.vectorResource(id = R.drawable.ic_fillermark_24dp)
        } else {
            ImageVector.vectorResource(id = R.drawable.ic_fillermark_border_24dp)
        },
    )
    val swipeBackground = MaterialTheme.colorScheme.primaryContainer
    val swipeStart = remember(updateSwipeStartAction, update.read, update.bookmark, update.fillermark, downloadStateProvider()) {
        getSwipeAction(
            action = updateSwipeStartAction,
            read = update.read,
            bookmark = update.bookmark,
            downloadState = downloadStateProvider(),
            background = swipeBackground,
            fillermarkPainter = fillermarkPainter,
            onSwipe = { onUpdateSwipe(updateSwipeStartAction) },
        )
    }
    val swipeEnd = remember(updateSwipeEndAction, update.read, update.bookmark, update.fillermark, downloadStateProvider()) {
        getSwipeAction(
            action = updateSwipeEndAction,
            read = update.read,
            bookmark = update.bookmark,
            downloadState = downloadStateProvider(),
            background = swipeBackground,
            fillermarkPainter = fillermarkPainter,
            onSwipe = { onUpdateSwipe(updateSwipeEndAction) },
        )
    }

    SwipeableActionsBox(
        modifier = modifier.clipToBounds(),
        startActions = listOfNotNull(swipeStart),
        endActions = listOfNotNull(swipeEnd),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        // KMK <--
        Row(
            modifier = Modifier
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongClick()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
                .padding(top = if (isLeader) MaterialTheme.padding.small else 0.dp)
                .padding(
                    // KMK -->
                    vertical = if (isLeader) MaterialTheme.padding.extraSmall else 0.dp,
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
            if (isLeader) {
                if (DebugToggles.HIDE_COVER_IMAGE_ONLY_SHOW_COLOR.enabled) {
                    MangaCoverHide.Book(
                        modifier = Modifier
                            .width(UpdateItemWidth),
                        bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { selected }),
                        tint = onBgColor,
                        size = MangaCover.Size.Medium,
                    )
                } else {
                    if (usePanoramaCover && coverIsWide) {
                        MangaCover.Panorama(
                            modifier = Modifier
                                .width(UpdateItemPanoramaWidth),
                            data = mangaCover,
                            onClick = onClickCover,
                            // KMK -->
                            bgColor = bgColor,
                            tint = onBgColor,
                            size = MangaCover.Size.Medium,
                            onCoverLoaded = { _, result ->
                                val image = result.result.image
                                coverRatio.floatValue = image.height.toFloat() / image.width
                            },
                            // KMK <--
                        )
                    } else {
                        // KMK <--
                        MangaCover.Book(
                            modifier = Modifier
                                // KMK -->
                                .width(UpdateItemWidth),
                            // KMK <--
                            data = mangaCover,
                            onClick = onClickCover,
                            // KMK -->
                            bgColor = bgColor,
                            tint = onBgColor,
                            size = MangaCover.Size.Medium,
                            onCoverLoaded = { _, result ->
                                val image = result.result.image
                                coverRatio.floatValue = image.height.toFloat() / image.width
                            },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(if (usePanoramaCover && coverIsWide) UpdateItemPanoramaWidth else UpdateItemWidth),
                )
                // KMK <--
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .weight(1f),
            ) {
                // KMK -->
                if (isLeader) {
                    // KMK <--
                    Text(
                        text = update.mangaTitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = textAlpha),
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    var textHeight by remember { mutableIntStateOf(0) }
                    if (!update.read) {
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
                    // AY -->
                    if (update.fillermark) {
                        Icon(
                            painter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.ic_fillermark_24dp)),
                            contentDescription = stringResource(AMR.strings.action_filter_fillermarked),
                            modifier = Modifier
                                .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    // <-- AY
                    Text(
                        text = update.chapterName,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = textAlpha),
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { textHeight = it.size.height },
                        modifier = Modifier
                            .weight(weight = 1f, fill = false),
                    )
                    if (readProgress != null) {
                        DotSeparatorText()
                        Text(
                            text = readProgress,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // KMK -->
            if (isLeader && isExpandable) {
                CollapseButton(
                    expanded = expanded,
                    collapseToggle = { collapseToggle(update.groupByDateAndManga()) },
                )
            }
            // KMK <--

            // AM (FILE_SIZE) -->
            var fileSizeAsync: Long? by remember { mutableStateOf(updatesItem.fileSize) }
            if (downloadStateProvider() == Download.State.DOWNLOADED &&
                storagePreferences.showChapterFileSize().get() &&
                fileSizeAsync == null
            ) {
                LaunchedEffect(update, Unit) {
                    fileSizeAsync = withIOContext {
                        downloadProvider.getChapterFileSize(
                            update.chapterName,
                            null,
                            update.scanlator,
                            // AM (CUSTOM_INFORMATION) -->
                            update.ogMangaTitle,
                            // <-- AM (CUSTOM_INFORMATION)
                            sourceManager.getOrStub(update.sourceId),
                        )
                    }
                    updatesItem.fileSize = fileSizeAsync
                }
            }
            // <-- AM (FILE_SIZE)

            ChapterDownloadIndicator(
                enabled = onDownloadChapter != null,
                modifier = Modifier.padding(start = 4.dp),
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadChapter?.invoke(it) },
                // AM (FILE_SIZE) -->
                fileSize = fileSizeAsync,
                // <-- AM (FILE_SIZE)
            )
        }
    }
}

// KMK -->
@Composable
fun CollapseButton(
    expanded: Boolean,
    collapseToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAnimatedVectorPainter(
        AnimatedImageVector.animatedVectorResource(R.drawable.anim_caret_down),
        !expanded,
    )

    Box(
        modifier = modifier
            .size(IndicatorSize + MaterialTheme.padding.extraSmall),
        contentAlignment = Alignment.TopCenter,
    ) {
        IconButton(
            onClick = { collapseToggle() },
            modifier = Modifier.size(IndicatorSize),
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val IndicatorSize = MaterialTheme.padding.large

private val UpdateItemPanoramaWidth = 108.dp // Book cover
private val UpdateItemWidth = 48.dp
// KMK <--

internal fun formatProgress(milliseconds: Long): String {
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

// AM (FILE_SIZE) -->
private val storagePreferences: StoragePreferences by injectLazy()
private val downloadProvider: DownloadProvider by injectLazy()
private val sourceManager: SourceManager by injectLazy()
// <-- AM (FILE_SIZE)
