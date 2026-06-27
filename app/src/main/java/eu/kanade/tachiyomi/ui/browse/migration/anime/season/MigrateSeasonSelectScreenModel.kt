package eu.kanade.tachiyomi.ui.browse.migration.anime.season

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
import eu.kanade.domain.anime.model.toDomainAnime
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.model.SAnime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSeasonSelectScreenModel(
    private val anime: Anime,
    sourceManager: SourceManager = Injekt.get(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
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

    private val hideInLibraryItems = sourcePreferences.hideInAnimeLibraryItems().get()
    val seasonPagerFlowFlow = flow { emit(anime) }
        .map { anime ->
            Pager(
                config = PagingConfig(pageSize = 25),
                pagingSourceFactory = {
                    SeasonListPagingSource {
                        source.getSeasonList(anime.toSAnime())
                    }
                },
            ).flow.map { pagingData ->
                pagingData.map {
                    networkToLocalAnime.await(it.toDomainAnime(anime.source))
                }
                    .filter { !hideInLibraryItems || !it.favorite }
                    .map { kotlinx.coroutines.flow.MutableStateFlow(it) }
            }
            .cachedIn(screenModelScope)
        }
        .stateIn(screenModelScope, SharingStarted.Lazily, emptyFlow())

    private class SeasonListPagingSource(
        private val loadSeasonList: suspend () -> List<SAnime>,
    ) : PagingSource<Int, SAnime>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SAnime> {
            return try {
                val seasonList = loadSeasonList()

                LoadResult.Page(
                    data = seasonList,
                    prevKey = null,
                    nextKey = null,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

        override fun getRefreshKey(state: PagingState<Int, SAnime>): Int? {
            return null
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    sealed interface Dialog {
        data class Migrate(val newAnime: Anime, val oldAnime: Anime) : Dialog
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
    )
}
