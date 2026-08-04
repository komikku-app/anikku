package app.anikku.macos.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.AnimeGrid
import app.anikku.macos.ui.components.AnimeList
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OverflowItem
import app.anikku.macos.ui.components.OverflowMenu
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.player.PlayerScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions

/**
 * Library screen tab — Phase 5.
 *
 * Shows the user's anime library with category filter chips,
 * search, and sort. Reads entries from [LibraryRepository].
 */
object LibraryTab : AnikkuScreen(), Tab {

    enum class DisplayMode { Grid, List }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current
        val extensionManager = LocalExtensionManager.current
        val downloadManager = LocalDownloadManager.current
        val toastHost = LocalToastHost.current

        var displayMode by remember { mutableStateOf(DisplayMode.Grid) }
        var searchQuery by remember { mutableStateOf("") }
        var sortMode by remember { mutableStateOf(LibrarySortMode.Title) }
        var showSortMenu by remember { mutableStateOf(false) }
        var selectedCategoryId by remember { mutableStateOf<Long?>(null) } // null = All
        var progressFilter by remember { mutableStateOf(LibraryProgressFilter.All) }

        // Observe repository mutations from favorites, restore, and background
        // updates instead of retaining a one-time snapshot for the tab's life.
        val libraryRevision by libraryRepo.revision.collectAsState()
        // History has no Flow; its revision signal drives last-watched/progress.
        val historyRevision = historyRepo?.revision?.collectAsState()?.value ?: 0L
        val libraryEntries = remember(libraryRevision) { libraryRepo.getAll() }
        val categories = remember(libraryRevision) { libraryRepo.getCategories() }

        val allAnime = remember(libraryEntries) {
            libraryEntries.map { entry ->
                AnimeModel(
                    id = entry.animeId,
                    title = entry.title,
                    source = entry.sourceId,
                    author = entry.author,
                    artist = entry.artist,
                    description = entry.description,
                    genre = entry.genre,
                    status = entry.status,
                    thumbnailUrl = entry.thumbnailUrl,
                    url = entry.url,
                    favorite = true,
                    coverLastModified = entry.lastUpdatedAt,
                )
            }
        }
        val allAnimeById = remember(allAnime) { allAnime.associateBy { it.id } }

        // Latest history entry per anime — drives the Last Watched sort and the
        // progress filter. Recomputed when either repo changes.
        val latestByAnime = remember(libraryRevision, historyRevision) {
            latestHistoryByAnime(historyRepo?.getAll().orEmpty())
        }

        // Filter by category + search query + progress, then sort (pure helper).
        val filteredEntries = remember(
            libraryEntries, searchQuery, selectedCategoryId, sortMode, progressFilter, latestByAnime,
        ) {
            filterAndSortLibrary(
                entries = libraryEntries,
                query = searchQuery,
                categoryId = selectedCategoryId,
                sortMode = sortMode,
                progressFilter = progressFilter,
                latestByAnime = latestByAnime,
            )
        }
        val filteredAnime = remember(filteredEntries) {
            filteredEntries.mapNotNull { allAnimeById[it.animeId] }
        }

        // In-progress episodes for the "Continue Watching" row, most recent first.
        val continueWatching = remember(libraryRevision, historyRevision, libraryEntries) {
            continueWatchingItems(historyRepo, libraryEntries)
        }

