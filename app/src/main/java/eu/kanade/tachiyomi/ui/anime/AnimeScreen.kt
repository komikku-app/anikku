package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.materialkolor.ktx.blend
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSAnime
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.manga.AnimeScreen
import eu.kanade.presentation.manga.DuplicateAnimeDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.EpisodeOptionsDialogScreen
import eu.kanade.presentation.manga.EpisodeSettingsDialog
import eu.kanade.presentation.manga.components.AnimeCoverDialog
import eu.kanade.presentation.manga.components.DeleteEpisodesDialog
import eu.kanade.presentation.manga.components.SetIntervalDialog
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen.SkipIntroLengthDialog
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import eu.kanade.tachiyomi.ui.anime.merged.EditMergedSettingsDialog
import eu.kanade.tachiyomi.ui.anime.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.browse.AddDuplicateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeAnimeCategoryDialog
import eu.kanade.tachiyomi.ui.browse.ChangeAnimesCategoryDialog
import eu.kanade.tachiyomi.ui.browse.RemoveAnimeDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeScreen(
    private val mangaId: Long,
    /** If it is opened from Source then it will auto expand the manga description */
    val fromSource: Boolean = false,
    private val smartSearchConfig: SourcesScreen.SmartSearchConfig? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            AnimeScreenModel(context, lifecycleOwner.lifecycle, mangaId, fromSource, smartSearchConfig != null)
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is AnimeScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeScreenModel.State.Success

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        val showingRelatedMangasScreen = rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = bulkFavoriteState.selectionMode || showingRelatedMangasScreen.value) {
            when {
                bulkFavoriteState.selectionMode -> bulkFavoriteScreenModel.toggleSelectionMode()
                showingRelatedMangasScreen.value -> showingRelatedMangasScreen.value = false
            }
        }

        val content = @Composable {
            Crossfade(
                targetState = showingRelatedMangasScreen.value,
                label = "manga_related_crossfade",
            ) { showRelatedMangasScreen ->
                when (showRelatedMangasScreen) {
                    true -> RelatedAnimesScreen(
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        navigateUp = { showingRelatedMangasScreen.value = false },
                        navigator = navigator,
                        scope = scope,
                    )
                    false -> MangaDetailContent(
                        context = context,
                        screenModel = screenModel,
                        successState = successState,
                        bulkFavoriteScreenModel = bulkFavoriteScreenModel,
                        showRelatedMangasScreen = { showingRelatedMangasScreen.value = true },
                        navigator = navigator,
                        scope = scope,
                    )
                }
            }
        }

        val seedColor = successState.seedColor
        TachiyomiTheme(
            seedColor = seedColor.takeIf { screenModel.themeCoverBased },
        ) {
            content()
        }

        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.AddDuplicateManga ->
                AddDuplicateAnimeDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.RemoveManga ->
                RemoveAnimeDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangaCategory ->
                ChangeAnimeCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeAnimesCategoryDialog(bulkFavoriteScreenModel)

            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)

            else -> {}
        }
    }

    @Composable
    fun MangaDetailContent(
        context: Context,
        screenModel: AnimeScreenModel,
        successState: AnimeScreenModel.State.Success,
        bulkFavoriteScreenModel: BulkFavoriteScreenModel,
        showRelatedMangasScreen: () -> Unit,
        navigator: Navigator,
        scope: CoroutineScope,
    ) {
        // KMK <--
        val haptic = LocalHapticFeedback.current
        val isHttpSource = remember { successState.source is HttpSource }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getAnimeUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get anime URL" }
                }
            }
        }

        // KMK -->
        val coverRatio = remember { mutableFloatStateOf(1f) }
        val hazeState = remember { HazeState() }
        val fullCoverBackground = MaterialTheme.colorScheme.surfaceTint.blend(MaterialTheme.colorScheme.surface)
        // KMK <--

        AnimeScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            episodeSwipeStartAction = screenModel.episodeSwipeStartAction,
            episodeSwipeEndAction = screenModel.episodeSwipeEndAction,
            showNextEpisodeAirTime = screenModel.showNextEpisodeAirTime,
            alwaysUseExternalPlayer = screenModel.alwaysUseExternalPlayer,
            // AM (FILE_SIZE) -->
            showFileSize = screenModel.showFileSize,
            // <-- AM (FILE_SIZE)
            onBackClicked = navigator::pop,
            onEpisodeClicked = { episode, alt ->
                scope.launchIO {
                    if (successState.source is MergedSource &&
                        successState.source.getMergedReferenceSources(screenModel.manga).any {
                            it.isSourceForTorrents()
                        } ||
                        successState.source.isSourceForTorrents()
                    ) {
                        TorrentServerService.start()
                        TorrentServerService.wait(10)
                        TorrentServerUtils.setTrackersList()
                    }
                    val extPlayer = screenModel.alwaysUseExternalPlayer != alt
                    openEpisode(context, episode, extPlayer)
                }
            },
            onDownloadEpisode = screenModel::runEpisodeDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            // SY -->
            onWebViewClicked = {
                if (successState.mergedData == null) {
                    openAnimeInWebView(
                        navigator,
                        screenModel.manga,
                        screenModel.source,
                    )
                } else {
                    openMergedMangaWebview(
                        context,
                        navigator,
                        successState.mergedData,
                    )
                }
            }.takeIf { isHttpSource },
            // SY <--
            onWebViewLongClicked = {
                copyAnimeUrl(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueWatching = {
                scope.launchIO {
                    val extPlayer = screenModel.alwaysUseExternalPlayer
                    continueWatching(context, screenModel.getNextUnseenEpisode(), extPlayer)
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = {
                shareAnime(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = screenModel::showSetAnimeFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            changeAnimeSkipIntro = screenModel::showAnimeSkipIntroDialog.takeIf { successState.manga.favorite },
            // SY -->
            onMigrateClicked = { migrateManga(navigator, screenModel.manga!!) }.takeIf { successState.manga.favorite },
            onEditInfoClicked = screenModel::showEditAnimeInfoDialog,
            onMergedSettingsClicked = screenModel::showEditMergedSettingsDialog,
            onMergeClicked = { openSmartSearch(navigator, successState.manga) },
            onMergeWithAnotherClicked = {
                mergeWithAnother(navigator, context, successState.manga, screenModel::smartSearchMerge)
            },
            // SY <--
            onMultiBookmarkClicked = screenModel::bookmarkEpisodes,
            // AM (FILLERMARK) -->
            onMultiFillermarkClicked = screenModel::fillermarkEpisodes,
            // <-- AM (FILLERMARK)
            onMultiMarkAsSeenClicked = screenModel::markEpisodesSeen,
            onMarkPreviousAsSeenClicked = screenModel::markPreviousEpisodeSeen,
            onMultiDeleteClicked = screenModel::showDeleteEpisodeDialog,
            onEpisodeSwipe = screenModel::episodeSwipe,
            onEpisodeSelected = screenModel::toggleSelection,
            onAllEpisodeSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            // KMK -->
            getMangaState = { screenModel.getManga(initialManga = it) },
            onRelatedAnimesScreenClick = {
                if (successState.isRelatedMangasFetched == null) {
                    scope.launchIO { screenModel.fetchRelatedMangasFromSource(onDemand = true) }
                }
                showRelatedMangasScreen()
            },
            onRelatedAnimeClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    navigator.push(AnimeScreen(manga.id, true))
                }
            },
            onRelatedAnimeLongClick = {
                scope.launchIO {
                    val manga = screenModel.networkToLocalManga.getLocal(it)
                    bulkFavoriteScreenModel.addRemoveManga(manga, haptic)
                }
            },
            onCoverLoaded = {
                if (screenModel.themeCoverBased || successState.manga.favorite) screenModel.setPaletteColor(it)
            },
            coverRatio = coverRatio,
            hazeState = hazeState,
            // KMK <--
        )

        val onDismissRequest = {
            screenModel.dismissDialog()
            if (screenModel.autoOpenTrack && screenModel.isFromChangeCategory) {
                screenModel.isFromChangeCategory = false
                screenModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveAnimeToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is AnimeScreenModel.Dialog.DeleteEpisodes -> {
                DeleteEpisodesDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteEpisodes(dialog.chapters)
                    },
                )
            }

            is AnimeScreenModel.Dialog.DuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        // SY -->
                        migrateManga(navigator, dialog.duplicate, screenModel.manga!!.id)
                        // SY <--
                    },
                    // KMK -->
                    duplicate = dialog.duplicate,
                    // KMK <--
                )
            }

            AnimeScreenModel.Dialog.SettingsSheet -> EpisodeSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnseenFilterChanged = screenModel::setUnseenFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                // AM (FILLERMARK) -->
                onFillermarkedFilterChanged = screenModel::setFillermarkedFilter,
                // <-- AM (FILLERMARK)
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
            )
            AnimeScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        animeId = successState.manga.id,
                        animeTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            AnimeScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { AnimeCoverScreenModel(successState.manga.id) }
                val anime by sm.state.collectAsState()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    AnimeCoverDialog(
                        manga = anime!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(anime) { anime!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                        // KMK -->
                        modifier = Modifier
                            .hazeChild(
                                state = hazeState,
                                style = HazeStyle(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(fullCoverBackground),
                                    blurRadius = 10.dp,
                                ),
                            ),
                        // KMK <--
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is AnimeScreenModel.Dialog.SetAnimeFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            // SY -->
            is AnimeScreenModel.Dialog.EditAnimeInfo -> {
                EditAnimeDialog(
                    manga = dialog.manga,
                    // KMK -->
                    coverRatio = coverRatio,
                    // KMK <--
                    onDismissRequest = screenModel::dismissDialog,
                    onPositiveClick = screenModel::updateAnimeInfo,
                )
            }

            is AnimeScreenModel.Dialog.EditMergedSettings -> {
                EditMergedSettingsDialog(
                    mergedData = dialog.mergedData,
                    onDismissRequest = screenModel::dismissDialog,
                    onDeleteClick = screenModel::deleteMerge,
                    onPositiveClick = screenModel::updateMergeSettings,
                )
            }
            // SY <--
            AnimeScreenModel.Dialog.ChangeAnimeSkipIntro -> {
                fun updateSkipIntroLength(newLength: Long) {
                    scope.launchIO {
                        screenModel.setMangaViewerFlags.awaitSetSkipIntroLength(mangaId, newLength)
                    }
                }
                SkipIntroLengthDialog(
                    initialSkipIntroLength = if (!successState.manga.skipIntroDisable &&
                        successState.manga.skipIntroLength == 0
                    ) {
                        screenModel.gesturePreferences.defaultIntroLength().get()
                    } else {
                        successState.manga.skipIntroLength
                    },
                    onDismissRequest = onDismissRequest,
                    onValueChanged = {
                        updateSkipIntroLength(it.toLong())
                        onDismissRequest()
                    },
                )
            }
            is AnimeScreenModel.Dialog.ShowQualities -> {
                EpisodeOptionsDialogScreen.onDismissDialog = onDismissRequest
                val episodeTitle = if (dialog.manga.displayMode == Manga.EPISODE_DISPLAY_NUMBER) {
                    stringResource(
                        MR.strings.display_mode_episode,
                        formatEpisodeNumber(dialog.chapter.episodeNumber),
                    )
                } else {
                    dialog.chapter.name
                }
                NavigatorAdaptiveSheet(
                    screen = EpisodeOptionsDialogScreen(
                        useExternalDownloader = screenModel.useExternalDownloader,
                        episodeTitle = episodeTitle,
                        episodeId = dialog.chapter.id,
                        animeId = dialog.manga.id,
                        sourceId = dialog.source.id,
                    ),
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }

    private suspend fun continueWatching(
        context: Context,
        unseenChapter: Chapter?,
        useExternalPlayer: Boolean,
    ) {
        if (unseenChapter != null) openEpisode(context, unseenChapter, useExternalPlayer)
    }

    private suspend fun openEpisode(context: Context, chapter: Chapter, useExternalPlayer: Boolean) {
        withIOContext {
            MainActivity.startPlayerActivity(
                context,
                mangaId,
                chapter.id,
                useExternalPlayer,
            )
        }
    }

    private fun getAnimeUrl(manga: Manga?, source: Source?): String? {
        if (manga == null) return null

        return try {
            (source as? HttpSource)?.getAnimeUrl(manga.toSAnime())
        } catch (_: Exception) {
            null
        }
    }

    private fun openAnimeInWebView(navigator: Navigator, manga: Manga?, source: Source?) {
        getAnimeUrl(manga, source)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga?.title,
                    sourceId = source?.id,
                ),
            )
        }
    }

    private fun shareAnime(context: Context, manga: Manga?, source: Source?) {
        try {
            getAnimeUrl(manga, source)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                LibraryTab.search(query)
            }
            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: Source,
    ) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Anime URL to Clipboard
     */
    private fun copyAnimeUrl(context: Context, manga: Manga?, source: Source?) {
        if (manga == null) return
        val url = (source as? HttpSource)?.getAnimeUrl(manga.toSAnime()) ?: return
        context.copyToClipboard(url, url)
    }

    // SY -->
    /**
     * Initiates source migration for the specific manga.
     */
    private fun migrateManga(navigator: Navigator, manga: Manga, toMangaId: Long? = null) {
        // SY -->
        PreMigrationScreen.navigateToMigration(
            Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
            navigator,
            manga.id,
            toMangaId,
        )
        // SY <--
    }

    private fun openMergedMangaWebview(context: Context, navigator: Navigator, mergedAnimeData: MergedAnimeData) {
        val sourceManager: SourceManager = Injekt.get()
        val mergedManga = mergedAnimeData.manga.values.filterNot { it.source == MERGED_SOURCE_ID }
        val sources = mergedManga.map { sourceManager.getOrStub(it.source) }
        MaterialAlertDialogBuilder(context)
            .setTitle(MR.strings.action_open_in_web_view.getString(context))
            .setSingleChoiceItems(
                Array(mergedManga.size) { index -> sources[index].toString() },
                -1,
            ) { dialog, index ->
                dialog.dismiss()
                openAnimeInWebView(navigator, mergedManga[index], sources[index] as? HttpSource)
            }
            .setNegativeButton(MR.strings.action_cancel.getString(context), null)
            .show()
    }
    // SY <--

    // EXH -->
    /**
     * Called when click Merge on an entry to search for entries to merge.
     */
    private fun openSmartSearch(navigator: Navigator, manga: Manga) {
        val smartSearchConfig = SourcesScreen.SmartSearchConfig(manga.title, manga.id)

        navigator.push(SourcesScreen(smartSearchConfig))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mergeWithAnother(
        navigator: Navigator,
        context: Context,
        manga: Manga,
        smartSearchMerge: suspend (Manga, Long) -> Manga,
    ) {
        launchUI {
            try {
                val mergedManga = withNonCancellableContext {
                    smartSearchMerge(manga, smartSearchConfig?.origMangaId ?: throw IllegalStateException("smartSearchConfig is null"))
                }
                navigator.popUntil { it is SourcesScreen }
                navigator.pop()
                // KMK -->
                if (navigator.lastItem !is AnimeScreen) {
                    navigator push AnimeScreen(mergedManga.id)
                } else {
                    // KMK <--
                    navigator replace AnimeScreen(mergedManga.id)
                }
                context.toast(SYMR.strings.entry_merged)
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                context.toast(context.stringResource(SYMR.strings.failed_merge, e.message.orEmpty()))
            }
        }
    }
    // EXH <--
}
