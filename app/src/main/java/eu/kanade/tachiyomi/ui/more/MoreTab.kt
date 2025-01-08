package eu.kanade.tachiyomi.ui.more

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.category.CategoriesScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatisticsScreen
import eu.kanade.tachiyomi.ui.storage.StorageScreen
import eu.kanade.tachiyomi.util.system.isInstalledFromFDroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object MoreTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsState()
        val navStyle = currentNavigationStyle()
        MoreScreen(
            downloadQueueStateProvider = { downloadQueueState },
            downloadedOnly = screenModel.downloadedOnly,
            onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
            incognitoMode = screenModel.incognitoMode,
            onIncognitoModeChange = { screenModel.incognitoMode = it },
            isFDroid = context.isInstalledFromFDroid(),
            navStyle = navStyle,
            onClickAlt = { navigator.push(navStyle.moreTab) },
            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onClickCategories = { navigator.push(CategoriesScreen) },
            onClickStats = { navigator.push(StatisticsScreen) },
            onClickStorage = { navigator.push(StorageScreen) },
            onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
            onClickPlayerSettings = { navigator.push(PlayerSettingsScreen) },
            onClickSettings = { navigator.push(SettingsScreen()) },
            onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
            // AM (DISCORD) -->
            DiscordRPCService.setAnimeScreen(context, DiscordScreen.MORE)
            DiscordRPCService.setMangaScreen(context, DiscordScreen.MORE)
            // <-- AM (DISCORD)
        }
    }
}

private class MoreScreenModel(
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
) : ScreenModel {

    var downloadedOnly by preferences.downloadedOnly().asState(screenModelScope)
    var incognitoMode by preferences.incognitoMode().asState(screenModelScope)

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(
        DownloadQueueState.Stopped,
    )
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        // Handle running/paused status change and queue progress updating
        screenModelScope.launchIO {
            combine(
                animeDownloadManager.isDownloaderRunning,
                animeDownloadManager.queueState,
            ) { isRunningAnime, animeDownloadQueue ->
                Pair(
                    isRunningAnime,
                    animeDownloadQueue.size,
                )
            }
                .collectLatest { (isDownloadingAnime, animeDownloadQueueSize) ->
                    val pendingDownloadExists = animeDownloadQueueSize != 0
                    _downloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloadingAnime -> DownloadQueueState.Paused(animeDownloadQueueSize)
                        else -> DownloadQueueState.Downloading(animeDownloadQueueSize)
                    }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
