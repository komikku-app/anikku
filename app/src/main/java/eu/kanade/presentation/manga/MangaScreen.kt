package eu.kanade.presentation.manga

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
import eu.kanade.presentation.browse.RelatedMangaTitle
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.manga.components.MangaInfoButtons
import eu.kanade.presentation.manga.components.MangaToolbar
import eu.kanade.presentation.manga.components.MissingChapterCountListItem
import eu.kanade.presentation.manga.components.NextEpisodeAiringListItem
import eu.kanade.presentation.manga.components.OutlinedButtonWithArrow
import eu.kanade.presentation.manga.components.RelatedMangasRow
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.manga.MergedAnimeData
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
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
fun MangaScreen(
    state: MangaScreenModel.State.Success,
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
    onEpisodeClicked: (chapter: Chapter, alt: Boolean) -> Unit,
    onDownloadEpisode: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
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
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Chapter>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (ChapterList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Chapter selection
    onEpisodeSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable (Manga) -> State<Manga>,
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Manga) -> Unit,
    onRelatedAnimeLongClick: (Manga) -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
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
        MangaScreenSmallImpl(
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
            onMergedSettingsClicked = onMergedSettingsClicked,
            onMergeClicked = onMergeClicked,
            onMergeWithAnotherClicked = onMergeWithAnotherClicked,
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
            getMangaState = getMangaState,
            onRelatedAnimesScreenClick = onRelatedAnimesScreenClick,
            onRelatedAnimeClick = onRelatedAnimeClick,
            onRelatedAnimeLongClick = onRelatedAnimeLongClick,
            onCoverLoaded = onCoverLoaded,
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
        )
    } else {
        MangaScreenLargeImpl(
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
            onMergedSettingsClicked = onMergedSettingsClicked,
            onMergeClicked = onMergeClicked,
            onMergeWithAnotherClicked = onMergeWithAnotherClicked,
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
            getMangaState = getMangaState,
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
private fun MangaScreenSmallImpl(
    state: MangaScreenModel.State.Success,
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
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
    onDownloadEpisode: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
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
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Chapter>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (ChapterList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Chapter selection
    onEpisodeSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Manga) -> Unit,
    onRelatedAnimeLongClick: (Manga) -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--
) {
    val episodeListState = rememberLazyListState()

    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedAnimesEnabled by Injekt.get<SourcePreferences>().relatedMangas().collectAsState()
    val expandRelatedAnimes by uiPreferences.expandRelatedMangas().collectAsState()
    val showRelatedAnimesInOverflow by uiPreferences.relatedMangasInOverflow().collectAsState()

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
            MangaToolbar(
                title = state.manga.title,
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
                onClickEditInfo = onEditInfoClicked.takeIf { state.manga.favorite },
                // KMK -->
                onClickRelatedAnimes = onRelatedAnimesScreenClick.takeIf {
                    !expandRelatedAnimes &&
                        showRelatedAnimesInOverflow &&
                        state.manga.source != MERGED_SOURCE_ID
                },
                // KMK <--
                onClickMergedSettings = onMergedSettingsClicked.takeIf { state.manga.source == MERGED_SOURCE_ID },
                onClickMerge = onMergeClicked.takeIf { state.showMergeInOverflow },
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
            SharedMangaBottomActionMenu(
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
                episodes.fastAny { !it.chapter.seen } && !isAnySelected
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
                            state.episodes.fastAny { it.chapter.seen }
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
                        key = MangaScreenItem.INFO_BOX,
                        contentType = MangaScreenItem.INFO_BOX,
                    ) {
                        MangaInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            manga = state.manga,
                            sourceName = remember { state.source.getNameForMangaInfo(state.mergedData?.sources) },
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
                        key = MangaScreenItem.ACTION_ROW,
                        contentType = MangaScreenItem.ACTION_ROW,
                    ) {
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            // SY -->
                            onMergeClicked = onMergeClicked.takeUnless { state.showMergeInOverflow },
                            // SY <--
                        )
                    }

                    item(
                        key = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = MangaScreenItem.DESCRIPTION_WITH_TAG,
                    ) {
                        ExpandableMangaDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                        )
                    }

                    // KMK -->
                    if (state.source !is StubSource &&
                        relatedAnimesEnabled &&
                        state.manga.source != MERGED_SOURCE_ID
                    ) {
                        if (expandRelatedAnimes) {
                            if (state.relatedAnimesSorted?.isNotEmpty() != false) {
                                item { HorizontalDivider() }
                                item(
                                    key = MangaScreenItem.RELATED_ANIMES,
                                    contentType = MangaScreenItem.RELATED_ANIMES,
                                ) {
                                    Column {
                                        RelatedMangaTitle(
                                            title = stringResource(KMR.strings.pref_source_related_mangas),
                                            subtitle = null,
                                            onClick = onRelatedAnimesScreenClick,
                                            onLongClick = null,
                                            modifier = Modifier
                                                .padding(horizontal = MaterialTheme.padding.medium),
                                        )
                                        RelatedMangasRow(
                                            relatedMangas = state.relatedAnimesSorted,
                                            getMangaState = getMangaState,
                                            onAnimeClick = onRelatedAnimeClick,
                                            onAnimeLongClick = onRelatedAnimeLongClick,
                                        )
                                    }
                                }
                                item { HorizontalDivider() }
                            }
                        } else if (!showRelatedAnimesInOverflow) {
                            item(
                                key = MangaScreenItem.RELATED_ANIMES,
                                contentType = MangaScreenItem.RELATED_ANIMES,
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

                    // SY -->
                    if (state.showMergeWithAnother) {
                        item(
                            key = MangaScreenItem.INFO_BUTTONS,
                            contentType = MangaScreenItem.INFO_BUTTONS,
                        ) {
                            MangaInfoButtons(
                                showRecommendsButton = true,
                                showMergeWithAnotherButton = state.showMergeWithAnother,
                                onRecommendClicked = { },
                                onMergeWithAnotherClicked = onMergeWithAnotherClicked,
                            )
                        }
                    }
                    // SY <--

                    item(
                        key = MangaScreenItem.EPISODE_HEADER,
                        contentType = MangaScreenItem.EPISODE_HEADER,
                    ) {
                        val missingEpisodeCount = remember(episodes) {
                            episodes.map { it.chapter.episodeNumber }.missingChaptersCount()
                        }
                        ChapterHeader(
                            enabled = !isAnySelected,
                            episodeCount = episodes.size,
                            missingEpisodeCount = missingEpisodeCount,
                            onClick = onFilterClicked,
                        )
                    }

                    if (state.airingTime > 0L) {
                        item(
                            key = MangaScreenItem.AIRING_TIME,
                            contentType = MangaScreenItem.AIRING_TIME,
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
                                state.manga.status.toInt() != SManga.COMPLETED
                            ) {
                                NextEpisodeAiringListItem(
                                    title = stringResource(
                                        MR.strings.display_mode_episode,
                                        formatChapterNumber(state.airingEpisodeNumber),
                                    ),
                                    date = formatTime(state.airingTime, useDayFormat = true),
                                )
                            }
                        }
                    }

                    sharedChapterItems(
                        manga = state.manga,
                        // AM (FILE_SIZE) -->
                        source = state.source,
                        showFileSize = showFileSize,
                        // <-- AM (FILE_SIZE)
                        mergedData = state.mergedData,
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
private fun MangaScreenLargeImpl(
    state: MangaScreenModel.State.Success,
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
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
    onDownloadEpisode: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
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
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Chapter>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For swipe actions
    onEpisodeSwipe: (ChapterList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Chapter selection
    onEpisodeSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onRelatedAnimesScreenClick: () -> Unit,
    onRelatedAnimeClick: (Manga) -> Unit,
    onRelatedAnimeLongClick: (Manga) -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val episodes = remember(state) { state.processedEpisodes }
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            episodes.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedAnimesEnabled by Injekt.get<SourcePreferences>().relatedMangas().collectAsState()
    val expandRelatedAnimes by uiPreferences.expandRelatedMangas().collectAsState()
    val showRelatedAnimesInOverflow by uiPreferences.relatedMangasInOverflow().collectAsState()

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
            MangaToolbar(
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                title = state.manga.title,
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
                onClickEditInfo = onEditInfoClicked.takeIf { state.manga.favorite },
                // SY <--
                // KMK -->
                onClickRelatedAnimes = onRelatedAnimesScreenClick.takeIf {
                    !expandRelatedAnimes &&
                        showRelatedAnimesInOverflow &&
                        state.manga.source != MERGED_SOURCE_ID
                },
                // KMK <--
                onClickMergedSettings = onMergedSettingsClicked.takeIf { state.manga.source == MERGED_SOURCE_ID },
                onClickMerge = onMergeClicked.takeIf { state.showMergeInOverflow },
                // SY <--
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
                SharedMangaBottomActionMenu(
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
                episodes.fastAny { !it.chapter.seen } && !isAnySelected
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
                            state.episodes.fastAny { it.chapter.seen }
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
                        MangaInfoBox(
                            isTabletUi = true,
                            appBarPadding = contentPadding.calculateTopPadding(),
                            manga = state.manga,
                            sourceName = remember { state.source.getNameForMangaInfo(state.mergedData?.sources) },
                            isStubSource = remember { state.source is StubSource },
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                            // KMK -->
                            onCoverLoaded = onCoverLoaded,
                            coverRatio = coverRatio,
                            // KMK <--
                        )
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            nextUpdate = nextUpdate,
                            isUserIntervalMode = state.manga.fetchInterval < 0,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditIntervalClicked = onEditIntervalClicked,
                            onEditCategory = onEditCategoryClicked,
                            // SY -->
                            onMergeClicked = onMergeClicked.takeUnless { state.showMergeInOverflow },
                            // SY <--
                        )
                        ExpandableMangaDescription(
                            defaultExpandState = true,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                        )
                        // SY -->
                        if (state.showMergeWithAnother) {
                            MangaInfoButtons(
                                showRecommendsButton = true,
                                showMergeWithAnotherButton = state.showMergeWithAnother,
                                onRecommendClicked = { },
                                onMergeWithAnotherClicked = onMergeWithAnotherClicked,
                            )
                        }
                        // SY <--
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
                                relatedAnimesEnabled &&
                                state.manga.source != MERGED_SOURCE_ID
                            ) {
                                if (expandRelatedAnimes) {
                                    if (state.relatedAnimesSorted?.isNotEmpty() != false) {
                                        item(
                                            key = MangaScreenItem.RELATED_ANIMES,
                                            contentType = MangaScreenItem.RELATED_ANIMES,
                                        ) {
                                            Column {
                                                RelatedMangaTitle(
                                                    title = stringResource(KMR.strings.pref_source_related_mangas)
                                                        .uppercase(),
                                                    subtitle = null,
                                                    onClick = onRelatedAnimesScreenClick,
                                                    onLongClick = null,
                                                    modifier = Modifier
                                                        .padding(horizontal = MaterialTheme.padding.medium),
                                                )
                                                RelatedMangasRow(
                                                    relatedMangas = state.relatedAnimesSorted,
                                                    getMangaState = getMangaState,
                                                    onAnimeClick = onRelatedAnimeClick,
                                                    onAnimeLongClick = onRelatedAnimeLongClick,
                                                )
                                            }
                                        }
                                        item { HorizontalDivider() }
                                    }
                                } else if (!showRelatedAnimesInOverflow) {
                                    item(
                                        key = MangaScreenItem.RELATED_ANIMES,
                                        contentType = MangaScreenItem.RELATED_ANIMES,
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
                                key = MangaScreenItem.EPISODE_HEADER,
                                contentType = MangaScreenItem.EPISODE_HEADER,
                            ) {
                                val missingEpisodeCount = remember(episodes) {
                                    episodes.map { it.chapter.episodeNumber }.missingChaptersCount()
                                }
                                ChapterHeader(
                                    enabled = !isAnySelected,
                                    episodeCount = episodes.size,
                                    missingEpisodeCount = missingEpisodeCount,
                                    onClick = onFilterButtonClicked,
                                )
                            }

                            if (state.airingTime > 0L) {
                                item(
                                    key = MangaScreenItem.AIRING_TIME,
                                    contentType = MangaScreenItem.AIRING_TIME,
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
                                        state.manga.status.toInt() != SManga.COMPLETED
                                    ) {
                                        NextEpisodeAiringListItem(
                                            title = stringResource(
                                                MR.strings.display_mode_episode,
                                                formatChapterNumber(state.airingEpisodeNumber),
                                            ),
                                            date = formatTime(state.airingTime, useDayFormat = true),
                                        )
                                    }
                                }
                            }

                            sharedChapterItems(
                                manga = state.manga,
                                // AM (FILE_SIZE) -->
                                source = state.source,
                                showFileSize = showFileSize,
                                // <-- AM (FILE_SIZE)
                                mergedData = state.mergedData,
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
private fun SharedMangaBottomActionMenu(
    selected: List<ChapterList.Item>,
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AM (FILLERMARK) -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AM (FILLERMARK)
    onMultiMarkAsSeenClicked: (List<Chapter>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Chapter) -> Unit,
    onDownloadEpisode: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    MangaBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.bookmark } },
        // AM (FILLERMARK) -->
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.fillermark } },
        // <-- AM (FILLERMARK)
        onMarkAsSeenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.seen } },
        onMarkAsUnseenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.seen || it.chapter.lastSecondSeen > 0L } },
        onMarkPreviousAsSeenClicked = {
            onMarkPreviousAsSeenClicked(selected[0].chapter)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadEpisode!!(selected.toList(), ChapterDownloadAction.START)
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            onDownloadEpisode != null && selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        onExternalClicked = {
            onEpisodeClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
    )
}

private fun LazyListScope.sharedChapterItems(
    manga: Manga,
    // AM (FILE_SIZE) -->
    source: Source,
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    mergedData: MergedAnimeData?,
    episodes: List<ChapterList>,
    isAnyEpisodeSelected: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
    onDownloadEpisode: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onEpisodeSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onEpisodeSwipe: (ChapterList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
) {
    items(
        items = episodes,
        key = { item ->
            when (item) {
                // KMK: using hashcode to prevent edge-cases where the missing count might duplicate,
                // especially on merged manga
                is ChapterList.MissingCount -> "missing-count-${item.hashCode()}"
                is ChapterList.Item -> "episode-${item.id}"
            }
        },
        contentType = { MangaScreenItem.EPISODE },
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is ChapterList.MissingCount -> {
                MissingChapterCountListItem(count = item.count)
            }
            is ChapterList.Item -> {
                // AM (FILE_SIZE) -->
                var fileSizeAsync: Long? by remember { mutableStateOf(item.fileSize) }
                val isEpisodeDownloaded = item.downloadState == Download.State.DOWNLOADED
                if (isEpisodeDownloaded && showFileSize && fileSizeAsync == null) {
                    LaunchedEffect(item, Unit) {
                        fileSizeAsync = withIOContext {
                            downloadProvider.getEpisodeFileSize(
                                item.chapter.name,
                                item.chapter.url,
                                item.chapter.scanlator,
                                // AM (CUSTOM_INFORMATION) -->
                                manga.ogTitle,
                                // <-- AM (CUSTOM_INFORMATION)
                                source,
                            )
                        }
                        item.fileSize = fileSizeAsync
                    }
                }
                // <-- AM (FILE_SIZE)
                MangaChapterListItem(
                    title = if (manga.displayMode == Manga.EPISODE_DISPLAY_NUMBER) {
                        stringResource(
                            MR.strings.display_mode_episode,
                            formatChapterNumber(item.chapter.episodeNumber),
                        )
                    } else {
                        item.chapter.name
                    },
                    date = relativeDateTimeText(item.chapter.dateUpload),
                    watchProgress = item.chapter.lastSecondSeen
                        .takeIf { !item.chapter.seen && it > 0L }
                        ?.let {
                            stringResource(
                                MR.strings.episode_progress,
                                formatTime(it),
                                formatTime(item.chapter.totalSeconds),
                            )
                        },
                    scanlator = item.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                    seen = item.chapter.seen,
                    bookmark = item.chapter.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = item.chapter.fillermark,
                    // <-- AM (FILLERMARK)
                    selected = item.selected,
                    downloadIndicatorEnabled =
                    !isAnyEpisodeSelected && !(mergedData?.manga?.get(item.chapter.animeId) ?: manga).isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    episodeSwipeStartAction = episodeSwipeStartAction,
                    episodeSwipeEndAction = episodeSwipeEndAction,
                    onLongClick = {
                        onEpisodeSelected(item, !item.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onChapterItemClick(
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

private fun onChapterItemClick(
    episodeItem: ChapterList.Item,
    isAnyEpisodeSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
) {
    when {
        episodeItem.selected -> onToggleSelection(false)
        isAnyEpisodeSelected -> onToggleSelection(true)
        else -> onEpisodeClicked(episodeItem.chapter, false)
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
