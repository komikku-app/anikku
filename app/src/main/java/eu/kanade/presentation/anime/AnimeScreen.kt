package eu.kanade.presentation.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.components.AnimeActionRow
import eu.kanade.presentation.anime.components.AnimeBottomActionMenu
import eu.kanade.presentation.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.anime.components.AnimeInfoBox
import eu.kanade.presentation.anime.components.AnimeToolbar
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.anime.components.EpisodeHeader
import eu.kanade.presentation.anime.components.ExpandableAnimeDescription
import eu.kanade.presentation.anime.components.MissingEpisodeCountListItem
import eu.kanade.presentation.anime.components.NextEpisodeAiringListItem
import eu.kanade.presentation.anime.components.OutlinedButtonWithArrow
import eu.kanade.presentation.anime.components.RelatedAnimesRow
import eu.kanade.presentation.browse.RelatedAnimeTitle
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.service.missingEpisodesCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.ExtendedFloatingActionButton
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
    onTrackingClicked: () -> Unit,

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

    // KMK -->
    getAnimeState: @Composable (Anime) -> State<Anime>,
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onRelatedAnimeLongClick: (Anime) -> Unit,
    onCoverLoaded: (AnimeCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--
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
            // KMK -->
            getAnimeState = getAnimeState,
            onRelatedAnimesScreenClick = onRelatedAnimesScreenClick,
            onRelatedAnimeClick = onRelatedAnimeClick,
            onRelatedAnimeLongClick = onRelatedAnimeLongClick,
            onCoverLoaded = onCoverLoaded,
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
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
            // KMK -->
            getAnimeState = getAnimeState,
            onRelatedAnimesScreenClick = onRelatedAnimesScreenClick,
            onRelatedAnimeClick = onRelatedAnimeClick,
            onRelatedAnimeLongClick = onRelatedAnimeLongClick,
            onCoverLoaded = onCoverLoaded,
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
        )
    }
}

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
    onTrackingClicked: () -> Unit,

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

    // KMK -->
    getAnimeState: @Composable ((Anime) -> State<Anime>),
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onRelatedAnimeLongClick: (Anime) -> Unit,
    onCoverLoaded: (AnimeCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--
) {
    val episodeListState = rememberLazyListState()

    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedAnimesEnabled by Injekt.get<SourcePreferences>().relatedAnimes().collectAsState()
    val expandRelatedAnimes by uiPreferences.expandRelatedAnimes().collectAsState()
    val showRelatedAnimesInOverflow by uiPreferences.relatedAnimesInOverflow().collectAsState()

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var fabSize by remember { mutableStateOf(IntSize.Zero) }
    var positionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val fabPosition by uiPreferences.watchButtonPosition().collectAsState()
    val watchButtonPosition = uiPreferences.watchButtonPosition()
    // KMK <--

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

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
                titleAlphaProvider = { animatedTitleAlpha },
                backgroundAlphaProvider = { animatedBgAlpha },
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
                // KMK -->
                onClickRelatedAnimes = onRelatedAnimesScreenClick.takeIf {
                    !expandRelatedAnimes &&
                        showRelatedAnimesInOverflow
                },
                // KMK <--
                // SY <--
                onClickSettings = onSettingsClicked,
                changeAnimeSkipIntro = changeAnimeSkipIntro,
                actionModeCounter = selectedEpisodeCount,
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = { onInvertSelection() },
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
                // KMK -->
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .onGloballyPositioned { coordinates ->
                        fabSize = coordinates.size
                        positionOnScreen = coordinates.positionOnScreen()
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (positionOnScreen.x + fabSize.width / 2 >= layoutSize.width / 2) {
                                    watchButtonPosition.set(FabPosition.End.toString())
                                } else {
                                    watchButtonPosition.set(FabPosition.Start.toString())
                                }
                                offsetX = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    },
                // KMK <--
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        val isWatching = remember(state.episodes) {
                            state.episodes.fastAny { it.episode.seen }
                        }
                        Text(
                            text = stringResource(if (isWatching) MR.strings.action_resume else MR.strings.action_start),
                        )
                    },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueWatching,
                    expanded = episodeListState.shouldExpandFAB(),
                    // KMK -->
                    containerColor = MaterialTheme.colorScheme.primary,
                    // KMK <--
                )
            }
        },
        // KMK -->
        floatingActionButtonPosition = if (fabPosition == FabPosition.End.toString()) {
            FabPosition.End
        } else {
            FabPosition.Start
        },
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                layoutSize = coordinates.size
            }
            .haze(
                state = hazeState,
            ),
        // KMK <--
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current
            VerticalFastScroller(
                listState = episodeListState,
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = episodeListState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = AnimeScreenItem.INFO_BOX,
                        contentType = AnimeScreenItem.INFO_BOX,
                    ) {
                        AnimeInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            anime = state.anime,
                            sourceName = remember { state.source.getNameForAnimeInfo() },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                            // KMK -->
                            onCoverLoaded = onCoverLoaded,
                            coverRatio = coverRatio,
                            // KMK <--
                        )
                    }

                    item(
                        key = AnimeScreenItem.ACTION_ROW,
                        contentType = AnimeScreenItem.ACTION_ROW,
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
                        )
                    }

                    item(
                        key = AnimeScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = AnimeScreenItem.DESCRIPTION_WITH_TAG,
                    ) {
                        ExpandableAnimeDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.anime.description,
                            tagsProvider = { state.anime.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                        )
                    }

                    // KMK -->
                    if (state.source !is StubSource &&
                        relatedAnimesEnabled
                    ) {
                        if (expandRelatedAnimes) {
                            if (state.relatedAnimesSorted?.isNotEmpty() != false) {
                                item { HorizontalDivider() }
                                item(
                                    key = AnimeScreenItem.RELATED_ANIMES,
                                    contentType = AnimeScreenItem.RELATED_ANIMES,
                                ) {
                                    Column {
                                        RelatedAnimeTitle(
                                            title = stringResource(KMR.strings.pref_source_related_mangas),
                                            subtitle = null,
                                            onClick = onRelatedAnimesScreenClick,
                                            onLongClick = null,
                                            modifier = Modifier
                                                .padding(horizontal = MaterialTheme.padding.medium),
                                        )
                                        RelatedAnimesRow(
                                            relatedAnimes = state.relatedAnimesSorted,
                                            getAnimeState = getAnimeState,
                                            onAnimeClick = onRelatedAnimeClick,
                                            onAnimeLongClick = onRelatedAnimeLongClick,
                                        )
                                    }
                                }
                                item { HorizontalDivider() }
                            }
                        } else if (!showRelatedAnimesInOverflow) {
                            item(
                                key = AnimeScreenItem.RELATED_ANIMES,
                                contentType = AnimeScreenItem.RELATED_ANIMES,
                            ) {
                                OutlinedButtonWithArrow(
                                    text = stringResource(KMR.strings.pref_source_related_mangas)
                                        .uppercase(),
                                    onClick = onRelatedAnimesScreenClick,
                                )
                            }
                        }
                    }
                    // KMK <--

                    item(
                        key = AnimeScreenItem.EPISODE_HEADER,
                        contentType = AnimeScreenItem.EPISODE_HEADER,
                    ) {
                        val missingEpisodeCount = remember(episodes) {
                            episodes.map { it.episode.episodeNumber }.missingEpisodesCount()
                        }
                        EpisodeHeader(
                            enabled = !isAnySelected,
                            episodeCount = episodes.size,
                            missingEpisodeCount = missingEpisodeCount,
                            onClick = onFilterClicked,
                        )
                    }

                    if (state.airingTime > 0L) {
                        item(
                            key = AnimeScreenItem.AIRING_TIME,
                            contentType = AnimeScreenItem.AIRING_TIME,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeScreenLargeImpl(
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
    onTrackingClicked: () -> Unit,

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

    // KMK -->
    getAnimeState: @Composable ((Anime) -> State<Anime>),
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Anime) -> Unit,
    onRelatedAnimeLongClick: (Anime) -> Unit,
    onCoverLoaded: (AnimeCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.episodeListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedAnimesEnabled by Injekt.get<SourcePreferences>().relatedAnimes().collectAsState()
    val expandRelatedAnimes by uiPreferences.expandRelatedAnimes().collectAsState()
    val showRelatedAnimesInOverflow by uiPreferences.relatedAnimesInOverflow().collectAsState()

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var fabSize by remember { mutableStateOf(IntSize.Zero) }
    var positionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val fabPosition by uiPreferences.watchButtonPosition().collectAsState()
    val watchButtonPosition = uiPreferences.watchButtonPosition()
    // KMK <--

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    val episodeListState = rememberLazyListState()

    val internalOnBackPressed = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    Scaffold(
        topBar = {
            val selectedEpisodeCount = remember(episodes) {
                episodes.count { it.selected }
            }
            AnimeToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.anime.title,
                titleAlphaProvider = { if (isAnySelected) 1f else 0f },
                backgroundAlphaProvider = { 1f },
                hasFilters = state.filterActive,
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
                // KMK -->
                onClickRelatedAnimes = onRelatedAnimesScreenClick.takeIf {
                    !expandRelatedAnimes &&
                        showRelatedAnimesInOverflow
                },
                // KMK <--
                actionModeCounter = selectedEpisodeCount,
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = { onInvertSelection() },
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
                // KMK -->
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .onGloballyPositioned { coordinates ->
                        fabSize = coordinates.size
                        positionOnScreen = coordinates.positionOnScreen()
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (positionOnScreen.x + fabSize.width / 2 >= layoutSize.width / 2) {
                                    watchButtonPosition.set(FabPosition.End.toString())
                                } else {
                                    watchButtonPosition.set(FabPosition.Start.toString())
                                }
                                offsetX = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount
                        }
                    },
                // KMK <--
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
                    // KMK -->
                    containerColor = MaterialTheme.colorScheme.primary,
                    // KMK <--
                )
            }
        },
        // KMK -->
        floatingActionButtonPosition = if (fabPosition == FabPosition.End.toString()) {
            FabPosition.End
        } else {
            FabPosition.Start
        },
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                layoutSize = coordinates.size
            }
            .haze(
                state = hazeState,
            ),
        // KMK <--
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
                            // KMK -->
                            onCoverLoaded = onCoverLoaded,
                            coverRatio = coverRatio,
                            // KMK <--
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
                    VerticalFastScroller(
                        listState = episodeListState,
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = episodeListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            // KMK -->
                            if (state.source !is StubSource &&
                                relatedAnimesEnabled
                            ) {
                                if (expandRelatedAnimes) {
                                    if (state.relatedAnimesSorted?.isNotEmpty() != false) {
                                        item(
                                            key = AnimeScreenItem.RELATED_ANIMES,
                                            contentType = AnimeScreenItem.RELATED_ANIMES,
                                        ) {
                                            Column {
                                                RelatedAnimeTitle(
                                                    title = stringResource(KMR.strings.pref_source_related_mangas)
                                                        .uppercase(),
                                                    subtitle = null,
                                                    onClick = onRelatedAnimesScreenClick,
                                                    onLongClick = null,
                                                    modifier = Modifier
                                                        .padding(horizontal = MaterialTheme.padding.medium),
                                                )
                                                RelatedAnimesRow(
                                                    relatedAnimes = state.relatedAnimesSorted,
                                                    getAnimeState = getAnimeState,
                                                    onAnimeClick = onRelatedAnimeClick,
                                                    onAnimeLongClick = onRelatedAnimeLongClick,
                                                )
                                            }
                                        }
                                        item { HorizontalDivider() }
                                    }
                                } else if (!showRelatedAnimesInOverflow) {
                                    item(
                                        key = AnimeScreenItem.RELATED_ANIMES,
                                        contentType = AnimeScreenItem.RELATED_ANIMES,
                                    ) {
                                        OutlinedButtonWithArrow(
                                            text = stringResource(KMR.strings.pref_source_related_mangas),
                                            onClick = onRelatedAnimesScreenClick,
                                        )
                                    }
                                }
                            }
                            // KMK <--

                            item(
                                key = AnimeScreenItem.EPISODE_HEADER,
                                contentType = AnimeScreenItem.EPISODE_HEADER,
                            ) {
                                val missingEpisodeCount = remember(episodes) {
                                    episodes.map { it.episode.episodeNumber }.missingEpisodesCount()
                                }
                                EpisodeHeader(
                                    enabled = !isAnySelected,
                                    episodeCount = episodes.size,
                                    missingEpisodeCount = missingEpisodeCount,
                                    onClick = onFilterButtonClicked,
                                )
                            }

                            if (state.airingTime > 0L) {
                                item(
                                    key = AnimeScreenItem.AIRING_TIME,
                                    contentType = AnimeScreenItem.AIRING_TIME,
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
                            )
                        }
                    }
                },
            )
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

private fun LazyListScope.sharedEpisodeItems(
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
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is EpisodeList.MissingCount -> {
                MissingEpisodeCountListItem(count = item.count)
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

// AM (FILE_SIZE) -->
private val downloadProvider: DownloadProvider by injectLazy()
// <-- AM (FILE_SIZE)
