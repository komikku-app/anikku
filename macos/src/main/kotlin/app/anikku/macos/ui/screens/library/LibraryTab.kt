package app.anikku.macos.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeGrid
import app.anikku.macos.ui.components.AnimeList
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
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

    enum class SortMode { Title, Status, LastUpdated }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val libraryRepo = LocalLibraryRepository.current
        val extensionManager = LocalExtensionManager.current

        var displayMode by remember { mutableStateOf(DisplayMode.Grid) }
        var searchQuery by remember { mutableStateOf("") }
        var sortMode by remember { mutableStateOf(SortMode.Title) }
        var showSortMenu by remember { mutableStateOf(false) }
        var selectedCategoryId by remember { mutableStateOf<Long?>(null) } // null = All

        // Observe repository mutations from favorites, restore, and background
        // updates instead of retaining a one-time snapshot for the tab's life.
        val libraryRevision by libraryRepo.revision.collectAsState()
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

        // Filter by category + search query, then sort
        val filteredAnime = remember(allAnime, searchQuery, sortMode, selectedCategoryId) {
            var filtered = allAnime

            // Filter by category
            if (selectedCategoryId != null) {
                val categoryEntryIds = libraryEntries
                    .filter { it.categoryId == selectedCategoryId }
                    .map { it.animeId }
                    .toSet()
                filtered = filtered.filter { it.id in categoryEntryIds }
            }

            // Filter by search
            if (searchQuery.isNotBlank()) {
                filtered = filtered.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.author?.contains(searchQuery, ignoreCase = true) == true ||
                        it.genre?.any { g -> g.contains(searchQuery, ignoreCase = true) } == true
                }
            }

            when (sortMode) {
                SortMode.Title -> filtered.sortedBy { it.title }
                SortMode.Status -> filtered.sortedBy { it.status }
                SortMode.LastUpdated -> filtered.sortedByDescending { it.coverLastModified }
            }
        }

        LibraryContent(
            libraryAnime = filteredAnime,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
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
            onAnimeClick = { anime ->
                navigator.push(AnimeDetailScreen(
                    animeId = anime.id,
                    sourceId = anime.source.takeIf { it != 0L },
                    animeUrl = anime.url,
                    animeTitle = anime.title,
                    extensionManager = extensionManager,
                ))
            },
        )
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
    categories: List<CategoryEntry> = emptyList(),
    selectedCategoryId: Long? = null,
    libraryCount: Int = 0,
    displayMode: LibraryTab.DisplayMode,
    searchQuery: String,
    sortMode: LibraryTab.SortMode,
    showSortMenu: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSortModeChange: (LibraryTab.SortMode) -> Unit,
    onToggleSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onCategorySelect: (Long?) -> Unit,
    onAnimeClick: (AnimeModel) -> Unit,
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
                                onClick = { onSortModeChange(LibraryTab.SortMode.Title); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.Title)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Status") },
                                onClick = { onSortModeChange(LibraryTab.SortMode.Status); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.Status)
                                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Last Updated") },
                                onClick = { onSortModeChange(LibraryTab.SortMode.LastUpdated); onDismissSortMenu() },
                                leadingIcon = {
                                    if (sortMode == LibraryTab.SortMode.LastUpdated)
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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

            if (libraryAnime.isNotEmpty() || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search library...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
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
                            )
                        }
                    }
                }
            }
        }
    }
}
