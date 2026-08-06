package app.anikku.macos.ui.screens.updates

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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.LocalBackgroundJobs
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.ErrorBanner
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.components.ErrorType
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Updates screen tab — Phase 5.
 *
 * Shows recent episode updates from tracked anime.
 * Reads library entries and checks sources for new episodes.
 * Falls back gracefully when sources or library are unavailable.
 */
object UpdatesTab : AnikkuScreen(), Tab {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current
        val extensionManager = LocalExtensionManager.current
        val backgroundJobs = LocalBackgroundJobs.current
        val backgroundStatus = backgroundJobs?.status?.collectAsState()?.value

        val libraryRevision by libraryRepo.revision.collectAsState()
        val libraryEntries = remember(libraryRevision) { libraryRepo.getAll() }
        val historyRevision by historyRepo.revision.collectAsState()
        val historyEntries = remember(historyRevision) { historyRepo.getAll() }

        // Build update list from library + history
        val updates = remember(libraryEntries, historyEntries) {
            if (libraryEntries.isEmpty()) {
                emptyList()
            } else {
                // For each library entry, find the most recent history entry
                libraryEntries.mapNotNull { libEntry ->
                    if (libEntry.latestEpisodeNumber > 0.0) {
                        return@mapNotNull UpdateItemData(
                            animeId = libEntry.animeId,
                            animeTitle = libEntry.title,
                            episodeId = stableEpisodeId(libEntry.animeId, libEntry.latestEpisodeNumber),
                            episodeName = libEntry.latestEpisodeName
                                ?: "Episode ${formatEpisodeNumber(libEntry.latestEpisodeNumber)}",
                            episodeNumber = libEntry.latestEpisodeNumber,
                            seenAt = libEntry.lastUpdatedAt,
                            sourceId = libEntry.sourceId,
                            isNew = libEntry.unseenEpisodeCount > 0,
                            thumbnailUrl = libEntry.thumbnailUrl,
                        )
                    }
                    val lastWatched = historyEntries
                        .filter { it.animeId == libEntry.animeId }
                        .maxByOrNull { it.seenAt }
                    if (lastWatched != null) {
                        UpdateItemData(
                            animeId = libEntry.animeId,
                            animeTitle = libEntry.title,
                            episodeId = lastWatched.episodeId,
                            episodeName = lastWatched.episodeName,
                            episodeNumber = lastWatched.episodeNumber,
                            seenAt = lastWatched.seenAt,
                            sourceId = libEntry.sourceId,
                            thumbnailUrl = libEntry.thumbnailUrl,
                        )
                    } else {
                        UpdateItemData(
                            animeId = libEntry.animeId,
                            animeTitle = libEntry.title,
                            episodeId = libEntry.animeId,
                            episodeName = "Added to library",
                            episodeNumber = 1.0,
                            seenAt = libEntry.addedAt,
                            sourceId = libEntry.sourceId,
                        )
                    }
                }.sortedByDescending { it.seenAt }
            }
        }

        UpdatesContent(
            updates = updates,
            libraryCount = libraryEntries.size,
            isRefreshing = backgroundStatus?.runningTask == "Library update",
            statusMessage = backgroundStatus?.message,
            lastError = backgroundStatus?.lastError,
            onRefresh = { backgroundJobs?.updateLibraryNow() },
            onUpdateClick = { update ->
                val libraryEntry = libraryEntries.find { it.animeId == update.animeId }
                if (libraryEntry != null && libraryEntry.sourceId != 0L) {
                    navigator.push(
                        AnimeDetailScreen(
                            animeId = update.animeId,
                            sourceId = libraryEntry.sourceId,
                            animeUrl = libraryEntry.url,
                            animeTitle = update.animeTitle,
                            extensionManager = extensionManager,
                        )
                    )
                } else {
                    toastHost.show(
                        text = "Cannot open update — source information missing",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = null,
                        location = "UpdatesTab.onUpdateClick",
                    )
                }
            },
        )
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = "Updates",
            icon = rememberVectorPainter(Icons.Outlined.Refresh),
        )
}

private fun stableEpisodeId(animeId: Long, episodeNumber: Double): Long =
    31L * animeId + episodeNumber.toBits()

private fun formatEpisodeNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

data class UpdateItemData(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val episodeNumber: Double,
    val seenAt: Long,
    val sourceId: Long = 0L,
    val isNew: Boolean = false,
    val thumbnailUrl: String? = null,
)

@Composable
private fun UpdatesContent(
    updates: List<UpdateItemData>,
    libraryCount: Int = 0,
    isRefreshing: Boolean = false,
    statusMessage: String? = null,
    lastError: String? = null,
    onRefresh: () -> Unit = {},
    onUpdateClick: (UpdateItemData) -> Unit = {},
) {
    // Dismissible error banner for failed background library checks. Re-shows
    // whenever a fresh error arrives; cleared on the next successful run.
    var errorDismissed by remember(lastError) { mutableStateOf(lastError == null) }
    val lastErrorText = lastError?.takeIf(String::isNotBlank)
    if (lastErrorText != null && !errorDismissed) {
        ErrorBanner(
            errorType = ErrorType.GENERIC_ERROR,
            title = "Library update failed",
            message = lastErrorText,
            onDismiss = { errorDismissed = true },
            source = "Library update",
            location = "UpdatesTab.UpdatesContent",
        )
    }

    if (updates.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Refresh,
            title = "No recent updates",
            hint = buildString {
                statusMessage?.takeIf(String::isNotBlank)?.let { append(it).append("\n") }
                append(
                    if (libraryCount > 0) "New episodes of your anime will show up here"
                    else "Add anime to your library to track updates"
                )
            },
            actionLabel = if (isRefreshing) "Checking…" else "Check Now",
            onAction = onRefresh,
            actionEnabled = !isRefreshing && libraryCount > 0,
            actionLoading = isRefreshing,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Updates",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Check library for updates")
                        }
                    }
                }
                // Live progress while the background library check runs.
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                    )
                    statusMessage?.takeIf(String::isNotBlank)?.let { message ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(
                items = updates,
                key = { it.episodeId },
            ) { update ->
                UpdatesItem(update = update, onClick = { onUpdateClick(update) })
            }
        }
    }
}

@Composable
private fun UpdatesItem(
    update: UpdateItemData,
    onClick: () -> Unit = {},
) {
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
                thumbnailUrl = update.thumbnailUrl,
                contentDescription = update.animeTitle,
                title = update.animeTitle,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = update.animeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = update.episodeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (update.isNew) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
