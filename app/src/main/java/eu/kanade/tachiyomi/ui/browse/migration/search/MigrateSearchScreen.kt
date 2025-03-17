package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.AllowDuplicateDialog
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.ChangeAnimesCategoryDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigrationListScreen
import tachiyomi.core.common.util.lang.launchIO

class MigrateSearchScreen(private val mangaId: Long, private val validSources: List<Long>) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel =
            rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId, validSources = validSources) }
        val state by screenModel.state.collectAsState()

        val dialogScreenModel = rememberScreenModel { MigrateSearchScreenDialogScreenModel(mangaId = mangaId) }
        val dialogState by dialogScreenModel.state.collectAsState()

        // KMK -->
        val scope = rememberCoroutineScope()
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.toggleSelectionMode()
        }
        // KMK <--

        MigrateSearchScreen(
            state = state,
            fromSourceId = state.fromSourceId,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getAnime = { screenModel.getAnime(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = {
                // SY -->
                navigator.push(SourceSearchScreen(dialogState.manga!!, it.id, state.searchQuery))
                // SY <--
            },
            onClickItem = {
                // KMK -->
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    if (bulkFavoriteState.selectionMode) {
                        bulkFavoriteScreenModel.toggleSelection(manga)
                    } else
                        // KMK <--
                        {
                            // SY -->
                            navigator.items
                                .filterIsInstance<MigrationListScreen>()
                                .last()
                                .newSelectedItem = mangaId to it.id
                            navigator.popUntil { it is MigrationListScreen }
                            // SY <--
                        }
                }
            },
            onLongClickItem = {
                // KMK -->
                scope.launchIO {
                    val manga = screenModel.networkToLocalAnime.getLocal(it)
                    // KMK <--
                    navigator.push(AnimeScreen(manga.id, true))
                }
            },
            // KMK -->
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            hasPinnedSources = screenModel.hasPinnedSources(),
            // KMK <--
        )

        // KMK -->
        when (bulkFavoriteState.dialog) {
            is BulkFavoriteScreenModel.Dialog.ChangeMangasCategory ->
                ChangeAnimesCategoryDialog(bulkFavoriteScreenModel)
            is BulkFavoriteScreenModel.Dialog.AllowDuplicate ->
                AllowDuplicateDialog(bulkFavoriteScreenModel)
            else -> {}
        }
        // KMK <--
    }
}
