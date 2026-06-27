package eu.kanade.tachiyomi.ui.browse.migration.anime.season

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.BrowseSourceContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialog
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateDialogScreenModel
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.domain.anime.model.Anime
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource

data class MigrateSeasonSelectScreen(
    private val oldAnime: Anime,
    private val anime: Anime,
) : Screen() {
    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateSeasonSelectScreenModel(anime) }
        val state by screenModel.state.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = anime.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            val openMigrateDialog: (Anime) -> Unit = {
                screenModel.setDialog(MigrateSeasonSelectScreenModel.Dialog.Migrate(newAnime = it, oldAnime = oldAnime))
            }
            BrowseSourceContent(
                source = screenModel.source,
                animeList = screenModel.seasonPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = screenModel.source as? HttpSource ?: return@BrowseSourceContent
                    navigator.push(
                        WebViewScreen(
                            url = source.baseUrl,
                            initialTitle = source.name,
                            sourceId = source.id,
                        ),
                    )
                },
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = { uriHandler.openUri(LocalSource.HELP_URL) },
                onAnimeClick = openMigrateDialog,
                onAnimeLongClick = { navigator.push(AnimeScreen(it.id, true)) },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is MigrateSeasonSelectScreenModel.Dialog.Migrate -> {
                MigrateDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    screenModel = rememberScreenModel { MigrateDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.newAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.newAnime)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            else -> {}
        }
    }
}
