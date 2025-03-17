package eu.kanade.tachiyomi.ui.browse.migration.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateAnimeScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class MigrateAnimeScreen(
    private val sourceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateAnimeScreenModel(sourceId) }

        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        MigrateAnimeScreen(
            navigateUp = navigator::pop,
            title = state.source?.name ?: "???",
            state = state,
            onClickItem = {
                // SY -->
                PreMigrationScreen.navigateToMigration(
                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                    navigator,
                    listOf(it.anime.id),
                )
                // SY <--
            },
            onClickCover = { navigator.push(AnimeScreen(it.id)) },
            // KMK -->
            onMultiMigrateClicked = {
                if (state.selectionMode) {
                    PreMigrationScreen.navigateToMigration(
                        Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                        navigator,
                        state.selected.map { it.anime.id },
                    )
                } else {
                    context.toast(KMR.strings.migrating_all_entries)
                    PreMigrationScreen.navigateToMigration(
                        Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                        navigator,
                        state.titles.map { it.anime.id },
                    )
                }
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onAnimeSelected = screenModel::toggleSelection,
            // KMK <--
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    MigrationAnimeEvent.FailedFetchingFavorites -> {
                        context.toast(MR.strings.internal_error)
                    }
                }
            }
        }
    }
}
