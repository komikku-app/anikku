package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationScreen
import tachiyomi.domain.UnsortedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryUpdateErrorScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { LibraryUpdateErrorScreenModel() }
        val state by screenModel.state.collectAsState()

        LibraryUpdateErrorScreen(
            state = state,
            onClick = { item ->
                PreMigrationScreen.navigateToMigration(
                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                    navigator,
                    listOf(item.error.animeId),
                )
            },
            onClickCover = { item -> navigator.push(AnimeScreen(item.error.animeId)) },
            onMultiMigrateClicked = {
                PreMigrationScreen.navigateToMigration(
                    Injekt.get<UnsortedPreferences>().skipPreMigration().get(),
                    navigator,
                    state.selected.map { it.error.animeId },
                )
            },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onErrorSelected = screenModel::toggleSelection,
            navigateUp = navigator::pop,
        )
    }
}
