package eu.kanade.tachiyomi.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.stats.StatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

object StatsScreen : Screen {
    private fun readResolve(): Any = StatsScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val navigator = LocalNavigator.currentOrThrow

        val animeScreenModel = rememberScreenModel { StatsScreenModel() }
        val state by animeScreenModel.state.collectAsState()

        if (state is StatsScreenState.Loading) {
            LoadingScreen()
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                )
            },
        ) { contentPadding ->
            if (state is StatsScreenState.Loading) {
                LoadingScreen()
            } else {
                StatsScreenContent(
                    state = state as StatsScreenState.SuccessManga,
                    paddingValues = contentPadding,
                )
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
