package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import aniyomi.domain.anime.SeasonAnime
import aniyomi.domain.anime.SeasonDisplayMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.components.AnimeSeasonListItem
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
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.isIncognitoModeEnabled
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.manga.ChapterList
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.delay
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.TwoPanelBox
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
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    navigateUp: () -> Unit,
    onChapterClicked: (chapter: Chapter, altPlayer: Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    onSkipIntroClicked: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    onRecommendClicked: () -> Unit,
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable (Manga) -> State<Manga>,
    onClickSourceSettingsClicked: (() -> Unit)?,
    onClearManga: () -> Unit,
    onOpenMangaFolder: (() -> Unit)?,
    onRelatedMangasScreenClick: () -> Unit,
    onRelatedMangaClick: (Manga) -> Unit,
    onRelatedMangaLongClick: (Manga) -> Unit,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    // <-- AY
) {
    val context = LocalContext.current
    val onCopyTagToClipboard: (tag: String) -> Unit = {
        if (it.isNotEmpty()) {
            context.copyToClipboard(it, it)
        }
    }

    if (!isTabletUi) {
        MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            onMigrateClicked = onMigrateClicked,
            onEditNotesClicked = onEditNotesClicked,
            changeAnimeSkipIntro = onSkipIntroClicked,
            // SY -->
            onEditInfoClicked = onEditInfoClicked,
            onRecommendClicked = onRecommendClicked,
            onMergedSettingsClicked = onMergedSettingsClicked,
            onMergeClicked = onMergeClicked,
            onMergeWithAnotherClicked = onMergeWithAnotherClicked,
            // SY <--
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AY -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AY
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            // KMK -->
            getMangaState = getMangaState,
            onClickSourceSettingsClicked = onClickSourceSettingsClicked,
            onClearManga = onClearManga,
            onOpenMangaFolder = onOpenMangaFolder,
            onRelatedMangasScreenClick = onRelatedMangasScreenClick,
            onRelatedMangaClick = onRelatedMangaClick,
            onRelatedMangaLongClick = onRelatedMangaLongClick,
            librarySearch = librarySearch,
            onSourceClick = onSourceClick,
            onCoverLoaded = onCoverLoaded,
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
            // AY -->
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            // <-- AY
        )
    } else {
        MangaScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            nextUpdate = nextUpdate,
            chapterSwipeStartAction = chapterSwipeStartAction,
            chapterSwipeEndAction = chapterSwipeEndAction,
            showNextEpisodeAirTime = showNextEpisodeAirTime,
            alwaysUseExternalPlayer = alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = showFileSize,
            // <-- AM (FILE_SIZE)
            navigateUp = navigateUp,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagSearch = onTagSearch,
            onCopyTagToClipboard = onCopyTagToClipboard,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onDownloadActionClicked = onDownloadActionClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onEditIntervalClicked = onEditFetchIntervalClicked,
            changeAnimeSkipIntro = onSkipIntroClicked,
            onMigrateClicked = onMigrateClicked,
            onEditNotesClicked = onEditNotesClicked,
            // SY -->
            onEditInfoClicked = onEditInfoClicked,
            onRecommendClicked = onRecommendClicked,
            onMergedSettingsClicked = onMergedSettingsClicked,
            onMergeClicked = onMergeClicked,
            onMergeWithAnotherClicked = onMergeWithAnotherClicked,
            // SY <--
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            // AY -->
            onMultiFillermarkClicked = onMultiFillermarkClicked,
            // <-- AY
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSwipe = onChapterSwipe,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
            onInvertSelection = onInvertSelection,
            // KMK -->
            getMangaState = getMangaState,
            onClickSourceSettingsClicked = onClickSourceSettingsClicked,
            onClearManga = onClearManga,
            onOpenMangaFolder = onOpenMangaFolder,
            onRelatedMangasScreenClick = onRelatedMangasScreenClick,
            onRelatedMangaClick = onRelatedMangaClick,
            onRelatedMangaLongClick = onRelatedMangaLongClick,
            librarySearch = librarySearch,
            onSourceClick = onSourceClick,
            onCoverLoaded = onCoverLoaded,
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
            // AY -->
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onContinueWatchingClicked,
            // <-- AY
        )
    }
}

