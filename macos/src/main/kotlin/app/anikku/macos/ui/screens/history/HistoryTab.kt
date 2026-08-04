package app.anikku.macos.ui.screens.history

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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen tab — Phase 5.
 *
 * Shows the user's episode watch history chronologically.
 * Reads entries from [HistoryRepository], which persists history to a JSON file.
 */
object HistoryTab : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val historyRepo = LocalHistoryRepository.current
        val extensionManager = LocalExtensionManager.current
        var history by remember { mutableStateOf(historyRepo.getAll()) }

        HistoryContent(
            history = history.map { entry ->
                HistoryItemData(
                    id = entry.episodeId,
                    animeId = entry.animeId,
                    animeTitle = entry.animeTitle,
                    episodeName = entry.episodeName,
                    episodeNumber = entry.episodeNumber,
                    seenAt = entry.seenAt,
                    sourceId = entry.sourceId,
                    animeUrl = entry.animeUrl,
                )
            },
            onClearAll = {
                historyRepo.clearAll()
                history = emptyList()
                toastHost.show("History cleared", ToastDuration.SHORT)
            },
            onAnimeClick = { item ->
                if (item.sourceId != 0L && !item.animeUrl.isNullOrBlank()) {
                    navigator.push(
                        AnimeDetailScreen(
                            animeId = item.animeId,
                            sourceId = item.sourceId,
                            animeUrl = item.animeUrl,
                            animeTitle = item.animeTitle,
                            extensionManager = extensionManager,
                        )
                    )
                } else {
                    toastHost.show(
                        text = "Cannot resume — source information missing",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = null,
                        location = "HistoryTab.onAnimeClick",
                    )
                }
            },
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = "History",
            icon = rememberVectorPainter(Icons.Outlined.History),
        )
}

data class HistoryItemData(
    val id: Long,
    val animeId: Long = 0L,
    val animeTitle: String,
    val episodeName: String,
    val episodeNumber: Double,
    val seenAt: Long,
    val sourceId: Long = 0L,
    val animeUrl: String? = null,
)

@Composable
private fun HistoryContent(
    history: List<HistoryItemData>,
    onClearAll: () -> Unit = {},
    onAnimeClick: (HistoryItemData) -> Unit = {},
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No watch history",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Episodes you watch will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Watch History",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onClearAll) {
                        Text("Clear All")
                    }
                }
            }

            items(
                items = history,
                // episodeId alone is a URL hash and can collide across anime,
                // which makes LazyColumn throw "Key was already used". The
                // (animeId, episodeId) pair is unique per history entry.
                key = { it.animeId to it.id },
            ) { entry ->
                HistoryItem(entry = entry, onClick = { onAnimeClick(entry) })
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: HistoryItemData,
    onClick: () -> Unit = {},
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.animeTitle.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Episode ${String.format("%.0f", entry.episodeNumber)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.seenAt > 0L) {
                    Text(
                        text = dateFormat.format(Date(entry.seenAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
