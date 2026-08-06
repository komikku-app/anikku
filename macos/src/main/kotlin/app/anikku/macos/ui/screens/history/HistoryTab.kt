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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OverflowItem
import app.anikku.macos.ui.components.OverflowMenu
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.player.PlayerScreen
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
 * Shows the user's episode watch history chronologically, with search,
 * sort modes (recent / oldest / title / episode), and a per-anime collapse
 * option. Reads entries from [HistoryRepository], which persists history to
 * a JSON file.
 */
object HistoryTab : AnikkuScreen(), Tab {

    enum class SortMode { Recent, Oldest, TitleAsc, TitleDesc, Episode }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current
        val extensionManager = LocalExtensionManager.current
        val downloadManager = LocalDownloadManager.current
        // Revision-driven like Library/Stats: the repo bumps a revision on every
        // mutation, so history stays live even though this tab is keep-alive.
        val historyRevision by historyRepo.revision.collectAsState()
        val history = remember(historyRevision) { historyRepo.getAll() }

        var searchQuery by remember { mutableStateOf("") }
        var sortMode by remember { mutableStateOf(SortMode.Recent) }
        var showSortMenu by remember { mutableStateOf(false) }
        var showFilterMenu by remember { mutableStateOf(false) }

        val items = history.map { entry ->
            HistoryItemData(
                id = entry.episodeId,
                animeId = entry.animeId,
                animeTitle = entry.animeTitle,
                episodeName = entry.episodeName,
                episodeNumber = entry.episodeNumber,
                seenAt = entry.seenAt,
                sourceId = entry.sourceId,
                animeUrl = entry.animeUrl,
                episodeUrl = entry.episodeUrl,
                coverUrl = entry.coverUrl,
            )
        }

