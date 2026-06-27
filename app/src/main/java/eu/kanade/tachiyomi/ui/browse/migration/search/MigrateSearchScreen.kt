package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen

class MigrateSearchScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateSearchScreenModel(animeId = animeId) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel {
            MigrateSearchScreenDialogScreenModel(
                animeId = animeId,
            )
        }
        val dialogState by dialogScreenModel.state.collectAsState()

        MigrateSearchScreen(
            state = state,
            fromSourceId = dialogState.anime?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getAnime = { screenModel.getAnime(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                navigator.push(
                    SourceSearchScreen(dialogState.anime!!, it.id, state.searchQuery),
                )
            },
            onClickItem = {
                dialogScreenModel.setDialog(
                    (MigrateSearchScreenDialogScreenModel.Dialog.Migrate(it)),
                )
            },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is MigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateDialog(
                    oldAnime = dialogState.anime!!,
                    newAnime = dialog.anime,
                    screenModel = rememberScreenModel { MigrateDialogScreenModel() },
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(AnimeScreen(dialog.anime.id, true))
                    },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialogState.anime!!, dialog.anime)) },
                    onPopScreen = {
                        if (navigator.lastItem is AnimeScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(AnimeScreen(dialog.anime.id))
                        } else {
                            navigator.replace(AnimeScreen(dialog.anime.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
