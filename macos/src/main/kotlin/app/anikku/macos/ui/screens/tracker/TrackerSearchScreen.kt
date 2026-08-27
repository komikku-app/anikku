package app.anikku.macos.ui.screens.tracker
import app.anikku.macos.ui.theme.AnikkuStatusColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.auth.TrackerManager
import app.anikku.macos.platform.auth.TrackerSearchResult
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.EmptyState
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracker search screen — lets users manually search for an anime on their
 * connected trackers and choose which one to link for scrobbling.
 *
 * This screen is opened from the player when auto-match fails or when the
 * user wants to manually correct an anime-to-tracker mapping. Once a tracker
 * entry is selected, the mapping is saved via [TrackerManager.setAnimeMapping]
 * and subsequent [TrackerManager.scrobbleProgress] calls use the manual link.
 *
 * @param animeTitle The anime title displayed in the player, used as the
 *                   search query and as the key for the manual mapping.
 */
data class TrackerSearchScreen(
    val animeTitle: String,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val trackerManager = LocalTrackerManager.current
        val scope = rememberCoroutineScope()

        var query by remember { mutableStateOf(animeTitle) }
        var isSearching by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<Map<String, List<TrackerSearchResult>>>(emptyMap()) }
        var searchedTrackers by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedMapping by remember { mutableStateOf<Pair<String, String>?>(null) } // (tracker, animeId)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Link Anime to Tracker") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ── Search bar ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search anime on trackers") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (query.isBlank()) {
                                toastHost.show("Enter an anime title to search", ToastDuration.SHORT)
                                return@Button
                            }
                            if (trackerManager == null) {
                                toastHost.show(
                                    text = "Tracker manager not available",
                                    duration = ToastDuration.SHORT,
                                    isError = true,
                                    source = "tracker",
                                    location = "TrackerSearchScreen.search",
                                )
                                return@Button
                            }
                            isSearching = true
                            searchResults = emptyMap()
                            selectedMapping = null

                            scope.launch {
                                val results = withContext(Dispatchers.IO) {
                                    val trackers = trackerManager.loginStatuses.value
                                        .filter { it.isLoggedIn }
                                        .map { it.tracker }

                                    searchedTrackers = trackers

                                    trackers.associateWith { tracker ->
                                        trackerManager.searchAnime(tracker, query)
                                    }
                                }
                                searchResults = results
                                isSearching = false

                                val total = results.values.sumOf { it.size }
                                if (total == 0) {
                                    toastHost.show("No results found on any tracker", ToastDuration.SHORT)
                                }
                            }
                        },
                        enabled = !isSearching && query.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Search")
                    }
                }

                // ── Status ─────────────────────────────────────────────
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                if (searchedTrackers.isEmpty() && !isSearching) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Search for an anime to link with your trackers",
                        hint = "Connect at least one tracker in Settings > Tracking first.",
                        modifier = Modifier.weight(1f),
                    )
                } else if (!isSearching) {
                    // ── Results ─────────────────────────────────────────
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Selected mapping confirmation banner
                        if (selectedMapping != null) {
                            item(key = "selected_banner") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = AnikkuStatusColors.success().copy(alpha = 0.1f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = AnikkuStatusColors.success(),
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Anime linked!",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AnikkuStatusColors.success(),
                                            )
                                            Text(
                                                text = "Future scrobbles will use this mapping.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AnikkuStatusColors.success().copy(alpha = 0.7f),
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { selectedMapping = null },
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text("Undo", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        // No results message
                        val totalResults = searchResults.values.sumOf { it.size }
                        if (totalResults == 0 && searchedTrackers.isNotEmpty()) {
                            item(key = "no_results") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No results found. Try a different search term.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Results grouped by tracker
                        searchResults.forEach { (tracker, results) ->
                            if (results.isNotEmpty()) {
                                val displayName = trackerDisplayName(tracker)
                                val brandColor = trackerBrandColor(tracker)

                                item(key = "header_$tracker") {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(brandColor),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = brandColor,
                                        )
                                    }
                                }

                                items(
                                    items = results,
                                    key = { "${tracker}_${it.id}" },
                                ) { result ->
                                    val isSelected = selectedMapping?.first == tracker &&
                                        selectedMapping?.second == result.id

                                    TrackerSearchResultCard(
                                        result = result,
                                        tracker = tracker,
                                        isSelected = isSelected,
                                        onSelect = {
                                            if (trackerManager != null) {
                                                trackerManager.setAnimeMapping(
                                                    animeTitle = animeTitle,
                                                    tracker = tracker,
                                                    trackerAnimeId = result.id,
                                                )
                                                selectedMapping = Pair(tracker, result.id)
                                                toastHost.show(
                                                    "Linked \"$animeTitle\" to \"${result.title.take(40)}\" on $displayName",
                                                    ToastDuration.SHORT,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Done button at bottom ───────────────────────────────
                if (selectedMapping != null) {
                    Button(
                        onClick = { navigator.pop() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerSearchResultCard(
    result: TrackerSearchResult,
    tracker: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tracker brand initial circle
            val brandColor = trackerBrandColor(tracker)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(brandColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = result.title.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = brandColor,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Anime info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "ID: ${result.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Selection indicator
            if (isSelected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AnikkuStatusColors.success(),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun trackerDisplayName(tracker: String): String = when (tracker) {
    "myanimelist" -> "MyAnimeList"
    "anilist" -> "AniList"
    "kitsu" -> "Kitsu"
    "shikimori" -> "Shikimori"
    else -> tracker
}
