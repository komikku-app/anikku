package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

data object HistoryTab : Tab {

    private val resumeLastEpisodeSeenEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            val index: UShort = when (currentNavigationStyle()) {
                NavStyle.MOVE_HISTORY_TO_MORE -> 5u
                NavStyle.MOVE_BROWSE_TO_MORE -> 3u
                else -> 2u
            }
            return TabOptions(
                index = index,
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastEpisodeSeenEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val fromMore = currentNavigationStyle() == NavStyle.MOVE_HISTORY_TO_MORE
        // Hoisted for history tab's search bar
        val snackbarHostState = SnackbarHostState()

        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { HistoryScreenModel() }
        val state by screenModel.state.collectAsState()
        val searchQuery by screenModel.query.collectAsState()

        val scope = rememberCoroutineScope()
        val navigateUp: (() -> Unit)? = if (fromMore) {
            {
                if (navigator.lastItem == HomeScreen) {
                    scope.launch { HomeScreen.openTab(HomeScreen.Tab.AnimeLib()) }
                } else {
                    navigator.pop()
                }
            }
        } else {
            null
        }

        suspend fun openEpisode(context: Context, episode: Episode?) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            if (episode != null) {
                MainActivity.startPlayerActivity(
                    context,
                    episode.animeId,
                    episode.id,
                    extPlayer,
                )
            } else {
                snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_episode))
            }
        }

        HistoryScreen(
            state = state,
            searchQuery = searchQuery,
            snackbarHostState = snackbarHostState,
            onSearchQueryChange = screenModel::search,
            onClickCover = { navigator.push(AnimeScreen(it)) },
            onClickResume = screenModel::getNextEpisodeForAnime,
            onDialogChange = screenModel::setDialog,
            navigateUp = navigateUp,
        )

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        if (all) {
                            screenModel.removeAllFromHistory(dialog.history.animeId)
                        } else {
                            screenModel.removeFromHistory(dialog.history)
                        }
                    },
                )
            }
            is HistoryScreenModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = screenModel::removeAllHistory,
                )
            }
            null -> {}
        }

        LaunchedEffect(state.list) {
            if (state.list != null) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            // AM (DISCORD) -->
            DiscordRPCService.setAnimeScreen(context, DiscordScreen.HISTORY)
            // <-- AM (DISCORD)
            screenModel.events.collectLatest { e ->
                when (e) {
                    HistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryScreenModel.Event.OpenEpisode -> openEpisode(context, e.episode)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                openEpisode(context, screenModel.getNextEpisode())
            }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
