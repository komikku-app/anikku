package eu.kanade.presentation.anime

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
private val playerPreferences = Injekt.get<PlayerPreferences>()

class EpisodeOptionsDialogScreen(
    private val useExternalDownloader: Boolean,
    private val episodeTitle: String,
    private val episodeId: Long,
    private val animeId: Long,
    private val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val sm = rememberScreenModel {
            EpisodeOptionsDialogScreenModel(
                episodeId = episodeId,
                animeId = animeId,
                sourceId = sourceId,
            )
        }
        val state by sm.state.collectAsState()

        EpisodeOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            episodeTitle = episodeTitle,
            episode = state.episode,
            anime = state.anime,
            resultList = state.resultList,
        )
    }

    companion object {
        var onDismissDialog: () -> Unit = {}
    }
}

class EpisodeOptionsDialogScreenModel(
    episodeId: Long,
    animeId: Long,
    sourceId: Long,
) : StateScreenModel<State>(State()) {
    private val sourceManager: SourceManager = Injekt.get()

    init {
        screenModelScope.launch {
            // To show loading state
            mutableState.update { it.copy(episode = null, anime = null, resultList = null) }

            val episode = Injekt.get<GetEpisode>().await(episodeId)!!
            val anime = Injekt.get<GetAnime>().await(animeId)!!
            val source = sourceManager.getOrStub(sourceId)

            val result = withIOContext {
                try {
                    val results = EpisodeLoader.getLinks(episode, anime, source)
                    Result.success(results)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }

            mutableState.update { it.copy(episode = episode, anime = anime, resultList = result) }
        }
    }
}

@Immutable
data class State(
    val episode: Episode? = null,
    val anime: Anime? = null,
    val resultList: Result<List<Video>>? = null,
)

@Composable
fun EpisodeOptionsDialog(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episode: Episode?,
    anime: Anime?,
    resultList: Result<List<Video>>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = TabbedDialogPaddings.Vertical)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = episodeTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(MR.strings.choose_video_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (resultList == null || episode == null || anime == null) {
            LoadingScreen()
        } else {
            val videoList = resultList.getOrNull()
            if (!videoList.isNullOrEmpty()) {
                VideoList(
                    useExternalDownloader = useExternalDownloader,
                    episode = episode,
                    anime = anime,
                    videoList = videoList,
                )
            } else {
                logcat(LogPriority.ERROR) { "Error getting links" }
                scope.launchUI { context.toast("Video list is empty") }
                EpisodeOptionsDialogScreen.onDismissDialog()
            }
        }
    }
}

@Composable
private fun VideoList(
    useExternalDownloader: Boolean,
    episode: Episode,
    anime: Anime,
    videoList: List<Video>,
) {
    val downloadManager = Injekt.get<DownloadManager>()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(MR.strings.copied_video_link_to_clipboard)

    var showAllQualities by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf(videoList.first()) }

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (selectedVideo.videoUrl != null && !showAllQualities) {
                ClickableRow(
                    text = selectedVideo.quality,
                    icon = null,
                    onClick = { showAllQualities = true },
                    showDropdownArrow = true,
                )

                val downloadEpisode: (Boolean) -> Unit = {
                    downloadManager.downloadEpisodes(
                        anime,
                        listOf(episode),
                        true,
                        it,
                        selectedVideo,
                    )
                }

                QualityOptions(
                    onDownloadClicked = { downloadEpisode(useExternalDownloader) },
                    onExtDownloadClicked = { downloadEpisode(!useExternalDownloader) },
                    onCopyClicked = {
                        clipboardManager.setText(AnnotatedString(selectedVideo.videoUrl!!))
                        scope.launch { context.toast(copiedString) }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                true,
                                selectedVideo,
                                videoList,
                            )
                        }
                    },
                    onIntPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                false,
                                selectedVideo,
                                videoList,
                            )
                        }
                    },
                    // start tail cast
                    onCastClicked = {
                        scope.launch {
                            if (playerPreferences.enableCast().get()) {
                                sendEpisodesToCast(
                                    context,
                                    anime.title,
                                    episode.name,
                                    episode.lastSecondSeen,
                                    anime.thumbnailUrl.orEmpty(),
                                    selectedVideo.videoUrl!!,
                                )
                            } else {
                                context.toast("Cast is disabled")
                            }
                        }
                    },
                    // end tail cast
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Column {
                videoList.forEach { video ->
                    ClickableRow(
                        text = video.quality,
                        icon = null,
                        onClick = {
                            selectedVideo = video
                            showAllQualities = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
    onIntPlayerClicked: () -> Unit = {},
    onCastClicked: () -> Unit = {},
) {
    val closeMenu = { EpisodeOptionsDialogScreen.onDismissDialog() }

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_internally),
            icon = Icons.Outlined.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_start_download_externally),
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_play_externally),
            icon = Icons.Outlined.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(MR.strings.action_play_internally),
            icon = Icons.Outlined.Input,
            onClick = {
                onIntPlayerClicked()
                closeMenu()
            },

        )
        ClickableRow(
            text = stringResource(MR.strings.action_cast), // Texto para la nueva opción
            icon = Icons.Outlined.Cast, // Icono para la nueva opción
            onClick = {
                onCastClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showDropdownArrow) {
            Icon(
                imageVector = Icons.Outlined.NavigateNext,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// Start tail cast

private fun sendEpisodesToCast(
    context: Context,
    title: String,
    episode: String,
    lastSecondSeen: Long,
    image: String,
    videoUrl: String,
) {
    val castSession = CastContext.getSharedInstance(context).sessionManager.currentCastSession
    val remoteMediaClient = castSession?.remoteMediaClient
    if (castSession == null || !castSession.isConnected) {
        Toast.makeText(context, "Cast is not connected", Toast.LENGTH_SHORT).show()
        return
    }
    if (remoteMediaClient != null) {
        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, episode)
            addImage(WebImage(Uri.parse(image)))
        }
        val mediaInfo = MediaInfo.Builder(videoUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(mediaMetadata)
            .build()
        val mediaQueueItem = MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .setStartTime(lastSecondSeen.toDouble() / 1000)
            .build()
        val mediaStatus = remoteMediaClient.mediaStatus
        if (mediaStatus != null && mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING) {
            // Si hay un video reproduciéndose, agregar el nuevo video a la cola
            remoteMediaClient.queueAppendItem(mediaQueueItem, null)
        } else {
            // Si no hay un video reproduciéndose, cargar el video directamente
            val mediaLoadRequestData = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setCurrentTime(lastSecondSeen)
                .build()
            remoteMediaClient.load(mediaLoadRequestData)
        }
    } else {
        Toast.makeText(context, "remoteMediaClient is null", Toast.LENGTH_SHORT).show()
    }
}

// End tail cast0
