package eu.kanade.presentation.anime.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SwapCalls
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.components.DownloadDropdownMenu
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.isTabletUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimeBottomActionMenu(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBookmarkClicked: (() -> Unit)? = null,
    onRemoveBookmarkClicked: (() -> Unit)? = null,
    // AM (FILLERMARK) -->
    onFillermarkClicked: (() -> Unit)? = null,
    onRemoveFillermarkClicked: (() -> Unit)? = null,
    // <-- AM (FILLERMARK)
    onMarkAsSeenClicked: (() -> Unit)? = null,
    onMarkAsUnseenClicked: (() -> Unit)? = null,
    onMarkPreviousAsSeenClicked: (() -> Unit)? = null,
    onDownloadClicked: (() -> Unit)? = null,
    onDeleteClicked: (() -> Unit)? = null,
    onExternalClicked: (() -> Unit)? = null,
    onInternalClicked: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        val scope = rememberCoroutineScope()
        val playerPreferences: PlayerPreferences = Injekt.get()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            // AM (FILLERMARK) -->
            val confirm = remember { mutableStateListOf(false, false, false, false, false, false, false, false, false, false, false) }
            val confirmRange = 0..<11
            // <-- AM (FILLERMARK)
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (confirmRange).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            Row(
                modifier = Modifier
                    .padding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                if (onBookmarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_bookmark_episode),
                        icon = Icons.Outlined.BookmarkAdd,
                        toConfirm = confirm[0],
                        onLongClick = { onLongClickItem(0) },
                        onClick = onBookmarkClicked,
                    )
                }
                if (onRemoveBookmarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_remove_bookmark_episode),
                        icon = Icons.Outlined.BookmarkRemove,
                        toConfirm = confirm[1],
                        onLongClick = { onLongClickItem(1) },
                        onClick = onRemoveBookmarkClicked,
                    )
                }
                // AM (FILLERMARK) -->
                if (onFillermarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_fillermark_episode),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_fillermark_24dp),
                        toConfirm = confirm[2],
                        onLongClick = { onLongClickItem(2) },
                        onClick = onFillermarkClicked,
                    )
                }
                if (onRemoveFillermarkClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_remove_fillermark_episode),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_fillermark_border_24dp),
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = onRemoveFillermarkClicked,
                    )
                }
                // <-- AM (FILLERMARK)
                if (onMarkAsSeenClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_seen),
                        icon = Icons.Outlined.DoneAll,
                        toConfirm = confirm[4],
                        onLongClick = { onLongClickItem(4) },
                        onClick = onMarkAsSeenClicked,
                    )
                }
                if (onMarkAsUnseenClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_as_unseen),
                        icon = Icons.Outlined.RemoveDone,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = onMarkAsUnseenClicked,
                    )
                }
                if (onMarkPreviousAsSeenClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_mark_previous_as_seen),
                        icon = ImageVector.vectorResource(R.drawable.ic_done_prev_24dp),
                        toConfirm = confirm[6],
                        onLongClick = { onLongClickItem(6) },
                        onClick = onMarkPreviousAsSeenClicked,
                    )
                }
                if (onDownloadClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[7],
                        onLongClick = { onLongClickItem(7) },
                        onClick = onDownloadClicked,
                    )
                }
                if (onDeleteClicked != null) {
                    Button(
                        title = stringResource(MR.strings.action_delete),
                        icon = Icons.Outlined.Delete,
                        toConfirm = confirm[8],
                        onLongClick = { onLongClickItem(8) },
                        onClick = onDeleteClicked,
                    )
                }
                if (onExternalClicked != null && !playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(MR.strings.action_play_externally),
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        toConfirm = confirm[9],
                        onLongClick = { onLongClickItem(9) },
                        onClick = onExternalClicked,
                    )
                }
                if (onInternalClicked != null && playerPreferences.alwaysUseExternalPlayer().get()) {
                    Button(
                        title = stringResource(MR.strings.action_play_internally),
                        icon = Icons.AutoMirrored.Outlined.Input,
                        toConfirm = confirm[10],
                        onLongClick = { onLongClickItem(10) },
                        onClick = onInternalClicked,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RowScope.Button(
    title: String,
    icon: ImageVector,
    toConfirm: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    // KMK -->
    enabled: Boolean = true,
    // KMK <--
    content: (@Composable () -> Unit)? = null,
) {
    val animatedWeight by animateFloatAsState(
        targetValue = if (toConfirm) 2f else 1f,
        label = "weight",
    )
    // KMK -->
    val animatedColor by animateColorAsState(
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f,
            )
        },
        label = "color",
    )
    // KMK <--
    Column(
        modifier = Modifier
            .size(48.dp)
            .weight(animatedWeight)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            // KMK -->
            tint = animatedColor,
            // KMK <--
        )
        AnimatedVisibility(
            visible = toConfirm,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Text(
                text = title,
                overflow = TextOverflow.Visible,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                // KMK -->
                color = animatedColor,
                // KMK <--
            )
        }
        content?.invoke()
    }
}

