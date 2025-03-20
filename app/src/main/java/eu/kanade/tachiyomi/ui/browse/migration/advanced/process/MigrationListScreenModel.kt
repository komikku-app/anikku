package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.content.Context
import android.widget.Toast
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.getNameForAnimeInfo
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.MigrationType
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingAnime.SearchResult
import eu.kanade.tachiyomi.util.system.toast
import exh.smartsearch.SmartSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetMergedReferencesById
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.history.interactor.GetHistoryByAnimeId
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.DeleteTrack
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.sy.SYMR
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicInteger

class MigrationListScreenModel(
    private val config: MigrationProcedureConfig,
    private val preferences: UnsortedPreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getMergedReferencesById: GetMergedReferencesById = Injekt.get(),
    private val getHistoryByAnimeId: GetHistoryByAnimeId = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val deleteTrack: DeleteTrack = Injekt.get(),
) : ScreenModel {

    private val smartSearchEngine = SmartSearchEngine(config.extraSearchParams)

    val migratingItems = MutableStateFlow<ImmutableList<MigratingAnime>?>(null)
    val migrationDone = MutableStateFlow(false)
    val finishedCount = MutableStateFlow(0)

    val manualMigrations = MutableStateFlow(0)

    var hideNotFound = preferences.hideNotFoundMigration().get()
    private var showOnlyUpdates = preferences.showOnlyUpdatesMigration().get()
    private var useSourceWithMost = preferences.useSourceWithMost().get()
    private var useSmartSearch = preferences.smartMigration().get()

    val navigateOut = MutableSharedFlow<Unit>()

    val dialog = MutableStateFlow<Dialog?>(null)

    val migratingProgress = MutableStateFlow(Float.MAX_VALUE)

    private var migrateJob: Job? = null

    init {
        screenModelScope.launchIO {
            val mangaIds = when (val migration = config.migration) {
                is MigrationType.MangaList -> {
                    migration.mangaIds
                }
                is MigrationType.MangaSingle -> listOf(migration.fromMangaId)
            }
            runMigrations(
                mangaIds
                    .map {
                        async {
                            val manga = getAnime.await(it) ?: return@async null
                            MigratingAnime(
                                manga = manga,
                                episodeInfo = getChapterInfo(it),
                                sourcesString = sourceManager.getOrStub(manga.source).getNameForAnimeInfo(),
                                parentContext = screenModelScope.coroutineContext,
                            )
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .also {
                        migratingItems.value = it.toImmutableList()
                    },
            )
        }
    }

    suspend fun getManga(result: SearchResult.Result) = getManga(result.id)
    suspend fun getManga(id: Long) = getAnime.await(id)
    suspend fun getChapterInfo(result: SearchResult.Result) = getChapterInfo(result.id)
    private suspend fun getChapterInfo(id: Long) = getEpisodesByAnimeId.await(id).let { chapters ->
        MigratingAnime.EpisodeInfo(
            latestChapter = chapters.maxOfOrNull { it.episodeNumber },
            chapterCount = chapters.size,
        )
    }
    fun getSourceName(manga: Anime) = sourceManager.getOrStub(manga.source).getNameForAnimeInfo()

    fun getMigrationSources() = preferences.migrationSources().get().split("/").mapNotNull {
        val value = it.toLongOrNull() ?: return@mapNotNull null
        sourceManager.get(value) as? CatalogueSource
    }

    private suspend fun runMigrations(mangas: List<MigratingAnime>) {
        // KMK: finishedCount.value = mangas.size

        val sources = getMigrationSources()
        for (manga in mangas) {
            if (!currentCoroutineContext().isActive) {
                break
            }
            // in case it was removed
            when (val migration = config.migration) {
                is MigrationType.MangaList -> if (manga.manga.id !in migration.mangaIds) {
                    continue
                }
                else -> Unit
            }

            if (manga.searchResult.value == SearchResult.Searching && manga.migrationScope.isActive) {
                val mangaObj = manga.manga
                val mangaSource = sourceManager.getOrStub(mangaObj.source)

                val result = try {
                    // KMK -->
                    manga.searchingJob = manga.migrationScope.async {
                        // KMK <--
                        val validSources = if (sources.size == 1) {
                            sources
                        } else {
                            sources.filter { it.id != mangaSource.id }
                        }
                        when (val migration = config.migration) {
                            is MigrationType.MangaSingle -> if (migration.toManga != null) {
                                val localManga = getAnime.await(migration.toManga)
                                if (localManga != null) {
                                    val source = sourceManager.get(localManga.source) as? CatalogueSource
                                    if (source != null) {
                                        val chapters = source.getEpisodeList(localManga.toSAnime())
                                        try {
                                            syncEpisodesWithSource.await(chapters, localManga, source)
                                        } catch (_: Exception) {
                                        }
                                        manga.progress.value = validSources.size to validSources.size
                                        return@async localManga
                                    }
                                }
                            }
                            else -> Unit
                        }
                        if (useSourceWithMost) {
                            val sourceSemaphore = Semaphore(3)
                            val processedSources = AtomicInteger()

                            validSources.map { source ->
                                async async2@{
                                    sourceSemaphore.withPermit {
                                        try {
                                            val searchResult = if (useSmartSearch) {
                                                smartSearchEngine.smartSearch(source, mangaObj.ogTitle)
                                            } else {
                                                smartSearchEngine.normalSearch(source, mangaObj.ogTitle)
                                            }

                                            if (searchResult != null &&
                                                !(searchResult.url == mangaObj.url && source.id == mangaObj.source)
                                            ) {
                                                val localManga = networkToLocalAnime.await(searchResult)
                                                val chapters = source.getEpisodeList(localManga.toSAnime())

                                                try {
                                                    syncEpisodesWithSource.await(chapters, localManga, source)
                                                } catch (e: Exception) {
                                                    return@async2 null
                                                }
                                                manga.progress.value =
                                                    validSources.size to processedSources.incrementAndGet()
                                                localManga to chapters.size
                                            } else {
                                                null
                                            }
                                        } catch (e: CancellationException) {
                                            // Ignore cancellations
                                            throw e
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                            }.mapNotNull { it.await() }.maxByOrNull { it.second }?.first
                        } else {
                            validSources.forEachIndexed { index, source ->
                                val searchResult = try {
                                    val searchResult = if (useSmartSearch) {
                                        smartSearchEngine.smartSearch(source, mangaObj.ogTitle)
                                    } else {
                                        smartSearchEngine.normalSearch(source, mangaObj.ogTitle)
                                    }

                                    if (searchResult != null) {
                                        val localManga = networkToLocalAnime.await(searchResult)
                                        val chapters = try {
                                            source.getEpisodeList(localManga.toSAnime())
                                        } catch (e: Exception) {
                                            this@MigrationListScreenModel.logcat(LogPriority.ERROR, e)
                                            emptyList()
                                        }
                                        syncEpisodesWithSource.await(chapters, localManga, source)
                                        localManga
                                    } else {
                                        null
                                    }
                                } catch (e: CancellationException) {
                                    // Ignore cancellations
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                                manga.progress.value = validSources.size to (index + 1)
                                if (searchResult != null) return@async searchResult
                            }

                            null
                        }
                    }
                    // KMK -->
                    manga.searchingJob?.await()
                    // KMK <--
                } catch (e: CancellationException) {
                    // Ignore canceled migrations
                    continue
                }

                if (result != null && result.thumbnailUrl == null) {
                    try {
                        val newManga = sourceManager.getOrStub(result.source).getAnimeDetails(result.toSAnime())
                        updateAnime.awaitUpdateFromSource(result, newManga, true)
                    } catch (e: CancellationException) {
                        // Ignore cancellations
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("MigrationListScreenModel").e(e, "Error updating manga from source")
                    }
                }

                manga.searchResult.value = if (result == null) {
                    SearchResult.NotFound
                } else {
                    SearchResult.Result(result.id)
                }
                if (result == null && hideNotFound) {
                    removeManga(manga)
                }
                if (result != null &&
                    showOnlyUpdates &&
                    (getChapterInfo(result.id).latestChapter ?: 0.0) <= (manga.episodeInfo.latestChapter ?: 0.0)
                ) {
                    removeManga(manga)
                }

                sourceFinished()
            }
        }
    }

    private suspend fun sourceFinished() {
        finishedCount.value = migratingItems.value.orEmpty().count {
            it.searchResult.value != SearchResult.Searching
        }
        if (allMangasDone()) {
            migrationDone.value = true
        }
        if (migratingItems.value?.isEmpty() == true) {
            navigateOut()
        }
    }

    private fun allMangasDone() = migratingItems.value.orEmpty().all { it.searchResult.value != SearchResult.Searching } &&
        migratingItems.value.orEmpty().any { it.searchResult.value is SearchResult.Result }

    private fun mangasSkipped() = migratingItems.value.orEmpty().count { it.searchResult.value == SearchResult.NotFound }

    private suspend fun migrateMangaInternal(
        prevManga: Anime,
        manga: Anime,
        replace: Boolean,
    ) {
        if (prevManga.id == manga.id) return // Nothing to migrate

        val flags = preferences.migrateFlags().get()
        // Update episodes read
        if (MigrationFlags.hasChapters(flags)) {
            val prevMangaChapters = getEpisodesByAnimeId.await(prevManga.id)
            val maxEpisodeRead = prevMangaChapters.filter(Episode::seen)
                .maxOfOrNull(Episode::episodeNumber)
            val dbChapters = getEpisodesByAnimeId.await(manga.id)
            val prevHistoryList = getHistoryByAnimeId.await(prevManga.id)

            val episodeUpdates = mutableListOf<EpisodeUpdate>()
            val historyUpdates = mutableListOf<HistoryUpdate>()

            dbChapters.forEach { chapter ->
                if (chapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters.find {
                        it.isRecognizedNumber &&
                            it.episodeNumber == chapter.episodeNumber
                    }
                    if (prevChapter != null) {
                        episodeUpdates += EpisodeUpdate(
                            id = chapter.id,
                            bookmark = prevChapter.bookmark,
                            seen = prevChapter.seen,
                            dateFetch = prevChapter.dateFetch,
                        )
                        prevHistoryList.find { it.episodeId == prevChapter.id }?.let { prevHistory ->
                            historyUpdates += HistoryUpdate(
                                chapter.id,
                                prevHistory.seenAt ?: return@let,
                                prevHistory.watchDuration,
                            )
                        }
                    } else if (maxEpisodeRead != null && chapter.episodeNumber <= maxEpisodeRead) {
                        episodeUpdates += EpisodeUpdate(
                            id = chapter.id,
                            seen = true,
                        )
                    }
                }
            }

            updateEpisode.awaitAll(episodeUpdates)
            upsertHistory.awaitAll(historyUpdates)
        }
        // Update categories
        if (MigrationFlags.hasCategories(flags)) {
            val categories = getCategories.await(prevManga.id)
            setAnimeCategories.await(manga.id, categories.map { it.id })
        }
        // Update track
        if (MigrationFlags.hasTracks(flags)) {
            val tracks = getTracks.await(prevManga.id)
            if (tracks.isNotEmpty()) {
                getTracks.await(manga.id).forEach {
                    deleteTrack.await(manga.id, it.trackerId)
                }
                insertTrack.awaitAll(tracks.map { it.copy(animeId = manga.id) })
            }
        }
        // Update custom cover
        if (MigrationFlags.hasCustomCover(flags) && prevManga.hasCustomCover(coverCache)) {
            coverCache.setCustomCoverToCache(manga, coverCache.getCustomCoverFile(prevManga.id).inputStream())
        }

        var animeUpdate = AnimeUpdate(manga.id, favorite = true, dateAdded = System.currentTimeMillis())
        var prevAnimeUpdate: AnimeUpdate? = null
        // Update extras
        if (MigrationFlags.hasExtra(flags)) {
            animeUpdate = animeUpdate.copy(
                episodeFlags = prevManga.episodeFlags,
                viewerFlags = prevManga.viewerFlags,
            )
        }
        // Delete downloaded
        if (MigrationFlags.hasDeleteChapters(flags)) {
            val oldSource = sourceManager.get(prevManga.source)
            if (oldSource != null) {
                downloadManager.deleteAnime(prevManga, oldSource)
            }
        }
        // Update favorite status
        if (replace) {
            prevAnimeUpdate = AnimeUpdate(
                id = prevManga.id,
                favorite = false,
                dateAdded = 0,
            )
            animeUpdate = animeUpdate.copy(
                dateAdded = prevManga.dateAdded,
            )
        }

        updateAnime.awaitAll(listOfNotNull(animeUpdate, prevAnimeUpdate))
    }

    /** Set a manga picked from manual search to be used as migration target */
    fun useMangaForMigration(context: Context, newMangaId: Long, selectedMangaId: Long) {
        val migratingManga = migratingItems.value.orEmpty().find { it.manga.id == selectedMangaId }
            ?: return
        migratingManga.searchResult.value = SearchResult.Searching
        screenModelScope.launchIO {
            val result = migratingManga.migrationScope.async {
                val manga = getManga(newMangaId)!!
                val localManga = networkToLocalAnime.await(manga)
                try {
                    val source = sourceManager.get(manga.source)!!
                    val chapters = source.getEpisodeList(localManga.toSAnime())
                    syncEpisodesWithSource.await(chapters, localManga, source)
                } catch (e: Exception) {
                    return@async null
                }
                localManga
            }.await()

            if (result != null) {
                try {
                    val newManga = sourceManager.getOrStub(result.source).getAnimeDetails(result.toSAnime())
                    updateAnime.awaitUpdateFromSource(result, newManga, true)
                } catch (e: CancellationException) {
                    // Ignore cancellations
                    throw e
                } catch (e: Exception) {
                    Timber.tag("MigrationListScreenModel").e(e, "Error updating manga from source")
                }

                migratingManga.searchResult.value = SearchResult.Result(result.id)
            } else {
                migratingManga.searchResult.value = SearchResult.NotFound
                withUIContext {
                    context.toast(SYMR.strings.no_chapters_found_for_migration, Toast.LENGTH_LONG)
                }
            }

            // KMK -->
            sourceFinished()
            // KMK <--
        }
    }

    fun migrateMangas() {
        migrateMangas(true)
    }

    fun copyMangas() {
        migrateMangas(false)
    }

    private fun migrateMangas(replace: Boolean) {
        dialog.value = null
        migrateJob = screenModelScope.launchIO {
            migratingProgress.value = 0f
            val items = migratingItems.value.orEmpty()
            try {
                items.forEachIndexed { index, manga ->
                    try {
                        ensureActive()
                        val toMangaObj = manga.searchResult.value.let {
                            if (it is SearchResult.Result) {
                                getAnime.await(it.id)
                            } else {
                                null
                            }
                        }
                        if (toMangaObj != null) {
                            migrateMangaInternal(
                                manga.manga,
                                toMangaObj,
                                replace,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    migratingProgress.value = index.toFloat() / items.size
                }

                navigateOut()
            } finally {
                migratingProgress.value = Float.MAX_VALUE
                migrateJob = null
            }
        }
    }

    fun cancelMigrate() {
        migrateJob?.cancel()
        migrateJob = null
    }

    private suspend fun navigateOut() {
        navigateOut.emit(Unit)
    }

    fun migrateManga(mangaId: Long, copy: Boolean) {
        manualMigrations.value++
        screenModelScope.launchIO {
            val manga = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO

            val toMangaObj = getAnime.await((manga.searchResult.value as? SearchResult.Result)?.id ?: return@launchIO)
                ?: return@launchIO
            migrateMangaInternal(
                manga.manga,
                toMangaObj,
                !copy,
            )

            removeManga(mangaId)
        }
    }

    // KMK -->
    /** Cancel searching without remove it from list so user can perform manual search */
    fun cancelManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO
            item.searchingJob?.cancel()
            item.searchingJob = null
            item.searchResult.value = SearchResult.NotFound
            sourceFinished()
        }
    }
    // KMK <--

    fun removeManga(mangaId: Long) {
        screenModelScope.launchIO {
            val item = migratingItems.value.orEmpty().find { it.manga.id == mangaId }
                ?: return@launchIO
            removeManga(item)
            item.migrationScope.cancel()
            sourceFinished()
        }
    }

    private fun removeManga(item: MigratingAnime) {
        when (val migration = config.migration) {
            is MigrationType.MangaList -> {
                val ids = migration.mangaIds.toMutableList()
                val index = ids.indexOf(item.manga.id)
                if (index > -1) {
                    ids.removeAt(index)
                    config.migration = MigrationType.MangaList(ids)
                    val index2 = migratingItems.value.orEmpty().indexOf(item)
                    if (index2 > -1) migratingItems.value = (migratingItems.value.orEmpty() - item).toImmutableList()
                }
            }
            is MigrationType.MangaSingle -> Unit
        }
    }

    override fun onDispose() {
        super.onDispose()
        migratingItems.value.orEmpty().forEach {
            it.migrationScope.cancel()
        }
    }

    fun openMigrateDialog(
        copy: Boolean,
    ) {
        dialog.value = Dialog.MigrateAnimeDialog(
            copy,
            migratingItems.value.orEmpty().size,
            mangasSkipped(),
        )
    }

    // KMK -->
    fun openMigrationOptionsDialog() {
        dialog.value = Dialog.MigrationOptionsDialog
    }

    fun updateOptions() {
        hideNotFound = preferences.hideNotFoundMigration().get()
        showOnlyUpdates = preferences.showOnlyUpdatesMigration().get()
        useSourceWithMost = preferences.useSourceWithMost().get()
        useSmartSearch = preferences.smartMigration().get()
    }
    // KMK <--

    sealed class Dialog {
        data class MigrateAnimeDialog(val copy: Boolean, val mangaSet: Int, val mangaSkipped: Int) : Dialog()
        data object MigrationExitDialog : Dialog()

        // KMK -->
        data object MigrationOptionsDialog : Dialog()
        // KMK <--
    }
}
