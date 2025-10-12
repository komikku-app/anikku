package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun MangaChapterListItem(
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    // SY -->
    sourceName: String?,
    // SY <--
    // AY -->
    summary: String?,
    previewUrl: String?,
    // <-- AY
    read: Boolean,
    bookmark: Boolean,
    // AY -->
    fillermark: Boolean,
    // <-- AY
    selected: Boolean,
    // AY -->
    isAnyEpisodeSelected: Boolean,
    // <-- AY
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    // AM (FILE_SIZE) -->
    fileSize: Long?,
    // <-- AM (FILE_SIZE)
    modifier: Modifier = Modifier,
) {
    // KMK -->
    val fillermarkPainter = rememberVectorPainter(
        if (!fillermark) {
            ImageVector.vectorResource(id = R.drawable.ic_fillermark_24dp)
        } else {
            ImageVector.vectorResource(id = R.drawable.ic_fillermark_border_24dp)
        },
    )
    val swipeBackground = MaterialTheme.colorScheme.primaryContainer
    val swipeStart = remember(chapterSwipeStartAction, read, bookmark, fillermark, downloadStateProvider()) {
        // KMK <--
        getSwipeAction(
            action = chapterSwipeStartAction,
            read = read,
            bookmark = bookmark,
            downloadState = downloadStateProvider(),
            background = swipeBackground,
            fillermarkPainter = fillermarkPainter,
            onSwipe = { onChapterSwipe(chapterSwipeStartAction) },
        )
    }
    // KMK -->
    val swipeEnd = remember(chapterSwipeEndAction, read, bookmark, fillermark, downloadStateProvider()) {
        // KMK <--
        getSwipeAction(
            action = chapterSwipeEndAction,
            read = read,
            bookmark = bookmark,
            downloadState = downloadStateProvider(),
            background = swipeBackground,
            fillermarkPainter = fillermarkPainter,
            onSwipe = { onChapterSwipe(chapterSwipeEndAction) },
        )
    }

    SwipeableActionsBox(
        modifier = modifier.clipToBounds(),
        startActions = listOfNotNull(swipeStart),
        endActions = listOfNotNull(swipeEnd),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            // AY -->
            verticalAlignment = Alignment.CenterVertically,
            // <-- AY
            modifier = Modifier
                .selectedBackground(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        ) {
            // AY -->
            if (previewUrl.isNullOrBlank() && summary.isNullOrBlank()) {
                SimpleEpisodeListItemImpl(
                    title = title,
                    date = date,
                    watchProgress = readProgress,
                    fillermark = fillermark,
                    scanlator = scanlator,
                    // SY -->
                    sourceName = sourceName,
                    // SY <--
                    seen = read,
                    bookmark = bookmark,
                    downloadIndicatorEnabled = downloadIndicatorEnabled,
                    downloadStateProvider = downloadStateProvider,
                    downloadProgressProvider = downloadProgressProvider,
                    onDownloadClick = onDownloadClick,
                    // AM (FILE_SIZE) -->
                    fileSize = fileSize,
                    // <-- AM (FILE_SIZE)
                )
                return@Row
            }

            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EpisodeThumbnail(previewUrl = previewUrl)

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val titleLines = if (previewUrl == null) 1 else 2
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 14.sp),
                                modifier = Modifier.weight(1f),
                                maxLines = titleLines,
                                minLines = titleLines,
                                overflow = TextOverflow.Ellipsis,
                                color = LocalContentColor.current.copy(alpha = if (read) DISABLED_ALPHA else 1f),
                            )

                            if (previewUrl == null) {
                                BookmarkDownloadIcons(
                                    bookmark = bookmark,
                                    downloadIndicatorEnabled = downloadIndicatorEnabled,
                                    downloadStateProvider = downloadStateProvider,
                                    downloadProgressProvider = downloadProgressProvider,
                                    onDownloadClick = onDownloadClick,
                                    // AM (FILE_SIZE) -->
                                    fileSize = fileSize,
                                    // <-- AM (FILE_SIZE)
                                )
                            }
                        }

                        EpisodeSummary(
                            seen = read,
                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                            summary = summary,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EpisodeInformation(
                        seen = read,
                        date = date,
                        watchProgress = readProgress,
                        fillermark = fillermark,
                        scanlator = scanlator,
                        // SY -->
                        sourceName = sourceName,
                        // SY <--
                    )

                    if (previewUrl != null) {
                        BookmarkDownloadIcons(
                            bookmark = bookmark,
                            downloadIndicatorEnabled = downloadIndicatorEnabled,
                            downloadStateProvider = downloadStateProvider,
                            downloadProgressProvider = downloadProgressProvider,
                            onDownloadClick = onDownloadClick,
                            // AM (FILE_SIZE) -->
                            fileSize = fileSize,
                            // <-- AM (FILE_SIZE)
                        )
                    }
                }
            }
            // <-- AY
        }
    }
}