@Composable
fun LibraryBottomActionMenu(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsSeenClicked: () -> Unit,
    onMarkAsUnseenClicked: () -> Unit,
    onDownloadClicked: ((DownloadAction) -> Unit)?,
    onDeleteClicked: () -> Unit,
    // SY -->
    onClickMigrate: (() -> Unit)?,
    onClickResetInfo: (() -> Unit)?,
    // SY <--
    // KMK -->
    onClickMerge: (() -> Unit)?,
    // KMK <--
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
    ) {
        val scope = rememberCoroutineScope()
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large.copy(bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val haptic = LocalHapticFeedback.current
            val confirm =
                remember {
                    mutableStateListOf(false, false, false, false, false /* SY --> */, false, false, false /* SY <-- */)
                }
            var resetJob: Job? = remember { null }
            val onLongClickItem: (Int) -> Unit = { toConfirmIndex ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                (0..<8).forEach { i -> confirm[i] = i == toConfirmIndex }
                resetJob?.cancel()
                resetJob = scope.launch {
                    delay(1.seconds)
                    if (isActive) confirm[toConfirmIndex] = false
                }
            }
            // SY -->
            val showOverflow = onClickResetInfo != null ||
                // KMK -->
                onClickMigrate != null ||
                onClickMerge != null
            // KMK <--
            val configuration = LocalConfiguration.current
            val isTabletUi = remember { configuration.isTabletUi() }
            var overFlowOpen by remember { mutableStateOf(false) }
            // SY <--
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Button(
                    title = stringResource(MR.strings.action_move_category),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    toConfirm = confirm[0],
                    onLongClick = { onLongClickItem(0) },
                    onClick = onChangeCategoryClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_seen),
                    icon = Icons.Outlined.DoneAll,
                    toConfirm = confirm[1],
                    onLongClick = { onLongClickItem(1) },
                    onClick = onMarkAsSeenClicked,
                )
                Button(
                    title = stringResource(MR.strings.action_mark_as_unseen),
                    icon = Icons.Outlined.RemoveDone,
                    toConfirm = confirm[2],
                    onLongClick = { onLongClickItem(2) },
                    onClick = onMarkAsUnseenClicked,
                )
                if (onDownloadClicked != null) {
                    var downloadExpanded by remember { mutableStateOf(false) }
                    Button(
                        title = stringResource(MR.strings.action_download),
                        icon = Icons.Outlined.Download,
                        toConfirm = confirm[3],
                        onLongClick = { onLongClickItem(3) },
                        onClick = { downloadExpanded = !downloadExpanded },
                    ) {
                        val onDismissRequest = { downloadExpanded = false }
                        DownloadDropdownMenu(
                            expanded = downloadExpanded,
                            onDismissRequest = onDismissRequest,
                            onDownloadClicked = onDownloadClicked,
                        )
                    }
                }
                Button(
                    title = stringResource(MR.strings.action_delete),
                    icon = Icons.Outlined.Delete,
                    toConfirm = confirm[4],
                    onLongClick = { onLongClickItem(4) },
                    onClick = onDeleteClicked,
                )
                // SY -->
                if (showOverflow) {
                    if (isTabletUi) {
                        if (onClickMigrate != null) {
                            Button(
                                title = stringResource(MR.strings.migrate),
                                icon = Icons.Outlined.SwapCalls,
                                toConfirm = confirm[6],
                                onLongClick = { onLongClickItem(6) },
                                onClick = onClickMigrate,
                            )
                        }
                        // KMK -->
                        if (onClickMerge != null) {
                            Button(
                                title = stringResource(SYMR.strings.merge),
                                icon = Icons.Outlined.Merge,
                                toConfirm = confirm[7],
                                onLongClick = { onLongClickItem(7) },
                                onClick = onClickMerge,
                            )
                        }
                        // KMK <--
                    }
                    Button(
                        title = stringResource(MR.strings.label_more),
                        icon = Icons.Outlined.MoreVert,
                        toConfirm = confirm[5],
                        onLongClick = { onLongClickItem(5) },
                        onClick = { overFlowOpen = true },
                    )
                    DropdownMenu(
                        expanded = overFlowOpen,
                        onDismissRequest = { overFlowOpen = false },
                        // KMK -->
                        offset = DpOffset((-10).dp, 0.dp),
                        // KMK <--
                    ) {
                        if (!isTabletUi) {
                            if (onClickMigrate != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.migrate)) },
                                    onClick = onClickMigrate,
                                )
                            }
                            // KMK -->
                            if (onClickMerge != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(SYMR.strings.merge)) },
                                    onClick = onClickMerge,
                                )
                            }
                            // KMK <--
                        }
                        if (onClickResetInfo != null) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(SYMR.strings.reset_info)) },
                                onClick = onClickResetInfo,
                            )
                        }
                    }
                    /* KMK -->
                } else {
                    if (onClickMigrate != null) {
                        Button(
                            title = stringResource(MR.strings.migrate),
                            icon = Icons.Outlined.SwapCalls,
                            toConfirm = confirm[6],
                            onLongClick = { onLongClickItem(6) },
                            onClick = onClickMigrate,
                        )
                    }
                    if (onClickMerge != null) {
                        Button(
                            title = stringResource(SYMR.strings.merge),
                            icon = Icons.Outlined.Merge,
                            toConfirm = confirm[7],
                            onLongClick = { onLongClickItem(7) },
                            onClick = onClickMerge,
                        )
                    }
                    // KMK <-- */
                }
                // SY <--
            }
        }
    }
}