        LibraryContent(
            libraryAnime = filteredAnime,
            continueWatching = continueWatching,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            progressFilter = progressFilter,
            libraryCount = libraryRepo.count(),
            displayMode = displayMode,
            searchQuery = searchQuery,
            sortMode = sortMode,
            showSortMenu = showSortMenu,
            onSearchQueryChange = { searchQuery = it },
            onToggleDisplayMode = {
                displayMode = if (displayMode == DisplayMode.Grid) DisplayMode.List else DisplayMode.Grid
            },
            onSortModeChange = { sortMode = it },
            onToggleSortMenu = { showSortMenu = !showSortMenu },
            onDismissSortMenu = { showSortMenu = false },
            onCategorySelect = { selectedCategoryId = it },
            onProgressFilterChange = { progressFilter = it },
            onAnimeClick = { anime ->
                if (anime.source == 0L && anime.url == null) {
                    // Entry imported from AniList has no streaming source — the
                    // detail screen auto-matches one; if none is found, it offers
                    // the manual LinkSourceScreen flow from its error state.
                    navigator.push(AnimeDetailScreen(
                        animeId = anime.id,
                        sourceId = null,
                        animeUrl = null,
                        animeTitle = anime.title,
                        extensionManager = extensionManager,
                    ))
                } else {
                    navigator.push(AnimeDetailScreen(
                        animeId = anime.id,
                        sourceId = anime.source.takeIf { it != 0L },
                        animeUrl = anime.url,
                        animeTitle = anime.title,
                        extensionManager = extensionManager,
                    ))
                }
            },
            onRemoveFromLibrary = { animeId ->
                libraryRepo.remove(animeId)
                toastHost.show("Removed from library", ToastDuration.SHORT)
            },
            onRemoveFromContinueWatching = { animeId ->
                historyRepo?.removeForAnime(animeId)
                toastHost.show("Removed from continue watching", ToastDuration.SHORT)
            },
            onContinueWatchingClick = { item ->
                val entry = item.entry
                if (entry.sourceId == 0L || entry.episodeUrl == null) {
                    navigator.push(AnimeDetailScreen(
                        animeId = entry.animeId,
                        sourceId = null,
                        animeUrl = null,
                        animeTitle = entry.animeTitle,
                        extensionManager = extensionManager,
                    ))
                } else {
                    navigator.push(PlayerScreen(
                        animeId = entry.animeId,
                        episodeId = entry.episodeId,
                        sourceId = entry.sourceId.takeIf { it != 0L },
                        episodeUrl = entry.episodeUrl,
                        animeUrl = entry.animeUrl,
                        animeTitle = entry.animeTitle,
                        extensionManager = extensionManager,
                        downloadManager = downloadManager,
                    ))
                }
            },
        )
    }

    // In-progress episodes for the "Continue Watching" row, most recent first.
    // Recomputed on library revision changes; history is small and read cheaply.
    private fun continueWatchingItems(
        historyRepo: HistoryRepository?,
        libraryEntries: List<LibraryRepository.LibraryEntry>,
    ): List<ContinueWatchingItem> {
        val covers = libraryEntries.associate { it.animeId to it.thumbnailUrl }
        return historyRepo?.getContinueWatching(limit = 12).orEmpty().map { entry ->
            ContinueWatchingItem(
                entry = entry,
                thumbnailUrl = covers[entry.animeId],
            )
        }
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = "Library",
            icon = rememberVectorPainter(Icons.Outlined.Book),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    libraryAnime: List<AnimeModel>,
    continueWatching: List<ContinueWatchingItem> = emptyList(),
    categories: List<CategoryEntry> = emptyList(),
    selectedCategoryId: Long? = null,
    progressFilter: LibraryProgressFilter = LibraryProgressFilter.All,
    libraryCount: Int = 0,
    displayMode: LibraryTab.DisplayMode,
    searchQuery: String,
    sortMode: LibrarySortMode,
    showSortMenu: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSortModeChange: (LibrarySortMode) -> Unit,
    onToggleSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onCategorySelect: (Long?) -> Unit,
    onProgressFilterChange: (LibraryProgressFilter) -> Unit = {},
    onAnimeClick: (AnimeModel) -> Unit,
    onRemoveFromLibrary: (Long) -> Unit = {},
    onRemoveFromContinueWatching: (Long) -> Unit = {},
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Library")
                },
                actions = {
                    Box {
                        IconButton(onClick = onToggleSortMenu) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort",
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = onDismissSortMenu,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Title") },
                                onClick = { onSortModeChange(LibrarySortMode.Title); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.Title)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Status") },
                                onClick = { onSortModeChange(LibrarySortMode.Status); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.Status)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Last Updated") },
                                onClick = { onSortModeChange(LibrarySortMode.LastUpdated); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.LastUpdated)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Last Watched") },
                                onClick = { onSortModeChange(LibrarySortMode.LastWatched); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.LastWatched)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Date Added") },
                                onClick = { onSortModeChange(LibrarySortMode.DateAdded); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.DateAdded)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Progress") },
                                onClick = { onSortModeChange(LibrarySortMode.Progress); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibrarySortMode.Progress)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }

                    IconButton(onClick = onToggleDisplayMode) {
                        Icon(
                            imageVector = if (displayMode == LibraryTab.DisplayMode.Grid)
                                Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = if (displayMode == LibraryTab.DisplayMode.Grid)
                                "Switch to list" else "Switch to grid",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Category filter chips
            if (categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    categories.forEach { category ->
                        if (!category.hidden) {
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = { onCategorySelect(category.id) },
                                label = { Text(category.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            // Watch-progress filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryProgressFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = progressFilter == filter,
                        onClick = { onProgressFilterChange(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    LibraryProgressFilter.All -> "All"
                                    LibraryProgressFilter.InProgress -> "In progress"
                                    LibraryProgressFilter.NotStarted -> "Not started"
                                    LibraryProgressFilter.Finished -> "Finished"
                                },
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            if (libraryAnime.isNotEmpty() || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Search library...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Continue Watching row — in-progress episodes, most recent first
            if (continueWatching.isNotEmpty()) {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Episode IDs are URL hashes that can collide across anime,
                    // so key on the unique (animeId, episodeId) pair.
                    items(continueWatching, key = { it.entry.animeId to it.entry.episodeId }) { item ->
                        ContinueWatchingCard(
                            item = item,
                            onClick = { onContinueWatchingClick(item) },
                            overflowItems = listOf(
                                OverflowItem(
                                    "Remove from continue watching",
                                    Icons.Outlined.History,
                                    { onRemoveFromContinueWatching(item.entry.animeId) },
                                ),
                                OverflowItem(
                                    "Remove from library",
                                    Icons.Outlined.Delete,
                                    { onRemoveFromLibrary(item.entry.animeId) },
                                ),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (libraryAnime.isEmpty() && searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Your library is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Browse sources and add anime to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else if (libraryAnime.isEmpty() && searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No results for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = displayMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "library_display",
                    modifier = Modifier.fillMaxSize(),
                ) { mode ->
                    when (mode) {
                        LibraryTab.DisplayMode.Grid -> {
                            AnimeGrid(
                                items = libraryAnime,
                                onClick = onAnimeClick,
                                modifier = Modifier.testTag("library_grid"),
                                getSubtitle = { anime ->
                                    when (anime.status) {
                                        1 -> "Ongoing"
                                        2 -> "Completed"
                                        3 -> "Licensed"
                                        4 -> "Finished"
                                        5 -> "Cancelled"
                                        6 -> "On Hiatus"
                                        else -> "Unknown"
                                    }
                                },
                                getOverflow = { anime ->
                                    listOf(
                                        OverflowItem(
                                            "Remove from library",
                                            Icons.Outlined.Delete,
                                            { onRemoveFromLibrary(anime.id) },
                                        ),
                                        OverflowItem(
                                            "Remove from continue watching",
                                            Icons.Outlined.History,
                                            { onRemoveFromContinueWatching(anime.id) },
                                        ),
                                    )
                                },
                            )
                        }
                        LibraryTab.DisplayMode.List -> {
                            AnimeList(
                                items = libraryAnime,
                                onClick = onAnimeClick,
                                modifier = Modifier.testTag("library_list"),
                                getSubtitle = { anime ->
                                    when (anime.status) {
                                        1 -> "Ongoing"
                                        2 -> "Completed"
                                        else -> null
                                    }
                                },
                                getOverflow = { anime ->
                                    listOf(
                                        OverflowItem(
                                            "Remove from library",
                                            Icons.Outlined.Delete,
                                            { onRemoveFromLibrary(anime.id) },
                                        ),
                                        OverflowItem(
                                            "Remove from continue watching",
                                            Icons.Outlined.History,
                                            { onRemoveFromContinueWatching(anime.id) },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single entry in the Continue Watching row: the in-progress episode plus
 * the anime's cover (resolved from the library so it works without a source).
 */
data class ContinueWatchingItem(
    val entry: HistoryRepository.HistoryEntry,
    val thumbnailUrl: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    overflowItems: List<OverflowItem>? = null,
) {
    val entry = item.entry
    val fraction = if (entry.totalSeconds > 0) {
        (entry.lastSecondSeen.toFloat() / entry.totalSeconds).coerceIn(0f, 1f)
    } else 0f

    Card(
        onClick = onClick,
        modifier = Modifier.width(110.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box {
                AnimeCoverImage(
                    thumbnailUrl = item.thumbnailUrl,
                    contentDescription = entry.animeTitle,
                    title = entry.animeTitle,
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                if (overflowItems != null) {
                    OverflowMenu(
                        items = overflowItems,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = entry.animeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.episodeName.ifBlank { "Episode ${entry.episodeNumber}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}