// AY -->
@Composable
private fun RowScope.SimpleEpisodeListItemImpl(
    title: String,
    date: String?,
    watchProgress: String?,
    fillermark: Boolean,
    scanlator: String?,
    // SY -->
    sourceName: String?,
    // SY <--
    seen: Boolean,
    bookmark: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    // AM (FILE_SIZE) -->
    fileSize: Long?,
    // <-- AM (FILE_SIZE)
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(if (fillermark) 0.dp else 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = LocalContentColor.current.copy(alpha = if (seen) DISABLED_ALPHA else 1f),
        )

        EpisodeInformation(
            seen = seen,
            date = date,
            watchProgress = watchProgress,
            fillermark = fillermark,
            scanlator = scanlator,
            sourceName = sourceName,
        )
    }

    BookmarkDownloadIcons(
        bookmark = bookmark,
        downloadIndicatorEnabled = downloadIndicatorEnabled,
        downloadStateProvider = downloadStateProvider,
        downloadProgressProvider = downloadProgressProvider,
        onDownloadClick = onDownloadClick,
        // AM (FILE_SIZE) -->
        fileSize = fileSize,
        // <-- AM (FILE_SIZE)
    )
}
// <-- AY

internal fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    background: Color,
    fillermarkPainter: VectorPainter,
    onSwipe: () -> Unit,
): SwipeAction? {
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> swipeAction(
            icon = if (!read) Icons.Outlined.Done else Icons.Outlined.RemoveDone,
            background = background,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> swipeAction(
            icon = if (!bookmark) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkRemove,
            background = background,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        // AY -->
        LibraryPreferences.ChapterSwipeAction.ToggleFillermark -> {
            swipeAction(
                painter = fillermarkPainter,
                background = background,
                isUndo = bookmark,
                onSwipe = onSwipe,
            )
        }
        // <-- AY
        LibraryPreferences.ChapterSwipeAction.Download -> swipeAction(
            icon = when (downloadState) {
                Download.State.NOT_DOWNLOADED, Download.State.ERROR -> Icons.Outlined.Download
                Download.State.QUEUE, Download.State.DOWNLOADING -> Icons.Outlined.FileDownloadOff
                Download.State.DOWNLOADED -> Icons.Outlined.Delete
            },
            background = background,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

@Composable
fun NextEpisodeAiringListItem(
    title: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(SECONDARY_ALPHA),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.alpha(SECONDARY_ALPHA)) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodySmall,
                ) {
                    Text(
                        text = date,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

internal fun swipeAction(
    onSwipe: () -> Unit,
    icon: ImageVector? = null,
    // KMK -->
    painter: VectorPainter? = null,
    // KMK <--
    background: Color,
    isUndo: Boolean = false,
): SwipeAction {
    return SwipeAction(
        icon = {
            if (icon != null) {
                Icon(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(IndicatorSize),
                    imageVector = icon,
                    tint = contentColorFor(background),
                    contentDescription = null,
                )
            }
            // KMK -->
            if (painter != null) {
                Icon(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(IndicatorSize),
                    painter = painter,
                    tint = contentColorFor(background),
                    contentDescription = null,
                )
            }
            // KMK <--
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

internal val swipeActionThreshold = 56.dp

// AY -->
@Composable
private fun EpisodeThumbnail(
    previewUrl: String?,
) {
    val targetWidth = (LocalConfiguration.current.screenWidthDp * 0.4f).coerceAtMost(250f)
    if (previewUrl != null) {
        MangaCover.Thumb(
            modifier = Modifier
                .width(targetWidth.dp)
                .padding(end = 8.dp),
            data = ImageRequest.Builder(LocalContext.current)
                .data(previewUrl)
                .crossfade(true)
                .build(),
        )
    }
}

@Composable
private fun EpisodeSummary(
    seen: Boolean,
    isAnyEpisodeSelected: Boolean,
    summary: String?,
) {
    var expandSummary by remember { mutableStateOf(false) }
    if (summary != null) {
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = if (expandSummary) Int.MAX_VALUE else 3,
            minLines = 3,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            overflow = TextOverflow.Ellipsis,
            color = LocalContentColor.current.copy(
                alpha = if (seen) DISABLED_ALPHA else SECONDARY_ALPHA,
            ),
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                .then(
                    if (isAnyEpisodeSelected) {
                        Modifier
                    } else {
                        Modifier.clickable { expandSummary = !expandSummary }
                    },
                ),
        )
    }
}

@Composable
private fun EpisodeInformation(
    seen: Boolean,
    date: String?,
    watchProgress: String?,
    fillermark: Boolean,
    scanlator: String?,
    // SY -->
    sourceName: String?,
    // SY <--
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val subtitleStyle = MaterialTheme.typography.bodySmall
            .merge(color = LocalContentColor.current.copy(alpha = if (seen) DISABLED_ALPHA else SECONDARY_ALPHA))
        ProvideTextStyle(value = subtitleStyle) {
            if (fillermark) {
                // ANK -->
                var textHeight by remember { mutableIntStateOf(0) }
                Icon(
                    painter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.ic_fillermark_24dp)),
                    // ANK <--
                    contentDescription = stringResource(AYMR.strings.filler),
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = subtitleStyle.alpha),
                    modifier = Modifier.padding(end = 4.dp)
                        // ANK -->
                        .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                    // ANK <--
                )
                Text(
                    text = stringResource(AYMR.strings.filler),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = subtitleStyle.alpha),
                    modifier = Modifier.padding(end = 4.dp),
                    // ANK -->
                    onTextLayout = { textHeight = it.size.height },
                    // ANK <--
                )
            }
            if (date != null) {
                Text(
                    text = date,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (watchProgress != null || scanlator != null/* SY --> */ || sourceName != null/* SY <-- */) DotSeparatorText()
            }
            if (watchProgress != null) {
                Text(
                    text = watchProgress,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = DISABLED_ALPHA),
                )
                if (scanlator != null/* SY --> */ || sourceName != null/* SY <-- */) DotSeparatorText()
            }
            // SY -->
            if (sourceName != null) {
                Text(
                    text = sourceName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (scanlator != null) DotSeparatorText()
            }
            // SY <--
            if (scanlator != null) {
                Text(
                    text = scanlator,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookmarkDownloadIcons(
    bookmark: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    // AM (FILE_SIZE) -->
    fileSize: Long?,
    // <-- AM (FILE_SIZE)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (bookmark) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        ChapterDownloadIndicator(
            enabled = downloadIndicatorEnabled,
            modifier = Modifier
                .padding(start = 4.dp),
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onClick = { onDownloadClick?.invoke(it) },
            // AM (FILE_SIZE) -->
            fileSize = fileSize,
            // <-- AM (FILE_SIZE)
        )
    }
}

@Preview
@Composable
fun AnimeEpisodeListItemPreview() {
    MangaChapterListItem(
        title = "Ep. 1 - To You, 2000 Years in the Future: The Fall of Zhiganshina (1)",
        date = "7/4/13",
        readProgress = null,
        scanlator = "Scanlator",
        sourceName = "Source",
        summary = "As Titans continue to rampage, the townspeople gather at the inner gate. But a new Titan breaks " +
            "through and this one is unlike the others. Source: crunchyroll",
        previewUrl = null,
        read = false,
        bookmark = false,
        fillermark = true,
        selected = false,
        isAnyEpisodeSelected = false,
        downloadIndicatorEnabled = true,
        downloadStateProvider = { Download.State.NOT_DOWNLOADED },
        downloadProgressProvider = { 0 },
        chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.Disabled,
        chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.Disabled,
        onLongClick = {},
        onClick = {},
        onDownloadClick = {},
        onChapterSwipe = {},
        // AM (FILE_SIZE) -->
        fileSize = null,
        // <-- AM (FILE_SIZE)
    )
}
// <-- AY
