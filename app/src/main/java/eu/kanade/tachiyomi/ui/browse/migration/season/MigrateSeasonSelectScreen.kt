// AY -->
package eu.kanade.tachiyomi.ui.browse.migration.season

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
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.feature.migration.dialog.SelectAnimeDialog
import mihon.feature.migration.list.MigrationListScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.Constants
import tachiyomi.domain.anime.model.Anime
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.ui.manga.MangaScreen as AnimeScreen
import mihon.feature.migration.dialog.MigrateMangaDialog as MigrateAnimeDialog

data class MigrateSeasonSelectScreen(
    private val oldAnime: Anime,
    private val anime: Anime,
    private val isFromList: Boolean = false,
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
            val openDialog: (Anime) -> Unit = {
                val dialog = if (isFromList) {
                    MigrateSeasonSelectScreenModel.Dialog.Select(anime = it)
                } else {
                    MigrateSeasonSelectScreenModel.Dialog.Migrate(newAnime = it, oldAnime = oldAnime)
                }
                screenModel.setDialog(dialog)
            }
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = screenModel.seasonPagerFlowFlow.collectAsLazyPagingItems(),
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = {
                    val source = screenModel.source as? AnimeHttpSource ?: return@BrowseSourceContent
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
                onMangaClick = openDialog,
                onMangaLongClick = { navigator.push(AnimeScreen(it.id, true)) },
                // KMK -->
                selection = emptyList(),
                // KMK <--
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is MigrateSeasonSelectScreenModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    current = dialog.oldAnime,
                    target = dialog.newAnime,
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.newAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.newAnime)) },
                    onComplete = {
                        val animeScreen = navigator.items
                            .filterIsInstance<AnimeScreen>()
                            .lastOrNull()

                        if (animeScreen != null) {
                            navigator.popUntil { it is AnimeScreen }
                            navigator.push(AnimeScreen(dialog.newAnime.id))
                        }
                    },
                )
            }
            is MigrateSeasonSelectScreenModel.Dialog.Select -> {
                SelectAnimeDialog(
                    selected = dialog.anime,
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.anime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(oldAnime, dialog.anime, true)) },
                    onClickSelect = {
                        val migrateListScreen = navigator.items
                            .filterIsInstance<MigrationListScreen>()
                            .last()

                        migrateListScreen.addMatchOverride(current = oldAnime.id, target = dialog.anime.id)
                        navigator.popUntil { screen -> screen is MigrationListScreen }
                    },
                )
            }
            null -> {}
        }
    }
}
// <-- AY
