package app.anikku.macos.ui.screens.torrent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeGrid
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.toAnimeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Torrents tab — discovers anime from torrent-flagged extensions (e.g. the
 * Nyaa.si source) and streams them through the app's torrent engine (bundled
 * TorrServer with a WebTorrent fallback).
 *
 * Selecting a result opens the anime detail screen; playing an episode hands
 * the magnet link to the player, which routes it through
 * TorrentStreamingCoordinator → local HTTP URL → mpv.
 */
object TorrentTab : AnikkuScreen(), Tab {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val extensionManager = LocalExtensionManager.current
        val scope = rememberCoroutineScope()

        val installedExtensions by remember(extensionManager) {
            extensionManager?.installedExtensionsFlow
                ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }.collectAsState()

        // Torrent-flagged catalogue sources (Nyaa etc.), e.g. installed via the
        // Extensions tab from a torrent-capable repository.
        val torrentSources = remember(installedExtensions) {
            installedExtensions
                .filterIsInstance<Extension.Installed>()
                .filter { it.isTorrent }
                .flatMap { it.sources }
                .filterIsInstance<CatalogueSource>()
                .distinctBy { it.id }
                .sortedBy { it.name }
        }

        var popular by remember { mutableStateOf<List<AnimeModel>>(emptyList()) }
        var searchResults by remember { mutableStateOf<List<AnimeModel>?>(null) } // null = showing popular
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // Load the torrent source's popular catalogue on mount / source change.
        val activeSource = torrentSources.firstOrNull()
        androidx.compose.runtime.LaunchedEffect(activeSource?.id) {
            val source = activeSource ?: return@LaunchedEffect
            isLoading = true
            loadError = null
            try {
                val page = kotlinx.coroutines.withTimeout(20_000L) {
                    source.getPopularAnime(page = 1)
                }
                // IDs are URL hashes — dedupe so LazyGrid keys never collide.
                popular = page.animes.mapNotNull { it.toAnimeModelSafe(source.id) }.distinctBy { it.id }
            } catch (e: Exception) {
                loadError = "Could not load torrent catalogue: ${e.message?.take(80)}"
            }
            isLoading = false
        }

        // Debounced search across the torrent source.
        androidx.compose.runtime.LaunchedEffect(searchQuery, activeSource?.id) {
            val source = activeSource
            if (searchQuery.isBlank()) {
                searchResults = null
                return@LaunchedEffect
            }
            if (source == null) return@LaunchedEffect
            delay(500)
            isLoading = true
            loadError = null
            try {
                val page = kotlinx.coroutines.withTimeout(20_000L) {
                    source.getSearchAnime(page = 1, query = searchQuery, filters = AnimeFilterList())
                }
                searchResults = page.animes.mapNotNull { it.toAnimeModelSafe(source.id) }.distinctBy { it.id }
            } catch (e: Exception) {
                loadError = "Search failed: ${e.message?.take(80)}"
            }
            isLoading = false
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Torrents") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    torrentSources.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No torrent source installed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Install a torrent extension (e.g. Nyaa) from the Extensions tab — " +
                                    "its results stream via the built-in torrent engine.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }

                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                placeholder = { Text("Search torrents (Nyaa)…") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )

                            if (isLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            if (searchResults != null || searchQuery.isNotBlank()) "Searching torrents…" else "Loading torrents…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                val shown = searchResults ?: popular
                                if (shown.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            loadError ?: if (searchQuery.isNotBlank()) "No torrents found" else "No torrents available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 32.dp),
                                        )
                                    }
                                } else {
                                    AnimeGrid(
                                        items = shown,
                                        onClick = { anime ->
                                            navigator.push(
                                                AnimeDetailScreen(
                                                    animeId = anime.id,
                                                    sourceId = anime.source.takeIf { it != 0L },
                                                    animeUrl = anime.url,
                                                    animeTitle = anime.title,
                                                    extensionManager = extensionManager,
                                                ),
                                            )
                                        },
                                        getSubtitle = { it.title.takeIf { t -> t != "" } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 5u,
            title = "Torrents",
            icon = rememberVectorPainter(Icons.Outlined.Download),
        )

    /** Lateinit-safe conversion of a torrent source's SAnime. */
    private fun eu.kanade.tachiyomi.animesource.model.SAnime.toAnimeModelSafe(sourceId: Long): AnimeModel? {
        val safeUrl = runCatching { url }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val safeTitle = runCatching { title }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return toAnimeModel(sourceId).copy(url = safeUrl, title = safeTitle)
    }
}
