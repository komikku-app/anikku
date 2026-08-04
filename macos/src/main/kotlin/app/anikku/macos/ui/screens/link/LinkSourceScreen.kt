package app.anikku.macos.ui.screens.link

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.extension.LocalExtensionManager
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.launch

/**
 * Links an AniList-imported (or otherwise source-less) library entry to a
 * streaming extension.
 *
 * Shows the installed sources; picking one searches it for [animeTitle]; the
 * user picks the match, which updates the library entry with that source's
 * id + URL and opens the anime detail screen so episodes stream from there.
 */
data class LinkSourceScreen(
    val animeId: Long,
    val animeTitle: String,
    val anilistId: Long? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    private data class Match(
        val sourceId: Long,
        val sourceName: String,
        val url: String,
        val title: String,
        val thumbnailUrl: String?,
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val extensionManager = LocalExtensionManager.current
        val libraryRepo = LocalLibraryRepository.current
        val scope = rememberCoroutineScope()

        val installedExtensions by remember(extensionManager) {
            extensionManager?.installedExtensionsFlow
                ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        }.collectAsState()

        val sources = remember(installedExtensions) {
            installedExtensions.flatMap { ext -> ext.sources.filterIsInstance<CatalogueSource>() }
                .distinctBy { it.id }
                .sortedBy { it.name }
        }

        var searchingSourceId by remember { mutableStateOf<Long?>(null) }
        var searchingSourceName by remember { mutableStateOf<String?>(null) }
        var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
        var searchError by remember { mutableStateOf<String?>(null) }

        fun link(match: Match) {
            val current = libraryRepo?.get(animeId)
            if (current != null) {
                libraryRepo.add(
                    current.copy(
                        sourceId = match.sourceId,
                        url = match.url,
                        thumbnailUrl = match.thumbnailUrl?.takeIf { it.isNotBlank() }
                            ?: current.thumbnailUrl,
                    ),
                )
            } else {
                libraryRepo?.add(
                    LibraryRepository.LibraryEntry(
                        animeId = animeId,
                        title = animeTitle,
                        anilistId = anilistId,
                        sourceId = match.sourceId,
                        url = match.url,
                        thumbnailUrl = match.thumbnailUrl?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            toastHost.show("Linked to ${match.sourceName}", ToastDuration.SHORT)
            navigator.replaceAll(
                AnimeDetailScreen(
                    animeId = animeId,
                    sourceId = match.sourceId,
                    animeUrl = match.url,
                    animeTitle = match.title,
                    extensionManager = extensionManager,
                ),
            )
        }

        fun searchSource(source: CatalogueSource) {
            searchingSourceId = source.id
            searchingSourceName = source.name
            searchError = null
            scope.launch {
                val page = runCatching {
                    source.getSearchAnime(page = 1, query = animeTitle, filters = AnimeFilterList())
                }.getOrNull()
                searchingSourceId = null
                searchingSourceName = null
                if (page == null || page.animes.isEmpty()) {
                    searchError = "No matches on \"${source.name}\" — try another source"
                } else {
                    // url/title are lateinit on SAnime — some sources leave them
                    // unset, which throws on access; skip those defensively.
                    matches = page.animes.mapNotNull { anime ->
                        val safeUrl = runCatching { anime.url }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val safeTitle = runCatching { anime.title }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        Match(source.id, source.name, safeUrl, safeTitle, anime.thumbnail_url)
                    }
                    if (matches.isEmpty()) searchError = "No usable matches on \"${source.name}\" — try another source"
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Find \"${animeTitle.take(40)}\"") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    searchingSourceId != null -> {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Searching \"$searchingSourceName\"…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    matches.isNotEmpty() -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Matches on \"${matches.first().sourceName}\" — pick the right one:",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    TextButton(onClick = {
                                        matches = emptyList()
                                        searchError = null
                                    }) {
                                        Text("Search another source")
                                    }
                                }
                            }
                            items(matches, key = { it.url }) { match ->
                                MatchRow(
                                    title = match.title,
                                    thumbnailUrl = match.thumbnailUrl,
                                    onClick = { link(match) },
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                Text(
                                    "This anime was synced from AniList and has no streaming source yet. " +
                                        "Pick an extension below — Anikku searches it and links the result so episodes stream from there.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            if (sources.isEmpty()) {
                                item {
                                    Text(
                                        "No extensions installed — add one via the Extensions tab first.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            items(sources, key = { it.id }) { source ->
                                Card(
                                    onClick = { searchSource(source) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                source.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                source.lang,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(
                                            Icons.Outlined.Link,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                searchError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchRow(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimeCoverImage(
                thumbnailUrl = thumbnailUrl,
                contentDescription = title,
                title = title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("Link & play", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
