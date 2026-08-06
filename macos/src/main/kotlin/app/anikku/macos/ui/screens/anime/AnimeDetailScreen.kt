package app.anikku.macos.ui.screens.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.anikku.macos.platform.MacOSShareUtil
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.data.LocalLibraryRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.library.LocalLibraryAutoLinkService
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.preference.LocalBookmarkStore
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.AnimeCoverImage
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OverflowItem
import app.anikku.macos.ui.components.OverflowMenu
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.components.EmptyState
import app.anikku.macos.ui.settings.LocalSettingsState
import app.anikku.macos.ui.screens.models.AnimeModel
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.toAnimeModel
import app.anikku.macos.ui.screens.models.toEpisodeModel
import app.anikku.macos.ui.screens.link.LinkSourceScreen
import app.anikku.macos.ui.screens.player.PlayerScreen
import app.anikku.macos.ui.screens.tracker.TrackerSearchScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * Ensures the [url] property is set on an [SAnime] result from a source call.
 *
 * Some source implementations (e.g. AnimeHttpSource subclasses like allanime's)
 * return a new SAnime from [animeDetailsParse] without copying [url] back from
 * the input. Accessing a lateinit var that hasn't been set throws
 * [kotlin.UninitializedPropertyAccessException].
 *
 * This helper silently copies the URL if the result's url is uninitialized.
 */
private fun ensureUrlIsSet(anime: SAnime, fallbackUrl: String) {
    try {
        // Trigger getter — will throw if url is not initialized
        @Suppress("UNUSED_EXPRESSION")
        anime.url
    } catch (_: UninitializedPropertyAccessException) {
        anime.url = fallbackUrl
    }
}

/**
 * Auto source-link state for [AnimeDetailScreen] when opened without a source.
 */
private enum class AutoMatchState { Idle, Searching, NotFound }

/**
 * Anime detail screen — Phase 5.7.
 *
 * Displays anime information (cover, title, description, status)
 * and a list of episodes.
 *
 * When [sourceId] and [extensionManager] are provided, fetches real data
 * from the extension source API. Shows error state otherwise.
 *
 * @param animeId       The ID of the anime to display.
 * @param sourceId      Source ID for extension API lookup (optional).
 * @param animeUrl      The anime URL on the source (required for source API).
 * @param animeTitle    The anime title (used for display while loading).
 * @param extensionManager Extension manager for source lookup (optional).
 */
data class AnimeDetailScreen(
    val animeId: Long,
    val sourceId: Long? = null,
    val animeUrl: String? = null,
    val animeTitle: String? = null,
    val extensionManager: MacOSExtensionManager? = null,
    val downloadManager: MacOSDownloadManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val bookmarkStore = LocalBookmarkStore.current
        val libraryRepo = LocalLibraryRepository.current
        val historyRepo = LocalHistoryRepository.current
        val trackerManager = LocalTrackerManager.current
        val effectiveDownloadManager = downloadManager ?: LocalDownloadManager.current
        val focusRequester = remember { FocusRequester() }
        val scope = rememberCoroutineScope()

        // State for source-backed data
        var isLoading by remember { mutableStateOf(true) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Anime data — starts null, populated from source API
        var anime by remember { mutableStateOf<AnimeModel?>(null) }
        var episodes by remember { mutableStateOf(emptyList<EpisodeModel>()) }

        // Auto source linking: when this screen is opened without a source
        // (AniList-imported library entry), search installed extensions for a
        // high-confidence title match and adopt it instead of forcing the
        // manual LinkSourceScreen flow.
        var autoMatchState by remember { mutableStateOf(AutoMatchState.Idle) }
        var autoLinkedSourceId by remember { mutableStateOf<Long?>(null) }
        var autoLinkedUrl by remember { mutableStateOf<String?>(null) }

        // The source actually backing this screen: either the explicit screen
        // params or the auto-linked match.
        val effectiveSourceId = sourceId ?: autoLinkedSourceId
        val effectiveAnimeUrl = animeUrl ?: autoLinkedUrl

        // Captured at composition — composition locals can't be read inside
        // LaunchedEffect (its block isn't a @Composable context).
        val autoLinkService = LocalLibraryAutoLinkService.current

        // Track download states per episode (keyed by episodeNumber) — full
        // entries so rows can show queued / progress / error states.
        var downloadStateMap by remember { mutableStateOf(mapOf<Double, DownloadRepository.DownloadEntry>()) }
        LaunchedEffect(downloadManager) {
            if (effectiveDownloadManager != null) {
                effectiveDownloadManager.downloads.collect { downloads ->
                    downloadStateMap = downloads
                        .filter { it.animeId == animeId }
                        .associateBy { it.episodeNumber }
                }
            }
        }

        UIActionLogger.logScreenOpen("AnimeDetailScreen", mapOf(
            "animeId" to animeId, "sourceId" to sourceId, "title" to animeTitle
        ))

        // Auto-match a source for source-less entries. Runs once per screen
        // instance; on success it flips autoLinkedSourceId/Url which re-keys
        // the load effect below.
        LaunchedEffect(sourceId, animeId, animeTitle) {
            if (sourceId != null) return@LaunchedEffect
            val stored = libraryRepo?.get(animeId)
            val storedSourceId = stored?.sourceId?.takeIf { it != 0L }
            val storedUrl = stored?.url?.takeIf { it.isNotBlank() }
            if (storedSourceId != null && storedUrl != null) {
                // Already linked in the library (stale screen params) — adopt it.
                autoLinkedSourceId = storedSourceId
                autoLinkedUrl = storedUrl
                return@LaunchedEffect
            }
            val service = autoLinkService
            if (service == null) {
                errorMessage = "This anime isn't linked to a source yet — link one to see its episodes"
                return@LaunchedEffect
            }
            autoMatchState = AutoMatchState.Searching
            val match = service.autoLinkOne(animeId, animeTitle ?: "")
            if (match != null) {
                autoLinkedSourceId = match.sourceId
                autoLinkedUrl = match.url
                toastHost.show("Found source: ${match.sourceName}", ToastDuration.SHORT)
            } else {
                autoMatchState = AutoMatchState.NotFound
                errorMessage = "No streaming source found for \"${animeTitle ?: "this anime"}\" — link one manually"
            }
        }

        // Fetch from source API if available
        var fetchRetryToken by remember { mutableIntStateOf(0) }
        LaunchedEffect(effectiveSourceId, effectiveAnimeUrl, fetchRetryToken) {
            if (effectiveSourceId == null || effectiveAnimeUrl == null) {
                // No source yet — the auto-match effect owns this case.
                isLoading = false
                return@LaunchedEffect
            }
            isLoading = true
            errorMessage = null
            val source = extensionManager?.getSource(effectiveSourceId)
            if (source != null) {
                UIActionLogger.logExtension(animeTitle ?: "Unknown", "fetchDetails", "sourceId=$effectiveSourceId")
                try {
                    // Fetch anime details
                    val sAnime = SAnime.create().apply {
                        url = effectiveAnimeUrl
                        title = animeTitle ?: ""
                    }
                    val details = source.getAnimeDetails(sAnime)

                    // Some source implementations return a new SAnime without copying
                    // url back from the input. Ensure it's set before using.
                    ensureUrlIsSet(details, sAnime.url)

                    val sourceAnime = details.toAnimeModel(effectiveSourceId)

                    // Fetch episode list
                    val sourceEpisodes = source.getEpisodeList(sAnime)
                    val episodeModels = sourceEpisodes.map { it.toEpisodeModel(sourceAnime.id) }

                    anime = sourceAnime
                    episodes = episodeModels
                } catch (e: NoClassDefFoundError) {
                    errorMessage = "This source needs its extension installed — install it from the Extensions tab"
                    toastHost.show(
                        text = "Missing source extension — install it from the Extensions tab",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = effectiveSourceId?.toString(),
                        throwable = e,
                        location = "AnimeDetailScreen.fetchAnimeDetails",
                    )
                } catch (e: Exception) {
                    errorMessage = "Couldn't load anime details — check your connection and try again"
                    toastHost.show(
                        text = "Couldn't load anime details",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = effectiveSourceId?.toString(),
                        throwable = e,
                        location = "AnimeDetailScreen.fetchAnimeDetails",
                    )
                }
            } else {
                errorMessage = "Source not found — install this anime's extension via the Extensions tab"
            }
            isLoading = false
        }

        // Request focus on mount so keyboard shortcuts work immediately
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        // Shortcut actions (⌘D favorite, ⌘⇧C copy URL, ⌘⇧O browser) — shared
        // between the onKeyEvent handler and the top-bar buttons below so the
        // advertised shortcuts always match the buttons.
        val toggleFavoriteAction: () -> Unit = {
            val currentAnime = anime
            if (currentAnime != null) {
                val isNowFavorite = !currentAnime.favorite
                anime = currentAnime.copy(favorite = isNowFavorite)
                if (isNowFavorite) {
                    libraryRepo.add(
                        LibraryRepository.LibraryEntry(
                            animeId = currentAnime.id,
                            title = currentAnime.title,
                            sourceId = sourceId ?: currentAnime.source,
                            url = currentAnime.url,
                            thumbnailUrl = currentAnime.thumbnailUrl,
                            author = currentAnime.author,
                            artist = currentAnime.artist,
                            description = currentAnime.description,
                            genre = currentAnime.genre,
                            status = currentAnime.status,
                        )
                    )
                    toastHost.show("Added to library", ToastDuration.SHORT)
                } else {
                    libraryRepo.remove(currentAnime.id)
                    toastHost.show("Removed from library", ToastDuration.SHORT)
                }
            }
        }
        val copyUrlAction: () -> Unit = {
            val url = anime?.url
            if (url != null) {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(url), null)
                    toastHost.show("URL copied to clipboard", ToastDuration.SHORT)
                } catch (_: Exception) {
                    toastHost.show(
                        text = "Could not copy URL",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = sourceId?.toString(),
                        location = "AnimeDetailScreen.copyUrl",
                    )
                }
            } else toastHost.show("No URL available", ToastDuration.SHORT)
        }
        val openInBrowserAction: () -> Unit = {
            val url = anime?.url
            if (url != null) {
                try {
                    Desktop.getDesktop().browse(URI(url))
                } catch (_: Exception) {
                    toastHost.show(
                        text = "Could not open browser",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = sourceId?.toString(),
                        location = "AnimeDetailScreen.openInBrowser",
                    )
                }
            } else toastHost.show("No URL available", ToastDuration.SHORT)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.isMetaPressed) {
                        when (event.key) {
                            Key.E -> {
                                val url = anime?.url
                                if (url != null) {
                                    val shared = MacOSShareUtil.shareUrl(
                                        title = "Anikku",
                                        text = url,
                                        description = "${anime?.title} — URL copied to clipboard",
                                    )
                                    if (shared) {
                                        toastHost.show("URL ready to share", ToastDuration.SHORT)
                                    } else {
                                        toastHost.show(
                                            text = "Could not share",
                                            duration = ToastDuration.SHORT,
                                            isError = true,
                                            source = sourceId?.toString(),
                                            location = "AnimeDetailScreen.share",
                                        )
                                    }
                                } else {
                                    toastHost.show("No URL available", ToastDuration.SHORT)
                                }
                                true
                            }
                            Key.D -> {
                                toggleFavoriteAction()
                                true
                            }
                            Key.C -> if (event.isShiftPressed) {
                                copyUrlAction()
                                true
                            } else {
                                false
                            }
                            Key.O -> if (event.isShiftPressed) {
                                openInBrowserAction()
                                true
                            } else {
                                false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading anime...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                errorMessage != null -> {
                    // Show error state when source API call failed or no match
                    // was found while auto-linking.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            if (this@AnimeDetailScreen.sourceId == null) {
                                OutlinedButton(onClick = {
                                    navigator.push(
                                        LinkSourceScreen(
                                            animeId = animeId,
                                            animeTitle = animeTitle ?: "",
                                            anilistId = libraryRepo.get(animeId)?.anilistId,
                                        ),
                                    )
                                }) {
                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Link to a source")
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Go back")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { fetchRetryToken++ }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                    }
                }

                anime == null -> {
                    // No anime yet — auto-matching a source for a source-less
                    // entry is in flight (or nothing to render).
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Searching for a streaming source…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                anime != null -> {
                    AnimeDetailContent(
                        anime = anime!!,
                        episodes = episodes,
                        downloadStates = downloadStateMap,
                        isFavorite = anime?.favorite ?: false,
                        onToggleFavorite = toggleFavoriteAction,
                        onToggleBookmark = { episodeId ->
                            val newState = bookmarkStore.toggleBookmark(episodeId)
                            episodes = episodes.map { ep ->
                                if (ep.id == episodeId) ep.copy(bookmark = newState) else ep
                            }
                            toastHost.show(
                                if (newState) "Episode bookmarked" else "Bookmark removed",
                                ToastDuration.SHORT,
                            )
                        },
                        onMarkAllSeen = {
                            val unseenCount = episodes.count { !it.seen }
                            if (unseenCount == 0) {
                                toastHost.show("All episodes already seen", ToastDuration.SHORT)
                            } else {
                                episodes = episodes.map { it.copy(seen = true) }
                                toastHost.show("Marked $unseenCount episodes as seen", ToastDuration.SHORT)
                                episodes.maxByOrNull { it.episodeNumber }?.let { lastEp ->
                                    scope.launch(Dispatchers.IO) {
                                        val result = trackerManager?.scrobbleProgress(
                                            anime?.title ?: animeTitle ?: "",
                                            lastEp.episodeNumber.toInt(),
                                        ) ?: return@launch
                                        result.toToastMessage()?.let { message ->
                                            withContext(Dispatchers.Main) {
                                                val duration = if (result.failures.isNotEmpty()) ToastDuration.LONG else ToastDuration.SHORT
                                                toastHost.show(
                                                    text = message,
                                                    duration = duration,
                                                    isError = result.failures.isNotEmpty(),
                                                    source = sourceId?.toString(),
                                                    location = "AnimeDetailScreen.scrobbleProgress",
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onShare = {
                            val url = anime?.url
                            if (url != null) {
                                val shared = MacOSShareUtil.shareUrl(
                                    title = "Anikku",
                                    text = url,
                                    description = "${anime?.title} — URL copied to clipboard",
                                )
                                if (shared) toastHost.show("URL ready to share", ToastDuration.SHORT)
                                else toastHost.show(
                                    text = "Could not share",
                                    duration = ToastDuration.SHORT,
                                    isError = true,
                                    source = sourceId?.toString(),
                                    location = "AnimeDetailScreen.onShare",
                                )
                            } else toastHost.show("No URL available", ToastDuration.SHORT)
                        },
                        onCopyUrl = copyUrlAction,
                        onOpenInBrowser = openInBrowserAction,
                        onBack = { navigator.pop() },
                        onPlayEpisode = { episode ->
                            navigator.push(
                                PlayerScreen(
                                    animeId = anime?.id ?: animeId,
                                    episodeId = episode.id,
                                    sourceId = effectiveSourceId,
                                    episodeUrl = episode.url,
                                    animeUrl = anime?.url ?: effectiveAnimeUrl,
                                    animeTitle = anime?.title ?: animeTitle,
                                    extensionManager = extensionManager,
                                    downloadManager = effectiveDownloadManager,
                                    coverUrl = anime?.thumbnailUrl,
                                )
                            )
                        },
                        onLinkToTracker = {
                                navigator.push(
                                    TrackerSearchScreen(
                                        animeTitle = anime?.title ?: animeTitle ?: "",
                                    )
                                )
                            },
                            onDownloadEpisode = { episode ->
                            val dm = effectiveDownloadManager
                            if (dm != null && effectiveSourceId != null) {
                            val isAlready = downloadStateMap.containsKey(episode.episodeNumber)
                                if (isAlready) {
                                    toastHost.show("Already in downloads", ToastDuration.SHORT)
                                } else {
                                    dm.enqueue(
                                        animeId = anime?.id ?: animeId,
                                        sourceId = effectiveSourceId,
                                        animeTitle = anime?.title ?: "Unknown",
                                        episodeName = episode.name,
                                        episodeNumber = episode.episodeNumber,
                                        episodeUrl = episode.url,
                                        coverUrl = anime?.thumbnailUrl,
                                    )
                                    toastHost.show("Queued: ${episode.name}", ToastDuration.SHORT)
                                }
                            } else {
                                val reason = when {
                                    effectiveSourceId == null -> "No streaming source available"
                                    dm == null -> "Download manager not initialized"
                                    else -> "Download not available"
                                }
                                toastHost.show(reason, ToastDuration.SHORT)
                            }
                        },
                        onDownloadNextN = {
                            val dm = effectiveDownloadManager
                            if (dm != null && effectiveSourceId != null) {
                                val target = episodes
                                    .filter { it.episodeNumber > 0 }
                                    .sortedBy { it.episodeNumber }
                                    .filter { !downloadStateMap.containsKey(it.episodeNumber) }
                                    .take(3)
                                if (target.isEmpty()) {
                                    toastHost.show("Next episodes already downloaded", ToastDuration.SHORT)
                                } else {
                                    target.forEach { episode ->
                                        dm.enqueue(
                                            animeId = anime?.id ?: animeId,
                                            sourceId = effectiveSourceId,
                                            animeTitle = anime?.title ?: "Unknown",
                                            episodeName = episode.name,
                                            episodeNumber = episode.episodeNumber,
                                            episodeUrl = episode.url,
                                        coverUrl = anime?.thumbnailUrl,
                                        )
                                    }
                                    toastHost.show("Queued next ${target.size} episode(s)", ToastDuration.SHORT)
                                }
                            } else {
                                val reason = when {
                                    effectiveSourceId == null -> "No streaming source available"
                                    dm == null -> "Download manager not initialized"
                                    else -> "Download not available"
                                }
                                toastHost.show(reason, ToastDuration.SHORT)
                            }
                        },
                        onDownloadAll = {
                            val dm = effectiveDownloadManager
                            if (dm != null && effectiveSourceId != null) {
                                val target = episodes
                                    .filter { it.episodeNumber > 0 && it.url?.isNotBlank() == true }
                                    .sortedBy { it.episodeNumber }
                                    .filter { !downloadStateMap.containsKey(it.episodeNumber) }
                                if (target.isEmpty()) {
                                    toastHost.show("All episodes already downloaded", ToastDuration.SHORT)
                                } else {
                                    target.forEach { episode ->
                                        dm.enqueue(
                                            animeId = anime?.id ?: animeId,
                                            sourceId = effectiveSourceId,
                                            animeTitle = anime?.title ?: "Unknown",
                                            episodeName = episode.name,
                                            episodeNumber = episode.episodeNumber,
                                            episodeUrl = episode.url,
                                        coverUrl = anime?.thumbnailUrl,
                                        )
                                    }
                                    toastHost.show("Queued ${target.size} episode(s)", ToastDuration.SHORT)
                                }
                            } else {
                                val reason = when {
                                    effectiveSourceId == null -> "No streaming source available"
                                    dm == null -> "Download manager not initialized"
                                    else -> "Download not available"
                                }
                                toastHost.show(reason, ToastDuration.SHORT)
                            }
                        },
                        onRemoveFromContinueWatching = { episode ->
                            historyRepo?.removeForEpisode(anime?.id ?: animeId, episode.id)
                            toastHost.show("Removed from continue watching", ToastDuration.SHORT)
                        },
                        onRemoveDownload = { episode ->
                            val dm = effectiveDownloadManager
                            if (dm != null) {
                                val ids = dm.downloads.value
                                    .filter { it.animeId == (anime?.id ?: animeId) && it.episodeNumber == episode.episodeNumber }
                                    .map { it.id }
                                ids.forEach { dm.cancel(it) }
                                toastHost.show(
                                    if (ids.isNotEmpty()) "Download removed" else "Not downloaded",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AnimeDetailContent(
    anime: AnimeModel,
    episodes: List<EpisodeModel>,
    downloadStates: Map<Double, DownloadRepository.DownloadEntry> = emptyMap(),
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBookmark: (Long) -> Unit,
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onLinkToTracker: () -> Unit = {},
    onBack: () -> Unit,
    onMarkAllSeen: () -> Unit = {},
    onPlayEpisode: (EpisodeModel) -> Unit,
    onDownloadEpisode: (EpisodeModel) -> Unit = {},
    onDownloadNextN: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onRemoveFromContinueWatching: (EpisodeModel) -> Unit = {},
    onRemoveDownload: (EpisodeModel) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = anime.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TooltipArea(
                        tooltip = { TooltipContent("Add to favorites  (⌘D)") },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TooltipArea(
                        tooltip = { TooltipContent("Share  (⌘E)") },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                    }
                    TooltipArea(
                        tooltip = { TooltipContent("Copy URL  (⌘⇧C)") },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onCopyUrl) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy URL")
                        }
                    }
                    TooltipArea(
                        tooltip = { TooltipContent("Open in browser  (⌘⇧O)") },
                        delayMillis = 500,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(8.dp, 8.dp),
                        ),
                    ) {
                        IconButton(onClick = onOpenInBrowser) {
                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open in browser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") {
                AnimeInfoHeader(
                    anime = anime,
                    episodes = episodes,
                    onPlayEpisode = onPlayEpisode,
                    onShare = onShare,
                    onCopyUrl = onCopyUrl,
                    onOpenInBrowser = onOpenInBrowser,
                    onLinkToTracker = onLinkToTracker,
                )
            }

            item(key = "description") {
                if (!anime.description.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(anime.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item(key = "episodes_header") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val detailSettings = LocalSettingsState.current
                val autoDownloadOn = detailSettings.isAutoDownloadEnabled(anime.id)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        TextButton(
                            onClick = onMarkAllSeen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark all seen", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = onDownloadNextN,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download next 3", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = onDownloadAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download all", style = MaterialTheme.typography.labelSmall)
                        }
                        // Per-anime auto-download: new episodes queue themselves
                        // after library checks (global switch in Settings).
                        TextButton(
                            onClick = { detailSettings.setAutoDownload(anime.id, !autoDownloadOn) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (autoDownloadOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (autoDownloadOn) "Auto-download on" else "Auto-download new",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoDownloadOn) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("${episodes.size} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(items = episodes, key = { it.id }) { episode ->
                val download = downloadStates[episode.episodeNumber]
                EpisodeItem(
                    episode = episode,
                    onClick = { onPlayEpisode(episode) },
                    onToggleBookmark = { onToggleBookmark(episode.id) },
                    onDownload = { onDownloadEpisode(episode) },
                    downloadStatus = download?.status,
                    downloadProgress = download?.progress ?: 0f,
                    overflowItems = listOf(
                        OverflowItem(
                            "Remove from continue watching",
                            Icons.Outlined.History,
                            { onRemoveFromContinueWatching(episode) },
                        ),
                        OverflowItem(
                            "Remove download",
                            Icons.Outlined.Delete,
                            { onRemoveDownload(episode) },
                        ),
                    ),
                )
            }

            if (episodes.isEmpty()) {
                item(key = "no_episodes") {
                    EmptyState(
                        icon = Icons.Outlined.PlayCircle,
                        title = "No episodes available",
                        hint = "The source returned no episodes — try another source or check back later",
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeInfoHeader(
    anime: AnimeModel,
    episodes: List<EpisodeModel>,
    onPlayEpisode: (EpisodeModel) -> Unit,
    onShare: () -> Unit = {},
    onCopyUrl: () -> Unit = {},
    onOpenInBrowser: () -> Unit = {},
    onLinkToTracker: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AnimeCoverImage(
            thumbnailUrl = anime.thumbnailUrl,
            contentDescription = anime.title,
            title = anime.title,
            modifier = Modifier.width(120.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(anime.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            val statusText = when (anime.status) {
                1 -> "Ongoing"; 2 -> "Completed"; 3 -> "Licensed"
                4 -> "Publishing Finished"; 5 -> "Cancelled"; 6 -> "On Hiatus"
                else -> "Unknown"
            }
            Text(statusText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            if (!anime.author.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("by ${anime.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))

            if (!anime.genre.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    anime.genre.take(3).forEach { genre ->
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Text(genre, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val firstUnseen = episodes.firstOrNull { !it.seen }
                    if (firstUnseen != null) onPlayEpisode(firstUnseen)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Continue Watching")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), enabled = anime.url != null) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCopyUrl, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), enabled = anime.url != null) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Copy URL")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenInBrowser, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), enabled = anime.url != null) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Open in Browser")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLinkToTracker, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Link to Tracker")
            }
        }
    }
}

@Composable
private fun TooltipContent(text: String) {
    Surface(
        modifier = Modifier.shadow(4.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeItem(
    episode: EpisodeModel,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    onDownload: () -> Unit = {},
    downloadStatus: DownloadRepository.DownloadStatus? = null,
    downloadProgress: Float = 0f,
    overflowItems: List<OverflowItem>? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (episode.seen) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (episode.seen) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (episode.seen) "Seen" else "Unseen",
                tint = if (episode.seen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Episode ${String.format("%.0f", episode.episodeNumber)}", style = MaterialTheme.typography.bodyMedium, fontWeight = if (!episode.seen) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(episode.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Download button — reflects the real download state (queued,
            // downloading with progress, paused, done, error).
            val downloadDescription = when (downloadStatus) {
                DownloadRepository.DownloadStatus.QUEUED -> "Queued"
                DownloadRepository.DownloadStatus.DOWNLOADING -> "Downloading"
                DownloadRepository.DownloadStatus.PAUSED -> "Paused"
                DownloadRepository.DownloadStatus.COMPLETED -> "Downloaded"
                DownloadRepository.DownloadStatus.ERROR -> "Download failed — click to retry"
                null -> "Download episode"
            }
            TooltipArea(
                tooltip = { TooltipContent(downloadDescription) },
                delayMillis = 500,
                tooltipPlacement = TooltipPlacement.CursorPoint(
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(8.dp, 8.dp),
                ),
            ) {
                IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                    when (downloadStatus) {
                        DownloadRepository.DownloadStatus.QUEUED ->
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        DownloadRepository.DownloadStatus.DOWNLOADING ->
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        DownloadRepository.DownloadStatus.PAUSED ->
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        DownloadRepository.DownloadStatus.COMPLETED ->
                            Icon(
                                imageVector = Icons.Outlined.DownloadDone,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        DownloadRepository.DownloadStatus.ERROR ->
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        null ->
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = downloadDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                    }
                }
            }

            TooltipArea(
                tooltip = {
                    TooltipContent(
                        if (episode.bookmark) "Remove bookmark" else "Bookmark episode",
                    )
                },
                delayMillis = 500,
                tooltipPlacement = TooltipPlacement.CursorPoint(
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(8.dp, 8.dp),
                ),
            ) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (episode.bookmark) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (episode.bookmark) "Remove bookmark" else "Bookmark episode",
                        tint = if (episode.bookmark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (overflowItems != null) {
                OverflowMenu(
                    items = overflowItems,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            if (!episode.seen) {
                Spacer(Modifier.width(4.dp))
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }

        // Live progress while this episode is downloading.
        if (downloadStatus == DownloadRepository.DownloadStatus.DOWNLOADING) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
