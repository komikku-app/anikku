package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.anime.components.AnimeCover
import eu.kanade.presentation.anime.components.RatioSwitchToPanorama
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.domain.anime.model.AnimeCover as EntryCoverModel

object CommonAnimeItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    @Suppress("ConstPropertyName")
    const val BrowseFavoriteCoverAlpha = 0.34f
}

private val ContinueWatchingButtonSizeSmall = 28.dp
private val ContinueWatchingButtonSizeLarge = 32.dp

private val ContinueWatchingButtonIconSizeSmall = 16.dp
private val ContinueWatchingButtonIconSizeLarge = 20.dp

private val ContinueWatchingButtonGridPadding = 6.dp
private val ContinueWatchingButtonListSpacing = 8.dp

private const val GRID_SELECTED_COVER_ALPHA = 0.76f

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun AnimeCompactGridItem(
    coverData: EntryCoverModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    title: String? = null,
    onClickContinueWatching: (() -> Unit)? = null,
    coverAlpha: Float = 1f,
    coverBadgeStart: @Composable (RowScope.() -> Unit)? = null,
    coverBadgeEnd: @Composable (RowScope.() -> Unit)? = null,
    // KMK -->
    libraryColored: Boolean = true,
    // KMK <--
) {
    // KMK -->
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        AnimeGridCover(
            cover = {
                AnimeCover.Book(
                    modifier = Modifier
                            // KMK -->
                            // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                            // KMK <--
                            .fillMaxWidth(),
                    data = coverData,
                        // KMK -->
                        alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                        bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                        tint = onBgColor,
                        // KMK <--
                )
            },
            badgesStart = coverBadgeStart,
            badgesEnd = coverBadgeEnd,
            content = {
                if (title != null) {
                    CoverTextOverlay(
                        title = title,
                        onClickContinueWatching = onClickContinueWatching,
                    )
                } else if (onClickContinueWatching != null) {
                    ContinueWatchingButton(
                        size = ContinueWatchingButtonSizeLarge,
                        iconSize = ContinueWatchingButtonIconSizeLarge,
                        onClick = onClickContinueWatching,
                        modifier = Modifier
                            .padding(ContinueWatchingButtonGridPadding)
                            .align(Alignment.BottomEnd),
                    )
                }
            },
        )
    }
}

/**
 * Title overlay for [AnimeCompactGridItem]
 */
@Composable
private fun BoxScope.CoverTextOverlay(
    title: String,
    onClickContinueWatching: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        GridItemTitle(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            title = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f,
                ),
            ),
            minLines = 1,
        )
        if (onClickContinueWatching != null) {
            ContinueWatchingButton(
                size = ContinueWatchingButtonSizeSmall,
                iconSize = ContinueWatchingButtonIconSizeSmall,
                onClick = onClickContinueWatching,
                modifier = Modifier.padding(
                    end = ContinueWatchingButtonGridPadding,
                    bottom = ContinueWatchingButtonGridPadding,
                ),
            )
        }
    }
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun AnimeComfortableGridItem(
    coverData: EntryCoverModel,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    titleMaxLines: Int = 2,
    coverAlpha: Float = 1f,
    coverBadgeStart: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    onClickContinueWatching: (() -> Unit)? = null,
    // KMK -->
    libraryColored: Boolean = true,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    usePanoramaCover: Boolean,
    fitToPanoramaCover: Boolean = false,
    // KMK <--
) {
    // KMK -->
    val coverIsWide = coverRatio.floatValue <= RatioSwitchToPanorama
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column {
            AnimeGridCover(
                cover = {
                        if (fitToPanoramaCover && usePanoramaCover && coverIsWide) {
                            AnimeCover.Panorama(
                                modifier = Modifier
                                    // KMK -->
                                    // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                                    // KMK <--
                                    .fillMaxWidth(),
                                data = coverData,
                                // KMK -->
                                alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                                bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                                tint = onBgColor,
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
                                    // .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha)
                                    // KMK <--
                                    .fillMaxWidth(),
                        data = coverData,
                                // KMK -->
                                alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                                bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                                tint = onBgColor,
                                onCoverLoaded = { _, result ->
                                    val image = result.result.image
                                    coverRatio.floatValue = image.height.toFloat() / image.width
                                },
                                scale = if (usePanoramaCover && coverIsWide) {
                                    ContentScale.Fit
                                } else {
                                    ContentScale.Crop
                                },
                                // KMK <--
                    )
                        }
                },
                // KMK -->
                ratio = if (fitToPanoramaCover && usePanoramaCover && coverIsWide) {
                    AnimeCover.Panorama.ratio
                } else {
                    AnimeCover.Book.ratio
                },
                // KMK <--
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
                content = {
                    if (onClickContinueWatching != null) {
                        ContinueWatchingButton(
                            size = ContinueWatchingButtonSizeLarge,
                            iconSize = ContinueWatchingButtonIconSizeLarge,
                            onClick = onClickContinueWatching,
                            modifier = Modifier
                                .padding(ContinueWatchingButtonGridPadding)
                                .align(Alignment.BottomEnd),
                        )
                    }
                },
            )
            GridItemTitle(
                modifier = Modifier.padding(4.dp),
                title = title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = titleMaxLines,
            )
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun AnimeGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    // KMK -->
    ratio: Float = AnimeCover.Book.ratio,
    // KMK <--
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }
    }
}

@Composable
private fun GridItemTitle(
    title: String,
    style: TextStyle,
    minLines: Int,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun GridItemSelectable(
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .selectedOutline(isSelected = isSelected, color = MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * @see GridItemSelectable
 */
private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
) = drawBehind { if (isSelected) drawRect(color = color) }

/**
 * Layout of list item.
 */
@Composable
fun AnimeListItem(
    coverData: EntryCoverModel,
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    badge: @Composable (RowScope.() -> Unit),
    isSelected: Boolean = false,
    coverAlpha: Float = 1f,
    onClickContinueWatching: (() -> Unit)? = null,
    entries: Int = -1,
    containerHeight: Int = 0,
    // KMK -->
    libraryColored: Boolean = true,
    // KMK <--
) {
    val density = LocalDensity.current
    // KMK -->
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    // KMK <--
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .height(
                when (entries) {
                    -1 -> 76.dp
                    0 -> with(density) { (containerHeight / 7).toDp() } - (3 / 7).dp
                    else -> with(density) { (containerHeight / entries).toDp() } - (3 / entries).dp
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeCover.Book(
            modifier = Modifier
                    // KMK -->
                    // .alpha(coverAlpha)
                    // KMK <--
                    .fillMaxHeight(),
            data = coverData,
                // KMK -->
                alpha = coverAlpha,
                bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                tint = onBgColor,
                size = AnimeCover.Size.Big,
                // KMK <--
        )
        Text(
            text = title,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        BadgeGroup(content = badge)
        if (onClickContinueWatching != null) {
            ContinueWatchingButton(
                size = ContinueWatchingButtonSizeSmall,
                iconSize = ContinueWatchingButtonIconSizeSmall,
                onClick = onClickContinueWatching,
                modifier = Modifier.padding(start = ContinueWatchingButtonListSpacing),
            )
        }
    }
}

@Composable
private fun ContinueWatchingButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FilledIconButton(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer),
            ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.action_resume),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