        // Filter by search query, then sort.
        val filtered = remember(items, searchQuery, sortMode) {
            var result = items
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim()
                result = result.filter {
                    it.animeTitle.contains(q, ignoreCase = true) ||
                        it.episodeName.contains(q, ignoreCase = true) ||
                        "Episode ${String.format("%.0f", it.episodeNumber)}".contains(q, ignoreCase = true)
                }
            }
            when (sortMode) {
                SortMode.Recent -> result.sortedByDescending { it.seenAt }
                SortMode.Oldest -> result.sortedBy { it.seenAt }
                SortMode.TitleAsc -> result.sortedWith(
                    compareBy<HistoryItemData> { it.animeTitle.lowercase() }.thenBy { it.episodeNumber },
                )
                SortMode.TitleDesc -> result.sortedWith(
                    compareByDescending<HistoryItemData> { it.animeTitle.lowercase() }.thenBy { it.episodeNumber },
                )
                SortMode.Episode -> result.sortedWith(
                    compareBy<HistoryItemData> { it.animeTitle.lowercase() }.thenBy { it.episodeNumber },
                )
            }
        }

        HistoryContent(
            history = filtered,
            totalCount = history.size,
            searchQuery = searchQuery,
            sortMode = sortMode,
            showSortMenu = showSortMenu,
            onSearchQueryChange = { searchQuery = it },
            onSortModeChange = { sortMode = it },
            onToggleSortMenu = { showSortMenu = !showSortMenu },
            onDismissSortMenu = { showSortMenu = false },
            onClearAll = {
                historyRepo.clearAll()
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
            onResumePlay = { item ->
                // Resume straight into the player — it reads the last watched
                // position from history itself.
                if (item.sourceId != 0L && !item.episodeUrl.isNullOrBlank()) {
                    navigator.push(
                        PlayerScreen(
                            animeId = item.animeId,
                            episodeId = item.id,
                            sourceId = item.sourceId,
                            episodeUrl = item.episodeUrl,
                            animeUrl = item.animeUrl,
                            animeTitle = item.animeTitle,
                            extensionManager = extensionManager,
                            downloadManager = downloadManager,
                            episodeNumber = item.episodeNumber,
                            episodeName = item.episodeName,
                            coverUrl = item.coverUrl,
                        )
                    )
                } else {
                    toastHost.show(
                        text = "Cannot resume — source information missing",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = null,
                        location = "HistoryTab.onResumePlay",
                    )
                }
            },
            onDeleteEntry = { item ->
                historyRepo.removeForEpisode(item.animeId, item.id)
                toastHost.show("Removed from history", ToastDuration.SHORT)
            },
            onRemoveFromLibrary = { item ->
                if (libraryRepo != null && libraryRepo.isInLibrary(item.animeId)) {
                    libraryRepo.remove(item.animeId)
                    toastHost.show("Removed from library", ToastDuration.SHORT)
                } else {
                    toastHost.show("Not in library", ToastDuration.SHORT)
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
    val episodeUrl: String? = null,
    val coverUrl: String? = null,
)

@Composable
private fun HistoryContent(
    history: List<HistoryItemData>,
    totalCount: Int = 0,
    searchQuery: String = "",
    sortMode: HistoryTab.SortMode = HistoryTab.SortMode.Recent,
    showSortMenu: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onSortModeChange: (HistoryTab.SortMode) -> Unit = {},
    onToggleSortMenu: () -> Unit = {},
    onDismissSortMenu: () -> Unit = {},
    onClearAll: () -> Unit = {},
    onAnimeClick: (HistoryItemData) -> Unit = {},
    onResumePlay: (HistoryItemData) -> Unit = {},
    onDeleteEntry: (HistoryItemData) -> Unit = {},
    onRemoveFromLibrary: (HistoryItemData) -> Unit = {},
) {
    if (history.isEmpty() && searchQuery.isBlank()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "No watch history",
            hint = "Episodes you watch will appear here",
        )
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
                    Column {
                        Text(
                            text = "Watch History",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "$totalCount episode${if (totalCount == 1) "" else "s"}"
                            } else {
                                "${history.size} of $totalCount"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            TextButton(onClick = onToggleSortMenu) {
                                Text("Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = onDismissSortMenu,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Recently viewed") },
                                    onClick = { onSortModeChange(HistoryTab.SortMode.Recent); onDismissSortMenu() },
                                    trailingIcon = {
                                        if (sortMode == HistoryTab.SortMode.Recent)
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest first") },
                                    onClick = { onSortModeChange(HistoryTab.SortMode.Oldest); onDismissSortMenu() },
                                    trailingIcon = {
                                        if (sortMode == HistoryTab.SortMode.Oldest)
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Title A → Z") },
                                    onClick = { onSortModeChange(HistoryTab.SortMode.TitleAsc); onDismissSortMenu() },
                                    trailingIcon = {
                                        if (sortMode == HistoryTab.SortMode.TitleAsc)
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Title Z → A") },
                                    onClick = { onSortModeChange(HistoryTab.SortMode.TitleDesc); onDismissSortMenu() },
                                    trailingIcon = {
                                        if (sortMode == HistoryTab.SortMode.TitleDesc)
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Episode number") },
                                    onClick = { onSortModeChange(HistoryTab.SortMode.Episode); onDismissSortMenu() },
                                    trailingIcon = {
                                        if (sortMode == HistoryTab.SortMode.Episode)
                                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                )
                            }
                        }
                        TextButton(onClick = onClearAll) {
                            Text("Clear All")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search history...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                )
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        text = "No matches for \"$searchQuery\"",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = history,
                    // episodeId alone is a URL hash and can collide across anime,
                    // which makes LazyColumn throw "Key was already used". The
                    // (animeId, episodeId) pair is unique per history entry.
                    key = { it.animeId to it.id },
                ) { entry ->
                    HistoryItem(
                        entry = entry,
                        onClick = { onAnimeClick(entry) },
                        onPlay = { onResumePlay(entry) },
                        overflowItems = listOf(
                            OverflowItem(
                                "Delete from history",
                                Icons.Outlined.Delete,
                                { onDeleteEntry(entry) },
                            ),
                            OverflowItem(
                                "Remove from library",
                                Icons.Outlined.History,
                                { onRemoveFromLibrary(entry) },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: HistoryItemData,
    onClick: () -> Unit = {},
    onPlay: (() -> Unit)? = null,
    overflowItems: List<OverflowItem>? = null,
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
            // Cover (falls back to initials when no URL is known).
            AnimeCoverImage(
                thumbnailUrl = entry.coverUrl,
                contentDescription = entry.animeTitle,
                title = entry.animeTitle,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

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

            // Quick resume straight into the player at the last position.
            if (onPlay != null) {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Resume playback",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (overflowItems != null) {
                OverflowMenu(
                    items = overflowItems,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
