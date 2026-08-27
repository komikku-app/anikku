package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.screens.browse.ExtensionsScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browse screen tab — shows available anime sources from loaded extensions.
 *
 * Two sections:
 * 1. Sources — loaded from installed extensions via [MacOSExtensionManager]
 * 2. Extensions management — install/uninstall extension JARs
 *
 * Phase 5.6: Full source list with language icons,
 * extension management (adapted for desktop JAR loading),
 * global search across sources.
 */
object BrowseTab : AnikkuScreen(), Tab {

    /** Extension manager reference — set during app initialization. */
    private val _extensionManager = MutableStateFlow<MacOSExtensionManager?>(null)
    val extensionManagerFlow: StateFlow<MacOSExtensionManager?> = _extensionManager.asStateFlow()

    /** Preference store reference — set during app initialization. */
    private val _preferenceStore = MutableStateFlow<MacOSPreferenceStore?>(null)
    val preferenceStoreFlow: StateFlow<MacOSPreferenceStore?> = _preferenceStore.asStateFlow()

    /** Set the extension manager reference (called from AnikkuApp). */
    fun setExtensionManager(manager: MacOSExtensionManager) {
        _extensionManager.value = manager
    }

    /** Set the preference store reference (called from AnikkuApp). */
    fun setPreferenceStore(store: MacOSPreferenceStore) {
        _preferenceStore.value = store
    }

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = "Browse",
            icon = rememberVectorPainter(Icons.Outlined.Explore),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val extensionManager by extensionManagerFlow.collectAsState()
        val preferenceStore by preferenceStoreFlow.collectAsState()

        // Collect sources from installed extensions
        val installedExtensions by remember(extensionManager) {
            extensionManager?.installedExtensionsFlow ?: MutableStateFlow(emptyList())
        }.collectAsState()

        val sources = remember(installedExtensions, extensionManager) {
            installedExtensions.flatMap { ext ->
                ext.sources.filterIsInstance<Source>()
            }
            // Deduplicate by source id to prevent "Key already used" crashes
            // when multiple extensions (e.g. auto-deployed + repo-installed)
            // provide sources with the same id.
            .distinctBy { it.id }
        }

        var searchQuery by remember { mutableStateOf("") }

