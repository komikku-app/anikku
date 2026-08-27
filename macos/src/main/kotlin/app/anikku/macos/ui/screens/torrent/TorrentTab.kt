package app.anikku.macos.ui.screens.torrent

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.anilist.AniListAnime
import app.anikku.macos.platform.anilist.AniListSearchClient
import app.anikku.macos.platform.anilist.LocalAniListSearchClient
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.platform.torrent.LocalTorrentServerBridge
import app.anikku.macos.platform.torrent.NyaaTorrentParser
import app.anikku.macos.platform.torrent.TorrentAnimeGrouper
import app.anikku.macos.platform.torrent.TorrentGroup
import app.anikku.macos.platform.torrent.TorrentRelease
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.components.AnimeGrid
import app.anikku.macos.ui.screens.models.AnimeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import app.anikku.macos.ui.screens.browse.ExtensionsScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Torrents tab — discovers anime from torrent-flagged extensions (e.g. the
 * Nyaa.si source) and streams them through the app's torrent engine (bundled
 * TorrServer with a WebTorrent fallback).
 *
 * Nyaa returns one result PER RELEASE (each file is its own row), so instead
 * of showing a flat list of single episodes the tab parses every filename,
 * groups releases by anime, and matches the top groups against the public
 * AniList API for a canonical title/cover. Clicking a group opens
 * [TorrentAnimeScreen] — seasons → ordered episodes → magnet → player.
 */
object TorrentTab : AnikkuScreen(), Tab {

    override val key: ScreenKey = uniqueScreenKey

    /** How many Nyaa result pages a search folds in (long series span pages). */
    private const val MAX_SEARCH_PAGES = 3

    /** How many groups get an AniList match attempt (one GraphQL call each). */
    private const val MAX_MATCHED_GROUPS = 8

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val extensionManager = LocalExtensionManager.current
        val anilistClient = LocalAniListSearchClient.current

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

