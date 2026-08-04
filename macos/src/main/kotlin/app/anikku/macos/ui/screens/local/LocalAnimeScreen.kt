package app.anikku.macos.ui.screens.local

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.local.LocalAnimeGroup
import app.anikku.macos.platform.local.LocalVideoEntry
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.player.PlayerScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

/**
 * Local video collection menu: every season/episode of one folder-imported
 * anime. Rows play the file through the player's local-file path (served over
 * the local HTTP server) with full history/resume support.
 */
data class LocalAnimeScreen(
    val displayTitle: String,
    val group: LocalAnimeGroup,
    val onRemoveAnime: (() -> Unit)? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val downloadManager = LocalDownloadManager.current

        fun play(entry: LocalVideoEntry) {
            if (!java.io.File(entry.filePath).isFile) {
                toastHost.show("File not found: ${entry.fileName}", ToastDuration.LONG, isError = true)
                return
            }
            navigator.push(
                PlayerScreen(
                    animeId = entry.animeId,
                    episodeId = entry.filePath.hashCode().toLong().let { if (it == 0L) 1L else it },
                    sourceId = null,
                    episodeUrl = entry.filePath,
                    animeTitle = entry.title,
                    episodeNumber = entry.episode.takeIf { it > 0 }?.toDouble(),
                    episodeName = entry.fileName,
                    downloadManager = downloadManager,
                ),
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        if (onRemoveAnime != null) {
                            IconButton(
                                onClick = {
                                    onRemoveAnime()
                                    toastHost.show("Removed \"$displayTitle\" from local library", ToastDuration.SHORT)
                                    navigator.pop()
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove from local library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "${group.episodeCount} episodes · ${group.totalFiles} files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Watched offline from your local library — history and tracker progress sync like any other episode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                group.seasons.forEach { season ->
                    item(key = "season_${season.season}") {
                        SectionHeader("Season ${season.season}", "${season.episodes.size} episodes")
                    }
                    items(season.episodes, key = { it.filePath }) { entry ->
                        LocalEpisodeRow(entry = entry, onPlay = { play(entry) })
                    }
                }

                if (group.other.isNotEmpty()) {
                    item(key = "other_header") {
                        SectionHeader("Other files", "${group.other.size}")
                    }
                    items(group.other, key = { it.filePath }) { entry ->
                        LocalEpisodeRow(entry = entry, onPlay = { play(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalEpisodeRow(entry: LocalVideoEntry, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (entry.episode > 0) "Ep ${entry.episode}" else "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
