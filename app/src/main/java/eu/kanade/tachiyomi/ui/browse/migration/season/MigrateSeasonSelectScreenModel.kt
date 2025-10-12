// AY -->
package eu.kanade.tachiyomi.ui.browse.migration.season

import android.content.res.Configuration
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import exh.metadata.metadata.RaisedSearchMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.domain.manga.model.toSManga as toSAnime
import mihon.domain.manga.model.toDomainManga as toDomainAnime
import tachiyomi.domain.manga.interactor.NetworkToLocalManga as NetworkToLocalAnime

class MigrateSeasonSelectScreenModel(
    private val anime: Anime,
    sourceManager: SourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
) : StateScreenModel<MigrateSeasonSelectScreenModel.State>(State()) {

    var displayMode by sourcePreferences.sourceDisplayMode().asState(screenModelScope)
    val source = sourceManager.getOrStub(anime.source)

    fun getColumnsPreference(orientation: Int): GridCells {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) {
            libraryPreferences.landscapeColumns()
        } else {
            libraryPreferences.portraitColumns()
        }.get()
        return if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns)
    }

    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems().get()
    val seasonPagerFlowFlow = flow { emit(anime) }
        .map { anime ->
            Pager(
                config = PagingConfig(pageSize = 25),
                pagingSourceFactory = {
                    SeasonListPagingSource(/* ANK --> */source/* ANK <-- */) {
                        source.getSeasonList(anime.toSAnime())
                    }
                },
            ).flow.map { pagingData ->
                pagingData.map { (anime, metadata) ->
                    // ANK -->
                    getAnime.subscribe(anime.url, anime.source)
                        .map { it ?: anime }
                        .combineMetadata(metadata)
                        // ANK <--
                        .stateIn(ioCoroutineScope)
                }
                    .filter { !hideInLibraryItems || !it.value.first.favorite }
            }
                .cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    private class SeasonListPagingSource(
        // ANK -->
        private val source: AnimeSource,
        private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
        // ANK <--
        private val loadSeasonList: suspend () -> List<SAnime>,
    ) : PagingSource<Int, /* ANK --> */ Pair<Anime, RaisedSearchMetadata?> /* ANK <-- */>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, /* ANK --> */ Pair<Anime, RaisedSearchMetadata?> /* ANK <-- */> {
            return try {
                val seasonList = loadSeasonList()
                    // ANK -->
                    .map { sManga -> sManga.toDomainAnime(source.id) to null }
                    .let { pairs -> networkToLocalAnime(pairs.map { it.first }).zip(pairs.map { it.second }) }
                // ANK <--

                LoadResult.Page(
                    data = seasonList,
                    prevKey = null,
                    nextKey = null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, /* ANK --> */ Pair<Anime, RaisedSearchMetadata?> /* ANK <-- */>): Int? {
            return null
        }
    }

    // ANK -->
    private fun Flow<Manga>.combineMetadata(metadata: RaisedSearchMetadata?): Flow<Pair<Anime, RaisedSearchMetadata?>> {
        return flatMapLatest { anime ->
            flowOf(anime to metadata)
        }
    }
    // ANK <--

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    sealed interface Dialog {
        data class Select(val anime: Anime) : Dialog
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
    )
}
// <-- AY
