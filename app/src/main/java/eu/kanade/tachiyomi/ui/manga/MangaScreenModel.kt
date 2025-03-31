package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.palette.graphics.Palette
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.Image
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.interactor.SmartSearchMerge
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.episodesFiltered
import eu.kanade.domain.manga.model.toDomainAnime
import eu.kanade.domain.manga.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.TrackEpisode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import eu.kanade.tachiyomi.ui.manga.RelatedAnime.Companion.isLoading
import eu.kanade.tachiyomi.ui.manga.RelatedAnime.Companion.removeDuplicates
import eu.kanade.tachiyomi.ui.manga.RelatedAnime.Companion.sorted
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.AniChartApi
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toast
import exh.source.MERGED_SOURCE_ID
import exh.util.nullIfEmpty
import exh.util.trimOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.calculateChapterGap
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.DeleteMergeById
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.interactor.UpdateMergedSettings
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergeMangaSettingsUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.floor
import androidx.compose.runtime.State as RuntimeState

class MangaScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val mangaId: Long,
    // SY -->
    /** If it is opened from Source then it will auto expand the manga description */
    private val isFromSource: Boolean,
    private val smartSearched: Boolean,
    // SY <--
    downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    // KMK -->
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    // KMK -->
    private val smartSearchMerge: SmartSearchMerge = Injekt.get(),
    // KMK <--
    private val updateMergedSettings: UpdateMergedSettings = Injekt.get(),
    private val deleteMergeById: DeleteMergeById = Injekt.get(),
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    // SY <--
    val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getAnime: GetManga = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val setMangaChapterFlags: SetMangaChapterFlags = Injekt.get(),
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val filterChaptersForDownload: FilterChaptersForDownload = Injekt.get(),
    internal val setMangaViewerFlags: SetMangaViewerFlags = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    // AM (FILE_SIZE) -->
    storagePreferences: StoragePreferences = Injekt.get(),
    // <-- AM (FILE_SIZE)
) : StateScreenModel<MangaScreenModel.State>(State.Loading) {

    private val successState: State.Success?
        get() = state.value as? State.Success

    // KMK -->
    val themeCoverBased = uiPreferences.themeCoverBased().get()
    // KMK <--

    val manga: Manga?
        get() = successState?.manga

    val source: Source?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val processedEpisodes: List<ChapterList.Item>?
        get() = successState?.processedEpisodes

    val episodeSwipeStartAction = libraryPreferences.swipeEpisodeEndAction().get()
    val episodeSwipeEndAction = libraryPreferences.swipeEpisodeStartAction().get()
    private var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    val showNextEpisodeAirTime = trackPreferences.showNextEpisodeAiringTime().get()
    val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer().get()
    val useExternalDownloader = downloadPreferences.useExternalDownloader().get()

    val isUpdateIntervalEnabled =
        LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in libraryPreferences.autoUpdateAnimeRestrictions().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    // SY -->
    private data class CombineState(
        val manga: Manga,
        val chapters: List<Chapter>,
        val mergedData: MergedAnimeData? = null,
    ) {
        constructor(pair: Pair<Manga, List<Chapter>>) :
            this(pair.first, pair.second)
    }

    // SY <--
    internal var isFromChangeCategory: Boolean = false

    internal val autoOpenTrack: Boolean
        get() = successState?.trackingAvailable == true && trackPreferences.trackOnAddingToLibrary().get()

    // AM (FILE_SIZE) -->
    val showFileSize = storagePreferences.showEpisodeFileSize().get()
    // <-- AM (FILE_SIZE)

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            getMangaWithChapters.subscribe(mangaId).distinctUntilChanged()
                // SY -->
                .combine(
                    getMergedChaptersByMangaId.subscribe(mangaId, true)
                        .distinctUntilChanged(),
                ) { (manga, chapters), mergedChapters ->
                    if (manga.source == MERGED_SOURCE_ID) {
                        manga to mergedChapters
                    } else {
                        manga to chapters
                    }
                }
                .map { CombineState(it) }
                .combine(
                    combine(
                        getMergedMangaById.subscribe(mangaId)
                            .distinctUntilChanged(),
                        getMergedReferencesById.subscribe(mangaId)
                            .distinctUntilChanged(),
                    ) { manga, references ->
                        if (manga.isNotEmpty()) {
                            MergedAnimeData(
                                references,
                                manga.associateBy { it.id },
                                references.map { it.animeSourceId }.distinct()
                                    .map { sourceManager.getOrStub(it) },
                            )
                        } else {
                            null
                        }
                    },
                ) { state, mergedData ->
                    state.copy(mergedData = mergedData)
                }
                .combine(downloadCache.changes) { state, _ -> state }
                .combine(downloadManager.queueState) { state, _ -> state }
                // SY <--
                .flowWithLifecycle(lifecycle)
                .collectLatest { (manga, chapters /* SY --> */, mergedData /* SY <-- */) ->
                    val chapterItems = chapters.toEpisodeListItems(manga /* SY --> */, mergedData /* SY <-- */)
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            episodes = chapterItems,
                            // SY -->
                            mergedData = mergedData,
                            // SY <--
                        )
                    }
                }
        }

        observeDownloads()

        screenModelScope.launchIO {
            val anime = getMangaWithChapters.awaitManga(mangaId)

            // SY -->
            val mergedData = getMergedReferencesById.await(mangaId).takeIf { it.isNotEmpty() }?.let { references ->
                MergedAnimeData(
                    references,
                    getMergedMangaById.await(mangaId).associateBy { it.id },
                    references.map { it.animeSourceId }.distinct()
                        .map { sourceManager.getOrStub(it) },
                )
            }
            val episodes = (
                if (anime.source ==
                    MERGED_SOURCE_ID
                ) {
                    getMergedChaptersByMangaId.await(mangaId)
                } else {
                    getMangaWithChapters.awaitChapters(mangaId)
                }
                )
                .toEpisodeListItems(anime, mergedData)
            // SY <--

            if (!anime.favorite) {
                setMangaDefaultChapterFlags.await(anime)
            }

            val needRefreshInfo = !anime.initialized
            val needRefreshEpisode = episodes.isEmpty()

            val animeSource = Injekt.get<SourceManager>().getOrStub(anime.source)
            // --> (Torrent)
            if (animeSource is MergedSource &&
                animeSource.getMergedReferenceSources(anime).any {
                    it.isSourceForTorrents()
                } ||
                animeSource.isSourceForTorrents()
            ) {
                TorrentServerService.start()
                TorrentServerService.wait(10)
                TorrentServerUtils.setTrackersList()
            }
            // <-- (Torrent)

            // Show what we have earlier
            mutableState.update {
                State.Success(
                    manga = anime,
                    source = animeSource,
                    isFromSource = isFromSource,
                    episodes = episodes,
                    isRefreshingData = needRefreshInfo || needRefreshEpisode,
                    dialog = null,
                    // SY -->
                    showMergeInOverflow = uiPreferences.mergeInOverflow().get(),
                    showMergeWithAnother = smartSearched,
                    mergedData = mergedData,
                    // SY <--
                )
            }
            // Start observe tracking since it only needs animeId
            observeTrackers()

            // Fetch info-episodes when needed
            if (screenModelScope.isActive) {
                val fetchFromSourceTasks = listOf(
                    async { if (needRefreshInfo) fetchAnimeFromSource() },
                    async { if (needRefreshEpisode) fetchEpisodesFromSource() },
                )
                fetchFromSourceTasks.awaitAll()
                // KMK -->
                launch { fetchRelatedMangasFromSource() }
                // KMK <--
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    // KMK -->
    /**
     * Get the color of the manga cover by loading cover with ImageRequest directly from network.
     */
    fun setPaletteColor(model: Any) {
        if (model is ImageRequest && model.defined.sizeResolver != null) return

        val imageRequestBuilder = if (model is ImageRequest) {
            model.newBuilder()
        } else {
            ImageRequest.Builder(context).data(model)
        }
            .allowHardware(false)

        val generatePalette: (Image) -> Unit = { image ->
            val bitmap = image.asDrawable(context.resources).getBitmapOrNull()
            if (bitmap != null) {
                Palette.from(bitmap).generate {
                    screenModelScope.launchIO {
                        if (it == null) return@launchIO
                        val mangaCover = when (model) {
                            is Manga -> model.asMangaCover()
                            is MangaCover -> model
                            else -> return@launchIO
                        }
                        if (mangaCover.isMangaFavorite) {
                            it.dominantSwatch?.let { swatch ->
                                mangaCover.dominantCoverColors = swatch.rgb to swatch.titleTextColor
                            }
                        }
                        val vibrantColor = it.getBestColor() ?: return@launchIO
                        mangaCover.vibrantCoverColor = vibrantColor
                        updateSuccessState {
                            it.copy(seedColor = Color(vibrantColor))
                        }
                    }
                }
            }
        }

        context.imageLoader.enqueue(
            imageRequestBuilder
                .target(
                    onSuccess = generatePalette,
                    onError = {
                        // TODO: handle error
                        // val file = coverCache.getCoverFile(manga!!)
                        // if (file.exists()) {
                        //     file.delete()
                        //     setPaletteColor()
                        // }
                    },
                )
                .build(),
        )
    }
    // KMK <--

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            val fetchFromSourceTasks = listOf(
                async { fetchAnimeFromSource(manualFetch) },
                async { fetchEpisodesFromSource(manualFetch) },
            )
            fetchFromSourceTasks.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
            successState?.let { updateAiringTime(it.manga, it.trackItems, manualFetch) }
        }
    }

    // Anime info - start

    /**
     * Fetch manga information from source.
     */
    private suspend fun fetchAnimeFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                val networkAnime = state.source.getAnimeDetails(state.manga.toSAnime())
                updateManga.awaitUpdateFromSource(state.manga, networkAnime, manualFetch)
            }
        } catch (e: Throwable) {
            // Ignore early hints "errors" that aren't handled by OkHttp
            if (e is HttpException && e.code == 103) return

            logcat(LogPriority.ERROR, e)
            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = with(context) { e.formattedMessage })
            }
        }
    }

    // SY -->
    fun updateAnimeInfo(
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) {
        val state = successState ?: return
        var anime = state.manga
        if (state.manga.isLocal()) {
            val newTitle = if (title.isNullOrBlank()) anime.url else title.trim()
            val newAuthor = author?.trimOrNull()
            val newArtist = artist?.trimOrNull()
            val newDesc = description?.trimOrNull()
            anime = anime.copy(
                ogTitle = newTitle,
                ogAuthor = author?.trimOrNull(),
                ogArtist = artist?.trimOrNull(),
                ogDescription = description?.trimOrNull(),
                ogGenre = tags?.nullIfEmpty(),
                ogStatus = status ?: 0,
                lastUpdate = anime.lastUpdate + 1,
            )
            (sourceManager.get(LocalSource.ID) as LocalSource).updateAnimeInfo(
                anime.toSAnime(),
            )
            screenModelScope.launchNonCancellable {
                updateManga.await(
                    MangaUpdate(
                        anime.id,
                        title = newTitle,
                        author = newAuthor,
                        artist = newArtist,
                        description = newDesc,
                        genre = tags,
                        status = status,
                    ),
                )
            }
        } else {
            val genre = if (!tags.isNullOrEmpty() && tags != state.manga.ogGenre) {
                tags
            } else {
                null
            }
            setCustomMangaInfo.set(
                CustomMangaInfo(
                    state.manga.id,
                    title?.trimOrNull(),
                    author?.trimOrNull(),
                    artist?.trimOrNull(),
                    thumbnailUrl?.trimOrNull(),
                    description?.trimOrNull(),
                    genre,
                    status.takeUnless { it == state.manga.ogStatus },
                ),
            )
            anime = anime.copy(lastUpdate = anime.lastUpdate + 1)
        }

        updateSuccessState { successState ->
            successState.copy(manga = anime)
        }
    }
    // SY <--

    // KMK -->
    @Composable
    fun getManga(initialManga: Manga): RuntimeState<Manga> {
        return produceState(initialValue = initialManga) {
            getAnime.subscribe(initialManga.url, initialManga.source)
                .flowWithLifecycle(lifecycle)
                .collectLatest { manga ->
                    value = manga
                        // KMK -->
                        ?: initialManga
                    // KMK <--
                }
        }
    }

    suspend fun smartSearchMerge(manga: Manga, originalMangaId: Long): Manga {
        return smartSearchMerge.smartSearchMerge(manga, originalMangaId)
    }
    // KMK <--

    fun updateMergeSettings(mergedMangaReferences: List<MergedMangaReference>) {
        screenModelScope.launchNonCancellable {
            if (mergedMangaReferences.isNotEmpty()) {
                updateMergedSettings.awaitAll(
                    mergedMangaReferences.map {
                        MergeMangaSettingsUpdate(
                            id = it.id,
                            isInfoAnime = it.isInfoAnime,
                            getEpisodeUpdates = it.getEpisodeUpdates,
                            episodePriority = it.episodePriority,
                            downloadEpisodes = it.downloadEpisodes,
                            episodeSortMode = it.episodeSortMode,
                        )
                    },
                )
            }
        }
    }

    fun deleteMerge(reference: MergedMangaReference) {
        screenModelScope.launchNonCancellable {
            deleteMergeById.await(reference.id)
        }
    }
    // SY <--

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                screenModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.delete_downloads_for_anime),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        screenModelScope.launchIO {
            val anime = state.manga

            if (isFavorited) {
                // Remove from library
                if (updateManga.awaitUpdateFavorite(anime.id, false)) {
                    // Remove covers and update last modified in db
                    if (anime.removeCovers() != anime) {
                        updateManga.awaitUpdateCoverLastModified(anime.id)
                    }
                    withUIContext { onRemoved() }
                }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryManga.await(anime).getOrNull(0)
                    if (duplicate != null) {
                        updateSuccessState {
                            it.copy(
                                dialog = Dialog.DuplicateAnime(anime, duplicate),
                            )
                        }
                        return@launchIO
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                when {
                    // Default category set
                    defaultCategory != null -> {
                        val result = updateManga.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCategory(defaultCategory)
                    }

                    // Automatic 'Default' or no categories
                    defaultCategoryId == 0L || categories.isEmpty() -> {
                        val result = updateManga.awaitUpdateFavorite(anime.id, true)
                        if (!result) return@launchIO
                        moveAnimeToCategory(null)
                    }

                    // Choose a category
                    else -> {
                        isFromChangeCategory = true
                        showChangeCategoryDialog()
                    }
                }

                // Finally match with enhanced tracking when available
                addTracks.bindEnhancedTrackers(anime, state.source)
                if (autoOpenTrack) {
                    showTrackDialog()
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val anime = successState?.manga ?: return
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getAnimeCategoryIds(anime)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = anime,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    fun showSetAnimeFetchIntervalDialog() {
        val anime = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetAnimeFetchInterval(anime))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        screenModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedAnime = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedAnime) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val anime = successState?.manga ?: return false
        return downloadManager.getDownloadCount(anime) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        // SY -->
        if (state.source is MergedSource) {
            val mergedManga = state.mergedData?.manga?.map { it.value to sourceManager.getOrStub(it.value.source) }
            mergedManga?.forEach { (manga, source) ->
                downloadManager.deleteAnime(manga, source)
            }
        } else {
            /* SY <-- */ downloadManager.deleteAnime(state.manga, state.source)
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getAnimeCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveAnimeToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveAnimeToCategory(categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveAnimeToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveAnimeToCategory(categoryIds)
    }

    private fun moveAnimeToCategory(categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveAnimeToCategory(category: Category?) {
        moveAnimeToCategories(listOfNotNull(category))
    }

    // Anime info - end

    // Episodes list - start

    private fun observeDownloads() {
        // SY -->
        val isMergedSource = source is MergedSource
        val mergedIds = if (isMergedSource) successState?.mergedData?.manga?.keys.orEmpty() else emptySet()
        // SY <--
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter {
                    /* SY --> */ if (isMergedSource) {
                        it.manga.id in mergedIds
                    } else {
                        /* SY <-- */ it.manga.id ==
                            successState?.manga?.id
                    }
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter {
                    /* SY --> */ if (isMergedSource) {
                        it.manga.id in mergedIds
                    } else {
                        /* SY <-- */ it.manga.id ==
                            successState?.manga?.id
                    }
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: Download) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.episodes.indexOfFirst { it.id == download.chapter.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newEpisodes = successState.episodes.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    private fun List<Chapter>.toEpisodeListItems(
        anime: Manga,
        // SY -->
        mergedData: MergedAnimeData?,
        // SY <--
    ): List<ChapterList.Item> {
        val isLocal = anime.isLocal()
        return map { episode ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(episode.id)
            }

            // SY -->
            val manga = mergedData?.manga?.get(episode.animeId) ?: anime
            val source = mergedData?.sources?.find { manga.source == it.id }?.takeIf { mergedData.sources.size > 2 }
            // SY <--
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    anime.title,
                    anime.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> Download.State.DOWNLOADED
                else -> Download.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = episode,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = episode.id in selectedEpisodeIds,
                // SY -->
                sourceName = source?.getNameForAnimeInfo(),
                // SY <--
            )
        }
    }

    /**
     * Requests an updated list of episodes from the source.
     */
    private suspend fun fetchEpisodesFromSource(manualFetch: Boolean = false) {
        val state = successState ?: return
        try {
            withIOContext {
                // SY -->
                if (state.source !is MergedSource) {
                    // SY <--
                    val episodes = state.source.getEpisodeList(state.manga.toSAnime())

                    val newEpisodes = syncChaptersWithSource.await(
                        episodes,
                        state.manga,
                        state.source,
                        manualFetch,
                    )

                    if (manualFetch) {
                        downloadNewEpisodes(newEpisodes)
                    }
                    // SY -->
                } else {
                    state.source.fetchEpisodesForMergedAnime(state.manga, manualFetch)
                }
                // SY <--
            }
        } catch (e: Throwable) {
            val message = if (e is NoResultsException) {
                context.stringResource(MR.strings.no_episodes_error)
            } else {
                logcat(LogPriority.ERROR, e)
                with(context) { e.formattedMessage }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
            val newManga = mangaRepository.getMangaById(mangaId)
            updateSuccessState { it.copy(manga = newManga, isRefreshingData = false) }
        }
    }

    // KMK -->
    /**
     * Set the fetching related mangas status.
     * @param state
     * - false: started & fetching
     * - true: finished
     */
    private fun setRelatedMangasFetchedStatus(state: Boolean) {
        updateSuccessState { it.copy(isRelatedMangasFetched = state) }
    }

    /**
     * Requests an list of related mangas from the source.
     */
    internal suspend fun fetchRelatedMangasFromSource(onDemand: Boolean = false, onFinish: (() -> Unit)? = null) {
        val expandRelatedMangas = uiPreferences.expandRelatedAnimes().get()
        if (!onDemand && !expandRelatedMangas || manga?.source == MERGED_SOURCE_ID) return

        // start fetching related mangas
        setRelatedMangasFetchedStatus(false)

        fun exceptionHandler(e: Throwable) {
            logcat(LogPriority.ERROR, e)
            val message = with(context) { e.formattedMessage }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
        val state = successState ?: return
        val relatedMangasEnabled = sourcePreferences.relatedAnimes().get()

        try {
            if (state.source !is StubSource && relatedMangasEnabled) {
                state.source.getRelatedAnimeList(state.manga.toSAnime(), { e -> exceptionHandler(e) }) { pair, _ ->
                    /* Push found related mangas into collection */
                    val relatedAnime = RelatedAnime.Success.fromPair(pair) { mangaList ->
                        mangaList.map {
                            // KMK -->
                            it.toDomainAnime(state.source.id)
                            // KMK <--
                        }
                    }

                    updateSuccessState { successState ->
                        val relatedMangaCollection =
                            successState.relatedAnimeCollection
                                ?.toMutableStateList()
                                ?.apply { add(relatedAnime) }
                                ?: listOf(relatedAnime)
                        successState.copy(relatedAnimeCollection = relatedMangaCollection)
                    }
                }
            }
        } catch (e: Exception) {
            exceptionHandler(e)
        } finally {
            if (onFinish != null) {
                onFinish()
            } else {
                setRelatedMangasFetchedStatus(true)
            }
        }
    }
    // KMK <--

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    fun episodeSwipe(episodeItem: ChapterList.Item, swipeAction: LibraryPreferences.EpisodeSwipeAction) {
        screenModelScope.launch {
            executeEpisodeSwipeAction(episodeItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    private fun executeEpisodeSwipeAction(
        episodeItem: ChapterList.Item,
        swipeAction: LibraryPreferences.EpisodeSwipeAction,
    ) {
        val episode = episodeItem.chapter
        when (swipeAction) {
            LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> {
                markEpisodesSeen(listOf(episode), !episode.seen)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> {
                bookmarkEpisodes(listOf(episode), !episode.bookmark)
            }
            // AM (FILLERMARK) -->
            LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> {
                fillermarkEpisodes(listOf(episode), !episode.fillermark)
            }
            // <-- AM (FILLERMARK)
            LibraryPreferences.EpisodeSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (episodeItem.downloadState) {
                    Download.State.ERROR,
                    Download.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    Download.State.QUEUE,
                    Download.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runEpisodeDownloadActions(
                    items = listOf(episodeItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.EpisodeSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    /**
     * Returns the next unseen episode or null if everything is seen.
     */
    fun getNextUnseenEpisode(): Chapter? {
        val successState = successState ?: return null
        return successState.episodes.getNextUnread(successState.manga)
    }

    private fun getUnseenEpisodes(): List<Chapter> {
        return successState?.processedEpisodes
            ?.filter { (episode, dlStatus) -> !episode.seen && dlStatus == Download.State.NOT_DOWNLOADED }
            ?.map { it.chapter }
            ?.toList()
            ?: emptyList()
    }

    private fun getUnseenEpisodesSorted(): List<Chapter> {
        val anime = successState?.manga ?: return emptyList()
        val episodes = getUnseenEpisodes().sortedWith(getChapterSort(anime))
        return if (anime.sortDescending()) episodes.reversed() else episodes
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
        video: Video? = null,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val episodeId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(episodeId)
            } else {
                downloadEpisodes(chapters, false, video)
            }
            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(MR.strings.snack_add_to_anime_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    toggleFavorite()
                }
            }
        }
    }

    fun runEpisodeDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(items.map { it.chapter }, false)
                if (items.any { it.downloadState == Download.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val episode = items.singleOrNull()?.chapter ?: return
                startDownload(listOf(episode), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val episodeId = items.singleOrNull()?.id ?: return
                cancelDownload(episodeId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteEpisodes(items.map { it.chapter })
            }
            ChapterDownloadAction.SHOW_QUALITIES -> {
                val episode = items.singleOrNull()?.chapter ?: return
                showQualitiesDialog(episode)
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val episodesToDownload = when (action) {
            DownloadAction.NEXT_1_EPISODE -> getUnseenEpisodesSorted().take(1)
            DownloadAction.NEXT_5_EPISODES -> getUnseenEpisodesSorted().take(5)
            DownloadAction.NEXT_10_EPISODES -> getUnseenEpisodesSorted().take(10)
            DownloadAction.NEXT_25_EPISODES -> getUnseenEpisodesSorted().take(25)

            DownloadAction.UNSEEN_EPISODES -> getUnseenEpisodes()
        }
        if (episodesToDownload.isNotEmpty()) {
            startDownload(episodesToDownload, false)
        }
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    fun markPreviousEpisodeSeen(pointer: Chapter) {
        val anime = successState?.manga ?: return
        val episodes = processedEpisodes.orEmpty().map { it.chapter }.toList()
        val prevEpisodes = if (anime.sortDescending()) episodes.asReversed() else episodes
        val pointerPos = prevEpisodes.indexOf(pointer)
        if (pointerPos != -1) markEpisodesSeen(prevEpisodes.take(pointerPos), true)
    }

    /**
     * Mark the selected episode list as seen/unseen.
     * @param chapters the list of selected episodes.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markEpisodesSeen(chapters: List<Chapter>, seen: Boolean) {
        toggleAllSelection(false)
        screenModelScope.launchIO {
            setReadStatus.await(
                seen = seen,
                chapters = chapters.toTypedArray(),
            )

            if (!seen || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            val tracks = getTracks.await(mangaId)
            val maxEpisodeNumber = chapters.maxOf { it.episodeNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxEpisodeNumber > track.lastEpisodeSeen }

            if (!shouldPromptTrackingUpdate) return@launchIO

            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackEpisode.await(context, mangaId, maxEpisodeNumber)
                withUIContext {
                    context.toast(
                        context.stringResource(MR.strings.trackers_updated_summary_anime, maxEpisodeNumber.toInt()),
                    )
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.confirm_tracker_update_anime, maxEpisodeNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackEpisode.await(context, mangaId, maxEpisodeNumber)
            }
        }
    }

    /**
     * Downloads the given list of episodes with the manager.
     * @param chapters the list of episodes to download.
     */
    private fun downloadEpisodes(
        chapters: List<Chapter>,
        alt: Boolean = false,
        video: Video? = null,
    ) {
        // SY -->
        val state = successState ?: return
        if (state.source is MergedSource) {
            chapters.groupBy { it.animeId }.forEach { map ->
                val manga = state.mergedData?.manga?.get(map.key) ?: return@forEach
                downloadManager.downloadEpisodes(manga, map.value)
            }
        } else {
            // SY <--
            val anime = successState?.manga ?: return
            downloadManager.downloadEpisodes(anime, chapters, true, alt, video)
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param chapters the list of episodes to bookmark.
     */
    fun bookmarkEpisodes(chapters: List<Chapter>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    // AM (FILLERMARK) -->
    /**
     * Fillermarks the given list of episodes.
     * @param chapters the list of episodes to fillermark.
     */
    fun fillermarkEpisodes(chapters: List<Chapter>, fillermarked: Boolean) {
        screenModelScope.launchIO {
            chapters
                .filterNot { it.fillermark == fillermarked }
                .map { ChapterUpdate(id = it.id, fillermark = fillermarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }
    // <-- AM (FILLERMARK)

    /**
     * Deletes the given list of episode.
     *
     * @param chapters the list of episodes to delete.
     */
    fun deleteEpisodes(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteEpisodes(
                        chapters,
                        state.manga,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewEpisodes(chapters: List<Chapter>) {
        screenModelScope.launchNonCancellable {
            val anime = successState?.manga ?: return@launchNonCancellable
            val episodesToDownload = filterChaptersForDownload.await(anime, chapters)

            if (episodesToDownload.isNotEmpty()) {
                downloadEpisodes(episodesToDownload)
            }
        }
    }

    /**
     * Sets the seen filter and requests an UI update.
     * @param state whether to display only unseen episodes or all episodes.
     */
    fun setUnseenFilter(state: TriState) {
        val anime = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Manga.EPISODE_SHOW_SEEN
        }
        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(anime, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded episodes or all episodes.
     */
    fun setDownloadedFilter(state: TriState) {
        val anime = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.EPISODE_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked episodes or all episodes.
     */
    fun setBookmarkedFilter(state: TriState) {
        val anime = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.EPISODE_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    // AM (FILLERMARK) -->
    /**
     * Sets the fillermark filter and requests an UI update.
     * @param state whether to display only fillermarked episodes or all episodes.
     */
    fun setFillermarkedFilter(state: TriState) {
        val anime = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.EPISODE_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Manga.EPISODE_SHOW_NOT_FILLERMARKED
        }

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetFillermarkFilter(anime, flag)
        }
    }
    // <-- AM (FILLERMARK)

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val anime = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val anime = successState?.manga ?: return

        screenModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.manga ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setEpisodeSettingsDefault(anime)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.episode_settings_updated),
            )
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newEpisodes = successState.processedEpisodes.toMutableList().apply {
                val selectedIndex = successState.processedEpisodes.indexOfFirst { it.id == item.chapter.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedEpisodeIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    // Episodes list - end

    // Track sheet - start

    private fun observeTrackers() {
        val anime = successState?.manga ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter {
                    (it as? EnhancedTracker)?.accept(source!!) ?: true
                }
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = animeTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks.size to supportedTrackers.isNotEmpty()
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                loggedInTrackers
                    .map { service ->
                        TrackItem(
                            animeTracks.find {
                                it.trackerId == service.id
                            },
                            service,
                        )
                    }
            }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateAiringTime(anime, trackItems, manualFetch = false)
                }
        }
    }

    private suspend fun updateAiringTime(
        manga: Manga,
        trackItems: List<TrackItem>,
        manualFetch: Boolean,
    ) {
        val airingEpisodeData = AniChartApi().loadAiringTime(manga, trackItems, manualFetch)
        setMangaViewerFlags.awaitSetNextEpisodeAiring(manga.id, airingEpisodeData)
        updateSuccessState { it.copy(nextAiringEpisode = airingEpisodeData) }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteEpisodes(val chapters: List<Chapter>) : Dialog
        data class DuplicateAnime(val manga: Manga, val duplicate: Manga) : Dialog
        data class SetAnimeFetchInterval(val manga: Manga) : Dialog
        data class ShowQualities(val chapter: Chapter, val manga: Manga, val source: Source) : Dialog

        // SY -->
        data class EditAnimeInfo(val manga: Manga) : Dialog
        data class EditMergedSettings(val mergedData: MergedAnimeData) : Dialog
        // SY <--

        data object ChangeAnimeSkipIntro : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteEpisodeDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteEpisodes(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    // SY -->
    fun showEditAnimeInfoDialog() {
        mutableState.update { state ->
            when (state) {
                State.Loading -> state
                is State.Success -> {
                    state.copy(dialog = Dialog.EditAnimeInfo(state.manga))
                }
            }
        }
    }

    fun showEditMergedSettingsDialog() {
        val mergedData = successState?.mergedData ?: return
        mutableState.update { state ->
            when (state) {
                State.Loading -> state
                is State.Success -> {
                    state.copy(dialog = Dialog.EditMergedSettings(mergedData))
                }
            }
        }
    }
    // SY <--

    fun showAnimeSkipIntroDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ChangeAnimeSkipIntro) }
    }

    private fun showQualitiesDialog(chapter: Chapter) {
        updateSuccessState { it.copy(dialog = Dialog.ShowQualities(chapter, it.manga, it.source)) }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: Source,
            val isFromSource: Boolean,
            val episodes: List<ChapterList.Item>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val trackItems: List<TrackItem> = emptyList(),
            val nextAiringEpisode: Pair<Int, Long> = Pair(
                manga.nextEpisodeToAir,
                manga.nextEpisodeAiringAt,
            ),

            // SY -->
            val mergedData: MergedAnimeData?,
            val showMergeInOverflow: Boolean,
            val showMergeWithAnother: Boolean,
            // SY <--
            // KMK -->
            /**
             * status of fetching related mangas
             * - null: not started
             * - false: started & fetching
             * - true: finished
             */
            val isRelatedMangasFetched: Boolean? = null,
            /**
             * a list of <keyword, related mangas>
             */
            val relatedAnimeCollection: List<RelatedAnime>? = null,
            val seedColor: Color? = manga.asMangaCover().vibrantCoverColor?.let { Color(it) },
            // KMK <--
        ) : State {
            // KMK -->
            /**
             * a value of null will be treated as still loading, so if all searching were failed and won't update
             * 'relatedAnimeCollection` then we should return empty list
             */
            val relatedAnimesSorted = relatedAnimeCollection
                ?.sorted(manga)
                ?.removeDuplicates(manga)
                ?.filter { it.isVisible() }
                ?.isLoading(isRelatedMangasFetched)
                ?: if (isRelatedMangasFetched == true) emptyList() else null
            // KMK <--

            val processedEpisodes by lazy {
                episodes.applyFilters(manga).toList()
                    // KMK -->
                    // safe-guard some edge-cases where episodes are duplicated some how on a merged entry
                    .distinctBy { it.id }
                // KMK <--
            }

            val chapterListItems by lazy {
                processedEpisodes.insertSeparators { before, after ->
                    val (lowerEpisode, higherEpisode) = if (manga.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherEpisode == null) return@insertSeparators null

                    if (lowerEpisode == null) {
                        floor(higherEpisode.chapter.episodeNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateChapterGap(higherEpisode.chapter, lowerEpisode.chapter)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            ChapterList.MissingCount(
                                id = "${lowerEpisode?.id}-${higherEpisode.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val trackingAvailable: Boolean
                get() = trackItems.isNotEmpty()

            val airingEpisodeNumber: Double
                get() = nextAiringEpisode.first.toDouble()

            val airingTime: Long
                get() = nextAiringEpisode.second.times(1000L).minus(
                    Calendar.getInstance().timeInMillis,
                )

            val filterActive: Boolean
                get() = manga.episodesFiltered()

            /**
             * Applies the view filters to the list of episodes obtained from the database.
             * @return an observable of the list of episodes filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalAnime = manga.isLocal()
                val unseenFilter = manga.unseenFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                // AM (FILLERMARK) -->
                val fillermarkedFilter = manga.fillermarkedFilter
                // <-- AM (FILLERMARK)
                return asSequence()
                    .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
                    .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
                    // AM (FILLERMARK) -->
                    .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
                    // <-- AM (FILLERMARK)
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
                    .sortedWith { (episode1), (episode2) ->
                        getChapterSort(manga).invoke(
                            episode1,
                            episode2,
                        )
                    }
            }
        }
    }
}

// SY -->
data class MergedAnimeData(
    val references: List<MergedMangaReference>,
    val manga: Map<Long, Manga>,
    val sources: List<Source>,
)
// SY <--

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: Download.State,
        val downloadProgress: Int,
        // AM (FILE_SIZE) -->
        var fileSize: Long? = null,
        // <-- AM (FILE_SIZE)
        val selected: Boolean = false,
        // SY -->
        val sourceName: String?,
        // SY <--
    ) : ChapterList() {
        val id = chapter.id
        val isDownloaded = downloadState == Download.State.DOWNLOADED
    }
}

// KMK -->
sealed interface RelatedAnime {
    data object Loading : RelatedAnime

    data class Success(
        val keyword: String,
        val mangaList: List<Manga>,
    ) : RelatedAnime {
        val isEmpty: Boolean
            get() = mangaList.isEmpty()

        companion object {
            suspend fun fromPair(
                pair: Pair<String, List<SManga>>,
                toManga: suspend (mangaList: List<SManga>) -> List<Manga>,
            ) = Success(pair.first, toManga(pair.second))
        }
    }

    fun isVisible(): Boolean {
        return this is Loading || (this is Success && !this.isEmpty)
    }

    companion object {
        internal fun List<RelatedAnime>.sorted(manga: Manga): List<RelatedAnime> {
            val success = filterIsInstance<Success>()
            val loading = filterIsInstance<Loading>()
            val title = manga.title.lowercase()
            val ogTitle = manga.ogTitle.lowercase()
            return success.filter { it.keyword.isEmpty() } +
                success.filter { it.keyword.lowercase() == title } +
                success.filter { it.keyword.lowercase() == ogTitle && ogTitle != title } +
                success.filter { it.keyword.isNotEmpty() && it.keyword.lowercase() !in listOf(title, ogTitle) }
                    .sortedByDescending { it.keyword.length }
                    .sortedBy { it.mangaList.size } +
                loading
        }

        internal fun List<RelatedAnime>.removeDuplicates(manga: Manga): List<RelatedAnime> {
            val mangaHashes = HashSet<Int>().apply { add(manga.url.hashCode()) }

            return map { relatedManga ->
                if (relatedManga is Success) {
                    val stripedList = relatedManga.mangaList.mapNotNull {
                        if (!mangaHashes.contains(it.url.hashCode())) {
                            mangaHashes.add(it.url.hashCode())
                            it
                        } else {
                            null
                        }
                    }
                    Success(
                        relatedManga.keyword,
                        stripedList,
                    )
                } else {
                    relatedManga
                }
            }
        }

        internal fun List<RelatedAnime>.isLoading(isRelatedMangaFetched: Boolean?): List<RelatedAnime> {
            return if (isRelatedMangaFetched == false) this + listOf(Loading) else this
        }
    }
}
// KMK <--
