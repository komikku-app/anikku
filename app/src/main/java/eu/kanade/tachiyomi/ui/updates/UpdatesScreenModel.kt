package eu.kanade.tachiyomi.ui.updates

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.episode.interactor.SetSeenStatus
import eu.kanade.presentation.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime

class UpdatesScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    downloadPreferences: DownloadPreferences = Injekt.get(),
) : StateScreenModel<UpdatesScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Int.MAX_VALUE)
    val events: Flow<Event> = _events.receiveAsFlow()

    val lastUpdated by libraryPreferences.lastUpdatedTimestamp().asState(screenModelScope)

    val useExternalDownloader = downloadPreferences.useExternalDownloader().get()

    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            // Set date limit for recent episodes
            val limit = ZonedDateTime.now().minusMonths(3).toInstant()

            combine(
                getUpdates.subscribe(limit).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { updates, _, _ -> updates }
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.InternalError)
                }
                .collectLatest { updates ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = updates.toUpdateItems(),
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            merge(downloadManager.statusFlow(), downloadManager.progressFlow())
                .catch { logcat(LogPriority.ERROR, it) }
                .collect(this@UpdatesScreenModel::updateDownloadState)
        }
    }

    private fun List<UpdatesWithRelations>.toUpdateItems(): PersistentList<UpdatesItem> {
        return this
            .map { update ->
                val activeDownload = downloadManager.getQueuedDownloadOrNull(update.episodeId)
                val downloaded = downloadManager.isEpisodeDownloaded(
                    update.episodeName,
                    update.scanlator,
                    // SY -->
                    update.ogAnimeTitle,
                    // SY <--
                    update.sourceId,
                )
                val downloadState = when {
                    activeDownload != null -> activeDownload.status
                    downloaded -> Download.State.DOWNLOADED
                    else -> Download.State.NOT_DOWNLOADED
                }
                UpdatesItem(
                    update = update,
                    downloadStateProvider = { downloadState },
                    downloadProgressProvider = { activeDownload?.progress ?: 0 },
                    selected = update.episodeId in selectedEpisodeIds,
                    // AM (FILE_SIZE) -->
                    fileSize = null,
                    // <-- AM (FILE_SIZE)
                )
            }
            .toPersistentList()
    }

    fun updateLibrary(): Boolean {
        val started = LibraryUpdateJob.startNow(Injekt.get<Application>())
        screenModelScope.launch {
            _events.send(Event.LibraryUpdateTriggered(started))
        }
        return started
    }

    /**
     * Update status of episodes.
     *
     * @param download download object containing progress.
     */
    private fun updateDownloadState(download: Download) {
        mutableState.update { state ->
            val newItems = state.items.mutate { list ->
                val modifiedIndex = list.indexOfFirst { it.update.episodeId == download.episode.id }
                if (modifiedIndex < 0) return@mutate

                val item = list[modifiedIndex]
                list[modifiedIndex] = item.copy(
                    downloadStateProvider = { download.status },
                    downloadProgressProvider = { download.progress },
                )
            }
            state.copy(items = newItems)
        }
    }

    fun downloadEpisodes(items: List<UpdatesItem>, action: EpisodeDownloadAction) {
        if (items.isEmpty()) return
        screenModelScope.launch {
            when (action) {
                EpisodeDownloadAction.START -> {
                    downloadEpisodes(items)
                    if (items.any { it.downloadStateProvider() == Download.State.ERROR }) {
                        downloadManager.startDownloads()
                    }
                }
                EpisodeDownloadAction.START_NOW -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    startDownloadingNow(episodeId)
                }
                EpisodeDownloadAction.CANCEL -> {
                    val episodeId = items.singleOrNull()?.update?.episodeId ?: return@launch
                    cancelDownload(episodeId)
                }
                EpisodeDownloadAction.DELETE -> {
                    deleteEpisodes(items)
                }
                EpisodeDownloadAction.SHOW_QUALITIES -> {
                    val update = items.singleOrNull()?.update ?: return@launch
                    showQualitiesDialog(update)
                }
            }
            toggleAllSelection(false)
        }
    }

    private fun startDownloadingNow(episodeId: Long) {
        downloadManager.startDownloadNow(episodeId)
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = Download.State.NOT_DOWNLOADED })
    }

    /**
     * Mark the selected updates list as seen/unseen.
     * @param updates the list of selected updates.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markUpdatesSeen(updates: List<UpdatesItem>, seen: Boolean) {
        screenModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = updates
                    .mapNotNull { getEpisode.await(it.update.episodeId) }
                    .toTypedArray(),
            )
        }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param updates the list of episodes to bookmark.
     */
    fun bookmarkUpdates(updates: List<UpdatesItem>, bookmark: Boolean) {
        screenModelScope.launchIO {
            updates
                .filterNot { it.update.bookmark == bookmark }
                .map { EpisodeUpdate(id = it.update.episodeId, bookmark = bookmark) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    // AM (FILLERMARK) -->
    /**
     * Fillermarks the given list of episodes.
     * @param updates the list of episodes to fillermark.
     */
    fun fillermarkUpdates(updates: List<UpdatesItem>, fillermark: Boolean) {
        screenModelScope.launchIO {
            updates
                .filterNot { it.update.fillermark == fillermark }
                .map { EpisodeUpdate(id = it.update.episodeId, fillermark = fillermark) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }
    // <-- AM (FILLERMARK)

    /**
     * Downloads the given list of episodes with the manager.
     * @param updatesItem the list of episodes to download.
     */
    private fun downloadEpisodes(updatesItem: List<UpdatesItem>, alt: Boolean = false) {
        screenModelScope.launchNonCancellable {
            val groupedUpdates = updatesItem.groupBy { it.update.animeId }.values
            for (updates in groupedUpdates) {
                val animeId = updates.first().update.animeId
                val anime = getAnime.await(animeId) ?: continue
                // Don't download if source isn't available
                sourceManager.get(anime.source) ?: continue
                val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                downloadManager.downloadEpisodes(anime, episodes, true, alt)
            }
        }
    }

    /**
     * Delete selected episodes
     *
     * @param updatesItem list of episodes
     */
    fun deleteEpisodes(updatesItem: List<UpdatesItem>) {
        screenModelScope.launchNonCancellable {
            updatesItem
                .groupBy { it.update.animeId }
                .entries
                .forEach { (animeId, updates) ->
                    val anime = getAnime.await(animeId) ?: return@forEach
                    val source = sourceManager.get(anime.source) ?: return@forEach
                    val episodes = updates.mapNotNull { getEpisode.await(it.update.episodeId) }
                    downloadManager.deleteEpisodes(
                        episodes,
                        anime,
                        source,
                    )
                }
        }
        toggleAllSelection(false)
    }

    fun showConfirmDeleteEpisodes(updatesItem: List<UpdatesItem>) {
        setDialog(Dialog.DeleteConfirmation(updatesItem))
    }

    private fun showQualitiesDialog(update: UpdatesWithRelations) {
        setDialog(
            Dialog.ShowQualities(
                update.episodeName,
                update.episodeId,
                update.animeId,
                update.sourceId,
            ),
        )
    }

    fun toggleSelection(
        item: UpdatesItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.items.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.update.episodeId == item.update.episodeId }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.update.episodeId, selected)

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
                                selectedEpisodeIds.add(inbetweenItem.update.episodeId)
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
            state.copy(items = newItems.toPersistentList())
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedEpisodeIds.addOrRemove(it.update.episodeId, selected)
                it.copy(selected = selected)
            }
            state.copy(items = newItems.toPersistentList())
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                selectedEpisodeIds.addOrRemove(it.update.episodeId, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(items = newItems.toPersistentList())
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun resetNewUpdatesCount() {
        libraryPreferences.newUpdatesCount().set(0)
    }

    // KMK -->
    fun toggleExpandedState(key: String) {
        mutableState.update {
            it.copy(
                expandedState = it.expandedState.toMutableSet().apply {
                    if (it.expandedState.contains(key)) remove(key) else add(key)
                },
            )
        }
    }
    // KMK <--

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: PersistentList<UpdatesItem> = persistentListOf(),
        // KMK -->
        val expandedState: Set<String> = persistentSetOf(),
        // KMK <--
        val dialog: Dialog? = null,
    ) {
        val selected = items.filter { it.selected }
        val selectionMode = selected.isNotEmpty()

        fun getUiModel(): List<UpdatesUiModel> {
            // KMK -->
            var lastMangaId = -1L
            // KMK <--
            return items.groupBy { it.update.dateFetch.toLocalDate() }
                .flatMap { groupDate ->
                    groupDate.value.groupBy { it.update.animeId }
                        .flatMap { groupManga ->
                            val list = groupManga.value
                            list.sortedBy { it.update.dateFetch }
                                .map { UpdatesUiModel.Item(it, list.size > 1) }
                        }
                }
                .insertSeparators { before, after ->
                    val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                    val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                    when {
                        beforeDate != afterDate && afterDate != null -> UpdatesUiModel.Header(afterDate)
                        // Return null to avoid adding a separator between two items.
                        else -> null
                    }
                }
                // KMK -->
                .map {
                    if (it is UpdatesUiModel.Header) {
                        lastMangaId = -1L
                        it
                    } else {
                        if ((it as UpdatesUiModel.Item).item.update.animeId != lastMangaId) {
                            lastMangaId = it.item.update.animeId
                            UpdatesUiModel.Leader(it.item, it.isExpandable)
                        } else {
                            it
                        }
                    }
                }
            // KMK <--
        }
    }

    sealed interface Dialog {
        data class DeleteConfirmation(val toDelete: List<UpdatesItem>) : Dialog
        data class ShowQualities(
            val episodeTitle: String,
            val episodeId: Long,
            val animeId: Long,
            val sourceId: Long,
        ) : Dialog
    }

    sealed interface Event {
        data object InternalError : Event
        data class LibraryUpdateTriggered(val started: Boolean) : Event
    }
}

@Immutable
data class UpdatesItem(
    val update: UpdatesWithRelations,
    val downloadStateProvider: () -> Download.State,
    val downloadProgressProvider: () -> Int,
    val selected: Boolean = false,
    // AM (FILE_SIZE) -->
    var fileSize: Long?,
    // <-- AM (FILE_SIZE)
)

// KMK -->
fun UpdatesWithRelations.groupByDateAndAnime() = "${dateFetch.toLocalDate().toEpochDay()}-$animeId"
// KMK <--
