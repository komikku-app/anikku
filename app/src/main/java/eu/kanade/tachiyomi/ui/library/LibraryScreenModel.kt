package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.presentation.anime.DownloadAction
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.model.applyFilter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.GetTracksPerAnime
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.sy.SYMR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.random.Random
import tachiyomi.domain.source.model.Source as DomainSource

/**
 * Typealias for the library anime, using the category as keys, and list of anime as values.
 */
typealias AnimeLibraryMap = Map<Category, List<LibraryItem>>

@Suppress("LargeClass")
class LibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracksPerAnime: GetTracksPerAnime = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    // SY -->
    private val getTracks: GetTracks = Injekt.get(),
    private val setCustomAnimeInfo: SetCustomAnimeInfo = Injekt.get(),
    // SY <--
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedCategory().asState(screenModelScope)

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                getLibraryFlow(),
                getTracksPerAnime.subscribe(),
                combine(
                    getTrackingFilterFlow(),
                    downloadCache.changes,
                    ::Pair,
                ),
                // SY -->
                combine(
                    state.map { it.groupType }.distinctUntilChanged(),
                    libraryPreferences.sortingMode().changes(),
                    ::Pair,
                ),
                // SY <--
            ) { searchQuery, library, tracks, (trackingFilter, _), (groupType, sort) ->
                library
                    // SY -->
                    .applyGrouping(groupType)
                    // SY <--
                    .applyFilters(tracks, trackingFilter)
                    .applySort(tracks, sort.takeIf { groupType != LibraryGroup.BY_DEFAULT }, trackingFilter.keys)
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            value.filter { it.matches(searchQuery) }
                        } else {
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueWatchingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showAnimeCount, showAnimeContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showAnimeCount = showAnimeCount,
                        showAnimeContinueButton = showAnimeContinueButton,
                    )
                }
            }
            .launchIn(screenModelScope)

        combine(
            getAnimelibItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            (
                listOf(
                    prefs.filterDownloaded,
                    prefs.filterUnseen,
                    prefs.filterStarted,
                    prefs.filterBookmarked,
                    // AM (FILLERMARK) -->
                    prefs.filterFillermarked,
                    // <-- AM (FILLERMARK)
                    prefs.filterCompleted,
                    prefs.filterIntervalCustom,
                ) + trackFilter.values
                ).any { it != TriState.DISABLED }
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(screenModelScope)

        // SY -->
        libraryPreferences.groupLibraryBy().changes()
            .onEach {
                mutableState.update { state ->
                    state.copy(groupType = it)
                }
            }
            .launchIn(screenModelScope)
        // SY <--
    }

    private suspend fun AnimeLibraryMap.applyFilters(
        trackMap: Map<Long, List<Track>>,
        trackingFilter: Map<Long, TriState>,
    ): AnimeLibraryMap {
        val prefs = getAnimelibItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val skipOutsideReleasePeriod = prefs.skipOutsideReleasePeriod
        val filterDownloaded = if (downloadedOnly) TriState.ENABLED_IS else prefs.filterDownloaded
        val filterUnseen = prefs.filterUnseen
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        // AM (FILLERMARK) -->
        val filterFillermarked = prefs.filterFillermarked
        // <-- AM (FILLERMARK)
        val filterCompleted = prefs.filterCompleted
        val filterIntervalCustom = prefs.filterIntervalCustom

        val isNotLoggedInAnyTrack = trackingFilter.isEmpty()

        val excludedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_NOT) it.key else null }
        val includedTracks = trackingFilter.mapNotNull { if (it.value == TriState.ENABLED_IS) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = {
            applyFilter(filterDownloaded) {
                it.libraryAnime.anime.isLocal() ||
                    it.downloadCount > 0 ||
                    downloadManager.getDownloadCount(it.libraryAnime.anime) > 0
            }
        }

        val filterFnUnseen: (LibraryItem) -> Boolean = {
            applyFilter(filterUnseen) { it.libraryAnime.unseenCount > 0 }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = {
            applyFilter(filterStarted) { it.libraryAnime.hasStarted }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = {
            applyFilter(filterBookmarked) { it.libraryAnime.hasBookmarks }
        }

        // AM (FILLERMARK) -->
        val filterFnFillermarked: (LibraryItem) -> Boolean = {
            applyFilter(filterFillermarked) { it.libraryAnime.hasFillermarks }
        }
        // <-- AM (FILLERMARK)

        val filterFnCompleted: (LibraryItem) -> Boolean = {
            applyFilter(filterCompleted) { it.libraryAnime.anime.status.toInt() == SAnime.COMPLETED }
        }

        val filterFnIntervalCustom: (LibraryItem) -> Boolean = {
            if (skipOutsideReleasePeriod) {
                applyFilter(filterIntervalCustom) { it.libraryAnime.anime.fetchInterval < 0 }
            } else {
                true
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val animeTracks = trackMap
                .mapValues { entry -> entry.value.map { it.trackerId } }[item.libraryAnime.id]
                .orEmpty()

            val isExcluded = excludedTracks.isNotEmpty() && animeTracks.fastAny { it in excludedTracks }
            val isIncluded = includedTracks.isEmpty() || animeTracks.fastAny { it in includedTracks }

            !isExcluded && isIncluded
        }

        val filterFn: (LibraryItem) -> Boolean = {
            filterFnDownloaded(it) &&
                filterFnUnseen(it) &&
                filterFnStarted(it) &&
                filterFnBookmarked(it) &&
                // AM (FILLERMARK) -->
                filterFnFillermarked(it) &&
                // <-- AM (FILLERMARK)
                filterFnCompleted(it) &&
                filterFnIntervalCustom(it) &&
                filterFnTracking(it)
        }

        return mapValues { (_, value) -> value.fastFilter(filterFn) }
    }

    private fun AnimeLibraryMap.applySort(
        trackMap: Map<Long, List<Track>>,
        groupSort: LibrarySort? = null,
        loggedInTrackerIds: Set<Long>,
    ): AnimeLibraryMap {
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            i1.libraryAnime.anime.title.lowercase().compareToWithCollator(i2.libraryAnime.anime.title.lowercase())
        }

        val defaultTrackerScoreSortValue = -1.0
        val trackerScores by lazy {
            val trackerMap = trackerManager.getAll(loggedInTrackerIds).associateBy { e -> e.id }
            trackMap.mapValues { entry ->
                when {
                    entry.value.isEmpty() -> null
                    else ->
                        entry.value
                            .mapNotNull { trackerMap[it.trackerId]?.animeService?.get10PointScore(it) }
                            .average()
                }
            }
        }

        fun LibrarySort.comparator(): Comparator<LibraryItem> = Comparator { i1, i2 ->
            // SY -->
            val sort = groupSort ?: keys.find { it.id == i1.libraryAnime.category }!!.sort
            // SY <--
            when (this.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                LibrarySort.Type.LastSeen -> {
                    i1.libraryAnime.lastSeen.compareTo(i2.libraryAnime.lastSeen)
                }
                LibrarySort.Type.LastUpdate -> {
                    i1.libraryAnime.anime.lastUpdate.compareTo(i2.libraryAnime.anime.lastUpdate)
                }
                LibrarySort.Type.UnseenCount -> when {
                    // Ensure unseen content comes first
                    i1.libraryAnime.unseenCount == i2.libraryAnime.unseenCount -> 0
                    i1.libraryAnime.unseenCount == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.unseenCount == 0L -> if (this.isAscending) -1 else 1
                    else -> i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                }
                LibrarySort.Type.TotalEpisodes -> {
                    i1.libraryAnime.totalEpisodes.compareTo(i2.libraryAnime.totalEpisodes)
                }
                LibrarySort.Type.LatestEpisode -> {
                    i1.libraryAnime.latestUpload.compareTo(i2.libraryAnime.latestUpload)
                }
                LibrarySort.Type.EpisodeFetchDate -> {
                    i1.libraryAnime.episodeFetchedAt.compareTo(i2.libraryAnime.episodeFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    i1.libraryAnime.anime.dateAdded.compareTo(i2.libraryAnime.anime.dateAdded)
                }
                LibrarySort.Type.TrackerMean -> {
                    val item1Score = trackerScores[i1.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    val item2Score = trackerScores[i2.libraryAnime.id] ?: defaultTrackerScoreSortValue
                    item1Score.compareTo(item2Score)
                }
                LibrarySort.Type.AiringTime -> when {
                    i1.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) 1 else -1
                    i2.libraryAnime.anime.nextEpisodeAiringAt == 0L -> if (this.isAscending) -1 else 1
                    i1.libraryAnime.unseenCount == i2.libraryAnime.unseenCount ->
                        i1.libraryAnime.anime.nextEpisodeAiringAt.compareTo(
                            i2.libraryAnime.anime.nextEpisodeAiringAt,
                        )
                    else -> i1.libraryAnime.unseenCount.compareTo(i2.libraryAnime.unseenCount)
                }
                LibrarySort.Type.Random -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
                else -> {
                    error("Why Are We Still Here? Just To Suffer?")
                }
            }
        }

        return mapValues { (key, value) ->
            if (key.sort.type == LibrarySort.Type.Random) {
                return@mapValues value.shuffled(Random(libraryPreferences.randomSortSeed().get()))
            }

            val comparator = key.sort.comparator()
                .let { if (key.sort.isAscending) it else it.reversed() }
                .thenComparator(sortAlphabetically)

            value.sortedWith(comparator)
        }
    }

    private fun getAnimelibItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),
            libraryPreferences.autoUpdateAnimeRestrictions().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloaded().changes(),
            libraryPreferences.filterUnseen().changes(),
            libraryPreferences.filterStarted().changes(),
            libraryPreferences.filterBookmarked().changes(),
            // AM (FILLERMARK) -->
            libraryPreferences.filterFillermarkedAnime().changes(),
            // <-- AM (FILLERMARK)
            libraryPreferences.filterCompleted().changes(),
            libraryPreferences.filterIntervalCustom().changes(),
            // KMK -->
            libraryPreferences.sourceBadge().changes(),
            libraryPreferences.useLangIcon().changes(),
            // KMK <--
        ) {
            ItemPreferences(
                downloadBadge = it[0] as Boolean,
                localBadge = it[1] as Boolean,
                languageBadge = it[2] as Boolean,
                skipOutsideReleasePeriod = LibraryPreferences.ANIME_OUTSIDE_RELEASE_PERIOD in (it[3] as Set<*>),
                globalFilterDownloaded = it[4] as Boolean,
                filterDownloaded = it[5] as TriState,
                filterUnseen = it[6] as TriState,
                filterStarted = it[7] as TriState,
                filterBookmarked = it[8] as TriState,
                // AM (FILLERMARK) -->
                filterFillermarked = it[9] as TriState,
                filterCompleted = it[10] as TriState,
                filterIntervalCustom = it[11] as TriState,
                // <-- AM (FILLERMARK)
                // KMK -->
                sourceBadge = it[12] as Boolean,
                useLangIcon = it[13] as Boolean,
                // KMK <--
            )
        }
    }

    /**
     * Get the categories and all its anime from the database.
     */
    private fun getLibraryFlow(): Flow<AnimeLibraryMap> {
        val animelibAnimesFlow = combine(
            getLibraryAnime.subscribe(),
            getAnimelibItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryMangaList, prefs, _ ->
            libraryMangaList
                .map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    // KMK -->
                    val source = sourceManager.getOrStub(libraryManga.anime.source)
                    // KMK <--
                    LibraryItem(
                        libraryManga,
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(libraryManga.anime).toLong()
                        } else {
                            0
                        },
                        unseenCount = libraryManga.unseenCount,
                        isLocal = if (prefs.localBadge) libraryManga.anime.isLocal() else false,
                        sourceLanguage = if (prefs.languageBadge) {
                            source.lang
                        } else {
                            ""
                        },
                        // KMK -->
                        useLangIcon = prefs.useLangIcon,
                        source = if (prefs.sourceBadge) {
                            DomainSource(
                                source.id,
                                source.lang,
                                source.name,
                                supportsLatest = false,
                                isStub = source is StubSource,
                            )
                        } else {
                            null
                        },
                        // KMK <--
                    )
                }
                .groupBy { it.libraryAnime.category }
        }

        return combine(
            // KMK -->
            libraryPreferences.showHiddenCategories().changes(),
            // KMK <--
            getCategories.subscribe(),
            animelibAnimesFlow,
        ) { showHiddenCategories, categories, animelibAnime ->
            val displayCategories = if (animelibAnime.isNotEmpty() && !animelibAnime.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            displayCategories
                // KMK -->
                .filterNot { !showHiddenCategories && it.hidden }
                // KMK <--
                .associateWith { animelibAnime[it.id].orEmpty() }
        }
    }

    // SY -->
    private fun AnimeLibraryMap.applyGrouping(groupType: Int): AnimeLibraryMap {
        val items = when (groupType) {
            LibraryGroup.BY_DEFAULT -> this
            LibraryGroup.UNGROUPED -> {
                mapOf(
                    Category(
                        0,
                        preferences.context.stringResource(SYMR.strings.ungrouped),
                        0,
                        0,
                        // KMK -->
                        false,
                        // KMK <--
                    ) to
                        values.flatten().distinctBy { it.libraryAnime.anime.id },
                )
            }
            else -> {
                getGroupedAnimeItems(
                    groupType = groupType,
                    libraryAnime = this.values.flatten().distinctBy { it.libraryAnime.anime.id },
                )
            }
        }

        return items
    }
    // SY <--

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, TriState>> {
        return trackerManager.loggedInTrackersFlow().flatMapLatest { loggedInTrackers ->
            if (loggedInTrackers.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val prefFlows = loggedInTrackers.map { tracker ->
                libraryPreferences.filterTracking(tracker.id.toInt()).changes()
            }
            combine(prefFlows) {
                loggedInTrackers
                    .mapIndexed { index, tracker -> tracker.id to it[index] }
                    .toMap()
            }
        }
    }

    /**
     * Returns the common categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getCommonCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        return animes
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of anime.
     *
     * @param animes the list of anime.
     */
    private suspend fun getMixCategories(animes: List<Anime>): Collection<Category> {
        if (animes.isEmpty()) return emptyList()
        val nimeCategories = animes.map { getCategories.await(it.id).toSet() }
        val common = nimeCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return nimeCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val animes = selection.map { it.anime }.toList()
        when (action) {
            DownloadAction.NEXT_1_EPISODE -> downloadUnseenEpisodes(animes, 1)
            DownloadAction.NEXT_5_EPISODES -> downloadUnseenEpisodes(animes, 5)
            DownloadAction.NEXT_10_EPISODES -> downloadUnseenEpisodes(animes, 10)
            DownloadAction.NEXT_25_EPISODES -> downloadUnseenEpisodes(animes, 25)
            DownloadAction.UNSEEN_EPISODES -> downloadUnseenEpisodes(animes, null)
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unseen episodes from the list of animes given.
     *
     * @param animes the list of anime.
     * @param amount the amount to queue or null to queue all
     */
    private fun downloadUnseenEpisodes(animes: List<Anime>, amount: Int?) {
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                val episodes = getNextEpisodes.await(anime.id)
                    .fastFilterNot { episode ->
                        downloadManager.getQueuedDownloadOrNull(episode.id) != null ||
                            downloadManager.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                anime.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    // SY -->
    fun resetInfo() {
        state.value.selection.fastForEach { (anime) ->
            val animeInfo = CustomAnimeInfo(
                id = anime.id,
                title = null,
                author = null,
                artist = null,
                thumbnailUrl = null,
                description = null,
                genre = null,
                status = null,
            )

            setCustomAnimeInfo.set(animeInfo)
        }
        clearSelection()
    }
    // SY <--

    /**
     * Marks animes' episodes seen status.
     */
    fun markSeenSelection(seen: Boolean) {
        val animes = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                setSeenStatus.await(
                    anime = anime.anime,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected anime.
     *
     * @param animeList the list of anime to delete.
     * @param deleteFromLibrary whether to delete anime from library.
     * @param deleteEpisodes whether to delete downloaded episodes.
     */
    fun removeAnimes(animeList: List<Anime>, deleteFromLibrary: Boolean, deleteEpisodes: Boolean) {
        screenModelScope.launchNonCancellable {
            val animeToDelete = animeList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = animeToDelete.map {
                    it.removeCovers(coverCache)
                    AnimeUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateAnime.awaitAll(toDelete)
            }

            if (deleteEpisodes) {
                animeToDelete.forEach { anime ->
                    val source = sourceManager.get(anime.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteAnime(anime, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of anime using old and new common categories.
     *
     * @param animeList the list of anime to move.
     * @param addCategories the categories to add for all animes.
     * @param removeCategories the categories to remove in all animes.
     */
    fun setAnimeCategories(
        animeList: List<Anime>,
        addCategories: List<Long>,
        removeCategories: List<Long>,
    ) {
        screenModelScope.launchNonCancellable {
            animeList.forEach { anime ->
                val categoryIds = getCategories.await(anime.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setAnimeCategories.await(anime.id, categoryIds)
            }
        }
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode().asState(screenModelScope)
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (
            if (isLandscape) {
                libraryPreferences.landscapeColumns()
            } else {
                libraryPreferences.portraitColumns()
            }
            ).asState(
            screenModelScope,
        )
    }

    suspend fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        if (state.value.categories.isEmpty()) return null

        return withIOContext {
            state.value
                .getAnimelibItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun showSettingsDialog() {
        mutableState.update { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun toggleSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.fastAny { it.id == anime.id }) {
                    list.removeAll { it.id == anime.id }
                } else {
                    list.add(anime)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all nimes between and including the given anime and the last pressed anime from the
     * same category as the given anime
     */
    fun toggleRangeSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val lastSelected = list.lastOrNull()
                if (lastSelected?.category != anime.category) {
                    list.add(anime)
                    return@mutate
                }

                val items = state.getAnimelibItemsByCategoryId(anime.category)
                    ?.fastMap { it.libraryAnime }.orEmpty()
                val lastAnimeIndex = items.indexOf(lastSelected)
                val curAnimeIndex = items.indexOf(anime)

                val selectedIds = list.fastMap { it.id }
                val selectionRange = when {
                    lastAnimeIndex < curAnimeIndex -> IntRange(lastAnimeIndex, curAnimeIndex)
                    curAnimeIndex < lastAnimeIndex -> IntRange(curAnimeIndex, lastAnimeIndex)
                    // We shouldn't reach this point
                    else -> return@mutate
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                list.addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = list.fastMap { it.id }
                state.getAnimelibItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryAnime.takeUnless { it.id in selectedIds }
                    }
                    ?.let { list.addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                val categoryId = state.categories[index].id
                val items = state.getAnimelibItemsByCategoryId(categoryId)?.fastMap { it.libraryAnime }.orEmpty()
                val selectedIds = list.fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                list.removeAll { it.id in toRemoveIds }
                list.addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCategoryDialog() {
        screenModelScope.launchIO {
            // Create a copy of selected anime
            val animeList = state.value.selection.map { it.anime }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(animeList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(animeList)
            val preselected = categories
                .map {
                    when (it) {
                        in common -> CheckboxState.State.Checked(it)
                        in mix -> CheckboxState.TriState.Exclude(it)
                        else -> CheckboxState.State.None(it)
                    }
                }
                .toImmutableList()
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(animeList, preselected)) }
        }
    }

    fun openDeleteAnimeDialog() {
        val animeList = state.value.selection.map { it.anime }
        mutableState.update { it.copy(dialog = Dialog.DeleteAnime(animeList)) }
    }

    fun openResetInfoAnimeDialog() {
        val animeList = state.value.selection.map { it.anime }
        mutableState.update { it.copy(dialog = Dialog.ResetInfoAnime(animeList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Dialog {
        data object SettingsSheet : Dialog
        data class ChangeCategory(
            val anime: List<Anime>,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteAnime(val anime: List<Anime>) : Dialog
        data class ResetInfoAnime(val anime: List<Anime>) : Dialog
    }

    // SY -->
    /** Returns first unread chapter of a anime */
    suspend fun getFirstUnseen(anime: Anime): Episode? {
        return getNextEpisodes.await(anime.id).firstOrNull()
    }

    @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
    private fun getGroupedAnimeItems(
        groupType: Int,
        libraryAnime: List<LibraryItem>,
    ): AnimeLibraryMap {
        val context = preferences.context
        return when (groupType) {
            LibraryGroup.BY_TRACK_STATUS -> {
                val tracks = runBlocking { getTracks.await() }.groupBy { it.animeId }
                libraryAnime.groupBy { item ->
                    val status = tracks[item.libraryAnime.anime.id]?.firstNotNullOfOrNull { track ->
                        TrackStatus.parseTrackerStatus(track.trackerId, track.status)
                    } ?: TrackStatus.OTHER

                    status.int
                }.mapKeys { (id) ->
                    Category(
                        id = id.toLong(),
                        name = TrackStatus.entries
                            .find { it.int == id }
                            .let { it ?: TrackStatus.OTHER }
                            .let { context.getString(it.res) },
                        order = TrackStatus.entries.toTypedArray().indexOfFirst {
                            it.int == id
                        }.takeUnless { it == -1 }?.toLong() ?: TrackStatus.OTHER.ordinal.toLong(),
                        flags = 0,
                        hidden = false,
                    )
                }
            }
            LibraryGroup.BY_SOURCE -> {
                val sources: List<Long>
                libraryAnime.groupBy { item ->
                    item.libraryAnime.anime.source
                }.also {
                    sources = it.keys
                        .map {
                            sourceManager.getOrStub(it)
                        }
                        .sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id.toString() } },
                        )
                        .map { it.id }
                }.mapKeys {
                    Category(
                        id = it.key,
                        name = if (it.key == LocalSource.ID) {
                            context.getString(R.string.local_source)
                        } else {
                            val source = sourceManager.getOrStub(it.key)
                            source.name.ifBlank { source.id.toString() }
                        },
                        order = sources.indexOf(it.key).takeUnless { it == -1 }?.toLong() ?: Long.MAX_VALUE,
                        flags = 0,
                        hidden = false,
                    )
                }
            }
            LibraryGroup.BY_TAG -> {
                val tags: List<String> = libraryAnime.flatMap { item ->
                    item.libraryAnime.anime.genre?.distinct() ?: emptyList()
                }
                libraryAnime.flatMap { item ->
                    item.libraryAnime.anime.genre?.distinct()?.map { genre ->
                        Pair(genre, item)
                    } ?: emptyList()
                }.groupBy({ it.first }, { it.second }).filterValues { it.size > 3 }
                    .mapKeys { (genre, _) ->
                        Category(
                            id = genre.hashCode().toLong(),
                            name = genre,
                            order = tags.indexOf(genre).takeUnless { it == -1 }?.toLong() ?: Long.MAX_VALUE,
                            flags = 0,
                            hidden = false,
                        )
                    }
            }
            else -> {
                libraryAnime.groupBy { item ->
                    item.libraryAnime.anime.status
                }.mapKeys {
                    Category(
                        id = it.key + 1,
                        name = when (it.key) {
                            SAnime.ONGOING.toLong() -> context.getString(R.string.ongoing)
                            SAnime.LICENSED.toLong() -> context.getString(R.string.licensed)
                            SAnime.CANCELLED.toLong() -> context.getString(R.string.cancelled)
                            SAnime.ON_HIATUS.toLong() -> context.getString(R.string.on_hiatus)
                            SAnime.PUBLISHING_FINISHED.toLong() -> context.getString(
                                R.string.publishing_finished,
                            )
                            SAnime.COMPLETED.toLong() -> context.getString(R.string.completed)
                            else -> context.getString(R.string.unknown)
                        },
                        order = when (it.key) {
                            SAnime.ONGOING.toLong() -> 1
                            SAnime.LICENSED.toLong() -> 2
                            SAnime.CANCELLED.toLong() -> 3
                            SAnime.ON_HIATUS.toLong() -> 4
                            SAnime.PUBLISHING_FINISHED.toLong() -> 5
                            SAnime.COMPLETED.toLong() -> 6
                            else -> 7
                        },
                        flags = 0,
                        hidden = false,
                    )
                }
            }
        }.toSortedMap(compareBy { it.order })
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,
        // KMK -->
        val useLangIcon: Boolean,
        val sourceBadge: Boolean,
        // KMK <--
        val skipOutsideReleasePeriod: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: TriState,
        val filterUnseen: TriState,
        val filterStarted: TriState,
        val filterBookmarked: TriState,
        // AM (FILLERMARK) -->
        val filterFillermarked: TriState,
        // <-- AM (FILLERMARK)
        val filterCompleted: TriState,
        val filterIntervalCustom: TriState,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: AnimeLibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAnime> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showAnimeCount: Boolean = false,
        val showAnimeContinueButton: Boolean = false,
        val dialog: Dialog? = null,
        // SY -->
        val groupType: Int = LibraryGroup.BY_DEFAULT,
        // SY <--
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryAnime.anime.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        // SY -->
        val showResetInfo: Boolean by lazy {
            selection.fastAny { (anime) ->
                anime.title != anime.ogTitle ||
                    anime.author != anime.ogAuthor ||
                    anime.artist != anime.ogArtist ||
                    anime.thumbnailUrl != anime.ogThumbnailUrl ||
                    anime.description != anime.ogDescription ||
                    anime.genre != anime.ogGenre ||
                    anime.status != anime.ogStatus
            }
        }
        // SY <--

        fun getAnimelibItemsByCategoryId(categoryId: Long): List<LibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getLibraryItemsByPage(page: Int): List<LibraryItem> {
            return library.values.toTypedArray().getOrNull(page).orEmpty()
        }

        fun getAnimeCountForCategory(category: Category): Int? {
            return if (showAnimeCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showAnimeCount -> null
                !showCategoryTabs -> getAnimeCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