@Composable
private fun MangaScreenSmallImpl(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    changeAnimeSkipIntro: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    onRecommendClicked: () -> Unit,
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickSourceSettingsClicked: (() -> Unit)?,
    onClearManga: () -> Unit,
    onOpenMangaFolder: (() -> Unit)?,
    onRelatedMangasScreenClick: () -> Unit,
    onRelatedMangaClick: (Manga) -> Unit,
    onRelatedMangaLongClick: (Manga) -> Unit,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    // <-- AY
) {
    // AY -->
    val density = LocalDensity.current
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.manga) { state.manga.seasonDisplayGridSize }
    val chapterListState = rememberLazyGridState()

    var toolbarHeight by remember { mutableIntStateOf(0) }
    // <-- AY

    val chapters = remember(state) { state.processedChapters }
    // AY -->
    val seasons = remember(state) { state.processedSeasons }
    // <-- AY
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            chapters.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedMangasEnabled by Injekt.get<SourcePreferences>().relatedMangas().collectAsState()
    val expandRelatedMangas by uiPreferences.expandRelatedMangas().collectAsState()
    val showRelatedMangasInOverflow by uiPreferences.relatedMangasInOverflow().collectAsState()

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var fabSize by remember { mutableStateOf(IntSize.Zero) }
    var positionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val fabPosition by uiPreferences.readButtonPosition().collectAsState()
    val readButtonPosition = uiPreferences.readButtonPosition()
    // KMK <--

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    // AY -->
    BoxWithConstraints {
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        // <-- AY
        Scaffold(
            topBar = {
                val selectedChapterCount: Int = remember(chapters) {
                    chapters.count { it.selected }
                }
                val isFirstItemVisible by remember {
                    derivedStateOf { chapterListState.firstVisibleItemIndex == 0 }
                }
                val isFirstItemScrolled by remember {
                    derivedStateOf { chapterListState.firstVisibleItemScrollOffset > 0 }
                }
                val titleAlpha by animateFloatAsState(
                    if (!isFirstItemVisible) 1f else 0f,
                    label = "Top Bar Title",
                )
                val backgroundAlpha by animateFloatAsState(
                    if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                    label = "Top Bar Background",
                )
                MangaToolbar(
                    title = state.manga.title,
                    hasFilters = state.filterActive,
                    navigateUp = navigateUp,
                    onClickFilter = onFilterClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickEditNotes = onEditNotesClicked,
                    // SY -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.manga.favorite },
                    // KMK -->
                    onClickSourceSettings = onClickSourceSettingsClicked,
                    onClearManga = onClearManga,
                    onOpenMangaFolder = onOpenMangaFolder,
                    onClickRelatedMangas = onRelatedMangasScreenClick.takeIf {
                        !expandRelatedMangas &&
                            showRelatedMangasInOverflow &&
                            state.manga.source != MERGED_SOURCE_ID
                    },
                    // KMK <--
                    onClickRecommend = onRecommendClicked.takeIf { state.showRecommendationsInOverflow },
                    onClickMergedSettings = onMergedSettingsClicked.takeIf { state.manga.source == MERGED_SOURCE_ID },
                    onClickMerge = onMergeClicked.takeIf { state.showMergeInOverflow },
                    // SY <--
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    actionModeCounter = selectedChapterCount,
                    onCancelActionMode = { onAllChapterSelected(false) },
                    onSelectAll = { onAllChapterSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                    // AY -->
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height },
                    // <-- AY
                )
            },
            bottomBar = {
                val selectedChapters = remember(chapters) {
                    chapters.filter { it.selected }
                }
                SharedMangaBottomActionMenu(
                    selected = selectedChapters,
                    onEpisodeClicked = onChapterClicked,
                    onMultiBookmarkClicked = onMultiBookmarkClicked,
                    // AY -->
                    onMultiFillermarkClicked = onMultiFillermarkClicked,
                    // <-- AY
                    onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadChapter = onDownloadChapter,
                    onMultiDeleteClicked = onMultiDeleteClicked,
                    fillFraction = 1f,
                    alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(chapters) {
                    chapters.fastAny { !it.chapter.read } && !isAnySelected
                }
                val isReading = remember(state.chapters) {
                    state.chapters.fastAny { it.chapter.read }
                }
                val textRes = if (isReading) {
                    MR.strings.action_resume
                } else {
                    MR.strings.action_start
                }
                SmallExtendedFloatingActionButton(
                    text = { Text(text = stringResource(textRes)) },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueReading,
                    expanded = chapterListState.shouldExpandFAB(),
                    modifier = Modifier.animateFloatingActionButton(
                        visible = isFABVisible,
                        alignment = Alignment.BottomEnd,
                    )
                        // KMK -->
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .onGloballyPositioned { coordinates ->
                            fabSize = coordinates.size
                            positionOnScreen = coordinates.positionOnScreen()
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (positionOnScreen.x + fabSize.width / 2 >= layoutSize.width / 2) {
                                        readButtonPosition.set(FabPosition.End.toString())
                                    } else {
                                        readButtonPosition.set(FabPosition.Start.toString())
                                    }
                                    offsetX = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val newOffsetX = offsetX + dragAmount
                                if (!newOffsetX.isNaN()) {
                                    offsetX = newOffsetX
                                }
                            }
                        },
                    containerColor = MaterialTheme.colorScheme.primary,
                    // KMK <--
                )
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
                .hazeSource(state = hazeState),
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
                // AY -->
                FastScrollLazyVerticalGrid(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                    contentPadding = PaddingValues(
                        start = GRID_PADDING + contentPadding.calculateStartPadding(layoutDirection),
                        end = GRID_PADDING + contentPadding.calculateEndPadding(layoutDirection),
                        // <-- AY
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = MangaScreenItem.INFO_BOX,
                        contentType = MangaScreenItem.INFO_BOX,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        MangaInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            manga = state.manga,
                            sourceName = remember { state.source.getNameForMangaInfo(state.mergedData?.sources) },
                            isStubSource = remember { state.source is StubSource },
                            // KMK -->
                            isSourceIncognito = remember { state.source.isIncognitoModeEnabled() },
                            // KMK <--
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                            // KMK -->
                            librarySearch = librarySearch,
                            onSourceClick = onSourceClick,
                            onCoverLoaded = onCoverLoaded,
                            coverRatio = coverRatio,
                            // KMK <--
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    item(
                        key = MangaScreenItem.ACTION_ROW,
                        contentType = MangaScreenItem.ACTION_ROW,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
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
                            // KMK -->
                            status = state.manga.status,
                            interval = state.manga.fetchInterval,
                            // KMK <--
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    item(
                        key = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        ExpandableMangaDescription(
                            defaultExpandState = state.isFromSource && !state.manga.favorite,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            notes = state.manga.notes,
                            onTagSearch = onTagSearch,
                            onCopyTagToClipboard = onCopyTagToClipboard,
                            onEditNotes = onEditNotesClicked,
                            // SY -->
                            doSearch = onSearch,
                            // SY <--
                            // AY -->
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                            // <-- AY
                        )
                    }

                    // KMK -->
                    if (state.source !is StubSource &&
                        relatedMangasEnabled &&
                        state.manga.source != MERGED_SOURCE_ID
                    ) {
                        if (expandRelatedMangas) {
                            if (state.relatedMangasSorted?.isNotEmpty() != false) {
                                item(
                                    // ANK -->
                                    span = { GridItemSpan(maxLineSpan) },
                                    // ANK <--
                                ) { HorizontalDivider() }
                                item(
                                    key = MangaScreenItem.RELATED_MANGAS,
                                    contentType = MangaScreenItem.RELATED_MANGAS,
                                    // ANK -->
                                    span = { GridItemSpan(maxLineSpan) },
                                    // ANK <--
                                ) {
                                    Column {
                                        RelatedMangaTitle(
                                            title = stringResource(KMR.strings.pref_source_related_mangas),
                                            subtitle = null,
                                            onClick = onRelatedMangasScreenClick,
                                            onLongClick = null,
                                            modifier = Modifier
                                                .padding(horizontal = MaterialTheme.padding.medium)
                                                // ANK -->
                                                .ignorePadding(offsetGridPaddingPx),
                                            // ANK <--
                                        )
                                        RelatedMangasRow(
                                            relatedMangas = state.relatedMangasSorted,
                                            getMangaState = getMangaState,
                                            onMangaClick = onRelatedMangaClick,
                                            onMangaLongClick = onRelatedMangaLongClick,
                                        )
                                    }
                                }
                                item(
                                    // ANK -->
                                    span = { GridItemSpan(maxLineSpan) },
                                    // ANK <--
                                ) { HorizontalDivider() }
                            }
                        } else if (!showRelatedMangasInOverflow) {
                            item(
                                key = MangaScreenItem.RELATED_MANGAS,
                                contentType = MangaScreenItem.RELATED_MANGAS,
                                // ANK -->
                                span = { GridItemSpan(maxLineSpan) },
                                // ANK <--
                            ) {
                                OutlinedButtonWithArrow(
                                    text = stringResource(KMR.strings.pref_source_related_mangas)
                                        .uppercase(),
                                    onClick = onRelatedMangasScreenClick,
                                    // ANK -->
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                    // ANK <--
                                )
                            }
                        }
                    }
                    // KMK <--

                    // SY -->
                    if (!state.showRecommendationsInOverflow || state.showMergeWithAnother) {
                        item(
                            key = MangaScreenItem.INFO_BUTTONS,
                            contentType = MangaScreenItem.INFO_BUTTONS,
                            // ANK -->
                            span = { GridItemSpan(maxLineSpan) },
                            // ANK <--
                        ) {
                            MangaInfoButtons(
                                showRecommendsButton = !state.showRecommendationsInOverflow,
                                showMergeWithAnotherButton = state.showMergeWithAnother,
                                onRecommendClicked = onRecommendClicked,
                                onMergeWithAnotherClicked = onMergeWithAnotherClicked,
                                // ANK -->
                                modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                // ANK <--
                            )
                        }
                    }
                    // SY <--

                    item(
                        key = MangaScreenItem.CHAPTER_HEADER,
                        contentType = MangaScreenItem.CHAPTER_HEADER,
                        // AY -->
                        span = { GridItemSpan(maxLineSpan) },
                        // <-- AY
                    ) {
                        val missingChapterCount = remember(chapters) {
                            chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                        }
                        // AY -->
                        val missingSeasonsCount = remember(seasons) {
                            seasons.map { it.seasonAnime.anime.seasonNumber }.missingChaptersCount()
                        }
                        ChapterHeader(
                            enabled = !isAnySelected,
                            chapterCount = when (state.manga.fetchType) {
                                FetchType.Seasons -> seasons.size
                                FetchType.Episodes -> chapters.size
                            },
                            missingChapterCount = when (state.manga.fetchType) {
                                FetchType.Seasons -> missingSeasonsCount
                                FetchType.Episodes -> missingChapterCount
                            },
                            onClick = onFilterClicked,
                            fetchType = state.manga.fetchType,
                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                        )
                        // <-- AY
                    }

                    // AY -->
                    when (state.manga.fetchType) {
                        FetchType.Seasons -> {
                            sharedSeasons(
                                anime = state.manga,
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
                                    key = MangaScreenItem.AIRING_TIME,
                                    contentType = MangaScreenItem.AIRING_TIME,
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
                                        state.manga.status.toInt() != SManga.COMPLETED
                                    ) {
                                        NextEpisodeAiringListItem(
                                            title = stringResource(
                                                AYMR.strings.display_mode_episode,
                                                formatChapterNumber(state.airingEpisodeNumber),
                                            ),
                                            date = formatTime(state.airingTime, useDayFormat = true),
                                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        )
                                    }
                                }
                            }
                            // <-- AY

                            sharedChapterItems(
                                manga = state.manga,
                                // AM (FILE_SIZE) -->
                                source = state.source,
                                showFileSize = showFileSize,
                                // <-- AM (FILE_SIZE)
                                mergedData = state.mergedData,
                                chapters = listItem,
                                isAnyChapterSelected = chapters.fastAny { it.selected },
                                // AY -->
                                showSummaries = state.showSummaries,
                                showPreviews = state.showPreviews,
                                // <-- AY
                                chapterSwipeStartAction = chapterSwipeStartAction,
                                chapterSwipeEndAction = chapterSwipeEndAction,
                                onChapterClicked = onChapterClicked,
                                onDownloadChapter = onDownloadChapter,
                                onChapterSelected = onChapterSelected,
                                onChapterSwipe = onChapterSwipe,
                                // AY -->
                                itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                // <-- AY
                            )
                        }
                    }
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
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    // AM (FILE_SIZE) -->
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onEditNotesClicked: () -> Unit,
    changeAnimeSkipIntro: (() -> Unit)?,
    // SY -->
    onEditInfoClicked: () -> Unit,
    onRecommendClicked: () -> Unit,
    onMergedSettingsClicked: () -> Unit,
    onMergeClicked: () -> Unit,
    onMergeWithAnotherClicked: () -> Unit,
    // SY <--

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For swipe actions
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // KMK -->
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickSourceSettingsClicked: (() -> Unit)?,
    onClearManga: () -> Unit,
    onOpenMangaFolder: (() -> Unit)?,
    onRelatedMangasScreenClick: () -> Unit,
    onRelatedMangaClick: (Manga) -> Unit,
    onRelatedMangaLongClick: (Manga) -> Unit,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    onCoverLoaded: (MangaCover) -> Unit,
    coverRatio: MutableFloatState,
    hazeState: HazeState,
    // KMK <--

    // AY -->
    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    // <-- AY
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val chapters = remember(state) { state.processedChapters }
    // AY -->
    val seasons = remember(state) { state.processedSeasons }
    // <-- AY
    val listItem = remember(state) { state.chapterListItems }

    val isAnySelected by remember {
        derivedStateOf {
            chapters.fastAny { it.selected }
        }
    }

    // KMK -->
    val uiPreferences = Injekt.get<UiPreferences>()
    val relatedMangasEnabled by Injekt.get<SourcePreferences>().relatedMangas().collectAsState()
    val expandRelatedMangas by uiPreferences.expandRelatedMangas().collectAsState()
    val showRelatedMangasInOverflow by uiPreferences.relatedMangasInOverflow().collectAsState()

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var fabSize by remember { mutableStateOf(IntSize.Zero) }
    var positionOnScreen by remember { mutableStateOf(Offset.Zero) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val fabPosition by uiPreferences.readButtonPosition().collectAsState()
    val readButtonPosition = uiPreferences.readButtonPosition()
    // KMK <--

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableIntStateOf(0) }

    // AY -->
    val offsetGridPaddingPx = with(density) { GRID_PADDING.roundToPx() }
    val gridSize = remember(state.manga) { state.manga.seasonDisplayGridSize }

    val chapterListState = rememberLazyGridState()
    // <-- AY

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllChapterSelected(false)
        } else {
            navigateUp()
        }
    })

    // AY -->
    BoxWithConstraints {
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }
        // <-- AY
        Scaffold(
            topBar = {
                val selectedChapterCount = remember(chapters) {
                    chapters.count { it.selected }
                }
                MangaToolbar(
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    title = state.manga.title,
                    hasFilters = state.filterActive,
                    navigateUp = navigateUp,
                    onClickFilter = onFilterButtonClicked,
                    onClickShare = onShareClicked,
                    onClickDownload = onDownloadActionClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickRefresh = onRefresh,
                    onClickMigrate = onMigrateClicked,
                    onClickEditNotes = onEditNotesClicked,
                    changeAnimeSkipIntro = changeAnimeSkipIntro,
                    onCancelActionMode = { onAllChapterSelected(false) },
                    // SY -->
                    onClickEditInfo = onEditInfoClicked.takeIf { state.manga.favorite },
                    // SY <--
                    // KMK -->
                    onClickSourceSettings = onClickSourceSettingsClicked,
                    onClearManga = onClearManga,
                    onOpenMangaFolder = onOpenMangaFolder,
                    onClickRelatedMangas = onRelatedMangasScreenClick.takeIf {
                        !expandRelatedMangas &&
                            showRelatedMangasInOverflow &&
                            state.manga.source != MERGED_SOURCE_ID
                    },
                    // KMK <--
                    onClickRecommend = onRecommendClicked.takeIf { state.showRecommendationsInOverflow },
                    onClickMergedSettings = onMergedSettingsClicked.takeIf { state.manga.source == MERGED_SOURCE_ID },
                    onClickMerge = onMergeClicked.takeIf { state.showMergeInOverflow },
                    // SY <--
                    actionModeCounter = selectedChapterCount,
                    onSelectAll = { onAllChapterSelected(true) },
                    onInvertSelection = { onInvertSelection() },
                    titleAlphaProvider = { 1f },
                    backgroundAlphaProvider = { 1f },
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    val selectedChapters = remember(chapters) {
                        chapters.filter { it.selected }
                    }
                    SharedMangaBottomActionMenu(
                        selected = selectedChapters,
                        onEpisodeClicked = onChapterClicked,
                        onMultiBookmarkClicked = onMultiBookmarkClicked,
                        // AY -->
                        onMultiFillermarkClicked = onMultiFillermarkClicked,
                        // <-- AY
                        onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
                        onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                        onDownloadChapter = onDownloadChapter,
                        onMultiDeleteClicked = onMultiDeleteClicked,
                        fillFraction = 0.5f,
                        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                val isFABVisible = remember(chapters) {
                    chapters.fastAny { !it.chapter.read } && !isAnySelected
                }
                val isReading = remember(state.chapters) {
                    state.chapters.fastAny { it.chapter.read }
                }
                val textRes = if (isReading) {
                    MR.strings.action_resume
                } else {
                    MR.strings.action_start
                }
                SmallExtendedFloatingActionButton(
                    text = { Text(text = stringResource(textRes)) },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueReading,
                    expanded = chapterListState.shouldExpandFAB(),
                    modifier = Modifier.animateFloatingActionButton(
                        visible = isFABVisible,
                        alignment = Alignment.BottomEnd,
                    )
                        // KMK -->
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .onGloballyPositioned { coordinates ->
                            fabSize = coordinates.size
                            positionOnScreen = coordinates.positionOnScreen()
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (positionOnScreen.x + fabSize.width / 2 >= layoutSize.width / 2) {
                                        readButtonPosition.set(FabPosition.End.toString())
                                    } else {
                                        readButtonPosition.set(FabPosition.Start.toString())
                                    }
                                    offsetX = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                val newOffsetX = offsetX + dragAmount
                                if (!newOffsetX.isNaN()) {
                                    offsetX = newOffsetX
                                }
                            }
                        },
                    containerColor = MaterialTheme.colorScheme.primary,
                    // KMK <--
                )
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
                .hazeSource(state = hazeState),
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
                                // KMK -->
                                isSourceIncognito = remember { state.source.isIncognitoModeEnabled() },
                                // KMK <--
                                onCoverClick = onCoverClicked,
                                doSearch = onSearch,
                                // KMK -->
                                librarySearch = librarySearch,
                                onSourceClick = onSourceClick,
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
                                // KMK -->
                                status = state.manga.status,
                                interval = state.manga.fetchInterval,
                                // KMK <--
                            )
                            ExpandableMangaDescription(
                                defaultExpandState = true,
                                description = state.manga.description,
                                tagsProvider = { state.manga.genre },
                                notes = state.manga.notes,
                                onTagSearch = onTagSearch,
                                onCopyTagToClipboard = onCopyTagToClipboard,
                                onEditNotes = onEditNotesClicked,
                                // SY -->
                                doSearch = onSearch,
                                // SY <--
                            )
                            // SY -->
                            if (!state.showRecommendationsInOverflow || state.showMergeWithAnother) {
                                MangaInfoButtons(
                                    showRecommendsButton = !state.showRecommendationsInOverflow,
                                    showMergeWithAnotherButton = state.showMergeWithAnother,
                                    onRecommendClicked = onRecommendClicked,
                                    onMergeWithAnotherClicked = onMergeWithAnotherClicked,
                                )
                            }
                            // SY <--
                        }
                    },
                    endContent = {
                        // AY -->
                        FastScrollLazyVerticalGrid(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            columns = if (gridSize == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(gridSize),
                            contentPadding = PaddingValues(
                                start = GRID_PADDING,
                                end = GRID_PADDING,
                                // <-- AY
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            // KMK -->
                            if (state.source !is StubSource &&
                                relatedMangasEnabled &&
                                state.manga.source != MERGED_SOURCE_ID
                            ) {
                                if (expandRelatedMangas) {
                                    if (state.relatedMangasSorted?.isNotEmpty() != false) {
                                        item(
                                            key = MangaScreenItem.RELATED_MANGAS,
                                            contentType = MangaScreenItem.RELATED_MANGAS,
                                            // ANK -->
                                            span = { GridItemSpan(maxLineSpan) },
                                            // ANK <--
                                        ) {
                                            Column {
                                                RelatedMangaTitle(
                                                    title = stringResource(KMR.strings.pref_source_related_mangas)
                                                        .uppercase(),
                                                    subtitle = null,
                                                    onClick = onRelatedMangasScreenClick,
                                                    onLongClick = null,
                                                    modifier = Modifier
                                                        .padding(horizontal = MaterialTheme.padding.medium)
                                                        // ANK -->
                                                        .ignorePadding(offsetGridPaddingPx),
                                                    // ANK <--
                                                )
                                                RelatedMangasRow(
                                                    relatedMangas = state.relatedMangasSorted,
                                                    getMangaState = getMangaState,
                                                    onMangaClick = onRelatedMangaClick,
                                                    onMangaLongClick = onRelatedMangaLongClick,
                                                )
                                            }
                                        }
                                        item(
                                            // ANK -->
                                            span = { GridItemSpan(maxLineSpan) },
                                            // ANK <--
                                        ) { HorizontalDivider() }
                                    }
                                } else if (!showRelatedMangasInOverflow) {
                                    item(
                                        key = MangaScreenItem.RELATED_MANGAS,
                                        contentType = MangaScreenItem.RELATED_MANGAS,
                                        // ANK -->
                                        span = { GridItemSpan(maxLineSpan) },
                                        // ANK <--
                                    ) {
                                        OutlinedButtonWithArrow(
                                            text = stringResource(KMR.strings.pref_source_related_mangas),
                                            onClick = onRelatedMangasScreenClick,
                                            // ANK -->
                                            modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                            // ANK <--
                                        )
                                    }
                                }
                            }
                            // KMK <--

                            item(
                                key = MangaScreenItem.CHAPTER_HEADER,
                                contentType = MangaScreenItem.CHAPTER_HEADER,
                                // AY -->
                                span = { GridItemSpan(maxLineSpan) },
                                // <-- AY
                            ) {
                                val missingChapterCount = remember(chapters) {
                                    chapters.map { it.chapter.chapterNumber }.missingChaptersCount()
                                }
                                // AY -->
                                val missingSeasonsCount = remember(seasons) {
                                    seasons.map { it.seasonAnime.anime.seasonNumber }.missingChaptersCount()
                                }
                                ChapterHeader(
                                    enabled = !isAnySelected,
                                    chapterCount = when (state.manga.fetchType) {
                                        FetchType.Seasons -> seasons.size
                                        FetchType.Episodes -> chapters.size
                                    },
                                    missingChapterCount = when (state.manga.fetchType) {
                                        FetchType.Seasons -> missingSeasonsCount
                                        FetchType.Episodes -> missingChapterCount
                                    },
                                    onClick = onFilterButtonClicked,
                                    fetchType = state.manga.fetchType,
                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                )
                                // <-- AY
                            }

                            // AY -->
                            when (state.manga.fetchType) {
                                FetchType.Seasons -> {
                                    sharedSeasons(
                                        anime = state.manga,
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
                                            key = MangaScreenItem.AIRING_TIME,
                                            contentType = MangaScreenItem.AIRING_TIME,
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
                                                state.manga.status.toInt() != SManga.COMPLETED
                                            ) {
                                                NextEpisodeAiringListItem(
                                                    title = stringResource(
                                                        AYMR.strings.display_mode_episode,
                                                        formatChapterNumber(state.airingEpisodeNumber),
                                                    ),
                                                    date = formatTime(state.airingTime, useDayFormat = true),
                                                    modifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                                )
                                            }
                                        }
                                    }
                                    // <-- AY

                                    sharedChapterItems(
                                        manga = state.manga,
                                        // AM (FILE_SIZE) -->
                                        source = state.source,
                                        showFileSize = showFileSize,
                                        // <-- AM (FILE_SIZE)
                                        mergedData = state.mergedData,
                                        chapters = listItem,
                                        isAnyChapterSelected = chapters.fastAny { it.selected },
                                        // AY -->
                                        showSummaries = state.showSummaries,
                                        showPreviews = state.showPreviews,
                                        // <-- AY
                                        chapterSwipeStartAction = chapterSwipeStartAction,
                                        chapterSwipeEndAction = chapterSwipeEndAction,
                                        onChapterClicked = onChapterClicked,
                                        onDownloadChapter = onDownloadChapter,
                                        onChapterSelected = onChapterSelected,
                                        onChapterSwipe = onChapterSwipe,
                                        // AY -->
                                        itemModifier = Modifier.ignorePadding(offsetGridPaddingPx),
                                        // <-- AY
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
private fun SharedMangaBottomActionMenu(
    selected: List<ChapterList.Item>,
    onEpisodeClicked: (Chapter, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    // AY -->
    onMultiFillermarkClicked: (List<Chapter>, fillermarked: Boolean) -> Unit,
    // <-- AY
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
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
        // AY -->
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAll { it.chapter.fillermark } },
        // <-- AY
        onMarkAsReadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, true)
        }.takeIf { selected.fastAny { !it.chapter.read } },
        onMarkAsUnreadClicked = {
            onMultiMarkAsReadClicked(selected.fastMap { it.chapter }, false)
        }.takeIf { selected.fastAny { it.chapter.read || it.chapter.lastPageRead > 0L } },
        onMarkPreviousAsReadClicked = {
            onMarkPreviousAsReadClicked(selected[0].chapter)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onDownloadChapter!!(selected.toList(), ChapterDownloadAction.START)
        }.takeIf {
            onDownloadChapter != null && selected.fastAny { it.downloadState != Download.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.chapter })
        }.takeIf {
            selected.fastAny { it.downloadState == Download.State.DOWNLOADED }
        },
        onExternalClicked = {
            onEpisodeClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
        onInternalClicked = {
            onEpisodeClicked(selected.fastMap { it.chapter }.first(), true)
        }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
    )
}

// AY -->
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
        key = { season -> season.seasonAnime.anime },
        span = { GridItemSpan(if (anime.seasonDisplayGridMode == SeasonDisplayMode.List) maxLineSpan else 1) },
    ) { item ->
        AnimeSeasonListItem(
            anime = anime,
            item = item,
            containerHeight = containerHeight,
            onSeasonClicked = onSeasonClicked,
            onClickContinueWatching = onClickContinueWatching,
            modifier = listItemModifier,
        )
    }
}

private fun LazyGridScope.sharedChapterItems(
    // <-- AY
    manga: Manga,
    // AM (FILE_SIZE) -->
    source: Source,
    showFileSize: Boolean,
    // <-- AM (FILE_SIZE)
    mergedData: MergedMangaData?,
    chapters: List<ChapterList>,
    isAnyChapterSelected: Boolean,
    // AY -->
    showSummaries: Boolean,
    showPreviews: Boolean,
    // <-- AY
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterClicked: (Chapter, Boolean) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,
    // AY -->
    itemModifier: Modifier = Modifier,
    // <-- AY
) {
    items(
        items = chapters,
        key = { item ->
            when (item) {
                // KMK: using hashcode to prevent edge-cases where the missing count might duplicate,
                // especially on merged manga
                is ChapterList.MissingCount -> "missing-count-${item.hashCode()}"
                is ChapterList.Item -> "chapter-${item.id}"
            }
        },
        contentType = { MangaScreenItem.CHAPTER },
        // AY -->
        span = { GridItemSpan(maxLineSpan) },
        // <-- AY
    ) { item ->
        val haptic = LocalHapticFeedback.current

        when (item) {
            is ChapterList.MissingCount -> {
                MissingChapterCountListItem(
                    count = item.count,
                    // AY -->
                    modifier = itemModifier,
                    // <-- AY
                )
            }
            is ChapterList.Item -> {
                // AM (FILE_SIZE) -->
                var fileSizeAsync: Long? by remember { mutableStateOf(item.fileSize) }
                val isChapterDownloaded = item.downloadState == Download.State.DOWNLOADED
                if (isChapterDownloaded && showFileSize && fileSizeAsync == null) {
                    LaunchedEffect(item, Unit) {
                        fileSizeAsync = withIOContext {
                            downloadProvider.getChapterFileSize(
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
                            AYMR.strings.display_mode_episode,
                            formatChapterNumber(item.chapter.chapterNumber),
                        )
                    } else {
                        item.chapter.name
                    },
                    date = item.chapter.dateUpload
                        .takeIf { it > 0L }
                        ?.let {
                            relativeDateTimeText(item.chapter.dateUpload)
                        },
                    readProgress = item.chapter.lastPageRead
                        .takeIf {
                            !item.chapter.read && it > 0L
                        }
                        ?.let {
                            stringResource(
                                AYMR.strings.episode_progress,
                                formatTime(it),
                                formatTime(item.chapter.totalPages),
                            )
                        },
                    scanlator = item.chapter.scanlator.takeIf {
                        !it.isNullOrBlank()
                    },
                    // AY -->
                    summary = item.chapter.summary.takeIf { !it.isNullOrBlank() && showSummaries },
                    previewUrl = item.chapter.previewUrl.takeIf { !it.isNullOrBlank() && showPreviews },
                    // <-- AY
                    // SY -->
                    sourceName = item.sourceName,
                    // SY <--
                    read = item.chapter.read,
                    bookmark = item.chapter.bookmark,
                    // AY -->
                    fillermark = item.chapter.fillermark,
                    // <-- AY
                    selected = item.selected,
                    // AY -->
                    isAnyEpisodeSelected = isAnyChapterSelected,
                    // <-- AY
                    downloadIndicatorEnabled =
                    !isAnyChapterSelected && !(mergedData?.manga?.get(item.chapter.mangaId) ?: manga).isLocal(),
                    downloadStateProvider = { item.downloadState },
                    downloadProgressProvider = { item.downloadProgress },
                    chapterSwipeStartAction = chapterSwipeStartAction,
                    chapterSwipeEndAction = chapterSwipeEndAction,
                    onLongClick = {
                        onChapterSelected(item, !item.selected, true, true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        onChapterItemClick(
                            chapterItem = item,
                            isAnyChapterSelected = isAnyChapterSelected,
                            onToggleSelection = { onChapterSelected(item, !item.selected, true, false) },
                            onChapterClicked = onChapterClicked,
                        )
                    },
                    onDownloadClick = if (onDownloadChapter != null) {
                        { onDownloadChapter(listOf(item), it) }
                    } else {
                        null
                    },
                    onChapterSwipe = {
                        onChapterSwipe(item, it)
                    },
                    // AM (FILE_SIZE) -->
                    fileSize = fileSizeAsync,
                    // <-- AM (FILE_SIZE)
                    // AY -->
                    modifier = itemModifier,
                    // <-- AY
                )
            }
        }
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterList.Item,
    isAnyChapterSelected: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter, Boolean) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        isAnyChapterSelected -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter, false)
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

// AY -->
private val GRID_PADDING = 14.dp
private fun Modifier.ignorePadding(gridPadding: Int) = layout { measurable, constraints ->
    val looseConstraints = constraints.offset(gridPadding * 2, 0)
    val placeable = measurable.measure(looseConstraints)

    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
// <-- AY

// AM (FILE_SIZE) -->
private val downloadProvider: DownloadProvider by injectLazy()
// <-- AM (FILE_SIZE)
