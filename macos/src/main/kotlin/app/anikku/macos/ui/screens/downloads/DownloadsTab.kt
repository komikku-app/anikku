package app.anikku.macos.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.player.PlayerScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Downloads tab — the in-app download queue as a first-class tab (it used to
 * live buried under Settings > Downloads). Same rows/actions as the settings
 * screen, plus a Play button on completed downloads.
 */
object DownloadsTab : AnikkuScreen(), Tab {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val downloadManager = LocalDownloadManager.current
        val toastHost = LocalToastHost.current
        val extensionManager = LocalExtensionManager.current

        val downloads by if (downloadManager != null) {
            downloadManager.downloads.collectAsState()
        } else {
            remember { mutableStateOf(emptyList<DownloadRepository.DownloadEntry>()) }
        }

        val data = DownloadQueueData(downloads, downloadManager)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Downloads") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                DownloadQueueContent(
                    data = data,
                    onPauseResume = { id ->
                        val item = downloads.find { it.id == id } ?: return@DownloadQueueContent
                        if (item.status == DownloadRepository.DownloadStatus.PAUSED) {
                            downloadManager?.resume(id)
                        } else if (item.status == DownloadRepository.DownloadStatus.DOWNLOADING) {
                            downloadManager?.pause(id)
                        }
                    },
                    onCancel = { id ->
                        val item = downloads.find { it.id == id } ?: return@DownloadQueueContent
                        downloadManager?.cancel(id)
                        toastHost.show("Cancelled: ${item.animeTitle}", ToastDuration.SHORT)
                    },
                    onRetry = { id ->
                        downloadManager?.retry(id)
                        toastHost.show("Retrying download", ToastDuration.SHORT)
                    },
                    onRemoveCompleted = { id ->
                        val item = downloads.find { it.id == id } ?: return@DownloadQueueContent
                        downloadManager?.cancel(id) // cancel cleans the file + removes the entry
                        toastHost.show("Removed: ${item.animeTitle}", ToastDuration.SHORT)
                    },
                    onClearAll = {
                        downloadManager?.cancelAll()
                        toastHost.show("All downloads cancelled", ToastDuration.SHORT)
                    },
                    onClearCompleted = {
                        val removed = downloadManager?.removeCompleted() ?: 0
                        toastHost.show(
                            if (removed > 0) "Cleared $removed completed download${if (removed == 1) "" else "s"}"
                            else "No completed downloads to clear",
                            ToastDuration.SHORT,
                        )
                    },
                    onPlay = { item ->
                        navigator.push(
                            PlayerScreen(
                                animeId = item.animeId,
                                episodeId = (item.episodeUrl ?: item.filePath ?: item.id.toString())
                                    .hashCode().toLong().let { if (it == 0L) 1L else it },
                                sourceId = item.sourceId.takeIf { it != 0L },
                                episodeUrl = item.episodeUrl,
                                animeTitle = item.animeTitle,
                                episodeNumber = item.episodeNumber,
                                episodeName = item.episodeName,
                                extensionManager = extensionManager,
                                downloadManager = downloadManager,
                            ),
                        )
                    },
                )
            }
        }
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 6u,
            title = "Downloads",
            icon = rememberVectorPainter(Icons.Outlined.CloudDownload),
        )
}
