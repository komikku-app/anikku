package app.anikku.macos.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OfflineBadge
import app.anikku.macos.ui.components.ToastDuration
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey

/**
 * Download queue screen — Phase 7: Real Download Pipeline.
 *
 * Shows ongoing and completed downloads with live progress bars.
 * Supports pause/resume, cancellation, and retry of individual downloads.
 * Uses MacOSDownloadManager for the actual download logic.
 */
class DownloadQueueScreen : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val downloadManager = LocalDownloadManager.current
        val toastHost = LocalToastHost.current

        // Collect real download data
        val downloads by if (downloadManager != null) {
            downloadManager.downloads.collectAsState()
        } else {
            remember { mutableStateOf(emptyList<DownloadRepository.DownloadEntry>()) }
        }

        val data = DownloadQueueData(downloads, downloadManager)

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
        )
    }
}

private data class DownloadQueueData(
    val downloads: List<DownloadRepository.DownloadEntry>,
    val manager: MacOSDownloadManager?,
)

@Composable
private fun DownloadQueueContent(
    data: DownloadQueueData,
    onPauseResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onClearAll: () -> Unit,
    onClearCompleted: () -> Unit,
) {
    val downloads = data.downloads
    val activeDownloads = downloads.count { it.isActive }
    val completedDownloads = downloads.count { it.status == DownloadRepository.DownloadStatus.COMPLETED }
    val errorDownloads = downloads.count { it.status == DownloadRepository.DownloadStatus.ERROR }
    val completedBytes = downloads.filter { it.status == DownloadRepository.DownloadStatus.COMPLETED }
        .sumOf { it.totalBytes }
    val freeBytes = data.manager?.downloadsDirectory()?.let { dir ->
        if (dir.exists()) dir.usableSpace else 0L
    } ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildString {
                            if (activeDownloads > 0) append("$activeDownloads active · ")
                            append("$completedDownloads completed")
                            if (errorDownloads > 0) append(" · $errorDownloads failed")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (completedDownloads > 0 || freeBytes > 0) {
                        Text(
                            text = buildString {
                                append("${formatBytes(completedBytes)} downloaded")
                                if (freeBytes > 0) append(" · ${formatBytes(freeBytes)} free")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (completedDownloads > 0) {
                        TextButton(onClick = onClearCompleted) {
                            Text("Clear completed", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (downloads.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear all", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No downloads",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (data.manager == null) "Download manager not initialized"
                            else "Tap the download button on any episode to save for offline viewing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        } else {
            items(
                items = downloads,
                key = { it.id },
            ) { item ->
                DownloadItemCard(
                    item = item,
                    onPauseResume = { onPauseResume(item.id) },
                    onCancel = { onCancel(item.id) },
                    onRetry = { onRetry(item.id) },
                )
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadRepository.DownloadEntry,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                DownloadRepository.DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                DownloadRepository.DownloadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.animeTitle.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.status == DownloadRepository.DownloadStatus.COMPLETED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.episodeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            OfflineBadge()
                        }
                    } else {
                        Text(
                            text = item.episodeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Status / action buttons
                when (item.status) {
                    DownloadRepository.DownloadStatus.QUEUED,
                    DownloadRepository.DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadRepository.DownloadStatus.PAUSED -> {
                        IconButton(onClick = onPauseResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    DownloadRepository.DownloadStatus.COMPLETED -> {
                        // Badge shown inline with episode name above
                    }
                    DownloadRepository.DownloadStatus.ERROR -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                if (item.status != DownloadRepository.DownloadStatus.COMPLETED) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Progress bar for active downloads
            if (item.status == DownloadRepository.DownloadStatus.QUEUED ||
                item.status == DownloadRepository.DownloadStatus.DOWNLOADING ||
                item.status == DownloadRepository.DownloadStatus.PAUSED
            ) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when (item.status) {
                        DownloadRepository.DownloadStatus.PAUSED ->
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        DownloadRepository.DownloadStatus.QUEUED ->
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(4.dp))
                val statusText = when (item.status) {
                    DownloadRepository.DownloadStatus.QUEUED -> "Queued"
                    DownloadRepository.DownloadStatus.DOWNLOADING ->
                        formatBytes(item.downloadedBytes) + " / " + formatBytes(item.totalBytes) +
                            " (${(item.progress * 100).toInt()}%)"
                    DownloadRepository.DownloadStatus.PAUSED -> "Paused"
                    else -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            // Error message
            if (item.status == DownloadRepository.DownloadStatus.ERROR) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Download failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