        var groups by remember { mutableStateOf<List<TorrentGroup>>(emptyList()) }
        var anilistMatches by remember { mutableStateOf<Map<String, AniListAnime?>>(emptyMap()) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // Session cache of AniList lookups keyed by normalized title, so
        // re-searching the same anime doesn't hit GraphQL again. Concurrent
        // map because match lookups run on the IO dispatcher.
        val matchCache = remember { ConcurrentHashMap<String, AniListAnime>() }
        val noMatchSentinel = remember { AniListAnime(id = -1, romajiTitle = "") }

        // Load the torrent source's popular catalogue on mount / source change.
        val activeSource = torrentSources.firstOrNull()
        var retryToken by remember { mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(activeSource?.id, retryToken) {
            val source = activeSource ?: return@LaunchedEffect
            isLoading = true
            loadError = null
            try {
                val page = kotlinx.coroutines.withTimeout(20_000L) {
                    source.getPopularAnime(page = 1)
                }
                val releases = page.animes.mapNotNull { it.toTorrentRelease() }
                groups = TorrentAnimeGrouper.group(releases)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                loadError = "Timed out loading the torrent catalogue"
            } catch (e: Throwable) {
                // Extension code runs in a separate classloader and can throw
                // Errors (e.g. NoClassDefFoundError when the packaged runtime
                // lacks a module the extension uses). Never let that crash the
                // app — degrade to an error message instead.
                loadError = "Could not load torrent catalogue: ${e.message?.take(80)}"
            }
            isLoading = false
        }

        // Debounced search across the torrent source. Nyaa paginates its
        // results, and a long series' episodes span many pages — fold in up to
        // MAX_SEARCH_PAGES pages so the grouped anime menu isn't truncated.
        androidx.compose.runtime.LaunchedEffect(searchQuery, activeSource?.id) {
            val source = activeSource
            if (searchQuery.isBlank()) {
                return@LaunchedEffect
            }
            if (source == null) return@LaunchedEffect
            delay(500)
            isLoading = true
            loadError = null
            try {
                val releases = mutableListOf<TorrentRelease>()
                var pageNumber = 1
                var hasNext = true
                while (pageNumber <= MAX_SEARCH_PAGES && hasNext) {
                    val page = kotlinx.coroutines.withTimeout(20_000L) {
                        source.getSearchAnime(page = pageNumber, query = searchQuery, filters = AnimeFilterList())
                    }
                    releases += page.animes.mapNotNull { it.toTorrentRelease() }
                    hasNext = page.hasNextPage
                    pageNumber++
                }
                groups = TorrentAnimeGrouper.group(releases)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                loadError = "Search timed out"
            } catch (e: Throwable) {
                // Same classloader-Error hardening as the popular load above.
                loadError = "Search failed: ${e.message?.take(80)}"
            }
            isLoading = false
        }

        // Best-effort AniList enrichment for the top groups: canonical title,
        // cover art, synopsis. Failures are silent — the grouped results still
        // work from the parsed Nyaa titles alone.
        androidx.compose.runtime.LaunchedEffect(groups) {
            val client = anilistClient ?: return@LaunchedEffect
            val top = groups.take(MAX_MATCHED_GROUPS)
            if (top.isEmpty()) return@LaunchedEffect

            val matches = coroutineScope {
                top.map { group ->
                    async {
                        val key = group.normalizedKey
                        matchCache[key] ?: runCatching {
                            val candidates = client.searchAnime(group.displayTitle)
                            AniListSearchClient.pickBest(group.displayTitle, candidates)
                        }.getOrNull().also { match ->
                            matchCache[key] = match ?: noMatchSentinel
                        }
                    }
                }.map { it.await() }
            }

            val updated = anilistMatches.toMutableMap()
            top.zip(matches) { group, match ->
                if (match !== noMatchSentinel) {
                    updated[group.normalizedKey] = match
                } else {
                    updated[group.normalizedKey] = null
                }
            }
            anilistMatches = updated
        }

        // Grouped display models: one synthetic AnimeModel per TorrentGroup so
        // the existing AnimeGrid renders them (cover = AniList match).
        val grouped = remember(groups, anilistMatches, activeSource?.id) {
            groups.map { group ->
                val match = anilistMatches[group.normalizedKey]
                val firstPageUrl = group.seasons.firstOrNull()?.episodes?.firstOrNull()?.best?.pageUrl
                    ?: group.batches.firstOrNull()?.pageUrl
                    ?: group.other.firstOrNull()?.pageUrl
                val model = AnimeModel(
                    id = group.normalizedKey.hashCode().toLong().let { if (it == 0L) 1L else it },
                    title = group.displayTitle,
                    source = activeSource?.id ?: 0L,
                    description = "${group.totalReleases} releases",
                    genre = listOf("Torrent"),
                    thumbnailUrl = match?.coverUrl,
                    url = firstPageUrl,
                    favorite = false,
                )
                group to model
            }
        }
        val displayModels = grouped.map { it.second }

        // Live torrent activity — the player streams magnets through the
        // app-scoped TorrServer bridge, so the tab can show progress + removal
        // while a stream is running.
        val torrentBridge = LocalTorrentServerBridge.current
        val activeTorrents by (torrentBridge?.torrents?.collectAsState()
            ?: remember { mutableStateOf(emptyList<app.anikku.macos.platform.torrent.TorrentInfo>()) })
        androidx.compose.runtime.LaunchedEffect(torrentBridge) {
            if (torrentBridge == null) return@LaunchedEffect
            while (true) {
                // Poll while something may be downloading; check cheaply
                // otherwise so a freshly started stream appears quickly.
                if (torrentBridge.isRunning || torrentBridge.torrents.value.isNotEmpty()) {
                    torrentBridge.listTorrents()
                    delay(2_000)
                } else {
                    delay(5_000)
                }
            }
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
                        val torrentNavigator = LocalNavigator.currentOrThrow
                        EmptyState(
                            icon = Icons.Outlined.Download,
                            title = "No torrent source installed",
                            hint = "Install a torrent extension (e.g. Nyaa) from the Extensions tab — " +
                                "its results stream via the built-in torrent engine.",
                            actionLabel = "Open Extensions",
                            onAction = { torrentNavigator.push(ExtensionsScreen()) },
                        )
                    }

                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            // Active torrents — live progress while the player
                            // streams a magnet through the bundled engine.
                            if (activeTorrents.isNotEmpty()) {
                                Text(
                                    "Active torrents",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                activeTorrents.forEach { torrent ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                torrent.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            LinearProgressIndicator(
                                                progress = { torrent.progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "${(torrent.progress * 100).toInt()}% · " +
                                                    "${torrent.seeders} peers · ${formatTorrentSize(torrent.size)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(onClick = { torrentBridge?.removeTorrent(torrent.hash) }) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Remove torrent",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }

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
                                            if (searchQuery.isNotBlank()) "Searching torrents…" else "Loading torrents…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else if (displayModels.isEmpty()) {
                                if (loadError != null) {
                                    EmptyState(
                                        icon = Icons.Outlined.Search,
                                        title = loadError ?: "Nothing here",
                                        hint = "The source couldn't be reached right now",
                                        actionLabel = "Retry",
                                        onAction = { retryToken++ },
                                    )
                                } else {
                                    EmptyState(
                                        icon = Icons.Outlined.Search,
                                        title = if (searchQuery.isNotBlank()) "No torrents found" else "No torrents available",
                                        hint = if (searchQuery.isNotBlank()) {
                                            "Try a different search term"
                                        } else {
                                            "Search Nyaa or another torrent source for anime"
                                        },
                                    )
                                }
                            } else {
                                AnimeGrid(
                                    items = displayModels,
                                    onClick = { anime ->
                                        val group = grouped.getOrNull(displayModels.indexOf(anime))?.first
                                        if (group != null) {
                                            navigator.push(
                                                TorrentAnimeScreen(
                                                    displayTitle = group.displayTitle,
                                                    group = group,
                                                    anilistMatch = anilistMatches[group.normalizedKey],
                                                    sourceId = activeSource?.id ?: 0L,
                                                    extensionManager = extensionManager,
                                                ),
                                            )
                                        }
                                    },
                                    getSubtitle = { anime ->
                                        grouped.getOrNull(displayModels.indexOf(anime))?.first?.let { group ->
                                            buildString {
                                                append("${group.episodeCount} episodes")
                                                if (group.seasonCount > 1) {
                                                    append(" · ${group.seasonCount} seasons")
                                                }
                                            }
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

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 5u,
            title = "Torrents",
            icon = rememberVectorPainter(Icons.Outlined.Download),
        )

    /** Lateinit-safe conversion of a torrent source's SAnime row into a release. */
    private fun SAnime.toTorrentRelease(): TorrentRelease? {
        val safeUrl = runCatching { url }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val safeTitle = runCatching { title }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        // Rows without a magnet can't play — skip them rather than surfacing
        // dead entries in the grouped menu.
        val magnet = author?.takeIf { it.isNotBlank() } ?: return null
        return TorrentRelease(
            magnetUrl = magnet,
            pageUrl = safeUrl,
            rawTitle = safeTitle,
            parsed = NyaaTorrentParser.parse(safeTitle),
            sizeSeeders = description ?: "",
        )
    }
}

/** Compact byte-size label for torrent rows (e.g. "1.2 GB"). */
private fun formatTorrentSize(bytes: Long): String {
    if (bytes <= 0) return "?"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes}B" else "%.1f %s".format(value, units[unit])
}
