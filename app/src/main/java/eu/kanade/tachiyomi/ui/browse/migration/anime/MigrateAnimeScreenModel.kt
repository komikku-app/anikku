package eu.kanade.tachiyomi.ui.browse.migration.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeScreenModel(
    private val sourceId: Long,
    private val sourceManager: SourceManager = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
) : StateScreenModel<MigrateAnimeScreenModel.State>(State()) {

    private val _events: Channel<MigrationAnimeEvent> = Channel()
    val events: Flow<MigrationAnimeEvent> = _events.receiveAsFlow()

    // KMK -->
    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedMangaIds: HashSet<Long> = HashSet()
    // KMK <--

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationAnimeEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                // KMK -->
                .map { manga ->
                    toMigrationMangaScreenItems(manga)
                }
                // KMK <--
                .map { manga ->
                    manga
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.anime.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    mutableState.update { it.copy(titleList = list) }
                }
        }
    }

    // KMK -->
    private fun toMigrationMangaScreenItems(animes: List<Anime>): List<MigrateAnimeItem> {
        return animes.map { manga ->
            MigrateAnimeItem(
                anime = manga,
                selected = manga.id in selectedMangaIds,
            )
        }
    }

    fun toggleSelection(
        item: MigrateAnimeItem,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        mutableState.update { state ->
            val newItems = state.titles.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.anime.id == item.anime.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedMangaIds.addOrRemove(item.anime.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1 until selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1) until selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inBetweenItem = get(it)
                            if (!inBetweenItem.selected) {
                                selectedMangaIds.add(inBetweenItem.anime.id)
                                set(it, inBetweenItem.copy(selected = true))
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
            state.copy(titleList = newItems.toImmutableList())
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.titles.map {
                selectedMangaIds.addOrRemove(it.anime.id, selected)
                it.copy(selected = selected)
            }
            state.copy(titleList = newItems.toImmutableList())
        }

        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.titles.map {
                selectedMangaIds.addOrRemove(it.anime.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            state.copy(titleList = newItems.toImmutableList())
        }
        selectedPositions[0] = -1
        selectedPositions[1] = -1
    }
    // KMK <--

    @Immutable
    data class State(
        val source: Source? = null,
        private val titleList: ImmutableList<MigrateAnimeItem>? = null,
    ) {
        // KMK -->
        val selected = titles.filter { it.selected }
        val selectionMode = selected.isNotEmpty()
        // KMK <--

        val titles: ImmutableList<MigrateAnimeItem>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()
    }
}

sealed interface MigrationAnimeEvent {
    data object FailedFetchingFavorites : MigrationAnimeEvent
}

// KMK -->
@Immutable
data class MigrateAnimeItem(
    val anime: Anime,
    val selected: Boolean,
)
// KMK <--
