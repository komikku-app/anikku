package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateSearchScreenDialogScreenModel(
    val mangaId: Long,
    getAnime: GetAnime = Injekt.get(),
) : StateScreenModel<MigrateSearchScreenDialogScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val manga = getAnime.await(mangaId)!!

            mutableState.update {
                it.copy(manga = manga)
            }
        }
    }

    @Immutable
    data class State(
        val manga: Anime? = null,
    )
}