        val filteredSources = remember(sources, searchQuery) {
            if (searchQuery.isBlank()) sources
            else sources.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        // Reactive health map — drives sorting, the summary line and grey-out.
        val healthMap by SourceHealthChecker.healthFlow.collectAsState()

        // Working sources first, then un-checked, then failing; alphabetical within groups.
        val sortedSources = remember(sources, searchQuery, healthMap) {
            val filtered = if (searchQuery.isBlank()) sources
            else sources.filter { it.name.contains(searchQuery, ignoreCase = true) }
            filtered.sortedWith(
                compareBy(
                    { healthRank(healthMap[it.id]?.status) },
                    { it.name.lowercase() },
                )
            )
        }

        // Curated recommendations that are actually installed, in curated order.
        val recommendedSources = remember(installedExtensions) {
            val firstSourceByPkg = mutableMapOf<String, Source>()
            installedExtensions.forEach { ext ->
                val src = ext.sources.filterIsInstance<Source>().firstOrNull()
                if (src != null) firstSourceByPkg.putIfAbsent(ext.pkgName, src)
            }
            KnownGoodSources.RECOMMENDED.mapNotNull { rec -> firstSourceByPkg[rec.pkgName] }
        }

        val healthStats = remember(sources, healthMap) {
            var working = 0
            var failing = 0
            var checking = 0
            sources.forEach {
                when (healthMap[it.id]?.status) {
                    SourceHealthChecker.Health.WORKING -> working++
                    SourceHealthChecker.Health.FAILING -> failing++
                    else -> checking++
                }
            }
            Triple(working, failing, checking)
        }

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
                        text = "Browse",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                navigator.push(ExtensionsScreen(extensionManager = extensionManager))
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Outlined.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Extensions", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                UIActionLogger.logClick("BrowseTab", "RecheckHealth", "recheck_all")
                                SourceHealthChecker.requestRecheckAll()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Re-check source health",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Global search bar — searches ALL sources
            item {
                Button(
                    onClick = {
                        UIActionLogger.logClick("BrowseTab", "GlobalSearch", "open_global_search")
                        navigator.push(GlobalSearchScreen(extensionManager = extensionManager))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Search all sources for anime...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Source filter search bar (filter the source list below)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search sources...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            // Curated "known to work" sources — one tap opens the source.
            if (searchQuery.isBlank() && recommendedSources.isNotEmpty()) {
                item {
                    Text(
                        text = "Recommended",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recommendedSources.forEach { src ->
                            RecommendedChip(
                                source = src,
                                onClick = {
                                    UIActionLogger.logClick("BrowseTab", src.name, "open recommended source", "id=${src.id}")
                                    navigator.push(SourceBrowseScreen(
                                        sourceId = src.id,
                                        sourceName = src.name,
                                        extensionManager = extensionManager,
                                        preferenceStore = preferenceStore,
                                    ))
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (sources.isEmpty()) {
                item {
                    val browseNavigator = LocalNavigator.currentOrThrow
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "No sources installed",
                        hint = "Install extensions from the Extensions tab to browse anime",
                        actionLabel = "Open Extensions",
                        onAction = { browseNavigator.push(ExtensionsScreen()) },
                    )
                }
            } else {
                item {
                    val (working, failing, checking) = healthStats
                    Text(
                        buildString {
                            append("${sources.size} source${if (sources.size != 1) "s" else ""} · ${installedExtensions.size} extension${if (installedExtensions.size != 1) "s" else ""}")
                            if (working > 0) append(" · $working working")
                            if (failing > 0) append(" · $failing unreachable")
                            if (checking > 0) append(" · $checking checking")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                if (filteredSources.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = "No sources match \"$searchQuery\"",
                            hint = "Try a different name",
                        )
                    }
                } else {
                    items(
                        items = sortedSources,
                        key = { it.id },
                    ) { source ->                        SourceItem(
                            source = source,
                            onClick = {
                                UIActionLogger.logClick("BrowseTab", source.name, "select source", "id=${source.id}")
                                navigator.push(SourceBrowseScreen(
                                    sourceId = source.id,
                                    sourceName = source.name,
                                    extensionManager = extensionManager,
                                    preferenceStore = preferenceStore,
                                ))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendedChip(
    source: Source,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        SourceHealthBadge(source = source)
        Spacer(Modifier.width(6.dp))
        Text(
            text = source.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

private fun healthRank(status: SourceHealthChecker.Health?): Int = when (status) {
    SourceHealthChecker.Health.WORKING -> 0
    SourceHealthChecker.Health.FAILING -> 2
    null, SourceHealthChecker.Health.UNKNOWN -> 1
}

@Composable
private fun SourceItem(
    source: Source,
    onClick: () -> Unit,
) {
    val health by SourceHealthChecker.observeHealth(source.id).collectAsState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (health.status == SourceHealthChecker.Health.FAILING) 0.55f else 1f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Language icon with health indicator overlay
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.lang.uppercase().take(2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    SourceHealthBadge(source = source)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${source.lang.uppercase()} source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val category = health.category
                    if (health.status == SourceHealthChecker.Health.FAILING && category != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· ${errorCategoryEmoji(category)} $category",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Returns a relevant emoji for a health-check error category.
 */
private fun errorCategoryEmoji(category: String): String = when (category.lowercase()) {
    "dns" -> "🌐"
    "ssl" -> "🔒"
    "timeout" -> "⏱"
    "403" -> "🚫"
    "404" -> "🔍"
    "5xx" -> "⚠️"
    "cloudflare" -> "☁️"
    else -> "❌"
}
