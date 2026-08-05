package app.anikku.macos.ui.screens.browse

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.screens.anime.AnimeDetailScreen
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.toAnimeModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Source browse screen — shows anime catalog from a selected source.
 *
 * Fetches popular anime from the source's [CatalogueSource.getPopularAnime] API on load.
 * Supports searching via [CatalogueSource.getSearchAnime] when the user types a query.
 * Shows error state when no compatible extension source is installed.
 */
data class SourceBrowseScreen(
    val sourceId: Long,
    val sourceName: String,
    val extensionManager: MacOSExtensionManager? = null,
    val preferenceStore: MacOSPreferenceStore? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    // ── Compose state stored at class level ─────────────────────────
    //
    // Voyager disposes the composition tree when this screen is pushed
    // to the backstack (e.g. when AnimeDetailScreen is pushed on top).
    // Standard `remember` blocks are lost on disposal.
    //
    // By storing mutable state as class-level properties, we survive
    // composition disposal because Voyager retains the Screen instance
    // in its backstack. When the user presses Back, this same instance
    // is recomposed with all state intact.
    private val _isLoading = mutableStateOf(true)
    private val _isSearching = mutableStateOf(false)
    private val _errorMessage = mutableStateOf<String?>(null)
    private val _animeList = mutableStateOf(emptyList<AnimeModel>())
    private val _searchQuery = mutableStateOf("")
    private val _isShowingSearchResults = mutableStateOf(false)
    private val _hasSearched = mutableStateOf(false)
    private val _showBaseUrlDialog = mutableStateOf(false)

    // Original baseUrl captured before any override so it can be restored on clear.
    private var originalBaseUrl: String? = null

    var isLoading: Boolean by _isLoading
    var isSearching: Boolean by _isSearching
    var errorMessage: String? by _errorMessage
    var animeList: List<AnimeModel> by _animeList
    var searchQuery: String by _searchQuery
    var isShowingSearchResults: Boolean by _isShowingSearchResults
    var hasSearched: Boolean by _hasSearched
    var showBaseUrlDialog: Boolean by _showBaseUrlDialog
    // ── End of class-level state ────────────────────────────────────

    /**
     * Fetch popular anime from the source.
     * Called on first composition and from the retry handler.
     */
    private suspend fun fetchPopular(toastHost: app.anikku.macos.ui.components.ToastHostState) {
        isLoading = true
        errorMessage = null

        val source = extensionManager?.getSource(sourceId)
        if (source is CatalogueSource) {
            UIActionLogger.logExtension(sourceName, "fetchPopular", "sourceId=$sourceId")
            try {
                val page = withContext(Dispatchers.IO) {
                    source.getPopularAnime(page = 1)
                }
                animeList = page.animes.map { it.toAnimeModel(sourceId) }
                UIActionLogger.logExtension(sourceName, "popularResults", "count=${animeList.size}")
            } catch (e: NoClassDefFoundError) {
                errorMessage = "This source needs an updated version of its extension. " +
                    "Check the Extensions tab for updates, or reinstall it."
                toastHost.show(
                    text = "Missing dependency for $sourceName",
                    duration = app.anikku.macos.ui.components.ToastDuration.LONG,
                    isError = true,
                    source = sourceName,
                    throwable = e,
                    location = "SourceBrowseScreen.fetchPopular",
                )
            } catch (e: Exception) {
                errorMessage = formatError(e)
                toastHost.show(
                    text = "$sourceName: ${formatError(e)}",
                    duration = app.anikku.macos.ui.components.ToastDuration.LONG,
                    isError = true,
                    source = sourceName,
                    throwable = e,
                    location = "SourceBrowseScreen.fetchPopular",
                )
            }
        } else if (source != null) {
            errorMessage = "Source does not support catalog browsing (missing CatalogueSource interface)"
        } else {
            errorMessage = "Source not found — install an extension via the Extensions tab"
        }

        isLoading = false
    }

    /**
     * Format an exception into a user-friendly, actionable error message.
     */
    private fun formatError(e: Throwable): String {
        val messages = collectMessages(e).lowercase()
        val hasSslError = walkCauseChain(e) { it is SSLException }
        val hasTimeout = walkCauseChain(e) { it is java.net.SocketTimeoutException }
        val hasUnknownHost = walkCauseChain(e) { it is UnknownHostException }
        return when {
            hasUnknownHost || messages.contains("unable to resolve") -> {
                "DNS error: $sourceName's domain could not be resolved. The site may be down or blocked. " +
                    "Try overriding the base URL from the settings icon (⚙) in the top bar."
            }
            hasSslError || messages.contains("ssl") || messages.contains("certificate") || messages.contains("cert") -> {
                "SSL/TLS error: $sourceName's certificate could not be verified. " +
                    "Try enabling insecure SSL in Settings → Network, or override the base URL."
            }
            messages.contains("403") || messages.contains("forbidden") -> {
                "Access denied (403): $sourceName is blocking requests. This may be Cloudflare or geo-blocking. " +
                    "Try opening the site in a browser first, or use a different source."
            }
            messages.contains("404") || messages.contains("not found") -> {
                "Not found (404): $sourceName's API path may have changed. " +
                    "Try overriding the base URL from the settings icon (⚙) in the top bar."
            }
            messages.contains("cloudflare") || messages.contains("cf-") -> {
                "Cloudflare challenge: $sourceName is protected. " +
                    "Make sure Chrome is installed, or open the site in a browser to solve the challenge."
            }
            hasTimeout || messages.contains("timeout") -> {
                "Timeout: $sourceName is responding too slowly. The site may be down or throttling requests."
            }
            else -> "${e::class.simpleName}: ${e.message}"
        }
    }

    /**
     * Walk the exception cause chain and return true if any cause matches the predicate.
     */
    private inline fun walkCauseChain(e: Throwable, predicate: (Throwable) -> Boolean): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (predicate(cause)) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Collect all messages from the exception cause chain into a single lowercase string.
     */
    private fun collectMessages(e: Throwable): String {
        val builder = StringBuilder()
        var cause: Throwable? = e
        while (cause != null) {
            cause.message?.let { builder.append(it).append(" ") }
            cause = cause.cause
        }
        return builder.toString()
    }    /**
     * Preference key used to persist the base URL override for this source.
     */
    private fun baseUrlPrefKey(): String = "source_base_url_$sourceId"

    /**
     * Apply a base URL override to the source instance via reflection.
     * Many Tachiyomi/Aniyomi extensions store the base URL in a field named `baseUrl`.
     * The override is persisted so it survives app restarts.
     */
    private fun applyBaseUrlOverride(
        newUrl: String,
        toastHost: app.anikku.macos.ui.components.ToastHostState,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        val source = extensionManager?.getSource(sourceId) as? CatalogueSource ?: return
        // Capture the original baseUrl before overriding so we can restore it later.
        captureOriginalBaseUrl(source)
        when (overrideSourceBaseUrl(source, newUrl)) {
            BaseUrlOverrideResult.SUCCESS -> {
                preferenceStore?.getString(baseUrlPrefKey(), "")?.set(newUrl)
                toastHost.show("Base URL updated for $sourceName", app.anikku.macos.ui.components.ToastDuration.SHORT)
                // Refresh the list with the new base URL
                scope.launch {
                    fetchPopular(toastHost)
                }
            }
            BaseUrlOverrideResult.MISSING_FIELD -> {
                toastHost.show(
                    text = "$sourceName does not support base URL override (field missing)",
                    duration = app.anikku.macos.ui.components.ToastDuration.LONG,
                    isError = true,
                    source = sourceName,
                    location = "SourceBrowseScreen.applyBaseUrlOverride",
                )
            }
            BaseUrlOverrideResult.ACCESS_DENIED -> {
                toastHost.show(
                    text = "$sourceName does not support base URL override (access denied)",
                    duration = app.anikku.macos.ui.components.ToastDuration.LONG,
                    isError = true,
                    source = sourceName,
                    location = "SourceBrowseScreen.applyBaseUrlOverride",
                )
            }
            BaseUrlOverrideResult.UNKNOWN_ERROR -> {
                toastHost.show(
                    text = "Failed to override base URL for $sourceName",
                    duration = app.anikku.macos.ui.components.ToastDuration.LONG,
                    isError = true,
                    source = sourceName,
                    location = "SourceBrowseScreen.applyBaseUrlOverride",
                )
            }
        }
    }

    /**
     * Clear any persisted base URL override for this source and refresh the list.
     */
    private fun clearBaseUrlOverride(
        toastHost: app.anikku.macos.ui.components.ToastHostState,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        preferenceStore?.getString(baseUrlPrefKey(), "")?.delete()
        // Restore the original baseUrl on the current source instance if we captured it.
        val original = originalBaseUrl
        if (original != null) {
            extensionManager?.getSource(sourceId)?.let { source ->
                if (source is CatalogueSource) {
                    overrideSourceBaseUrl(source, original)
                }
            }
        }
        toastHost.show("Base URL override cleared for $sourceName", app.anikku.macos.ui.components.ToastDuration.SHORT)
        scope.launch {
            fetchPopular(toastHost)
        }
    }

    /**
     * Re-apply a persisted base URL override to a source instance, if any.
     * Captures the original baseUrl before applying the override so it can be
     * restored if the user clears the override later.
     */
    private fun applyPersistedBaseUrl(source: CatalogueSource) {
        val url = preferenceStore?.getString(baseUrlPrefKey(), "")?.get() ?: return
        if (url.isNotBlank()) {
            captureOriginalBaseUrl(source)
            overrideSourceBaseUrl(source, url)
        }
    }

    /**
     * Capture the original `baseUrl` of a source instance so it can be restored later.
     * Does nothing if the original has already been captured or if the source does
     * not expose a `baseUrl` field.
     */
    private fun captureOriginalBaseUrl(source: CatalogueSource) {
        if (originalBaseUrl != null) return
        originalBaseUrl = runCatching {
            source.javaClass.getDeclaredField("baseUrl").apply { isAccessible = true }.get(source) as? String
        }.getOrNull()
    }

    /**
     * Result of attempting to override the `baseUrl` field on a source instance.
     */
    private enum class BaseUrlOverrideResult {
        SUCCESS,
        MISSING_FIELD,
        ACCESS_DENIED,
        UNKNOWN_ERROR,
    }

    /**
     * Set the `baseUrl` field on a source instance via reflection.
     * Returns a [BaseUrlOverrideResult] indicating the outcome.
     * Handles final/val fields by attempting to strip the FINAL modifier.
     */
    private fun overrideSourceBaseUrl(source: CatalogueSource, newUrl: String): BaseUrlOverrideResult {
        return try {
            val field = source.javaClass.getDeclaredField("baseUrl")
            field.isAccessible = true

            // Some extensions declare baseUrl as val/final. Attempt to make it writable.
            try {
                val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
            } catch (_: Exception) {
                // Ignore — field may already be non-final or JVM security may block this.
            }

            field.set(source, newUrl)
            BaseUrlOverrideResult.SUCCESS
        } catch (e: NoSuchFieldException) {
            BaseUrlOverrideResult.MISSING_FIELD
        } catch (e: IllegalAccessException) {
            BaseUrlOverrideResult.ACCESS_DENIED
        } catch (e: Exception) {
            BaseUrlOverrideResult.UNKNOWN_ERROR
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current

        // Fetch popular anime on first composition.
        // Skip re-fetch on back-navigation when state survived (class-level state
        // preserved by Voyager's backstack). Otherwise, re-fetching popular anime
        // would overwrite any search results the user had before navigating away.
        LaunchedEffect(sourceId) {
            if (animeList.isNotEmpty()) return@LaunchedEffect
            // Apply any persisted base URL override once, before the first fetch.
            extensionManager?.getSource(sourceId)?.let { source ->
                if (source is CatalogueSource) applyPersistedBaseUrl(source)
            }
            fetchPopular(toastHost)
        }

        // Debounced search — fires 400ms after the user stops typing.
        // On back-navigation, LaunchedEffect re-launches because remember
        // doesn't survive composition disposal. The class-level state guard
        // prevents redundant re-search when cached results exist.
        LaunchedEffect(searchQuery) {
            if (searchQuery.isBlank()) {
                // Clear search results — reload popular if we had searched
                if (isShowingSearchResults) {
                    isShowingSearchResults = false
                    hasSearched = false
                    val source = extensionManager?.getSource(sourceId)
                    if (source is CatalogueSource && errorMessage == null) {
                        try {
                            isLoading = true
                            val page = source.getPopularAnime(page = 1)
                            animeList = page.animes.map { it.toAnimeModel(sourceId) }
                        } catch (e: Exception) {
                            toastHost.show(
                                text = "Failed to reload popular anime",
                                duration = ToastDuration.SHORT,
                                isError = true,
                                source = sourceName,
                                throwable = e,
                                location = "SourceBrowseScreen.search.clearSearchResults",
                            )
                        }
                        isLoading = false
                    }
                }
                return@LaunchedEffect
            }

            // Skip re-search if state survived back-navigation with results
            if (hasSearched && animeList.isNotEmpty()) {
                return@LaunchedEffect
            }

            // Debounce 400ms
            delay(400)

            val source = extensionManager?.getSource(sourceId)
            if (source is CatalogueSource && errorMessage == null) {
                isSearching = true
                isShowingSearchResults = true
                hasSearched = true
                errorMessage = null
                UIActionLogger.logExtension(sourceName, "search", "query=$searchQuery")
                try {
                    val page = source.getSearchAnime(page = 1, query = searchQuery, filters = AnimeFilterList())
                    animeList = page.animes.map { it.toAnimeModel(sourceId) }
                    UIActionLogger.logExtension(sourceName, "searchResults", "count=${animeList.size}, query=$searchQuery")
                } catch (e: Exception) {
                    errorMessage = "Search error: ${e.message}"
                    toastHost.show(
                        text = "Search failed: ${e.message?.take(80)}",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = sourceName,
                        throwable = e,
                        location = "SourceBrowseScreen.search",
                    )
                }
                isSearching = false
            }
        }

        val scope = androidx.compose.runtime.rememberCoroutineScope()

        val persistedBaseUrl = preferenceStore?.getString(baseUrlPrefKey(), "")?.get().orEmpty()
        if (showBaseUrlDialog) {
            BaseUrlOverrideDialog(
                sourceName = sourceName,
                currentUrl = persistedBaseUrl,
                onDismiss = { showBaseUrlDialog = false },
                onOverride = { newUrl ->
                    showBaseUrlDialog = false
                    applyBaseUrlOverride(newUrl, toastHost, scope)
                },
                onClear = {
                    showBaseUrlDialog = false
                    clearBaseUrlOverride(toastHost, scope)
                },
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBaseUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Override base URL",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize()) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            // Reset searched flag on any user edit so the guard
                            // doesn't block re-search when the query changes
                            hasSearched = false
                            searchQuery = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Search anime...") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                    )

                    // Error banner (inside the scrollable column so it pushes content down)
                    errorMessage?.let { msg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                fetchPopular(toastHost)
                                            }
                                        },
                                    ) {
                                        Text("Retry")
                                    }
                                    TextButton(
                                        onClick = { showBaseUrlDialog = true },
                                    ) {
                                        Text("Override base URL")
                                    }
                                }
                            }
                        }
                    }

                    // Loading state
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Loading anime...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        return@Box
                    }

                    // Search status
                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Searching...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        return@Box
                    }

                    if (hasSearched && animeList.isEmpty()) {
                        // No search results
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No results for \"$searchQuery\"",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Try a different search term",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                        return@Box
                    }

                    if (animeList.isEmpty() && !isShowingSearchResults) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No anime found",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Try a different keyword or another source",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                        return@Box
                    }

                    // Result count
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isShowingSearchResults)
                                "${animeList.size} result${if (animeList.size != 1) "s" else ""} for \"$searchQuery\""
                            else
                                "${animeList.size} title${if (animeList.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Anime grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(animeList, key = { it.id }) { anime ->
                            SourceAnimeItem(
                                anime = anime,
                                onClick = {
                                    navigator.push(
                                        AnimeDetailScreen(
                                            animeId = anime.id,
                                            sourceId = sourceId,
                                            animeUrl = anime.url,
                                            animeTitle = anime.title,
                                            extensionManager = extensionManager,
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

            }
        }
    }
}/**
 * Dialog for overriding a source's base URL.
 */
@Composable
private fun BaseUrlOverrideDialog(
    sourceName: String,
    currentUrl: String,
    onDismiss: () -> Unit,
    onOverride: (String) -> Unit,
    onClear: () -> Unit,
) {
    var url by androidx.compose.runtime.remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override base URL") },
        text = {
            Column {
                Text(
                    "Enter a new base URL for $sourceName. This is useful when the source's domain changes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            val isValidUrl = remember(url) {
                url.isNotBlank() && runCatching {
                    val parsed = URL(url)
                    parsed.protocol == "http" || parsed.protocol == "https"
                }.isSuccess
            }
            TextButton(
                onClick = { onOverride(url) },
                enabled = isValidUrl,
            ) {
                Text("Override")
            }
        },
        dismissButton = {
            Row {
                if (currentUrl.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun SourceAnimeItem(
    anime: AnimeModel,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            AnimeCoverImage(
                thumbnailUrl = anime.thumbnailUrl,
                contentDescription = anime.title,
                title = anime.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!anime.genre.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = anime.genre.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
