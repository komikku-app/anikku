package eu.kanade.presentation.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import aniyomi.domain.anime.SeasonAnime
import aniyomi.domain.anime.SeasonDisplayMode
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.anime.model.episodesFiltered
import eu.kanade.domain.anime.model.seasonsFiltered
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.anime.components.AnimeActionRow
import eu.kanade.presentation.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.anime.components.AnimeInfoBox
import eu.kanade.presentation.anime.components.AnimeSeasonListItem
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.anime.components.ExpandableAnimeDescription
import eu.kanade.presentation.anime.components.NextEpisodeAiringListItem
import eu.kanade.presentation.anime.components.AnimeBottomActionMenu
import eu.kanade.presentation.anime.components.AnimeToolbar
import eu.kanade.presentation.anime.components.ItemHeader
import eu.kanade.presentation.anime.components.MissingEpisodeCountListItem
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FetchType
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.service.missingEpisodesCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.util.concurrent.TimeUnit

@Composable
fun AnimeScreen(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    onBackClicked: () -> Unit,
    onEpisodeClicked: (episode: Episode, alt: Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(SourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableSource }

    if (!isTabletUi) {
        AnimeScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            onBackClicked = onBackClicked,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            // SY -->
            onEditInfoClicked = onEditInfoClicked,
            // SY <--
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AM (FILLERMARK) -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AM (FILLERMARK)
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
        )
    } else {
        AnimeScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            episodeSwipeStartAction = episodeSwipeStartAction,
            episodeSwipeEndAction = episodeSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            onBackClicked = onBackClicked,
            onEpisodeClicked = onEpisodeClicked,
            onDownloadEpisode = onDownloadEpisode,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueWatching = onContinueWatching,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            changeAnimeSkipIntro = changeAnimeSkipIntro,
            onMigrateClicked = onMigrateClicked,
            // SY -->
            onEditInfoClicked = onEditInfoClicked,
            // SY <--
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AM (FILLERMARK) -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AM (FILLERMARK)
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onEpisodeSwipe = onEpisodeSwipe,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
            onSettingsClicked = onSettingsClicked,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeScreenSmallImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    onBackClicked: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
) {
    val density = LocalDensity.current
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val episodeListState = rememberLazyGridState()

    val seasons = remember(state) { state.processedSeasons }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }
    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered()
            FetchType.Episodes -> state.anime.episodesFiltered()
        }
    }

    var toolbarHeight by remember { mutableIntStateOf(0) }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        Scaffold(
            topBar = {
                val selectedEpisodeCount: Int = remember(episodes) {
                    episodes.count { it.selected }
                }
                val isFirstItemVisible by remember {
                    derivedStateOf { episodeListState.firstVisibleItemIndex == 0 }
                }
                val isFirstItemScrolled by remember {
                    derivedStateOf { episodeListState.firstVisibleItemScrollOffset > 0 }
                }
                val animatedTitleAlpha by animateFloatAsState(
                    if (!isFirstItemVisible) 1f else 0f,
                    label = "Top Bar Title",
                )
                val animatedBgAlpha by animateFloatAsState(
                    if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                    label = "Top Bar Background",
                )
                AnimeToolbar(
                    title = state.anime.title,
                    hasFilters = state.filterActive,
                    onBackClicked = internalOnBackPressed,
                    onClickFilter = onFilterClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    // SY -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.anime.favorite },
                    // SY <--
                    onClickSettings = onSettingsClicked,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedEpisodeCount,
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { animatedTitleAlpha },
                    backgroundAlphaProvider = { animatedBgAlpha },
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height },
                )
            },
            bottomBar = {
                val selectedEpisodes = remember(episodes) {
                    episodes.filter { it.selected }
                }
                SharedAnimeBottomActionMenu(
                    selected = selectedEpisodes,
                    onEpisodeClicked = onEpisodeClicked,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    // AM (FILLERMARK) -->
                    onMultiFillermarkClicked = onMultiFillermarkClicked,
                    // <-- AM (FILLERMARK)
                    onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                    onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                    onDownloadEpisode = onDownloadEpisode,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 1f,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(episodes) {
                    episodes.fastAny { !it.episode.seen } && !isAnySelected
                }
                AnimatedVisibility(
                    visible = isFABVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val isWatching = remember(state.episodes) {
                                state.episodes.fastAny { it.episode.seen }
                            }
                            Text(
                                text = stringResource(
                                    if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                            )
                        },
                        onClick = onContinueWatching,
                        expanded = episodeListState.shouldExpandFAB(),
                    )
                }
            },
        ) { contentPadding ->
            val topPadding = contentPadding.calculateTopPadding()

            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(top = topPadding),
            ) {
                val layoutDirection = LocalLayoutDirection.current
                FastScrollLazyVerticalGrid(
                    modifier = Modifier.fillMaxHeight(),
                    state = episodeListState,
                    columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                    contentPadding = PaddingValues(
                        start = GRID_PADDING + contentPadding.calculateStartPadding(layoutDirection),
                        end = GRID_PADDING + contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = AnimeScreenItem.INFO_BOX,
                        contentType = AnimeScreenItem.INFO_BOX,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        AnimeInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            anime = state.anime,
                            sourceName = remember { state.source.getNameForAnimeInfo() },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                    }

                    item(
                        key = AnimeScreenItem.ACTION_ROW,
                        contentType = AnimeScreenItem.ACTION_ROW,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        AnimeActionRow(
                            favorite = state.anime.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.anime.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                    }

                    item(
                        key = AnimeScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = AnimeScreenItem.DESCRIPTION_WITH_TAG,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        ExpandableAnimeDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.anime.description,
                            tagsProvider = { state.anime.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                    }

                    item(
                        key = AnimeScreenItem.EPISODE_HEADER,
                        contentType = AnimeScreenItem.EPISODE_HEADER,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        val missingEpisodeCount = remember(episodes) {
                            episodes.map { it.episode.episodeNumber }.missingEpisodesCount()
                        }
                        val missingSeasonCount = remember(seasons) {
                            seasons.map { it.seasonAnime.anime.seasonNumber }.missingEpisodesCount()
                        }
                        ItemHeader(
                            enabled = !isAnySelected,
                            episodeCount = when (state.anime.fetchType) {
                                FetchType.Seasons -> seasons.size
                                FetchType.Episodes -> episodes.size
                            },
                            missingEpisodeCount = maxOf(missingEpisodeCount, missingSeasonCount),
                            onClick = onFilterClicked,
                            fetchType = state.anime.fetchType,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                    }

                    when (state.anime.fetchType) {
                        FetchType.Seasons -> {
                            sharedSeasons(
                                anime = state.anime,
                                seasons = seasons,
                                containerHeight = containerHeightPx - toolbarHeight,
                                onSeasonClicked = onSeasonClicked,
                                onClickContinueWatching = onClickContinueWatching,
                                listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            )
                        }

                        FetchType.Episodes -> {
                            if (state.airingTime > 0L) {
                                item(
                                    key = AnimeScreenItem.AIRING_TIME,
                                    contentType = AnimeScreenItem.AIRING_TIME,
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    // Handles the second by second countdown
                                    var timer by remember { mutableLongStateOf(state.airingTime) }
                                    LaunchedEffect(key1 = timer) {
                                        if (timer > 0L) {
                                            delay(1000L)
                                            timer -= 1000L
                                        }
                                    }
                                    if (timer > 0L &&
                                        showNextEpisodeAirTime &&
                                        state.anime.status.toInt() != SAnime.COMPLETED
                                    ) {
                                        NextEpisodeAiringListItem(
                                            title = stringResource(
                                                MR.strings.display_mode_episode,
                                                formatEpisodeNumber(state.airingEpisodeNumber),
                                            ),
                                            date = formatTime(state.airingTime, useDayFormat = true),
                                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        )
                                    }
                                }
                            }

                            sharedEpisodeItems(
                                anime = state.anime,
                                episodes = listItem,
                                isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                // AM (FILE_SIZE) -->
                                source = state.source,
                                showFileSize = showFileSize,
                                // <-- AM (FILE_SIZE)
                                episodeSwipeStartAction = episodeSwipeStartAction,
                                episodeSwipeEndAction = episodeSwipeEndAction,
                                onEpisodeClicked = onEpisodeClicked,
                                onDownloadEpisode = onDownloadEpisode,
                                onEpisodeSelected = onEpisodeSelected,
                                onEpisodeSwipe = onEpisodeSwipe,
                                itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreenLargeImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    onBackClicked: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onSettingsClicked: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For swipe actions
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val seasons = remember(state) { state.processedSeasons }
    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.anime) { state.anime.seasonDisplayGridSize }

    val episodeListState = rememberLazyGridState()
    val hasFilters = remember(state) {
        when (state.anime.fetchType) {
            FetchType.Seasons -> state.anime.seasonsFiltered()
            FetchType.Episodes -> state.anime.episodesFiltered()
        }
    }

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        Scaffold(
            topBar = {
                val selectedEpisodeCount = remember(episodes) {
                    episodes.count { it.selected }
                }
                AnimeToolbar(
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    title = state.anime.title,
                    hasFilters = hasFilters,
                    onBackClicked = internalOnBackPressed,
                    onClickFilter = onFilterButtonClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickSettings = onSettingsClicked,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    // SY -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.anime.favorite },
                    // SY <--
                    actionModeCounter = selectedEpisodeCount,
                    onSelectAll = { onAllEpisodeSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { if (isAnySelected) 1f else 0f },
                    backgroundAlphaProvider = { 1f },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    val selectedEpisodes = remember(episodes) {
                        episodes.filter { it.selected }
                    }
                    SharedAnimeBottomActionMenu(
                        selected = selectedEpisodes,
                        onEpisodeClicked = onEpisodeClicked,
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        // AM (FILLERMARK) -->
                        onMultiFillermarkClicked = onMultiFillermarkClicked,
                        // <-- AM (FILLERMARK)
                        onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                        onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                        onDownloadEpisode = onDownloadEpisode,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
                        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(episodes) {
                    episodes.fastAny { !it.episode.seen } && !isAnySelected
                }
                AnimatedVisibility(
                    visible = isFABVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val isWatching = remember(state.episodes) {
                                state.episodes.fastAny { it.episode.seen }
                            }
                            Text(
                                text = stringResource(
                                    if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = onContinueWatching,
                        expanded = episodeListState.shouldExpandFAB(),
                    )
                }
            },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.isRefreshingData,
                onRefresh = onRefresh,
                enabled = !isAnySelected,
                indicatorPadding = PaddingValues(
                    start = insetPadding.calculateStartPadding(layoutDirection),
                    top = with(density) { topBarHeight.toDp() },
                    end = insetPadding.calculateEndPadding(layoutDirection),
                ),
            ) {
                TwoPanelBox(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
                    startContent = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = contentPadding.calculateBottomPadding()),
                        ) {
                            AnimeInfoBox(
                                isTabletUi = true,
                                appBarPadding = contentPadding.calculateTopPadding(),
                                anime = state.anime,
                                sourceName = remember { state.source.getNameForAnimeInfo() },
                                isStubSource = remember { state.source is StubSource },
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                            )
                            AnimeActionRow(
                                favorite = state.anime.favorite,
                                trackingCount = state.trackingCount,
                                nextUpdate = nextUpdate,
                                isUserIntervalMode = state.anime.fetchInterval < 0,
                                onAddToLibraryClicked = onAddToLibraryClicked,
                                onWebViewClicked = onWebViewClicked,
                                onWebViewLongClicked = onWebViewLongClicked,
                                onTrackingClicked = onTrackingClicked,
                                onEditIntervalClicked = onEditIntervalClicked,
                                onEditCategory = onEditCategoryClicked,
                            )
                            ExpandableAnimeDescription(
                                defaultExpandState = true,
                                description = state.anime.description,
                                tagsProvider = { state.anime.genre },
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                            )
                        }
                    },
                    endContent = {
                        FastScrollLazyVerticalGrid(
                            modifier = Modifier.fillMaxHeight(),
                            state = episodeListState,
                            columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                            contentPadding = PaddingValues(
                                start = GRID_PADDING,
                                end = GRID_PADDING,
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = AnimeScreenItem.EPISODE_HEADER,
                                contentType = AnimeScreenItem.EPISODE_HEADER,
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                val missingEpisodeCount = remember(episodes) {
                                    episodes.map { it.episode.episodeNumber }.missingEpisodesCount()
                                }
                                val missingSeasonCount = remember(seasons) {
                                    seasons.map { it.seasonAnime.anime.seasonNumber }.missingEpisodesCount()
                                }
                                ItemHeader(
                                    enabled = !isAnySelected,
                                    episodeCount = when (state.anime.fetchType) {
                                        FetchType.Seasons -> seasons.size
                                        FetchType.Episodes -> episodes.size
                                    },
                                    missingEpisodeCount = maxOf(missingEpisodeCount, missingSeasonCount),
                                    onClick = onFilterButtonClicked,
                                    fetchType = state.anime.fetchType,
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                            }

                            when (state.anime.fetchType) {
                                FetchType.Seasons -> {
                                    sharedSeasons(
                                        anime = state.anime,
                                        seasons = seasons,
                                        containerHeight = containerHeightPx - topBarHeight,
                                        onSeasonClicked = onSeasonClicked,
                                        onClickContinueWatching = onClickContinueWatching,
                                        listItemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                }

                                FetchType.Episodes -> {
                                    if (state.airingTime > 0L) {
                                        item(
                                            key = AnimeScreenItem.AIRING_TIME,
                                            contentType = AnimeScreenItem.AIRING_TIME,
                                        ) {
                                            // Handles the second by second countdown reseting
                                            var timer by remember { mutableLongStateOf(state.airingTime) }
                                            LaunchedEffect(key1 = timer) {
                                                if (timer > 0L) {
                                                    delay(1000L)
                                                    timer -= 1000L
                                                }
                                            }
                                            if (timer > 0L &&
                                                showNextEpisodeAirTime &&
                                                state.anime.status.toInt() != SAnime.COMPLETED
                                            ) {
                                                NextEpisodeAiringListItem(
                                                    title = stringResource(
                                                        MR.strings.display_mode_episode,
                                                        formatEpisodeNumber(state.airingEpisodeNumber),
                                                    ),
                                                    date = formatTime(state.airingTime, useDayFormat = true),
                                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                )
                                            }
                                        }
                                    }

                                    sharedEpisodeItems(
                                        anime = state.anime,
                                        // AM (FILE_SIZE) -->
                                        source = state.source,
                                        showFileSize = showFileSize,
                                        // <-- AM (FILE_SIZE)
                                        episodes = listItem,
                                        isAnyEpisodeSelected = episodes.fastAny { it.selected },
                                        episodeSwipeStartAction = episodeSwipeStartAction,
                                        episodeSwipeEndAction = episodeSwipeEndAction,
                                        onEpisodeClicked = onEpisodeClicked,
                                        onDownloadEpisode = onDownloadEpisode,
                                        onEpisodeSelected = onEpisodeSelected,
                                        onEpisodeSwipe = onEpisodeSwipe,
                                        itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SharedAnimeBottomActionMenu(
    selected: List<EpisodeList.Item>,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimeBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.bookmark } },
        // AM (FILLERMARK) -->
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.fillermark } },
        // <-- AM (FILLERMARK)
        onMarkAsSeenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.seen } },
        onMarkAsUnseenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0L } },
        onMarkPreviousAsSeenClicked = {
            onMarkPreviousAsSeenClicked(selected[0].episode)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadEpisode!!(selected.toList(), EpisodeDownloadAction.START)
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.episode })
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        onExternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.episode }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
    )
}

private fun LazyGridScope.sharedSeasons(
    anime: Anime,
    seasons: List<AnimeSeasonItem>,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    listItemModifier: Modifier = Modifier,
) {
    items(
        items = seasons,
        key = { season -> season.seasonAnime.anime.id },
        span = { GridItemSpan(if (anime.seasonDisplayGridMode == SeasonDisplayMode.List) maxLineSpan else 1) },
    ) { item ->
        AnimeSeasonListItem(
            anime = anime,
            item = item,
            containerHeight = containerHeight,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onClickContinueWatching,
            listItemModifier = listItemModifier,
        )
    }
}

private fun LazyGridScope.sharedEpisodeItems(
    anime: Anime,
    // AM (FILE_SIZE) -->
    source: Source,
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    episodes: List<EpisodeList>,
    isAnyEpisodeSelected: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    itemModifier: Modifier = Modifier,
) {
    items(
        items = episodes,
        key = { item ->
            when (item) {
                is EpisodeList.MissingCount -> "missing-count-${item.hashCode()}"
                is EpisodeList.Item -> "episode-${item.id}"
            }
        },
        contentType = { AnimeScreenItem.EPISODE },
        span = { GridItemSpan(maxLineSpan) },
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is EpisodeList.MissingCount -> {
                MissingEpisodeCountListItem(
                    count = item.count,
                    modifier = itemModifier,
                )
            }
            is EpisodeList.Item -> {
                // AM (FILE_SIZE) -->
                var fileSizeAsync: Long? by remember { mutableStateOf(item.fileSize) }
                val isEpisodeDownloaded = item.downloadState == Download.State.DOWNLOADED
                if (isEpisodeDownloaded && showFileSize && fileSizeAsync == null) {
                    LaunchedEffect(item, Unit) {
                        fileSizeAsync = withIOContext {
                            downloadProvider.getEpisodeFileSize(
                                item.episode.name,
                                item.episode.url,
                                item.episode.scanlator,
                                // AM (CUSTOM_INFORMATION) -->
                                anime.ogTitle,
                                // <-- AM (CUSTOM_INFORMATION)
                                source,
                            )
                        }
                        item.fileSize = fileSizeAsync
                    }
                }
                // <-- AM (FILE_SIZE)
                AnimeEpisodeListItem(
                    title = if (anime.displayMode == Anime.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_episode,
                            formatEpisodeNumber(item.episode.episodeNumber),
                        )
                    } else {
                        item.episode.name
                    },
                    date = relativeDateTimeText(item.episode.dateUpload),
                    watchProgress = item.episode.lastSecondSeen
                        .takeIf { !item.episode.seen && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.episode_progress,
                                formatTime(it),
                                formatTime(item.episode.totalSeconds),
                            )
                        },
                    scanlator = item.episode.scanlator.takeIf { !it.isNullOrBlank() },
                    seen = item.episode.seen,
                    bookmark = item.episode.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = item.episode.fillermark,
                    // <-- AM (FILLERMARK)
                    selected = item.selected,
                    downloadIndicatorEnabled = !isAnyEpisodeSelected && !anime.isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    episodeSwipeStartAction = episodeSwipeStartAction,
                    episodeSwipeEndAction = episodeSwipeEndAction,
                    onLongClick = {
                        onEpisodeSelected(item, !item.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onEpisodeItemClick(
                            episodeItem = item,
                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                            onToggleSelection = { onEpisodeSelected(item, !item.selected, true, false) },
                            onEpisodeClicked = onEpisodeClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadEpisode != null) {
                        { onDownloadEpisode(listOf(item), it) }
                    } else {
                        null
                    },
                    onEpisodeSwipe = {
                        onEpisodeSwipe(item, it)
                    },
                    // AM (FILE_SIZE) -->
                    fileSize = fileSizeAsync,
                    // <-- AM (FILE_SIZE)
                    modifier = itemModifier,
                )
            }
        }
    }
}

private fun onEpisodeItemClick(
    episodeItem: EpisodeList.Item,
    isAnyEpisodeSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
) {
    when {
        episodeItem.selected -> onToggleSelection(false)
        isAnyEpisodeSelected -> onToggleSelection(true)
        else -> onEpisodeClicked(episodeItem.episode, false)
    }
}

private fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    return if (useDayFormat) {
        String.format(
            "Airing in %02dd %02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toDays(milliseconds),
            TimeUnit.MILLISECONDS.toHours(milliseconds) -
                TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds)),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else if (milliseconds > 3600000L) {
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

private val GRID_PADDING = 14.dp
// AM (FILE_SIZE) -->
private val downloadProvider: DownloadProvider by injectLazy()
// <-- AM (FILE_SIZE)
private fun Modifier.ignorePadding(gridPadding: Int) = layout { measurable, constraints ->
    val looseConstraints = constraints.offset(gridPadding * 2, 0)
    val placeable = measurable.measure(looseConstraints)

    layout(placeable.width - gridPadding * 2, placeable.height) {
        placeable.placeRelative(-gridPadding, 0)
    }
}
