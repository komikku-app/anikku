package app.anikku.macos.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anikku.macos.player.MPVLib
import app.anikku.macos.player.MPVSoftwareRenderer
import app.anikku.macos.player.MPVVideoSurface
import app.anikku.macos.player.PlaybackState
import app.anikku.macos.player.PlayerViewModel
import app.anikku.macos.platform.auth.LocalTrackerManager
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LocalDownloadManager
import app.anikku.macos.platform.data.LocalHistoryRepository
import app.anikku.macos.platform.download.MacOSDownloadManager
import app.anikku.macos.platform.extension.MacOSExtensionManager
import app.anikku.macos.platform.logging.UIActionLogger
import app.anikku.macos.platform.media.MacOSHttpServer
import app.anikku.macos.ui.AnikkuScreen
import app.anikku.macos.ui.components.LocalToastHost
import app.anikku.macos.ui.components.OfflineBadge
import app.anikku.macos.ui.components.OfflineCheckmarkAnimation
import app.anikku.macos.ui.components.PlaybackStateBadge
import app.anikku.macos.ui.components.VideoQualityBadge
import app.anikku.macos.ui.components.ToastDuration
import app.anikku.macos.ui.screens.models.EpisodeModel
import app.anikku.macos.ui.screens.models.toEpisodeModel
import app.anikku.macos.ui.screens.tracker.TrackerSearchScreen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private val logger = KotlinLogging.logger {}

/**
 * Player screen — Phase 6: mpv Integration + Phase 7: Offline playback.
 *
 * Full-screen video player with:
 * - MPV video surface (via JNA libmpv bindings)
 * - Auto-hiding controls overlay (top bar + bottom controls)
 * - Seek bar with elapsed / total time (from mpv position tracking)
 * - Play / pause, skip forward / backward
 * - Previous / next episode navigation
 * - Volume control
 * - Screenshot support
 * - Keyboard shortcuts (space, arrows, +/- for volume)
 * - **Offline playback**: if the episode is downloaded, serves it from
 *   local storage via [MacOSHttpServer] instead of fetching from source.
 *
 * When [sourceId] and [extensionManager] are provided, fetches video URLs
 * from the extension source API and loads them into mpv for real playback.
 * If [downloadManager] is provided, checks for local downloads first.
 * Falls back to mock simulation when source is not available.
 *
 * @param animeId     The anime these episodes belong to.
 * @param episodeId   The episode to start playing.
 * @param sourceId    Source ID for extension API lookup (optional).
 * @param episodeUrl  The episode URL on the source (required for source API).
 * @param extensionManager Extension manager for source lookup (optional).
 * @param downloadManager Download manager for offline playback (optional).
 */
data class PlayerScreen(
    val animeId: Long,
    val episodeId: Long,
    val sourceId: Long? = null,
    val episodeUrl: String? = null,
    val animeUrl: String? = null,
    val animeTitle: String? = null,
    val extensionManager: MacOSExtensionManager? = null,
    val downloadManager: MacOSDownloadManager? = null,
) : AnikkuScreen() {

    override val key: ScreenKey = uniqueScreenKey

    /**
     * Result of resolving a video for an episode.
     * @property url The video URL to play
     * @property headers Optional HTTP headers (Referer, User-Agent, etc.) needed by mpv
     */
    data class VideoResolution(
        val url: String,
        val headers: Map<String, String>? = null,
        val subtitleTracks: List<Track> = emptyList(),
    )

    /**
     * Immutable snapshot of a video candidate from the source's video list.
     * Used to store and display available quality options for the user.
     */
    data class VideoCandidate(
        val url: String,
        val label: String?,
        val resolution: Int?,
    )

    /**
     * Verify that a video URL isn't an HTML page by checking its Content-Type
     * via a lightweight HEAD request. Only performs the network check for URLs
     * that don't have a recognizable video file extension — ambiguous URLs
     * (e.g., CDN proxies with hashed paths) are assumed to be valid.
     *
     * @return true if the URL looks like a playable video stream,
     *         false if the server returned text/html (indicating an embed page).
     */
    private suspend fun checkUrlContentType(url: String): Boolean {
        // Only HTTP(S) URLs can be checked with a HEAD request.
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return true
        }

        // Fast path: URLs with known video extensions skip the HEAD request
        val knownVideoExts = listOf(
            ".mp4", ".m3u8", ".ts", ".webm", ".mkv", ".avi", ".mov",
            ".flv", ".wmv", ".ogv", ".3gp", ".mpd", ".m4v",
        )
        val lowerUrl = url.lowercase()
        if (knownVideoExts.any { lowerUrl.contains(it) }) {
            return true
        }

        // Slow path: do a HEAD request to check Content-Type
        return withContext(Dispatchers.IO) {
            try {
                val parsedUrl = java.net.URL(url)
                val connection = parsedUrl.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.setRequestProperty("User-Agent", MPVLib.DEFAULT_USER_AGENT)
                // Some CDNs require a Referer matching their embed page to serve content.
                // Use the URL origin (scheme + host + port) so query strings and trailing
                // slashes do not produce an broken Referer.
                val origin = runCatching {
                    val portSuffix = if (parsedUrl.port == -1 || parsedUrl.port == parsedUrl.defaultPort) "" else ":${parsedUrl.port}"
                    "${parsedUrl.protocol}://${parsedUrl.host}$portSuffix"
                }.getOrNull() ?: url.substringBeforeLast("/")
                connection.setRequestProperty("Referer", origin)
                connection.connect()

                val contentType = connection.contentType ?: ""
                val responseCode = connection.responseCode
                connection.disconnect()

                if (contentType.startsWith("text/html")) {
                    logger.warn { "🎬 VIDEO_URL_CONTENT_CHECK: URL returned text/html (HTTP $responseCode) " +
                        "— likely an embed/player page, not a video stream: ${url.take(80)}" }
                    return@withContext false
                }

                if (responseCode in 400..499) {
                    logger.warn { "🎬 VIDEO_URL_CONTENT_CHECK: URL returned HTTP $responseCode " +
                        "— may be blocked or require auth: ${url.take(80)}" }
                    // Don't reject — allow it through so mpv can try with proper headers
                }

                logger.info { "🎬 VIDEO_URL_CONTENT_CHECK: URL content-type=$contentType (HTTP $responseCode) — looks playable: ${url.take(60)}" }
                true
            } catch (e: Exception) {
                // If we can't check (timeout, DNS failure, etc.), allow it through.
                // mpv will try to play it and handle failures with its own timeouts.
                logger.debug { "🎬 VIDEO_URL_CONTENT_CHECK: Could not verify URL (non-fatal): ${e.message}" }
                true
            }
        }
    }

    /**
     * Result of resolving a video for an episode — includes the resolution and
     * which candidate was selected so the caller can track what was tried.
     * @property url The video URL to play
     * @property headers Optional HTTP headers (Referer, User-Agent, etc.) needed by mpv
     * @property candidateIndex The index in the [VideoCandidate] list that was selected
     */
    /**
     * Diagnostic error classification that tells the user WHY the video
     * resolution failed in plain English, with a suggestion for how to
     * resolve the issue.
     */
    data class ErrorDiagnostic(
        val title: String,
        val description: String,
        val suggestion: String,
        val category: ErrorCategory = ErrorCategory.OTHER,
    ) {
        enum class ErrorCategory {
            DNS,            // Domain name not found
            SSL,            // SSL/TLS certificate error
            TIMEOUT,        // Connection/read timeout
            FORBIDDEN,      // HTTP 403 — blocked
            NOT_FOUND,      // HTTP 404 — API changed
            SERVER_ERROR,   // HTTP 5xx — server down
            CONNECTION,     // Connection refused / network error
            EMPTY_RESULT,   // Source returned no videos
            EMBED_URL,      // Source returned embed page instead of video
            API_CHANGED,    // Extension API format doesn't match source
            OTHER,          // Unclassified
        }

        companion object {
            /**
             * Classify an exception into a user-friendly diagnostic.
             * @param e The exception thrown during video resolution
             * @param sourceName Optional source name for context in the suggestion
             */
            fun fromException(e: Exception, sourceName: String? = null): ErrorDiagnostic {
                val source = sourceName ?: "this source"

                return when (e) {
                    is UnknownHostException -> ErrorDiagnostic(
                        title = "DNS Resolution Failed",
                        description = "The domain for $source could not be found. This usually means the website is down, has moved to a new address, or your DNS resolver can't reach it.",
                        suggestion = "Try a different source extension. If the site recently changed domains, the extension may need to be updated.",
                        category = ErrorCategory.DNS,
                    )
                    is SSLException, is javax.net.ssl.SSLHandshakeException -> ErrorDiagnostic(
                        title = "SSL/TLS Error",
                        description = "$source returned an SSL certificate error. The website's security certificate may have expired or is invalid.",
                        suggestion = "Try a different source. If you trust this site, check if it's accessible in a browser.",
                        category = ErrorCategory.SSL,
                    )
                    is SocketTimeoutException, is java.util.concurrent.TimeoutException -> ErrorDiagnostic(
                        title = "Connection Timed Out",
                        description = "The request to $source timed out. The server may be overloaded or unreachable from your network.",
                        suggestion = "Check your internet connection and try again. The source may be temporarily down.",
                        category = ErrorCategory.TIMEOUT,
                    )
                    is ConnectException -> ErrorDiagnostic(
                        title = "Connection Refused",
                        description = "$source actively refused the connection. The server may be down or blocking requests from this app.",
                        suggestion = "Try again later, or use a different source extension.",
                        category = ErrorCategory.CONNECTION,
                    )
                    else -> {
                        // Try to classify by error message patterns
                        val msg = e.message ?: ""
                        val lowerMsg = msg.lowercase()
                        when {
                            lowerMsg.contains("403") || lowerMsg.contains("forbidden") ->
                                ErrorDiagnostic(
                                    title = "Access Forbidden (HTTP 403)",
                                    description = "$source is blocking the request. This is often due to Cloudflare protection or region restrictions.",
                                    suggestion = "Try enabling a proxy in Network Settings, or use a different source.",
                                    category = ErrorCategory.FORBIDDEN,
                                )
                            lowerMsg.contains("404") || lowerMsg.contains("not found") ->
                                ErrorDiagnostic(
                                    title = "Page Not Found (HTTP 404)",
                                    description = "The video API endpoint for $source returned a 'not found' error. The source's API may have changed.",
                                    suggestion = "This source's video API has likely changed and the extension needs to be updated. Try a different source.",
                                    category = ErrorCategory.NOT_FOUND,
                                )
                            lowerMsg.contains("5") && (lowerMsg.contains("server") || lowerMsg.contains("gateway") || lowerMsg.contains("503") || lowerMsg.contains("502") || lowerMsg.contains("520")) ->
                                ErrorDiagnostic(
                                    title = "Server Error (HTTP 5xx)",
                                    description = "$source's server returned a server error. The site may be temporarily down or overloaded.",
                                    suggestion = "Try again later, or use a different source.",
                                    category = ErrorCategory.SERVER_ERROR,
                                )
                            lowerMsg.contains("embed") || lowerMsg.contains("player") || lowerMsg.contains("iframe") ->
                                ErrorDiagnostic(
                                    title = "Embed Page Instead of Video",
                                    description = "$source returned a player/embed page URL instead of a direct video stream. This extension needs an update to extract video URLs from the embed page.",
                                    suggestion = "Try a different source that returns direct video URLs.",
                                    category = ErrorCategory.EMBED_URL,
                                )
                            lowerMsg.contains("cloudflare") || lowerMsg.contains("cf-") || lowerMsg.contains("checking your browser") ->
                                ErrorDiagnostic(
                                    title = "Cloudflare Challenge Detected",
                                    description = "$source is protected by Cloudflare and requires a JavaScript challenge to be solved.",
                                    suggestion = "The app will try to bypass Cloudflare automatically using Chrome. Press Retry or enable Chrome in Network Settings.",
                                    category = ErrorCategory.FORBIDDEN,
                                )
                            lowerMsg.contains("empty") || lowerMsg.contains("no videos") ->
                                ErrorDiagnostic(
                                    title = "No Videos Available",
                                    description = "$source returned an empty video list for this episode. The episode may not be available on this source.",
                                    suggestion = "Try a different episode or a different source.",
                                    category = ErrorCategory.EMPTY_RESULT,
                                )
                            else ->
                                ErrorDiagnostic(
                                    title = "Video Resolution Failed",
                                    description = "An unexpected error occurred while resolving the video URL from $source: ${e::class.simpleName ?: "Unknown error"}",
                                    suggestion = "Check the app logs for details. Try a different source or retry.",
                                    category = ErrorCategory.OTHER,
                                )
                        }
                    }
                }
            }

            /** Fallback diagnostic for when no exception is available (e.g., empty video list). */
            fun fromMessage(message: String, sourceName: String? = null): ErrorDiagnostic {
                val source = sourceName ?: "this source"
                val lowerMsg = message.lowercase()
                return when {
                    lowerMsg.contains("no videos") || lowerMsg.contains("empty") ->
                        ErrorDiagnostic(
                            title = "No Videos Available",
                            description = "$source returned an empty video list for this episode.",
                            suggestion = "Try a different episode or a different source.",
                            category = ErrorCategory.EMPTY_RESULT,
                        )
                    lowerMsg.contains("embed") || lowerMsg.contains("player") ->
                        ErrorDiagnostic(
                            title = "Embed Page Instead of Video",
                            description = "$source returned embed page URLs instead of direct video streams.",
                            suggestion = "Try a different source that returns direct video URLs.",
                            category = ErrorCategory.EMBED_URL,
                        )
                    lowerMsg.contains("remaining") || lowerMsg.contains("all candidate") ->
                        ErrorDiagnostic(
                            title = "All Video Qualities Rejected",
                            description = "Every available video quality from $source was checked and rejected. The extension may need an update.",
                            suggestion = "Try a different source, or check if the source website is accessible in a browser.",
                            category = ErrorCategory.API_CHANGED,
                        )
                    else ->
                        ErrorDiagnostic(
                            title = "Video Resolution Failed",
                            description = "$source was unable to provide a playable video URL for this episode: $message",
                            suggestion = "Try retrying, or use a different source.",
                            category = ErrorCategory.OTHER,
                        )
                }
            }
        }
    }

    data class ResolveResult(
        val url: String,
        val headers: Map<String, String>? = null,
        val subtitleTracks: List<Track> = emptyList(),
        val candidateIndex: Int = -1,
        val qualityResolution: Int? = null,
        val qualityLabel: String? = null,
    )

    /**
     * Resolve the video URL for a given episode.
     * Priority:
     * 1. Local file (if downloaded) → serve via MacOSHttpServer
     * 2. Source API (if extension is available) — tries videos starting from [startIndex]
     * 3. null (fallback mode)
     *
     * When a source returns multiple video qualities, [startIndex] allows retrying
     * with a different quality by skipping the previously-failed index.
     *
     * @param sEpisode The full episode object from the source (needed for getVideoList).
     * @param episodeNumber Episode number for local-file lookup.
     * @param httpServer Local HTTP server for serving downloaded files.
     * @param startIndex Index in the ordered video list to start from (0 = first quality).
     * @param onCandidateList Called with the full list of available video candidates.
     * @param onError Called when resolution fails with an error message.
     * @param onDiagnostic Called with a classified [ErrorDiagnostic] describing the failure cause.
     * @return A [ResolveResult] with the video URL and headers, or null if all videos fail.
     */
    private suspend fun resolveVideoUrl(
        sEpisode: SEpisode?,
        episodeNumber: Double,
        httpServer: MacOSHttpServer?,
        startIndex: Int = 0,
        onCandidateList: (List<VideoCandidate>) -> Unit = {},
        onError: ((String) -> Unit)? = null,
        onDiagnostic: ((ErrorDiagnostic) -> Unit)? = null,
    ): ResolveResult? {
        // Priority 1: Local download
        if (downloadManager != null && episodeNumber > 0) {
            val localFile = downloadManager.getLocalFile(animeId, episodeNumber)
            if (localFile != null && httpServer != null && httpServer.isRunning) {
                val streamUrl = httpServer.getStreamUrl(localFile)
                if (streamUrl != null) {
                    return ResolveResult(url = streamUrl)
                }
            }
        }

        // Priority 2: Source API — use full SEpisode with all fields populated
        if (sourceId != null && sEpisode != null) {
            val currentEpisodeUrl = try { sEpisode.url } catch (_: Exception) { null }
            val source = extensionManager?.getSource(sourceId)
            if (source != null && currentEpisodeUrl != null) {
                try {
                    val videos = source.getVideoList(sEpisode)
                    if (videos.isNotEmpty()) {
                        // Build ordered candidate list: preferred first, then others
                        val orderedVideos = listOfNotNull(
                            videos.firstOrNull { it.preferred },
                        ) + videos.filter { !it.preferred }

                        // Expose the candidate list to the caller for UI display
                        val candidates = orderedVideos.map { video ->
                            VideoCandidate(
                                url = video.videoUrl,
                                label = video.videoTitle.takeIf { it.isNotBlank() },
                                resolution = video.resolution,
                            )
                        }
                        onCandidateList(candidates)

                        // Try each video starting from startIndex
                        for ((orderedIndex, video) in orderedVideos.withIndex()) {
                            if (orderedIndex < startIndex) continue // skip previously-tried qualities

                            val videoUrl = video.videoUrl
                            if (videoUrl.isBlank()) continue

                            // Content-Type verification (HEAD request) — only for ambiguous URLs
                            val isPlayable = checkUrlContentType(videoUrl)
                            if (!isPlayable) {
                                logger.warn { "🎬 VIDEO_URL_REJECTED: Content-Type check failed for candidate #$orderedIndex — trying next quality" }
                                continue
                            }

                            // This URL passed all checks — use it
                            UIActionLogger.logVideoResolution(sourceId, currentEpisodeUrl, videoUrl.take(80))

                            // Extract headers from the video object for mpv.
                            // Many sources set a Referer header that the stream server requires.
                            val videoHeaders = try {
                                video.headers?.let { headers ->
                                    val map = mutableMapOf<String, String>()
                                    for (i in 0 until headers.size) {
                                        map[headers.name(i)] = headers.value(i)
                                    }
                                    if (!map.containsKey("User-Agent")) {
                                        map["User-Agent"] = MPVLib.DEFAULT_USER_AGENT
                                    }
                                    map
                                } ?: mapOf("User-Agent" to MPVLib.DEFAULT_USER_AGENT)
                            } catch (e: Exception) {
                                logger.warn { "Failed to extract headers from video: ${e.message}" }
                                mapOf("User-Agent" to MPVLib.DEFAULT_USER_AGENT)
                            }

                            return ResolveResult(
                                url = videoUrl,
                                headers = videoHeaders,
                                subtitleTracks = video.subtitleTracks,
                                candidateIndex = orderedIndex,
                                qualityResolution = video.resolution,
                                qualityLabel = video.videoTitle.takeIf { it.isNotBlank() },
                            )
                        }

                        // All videos starting from startIndex rejected
                        val rejectedCount = orderedVideos.size - startIndex
                        logger.warn { "🎬 VIDEO_URL_REJECTED: All $rejectedCount remaining candidate(s) rejected for source ${source.name} (started at index $startIndex)" }
                        UIActionLogger.logVideoResolution(sourceId, currentEpisodeUrl, "all_candidates_rejected")
                        val msg = if (startIndex > 0) {
                            "All remaining video qualities failed — try a different source"
                        } else {
                            "All video streams from this source appear to be embed pages — try a different source"
                        }
                        onError?.invoke(msg)
                        onDiagnostic?.invoke(ErrorDiagnostic.fromMessage(msg, source.name))
                    } else {
                        UIActionLogger.logVideoResolution(sourceId, currentEpisodeUrl, "no_videos_found")
                        val msg = "Source returned no videos for this episode"
                        onError?.invoke(msg)
                        onDiagnostic?.invoke(ErrorDiagnostic.fromMessage(msg, source.name))
                    }
                } catch (e: Exception) {
                    UIActionLogger.logVideoResolution(sourceId, currentEpisodeUrl, "error: ${e::class.simpleName}: ${e.message?.take(50)}")
                    val sourceName = try { source.name } catch (_: Exception) { null }
                    val diag = ErrorDiagnostic.fromException(e, sourceName)
                    onDiagnostic?.invoke(diag)
                    onError?.invoke("${diag.title}: ${e.message?.take(40)}")
                    // Fall through
                }
            }
        }

        return null
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val toastHost = LocalToastHost.current
        val historyRepo = LocalHistoryRepository.current
        val trackerManager = LocalTrackerManager.current

        // Initialize the player view model (Phase 6)
        val playerViewModel = remember { PlayerViewModel() }

        // Create and manage the local HTTP server for serving downloaded files
        // Derive the downloads directory from the first completed download's parent dir,
        // or fall back to a reasonable default.
        val httpServer = remember {
            val videosDir = downloadManager?.let { dm ->
                dm.getLocalFile(animeId, 1.0)?.parentFile
            } ?: File(System.getProperty("user.home"), "Library/Application Support/Anikku/downloads/videos")
            MacOSHttpServer(
                downloadsDir = videosDir,
            ).apply { startServer() }
        }

        // Stop the HTTP server when the screen is disposed
        DisposableEffect(httpServer) {
            onDispose {
                httpServer.stopServer()
            }
        }

        // Collect player state
        val mpvHandle by playerViewModel.handle.collectAsState()
        val softwareRenderer by playerViewModel.renderer.collectAsState()
        val playbackState by playerViewModel.playbackState.collectAsState()
        val currentPosition by playerViewModel.currentPosition.collectAsState()
        val duration by playerViewModel.duration.collectAsState()
        val isPaused by playerViewModel.isPaused.collectAsState()
        val volume by playerViewModel.volume.collectAsState()
        val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
        val brightness by playerViewModel.brightness.collectAsState()
        val contrast by playerViewModel.contrast.collectAsState()
        val saturation by playerViewModel.saturation.collectAsState()
        val gamma by playerViewModel.gamma.collectAsState()
        val subtitleDelay by playerViewModel.subtitleDelay.collectAsState()
        val audioDelay by playerViewModel.audioDelay.collectAsState()
        val aspectRatio by playerViewModel.aspectRatio.collectAsState()
        val videoRotation by playerViewModel.videoRotation.collectAsState()
        val isHflip by playerViewModel.isHflip.collectAsState()
        val isVflip by playerViewModel.isVflip.collectAsState()
        val audioTracks by playerViewModel.audioTracks.collectAsState()
        val subtitleTracks by playerViewModel.subtitleTracks.collectAsState()
        val selectedAudioTrack by playerViewModel.selectedAudioTrack.collectAsState()
        val selectedSubtitleTrack by playerViewModel.selectedSubtitleTrack.collectAsState()

        // State: episode data from source
        // Keep the original SEpisode objects so we can pass full data to getVideoList()
        var allEpisodes by remember { mutableStateOf<MutableList<EpisodeModel>>(mutableListOf()) }
        var sourceEpisodes by remember { mutableStateOf<List<SEpisode>>(emptyList()) }
        var currentEpisodeIndex by remember { mutableIntStateOf(0) }
        var animeTitle by remember { mutableStateOf(this@PlayerScreen.animeTitle ?: "Unknown") }
        var resolvedVideo by remember { mutableStateOf<VideoResolution?>(null) }
        var videoQualityResolution by remember { mutableStateOf<Int?>(null) }
        var videoQualityLabel by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var isResolvingVideo by remember { mutableStateOf(false) }
        var isOfflinePlayback by remember { mutableStateOf(false) }
        var resolutionStatusText by remember { mutableStateOf("Connecting...") }
        var videoResolutionError by remember { mutableStateOf<String?>(null) }
        var videoErrorDiagnostic by remember { mutableStateOf<ErrorDiagnostic?>(null) }
        var hasScrobbledThisEpisode by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // State for "Retry with different quality" functionality
        // Stores all video candidates from the source so we can iterate through them.
        var videoCandidates by remember { mutableStateOf<List<VideoCandidate>>(emptyList()) }
        // Index in videoCandidates that was just attempted (failed or passed checks but mpv failed).
        var lastAttemptedIndex by remember { mutableIntStateOf(-1) }

        // State for "Try All Qualities" auto-retry
        var isAutoRetrying by remember { mutableStateOf(false) }
        var autoRetryIndex by remember { mutableIntStateOf(0) }

        UIActionLogger.logScreenOpen("PlayerScreen", mapOf(
            "animeId" to animeId, "episodeId" to episodeId, "sourceId" to sourceId
        ))

        // On mount: initialize mpv and fetch episode data
        LaunchedEffect(Unit) {
            playerViewModel.initialize()

            // Step 1: Try to fetch episodes from the source
            var usedSource = false
            if (sourceId != null && episodeUrl != null) {
                try {
                    resolutionStatusText = "Fetching episode list..."
                    val source = extensionManager?.getSource(sourceId)
                    if (source != null) {
                                        // Use the explicit anime URL when available; fall back to deriving it
                        // from the episode URL only as a last resort (the heuristic is fragile
                        // and breaks for query parameters, hashed IDs, etc.).
                        val fallbackUrl = this@PlayerScreen.animeUrl ?: run {
                            logger.warn { "PlayerScreen: animeUrl is null for sourceId=$sourceId, deriving anime URL from episode URL. This may fail for some sources." }
                            episodeUrl?.substringBeforeLast("/")?.let { path ->
                                if (path.startsWith("/") || path.startsWith("http")) path else "/$path"
                            }
                        }
                        val resolvedAnimeUrl = fallbackUrl ?: ""
                        val sAnime = eu.kanade.tachiyomi.animesource.model.SAnime.create().apply {
                            url = resolvedAnimeUrl
                        }
                        resolutionStatusText = "Loading episodes from \"${source.name}\"..."
                        val fetchedEpisodes = source.getEpisodeList(sAnime)
                        sourceEpisodes = fetchedEpisodes
                        allEpisodes = fetchedEpisodes.map { it.toEpisodeModel(animeId) }.toMutableList()
                        val idx = fetchedEpisodes.indexOfFirst { ep ->
                            // Safely read episode URL — some sources return SEpisode objects
                            // with uninitialized lateinit url fields.
                            val epUrl = try { ep.url } catch (_: UninitializedPropertyAccessException) { null }
                            epUrl != null && epUrl == episodeUrl
                        }
                        if (idx >= 0) currentEpisodeIndex = idx
                        usedSource = true
                    }
                } catch (e: Exception) {
                    toastHost.show(
                        text = "Failed to fetch episodes: ${e.message?.take(60)}",
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = sourceId?.toString(),
                        throwable = e,
                        location = "PlayerScreen.LaunchedEffect.fetchEpisodes",
                    )
                }
            }

            // Step 2: Determine starting episode number for local-file check
            val currentEpisodeNumber = allEpisodes.getOrNull(currentEpisodeIndex)?.episodeNumber ?: 0.0

            // Step 3: Resolve video URL — use the full SEpisode with all fields populated
            isResolvingVideo = true
            val sourceName = extensionManager?.getSource(sourceId ?: 0)?.name ?: "source"
            resolutionStatusText = "Resolving video from \"$sourceName\"..."
            
            val currentSEpisode = sourceEpisodes.getOrNull(currentEpisodeIndex)
            val resolved = resolveVideoUrl(
                sEpisode = currentSEpisode,
                episodeNumber = currentEpisodeNumber,
                httpServer = httpServer,
                startIndex = 0,
                onCandidateList = { candidates ->
                    videoCandidates = candidates
                },
                onError = { msg ->
                    videoResolutionError = msg
                    toastHost.show(
                        text = msg,
                        duration = ToastDuration.LONG,
                        isError = true,
                        source = sourceId?.toString(),
                        location = "PlayerScreen.resolveVideoUrl",
                    )
                },
                onDiagnostic = { diag ->
                    videoErrorDiagnostic = diag
                },
            )
            isResolvingVideo = false

            // Determine if this is offline playback
            isOfflinePlayback = downloadManager != null && currentEpisodeNumber > 0 &&
                downloadManager.isDownloaded(animeId, currentEpisodeNumber)

            if (resolved != null) {
                resolvedVideo = VideoResolution(
                    url = resolved.url,
                    headers = resolved.headers,
                    subtitleTracks = resolved.subtitleTracks,
                )
                lastAttemptedIndex = resolved.candidateIndex
                videoQualityResolution = resolved.qualityResolution
                videoQualityLabel = resolved.qualityLabel
                resolutionStatusText = "Video resolved — loading into player..."
            } else if (!usedSource && allEpisodes.isEmpty()) {
                // No source data available — show loading completed with no video
                isLoading = false
                resolutionStatusText = ""
            } else {
                // Video resolution failed
                isLoading = false
                resolutionStatusText = ""
            }
        }

        // When video is resolved AND mpv is ready, load it into mpv
        // Uses a single VideoResolution state to ensure URL and headers
        // are always passed atomically (no race condition).
        LaunchedEffect(resolvedVideo, mpvHandle) {
            val video = resolvedVideo
            if (video != null && mpvHandle != null) {
                // Preserve quality-specific progress text during auto-retry
                resolutionStatusText = if (isAutoRetrying && videoQualityLabel != null) {
                    "${videoQualityLabel} — starting playback..."
                } else {
                    "Loading video into mpv player..."
                }
                playerViewModel.loadEpisode(video.url, video.headers, video.subtitleTracks)
            }
        }

        // Transition from loading to playing: when mpv reports playing state,
        // remove the loading screen so the user sees the video
        LaunchedEffect(resolvedVideo, playbackState) {
            if (resolvedVideo != null && playbackState != PlaybackState.IDLE && playbackState != PlaybackState.ERROR) {
                // Video is loading/buffering/playing — hide the loading screen
                isLoading = false
                resolutionStatusText = ""
            } else if (playbackState == PlaybackState.ERROR) {
                isLoading = false
                resolutionStatusText = ""
                videoResolutionError = "Failed to play video — stream URL may be invalid"
            }
        }

        // Save resume position to history
        fun saveResumePosition(episode: EpisodeModel?, position: Long, total: Long) {
            if (episode == null || position <= 0) return

            // Scrobble progress to linked trackers when the user has watched
            // at least 80% of the episode or reached the end. Guard with a flag
            // so we only hit the tracker APIs once per episode.
            if (!hasScrobbledThisEpisode && total > 0 && position.toDouble() / total >= 0.8) {
                hasScrobbledThisEpisode = true
                trackerManager?.let { manager ->
                    scope.launch(Dispatchers.IO) {
                        val result = manager.scrobbleProgress(animeTitle, episode.episodeNumber.toInt())
                        result.toToastMessage()?.let { message ->
                            withContext(Dispatchers.Main) {
                                val duration = if (result.failures.isNotEmpty()) ToastDuration.LONG else ToastDuration.SHORT
                                toastHost.show(
                                    text = message,
                                    duration = duration,
                                    isError = result.failures.isNotEmpty(),
                                    source = sourceId?.toString(),
                                    location = "PlayerScreen.scrobbleProgress",
                                )
                            }
                        }
                    }
                }
            }

            historyRepo.add(
                HistoryRepository.HistoryEntry(
                    animeId = animeId,
                    episodeId = episode.id,
                    animeTitle = animeTitle,
                    episodeName = episode.name,
                    episodeNumber = episode.episodeNumber,
                    sourceId = sourceId ?: 0L,
                    animeUrl = this@PlayerScreen.animeUrl,
                    episodeUrl = episode.url,
                    seenAt = System.currentTimeMillis(),
                    watchDuration = position,
                    lastSecondSeen = position,
                    totalSeconds = total,
                )
            )
        }

        // Save resume position on shutdown
        fun saveCurrentPosition() {
            val ep = allEpisodes.getOrNull(currentEpisodeIndex)
            if (ep != null && duration > 0 && currentPosition > 0) {
                saveResumePosition(ep, currentPosition.toLong(), duration.toLong())
            }
        }

        // Reset the scrobble guard when switching episodes so each episode
        // gets exactly one scrobble attempt. This runs AFTER the old episode's
        // saveResumePosition call in onNavigateEpisode (which fires before
        // currentEpisodeIndex is updated), so the flag is clean for the new episode.
        LaunchedEffect(currentEpisodeIndex) {
            hasScrobbledThisEpisode = false
        }

        // Auto-scrobble when the episode ends naturally (ENDED state) or when
        // the user pauses after watching >80%. The guard flag hasScrobbledThisEpisode
        // ensures we only fire once per episode regardless of how many times this
        // LaunchedEffect re-runs (e.g., pause → resume → pause again).
        LaunchedEffect(playbackState) {
            val episode = allEpisodes.getOrNull(currentEpisodeIndex) ?: return@LaunchedEffect
            val shouldScrobble = when (playbackState) {
                PlaybackState.ENDED -> true
                PlaybackState.PAUSED -> currentPosition > 0 && duration > 0 &&
                    currentPosition / duration >= 0.8
                else -> false
            }
            if (shouldScrobble && duration > 0) {
                saveResumePosition(episode, currentPosition.toLong(), duration.toLong())
            }
        }

        // Clean up when the screen is removed — save position + record history
        DisposableEffect(Unit) {
            onDispose {
                saveCurrentPosition()
                playerViewModel.shutdown()
            }
        }

        val currentEpisode = remember(currentEpisodeIndex, allEpisodes) {
            allEpisodes.getOrNull(currentEpisodeIndex)
        }

        // Shared helper: re-resolve the video URL starting from the given index.
        // Updates state consistently: error, video candidates, quality, and lastAttemptedIndex.
        fun resolveAndPlay(startIndex: Int) {
            scope.launch {
                videoResolutionError = null
                videoErrorDiagnostic = null
                resolvedVideo = null
                isResolvingVideo = true
                isLoading = true
                resolutionStatusText = when {
                    isAutoRetrying && videoCandidates.size > 1 -> {
                        "Auto-trying quality ${startIndex + 1}/${videoCandidates.size}..."
                    }
                    startIndex > 0 -> "Trying next video quality..."
                    else -> "Retrying video resolution..."
                }

                val se = sourceEpisodes.getOrNull(currentEpisodeIndex)
                val episodeNumber = allEpisodes.getOrNull(currentEpisodeIndex)?.episodeNumber ?: 0.0
                val resolved = resolveVideoUrl(
                    sEpisode = se,
                    episodeNumber = episodeNumber,
                    httpServer = httpServer,
                    startIndex = startIndex,
                    onCandidateList = { candidates ->
                        videoCandidates = candidates
                    },
                    onError = { msg ->
                        videoResolutionError = msg
                        toastHost.show(
                            text = msg,
                            duration = ToastDuration.LONG,
                            isError = true,
                            source = sourceId?.toString(),
                            location = "PlayerScreen.resolveAndPlay",
                        )
                    },
                    onDiagnostic = { diag ->
                        videoErrorDiagnostic = diag
                    },
                )
                isResolvingVideo = false
                isLoading = false

                if (resolved != null) {
                    resolvedVideo = VideoResolution(
                    url = resolved.url,
                    headers = resolved.headers,
                    subtitleTracks = resolved.subtitleTracks,
                )
                    lastAttemptedIndex = resolved.candidateIndex
                    videoQualityResolution = resolved.qualityResolution
                    videoQualityLabel = resolved.qualityLabel
                    // Keep quality progress visible during mpv loading
                    val qualityLabel = resolved.qualityLabel?.takeIf { it.isNotBlank() }
                    resolutionStatusText = if (isAutoRetrying && videoCandidates.size > 1) {
                        val label = qualityLabel ?: "quality ${resolved.candidateIndex + 1}"
                        "$label loaded — starting playback..."
                    } else {
                        "Video resolved — loading into player..."
                    }
                } else {
                    resolutionStatusText = ""
                }
            }
        }

        // Retry function: re-resolve the video URL for the current episode
        // starting from index 0 (same as initial load, useful after a transient failure).
        fun retryLoad() {
            resolveAndPlay(startIndex = 0)
        }

        // Retry with the next available video quality. Skips the previously-tried
        // candidate and picks the next one from the source's video list.
        fun retryWithNextQuality() {
            val nextIndex = lastAttemptedIndex + 1
            if (nextIndex < videoCandidates.size) {
                resolveAndPlay(startIndex = nextIndex)
            } else {
                // No more qualities — just do a full retry from the beginning
                toastHost.show("No more qualities available — retrying from start", ToastDuration.SHORT)
                retryLoad()
            }
        }

        // Try All Qualities: automatically iterates through all available video
        // qualities until one works or all are exhausted. Uses a LaunchedEffect
        // keyed on playbackState to detect mpv errors and try the next quality.
        fun retryAllQualities() {
            if (videoCandidates.size <= 1) {
                // Single quality (or none) — just do a normal retry
                retryLoad()
                return
            }
            isAutoRetrying = true
            autoRetryIndex = 0
            val total = videoCandidates.size
            resolutionStatusText = "Auto-trying all qualities (1/$total)..."
            resolveAndPlay(startIndex = 0)
        }

        // Auto-retry LaunchedEffect: watches for mpv ERROR state while auto-retry
        // mode is active, then automatically tries the next quality.
        // Keyed only on playbackState so intermediate resolvedVideo=null changes
        // during retry don't re-trigger the logic.
        LaunchedEffect(playbackState) {
            when {
                playbackState == PlaybackState.ERROR && isAutoRetrying && resolvedVideo != null -> {
                    val nextIndex = autoRetryIndex + 1
                    if (nextIndex < videoCandidates.size) {
                        autoRetryIndex = nextIndex
                        // Toast showing which quality is being tried
                        val total = videoCandidates.size
                        val nextCandidate = videoCandidates.getOrNull(nextIndex)
                        val qualityHint = nextCandidate?.label?.takeIf { it.isNotBlank() }
                            ?: "quality ${nextIndex + 1}"
                        toastHost.show("Trying $qualityHint (${nextIndex + 1}/$total)...", ToastDuration.SHORT)
                        resolveAndPlay(startIndex = nextIndex)
                    } else {
                        // All qualities exhausted
                        isAutoRetrying = false
                        videoResolutionError = "All $autoRetryIndex quality option(s) failed — try a different source"
                    }
                }
                playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.ENDED -> {
                    // Success — stop auto-retry
                    isAutoRetrying = false
                }
            }
        }

        PlayerContent(
            playerViewModel = playerViewModel,
            mpvHandle = mpvHandle,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            selectedAudioTrack = selectedAudioTrack,
            selectedSubtitleTrack = selectedSubtitleTrack,
            softwareRenderer = softwareRenderer,
            animeTitle = animeTitle,
            episodes = allEpisodes,
            currentEpisodeIndex = currentEpisodeIndex,
            currentEpisode = currentEpisode,
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            isPaused = isPaused,
            volume = volume,
            playbackSpeed = playbackSpeed,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            gamma = gamma,
            subtitleDelay = subtitleDelay,
            audioDelay = audioDelay,
            aspectRatio = aspectRatio,
            videoRotation = videoRotation,
            isHflip = isHflip,
            isVflip = isVflip,
            isMPVAvailable = playerViewModel.isMPVAvailable,
            isOfflinePlayback = isOfflinePlayback,
            hasVideoUrl = resolvedVideo != null,
            isResolvingVideo = isResolvingVideo,
            resolutionStatusText = resolutionStatusText,
            videoResolutionError = videoResolutionError,
            videoErrorDiagnostic = videoErrorDiagnostic,
            videoQualityResolution = videoQualityResolution,
            videoQualityLabel = videoQualityLabel,
            isLoading = isLoading,
            onBack = { navigator.pop() },
            onRetry = { retryLoad() },
            onRetryNext = { retryWithNextQuality() },
            onRetryAll = { retryAllQualities() },
            isAutoRetrying = isAutoRetrying,
            hasNextQuality = lastAttemptedIndex >= 0 && lastAttemptedIndex + 1 < videoCandidates.size,
            nextQualityLabel = videoCandidates.getOrNull(lastAttemptedIndex + 1)
                ?.label?.takeIf { it.isNotBlank() } ?: "next quality",
            onLinkToTracker = {
                navigator.push(
                    TrackerSearchScreen(
                        animeTitle = animeTitle,
                    )
                )
            },
            onNavigateEpisode = { index ->
                if (index in allEpisodes.indices) {
                    // Save resume position for current episode before switching
                    val oldEpisode = allEpisodes.getOrNull(currentEpisodeIndex)
                    if (oldEpisode != null && currentPosition > 0 && duration > 0) {
                        saveResumePosition(oldEpisode, currentPosition.toLong(), duration.toLong())
                    }

                    val oldIndex = currentEpisodeIndex
                    currentEpisodeIndex = index
                    val episode = allEpisodes[index]
                    val direction = if (index > oldIndex) "next" else "previous"
                    toastHost.show("$direction episode: ${String.format("%.0f", episode.episodeNumber)}", ToastDuration.SHORT)

                    // Resolve new episode's video URL — use full SEpisode with all fields
                    scope.launch {
                        isOfflinePlayback = downloadManager != null &&
                            episode.episodeNumber > 0 &&
                            downloadManager.isDownloaded(animeId, episode.episodeNumber)

                        val se = sourceEpisodes.getOrNull(index)
                        val resolved = resolveVideoUrl(
                            sEpisode = se,
                            episodeNumber = episode.episodeNumber,
                            httpServer = httpServer,
                            startIndex = 0,
                            onCandidateList = { candidates ->
                                videoCandidates = candidates
                            },
                            onError = { msg ->
                                toastHost.show(
                                    text = msg,
                                    duration = ToastDuration.LONG,
                                    isError = true,
                                    source = sourceId?.toString(),
                                    location = "PlayerScreen.onNavigateEpisode.resolveVideoUrl",
                                )
                            },
                            onDiagnostic = { diag ->
                                videoErrorDiagnostic = diag
                            },
                        )
                        if (resolved != null) {
                            resolvedVideo = VideoResolution(
                    url = resolved.url,
                    headers = resolved.headers,
                    subtitleTracks = resolved.subtitleTracks,
                )
                            lastAttemptedIndex = resolved.candidateIndex
                            videoQualityResolution = resolved.qualityResolution
                            videoQualityLabel = resolved.qualityLabel
                        } else {
                            toastHost.show(
                                text = "No video source available",
                                duration = ToastDuration.SHORT,
                                isError = true,
                                source = sourceId?.toString(),
                                location = "PlayerScreen.onNavigateEpisode",
                            )
                        }
                    }
                } else if (index > currentEpisodeIndex) {
                    toastHost.show("No next episode available", ToastDuration.SHORT)
                } else if (index < currentEpisodeIndex) {
                    toastHost.show("No previous episode available", ToastDuration.SHORT)
                }
            },
            onTogglePlay = { playerViewModel.togglePause() },
            onSeekTo = { seconds -> playerViewModel.seekTo(seconds) },
            onSeekRelative = { offset -> playerViewModel.seekRelative(offset) },
            onSetVolume = { vol -> playerViewModel.setVolume(vol) },
            onTakeScreenshot = {
                val result = playerViewModel.takeScreenshot()
                if (result != null) {
                    toastHost.show("Screenshot captured", ToastDuration.SHORT)
                } else {
                    toastHost.show(
                        text = "Screenshot failed",
                        duration = ToastDuration.SHORT,
                        isError = true,
                        source = sourceId?.toString(),
                        location = "PlayerScreen.takeScreenshot",
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerContent(
    playerViewModel: PlayerViewModel? = null,
    mpvHandle: com.sun.jna.Pointer? = null,
    softwareRenderer: MPVSoftwareRenderer? = null,
    audioTracks: List<app.anikku.macos.player.TrackInfo> = emptyList(),
    subtitleTracks: List<app.anikku.macos.player.TrackInfo> = emptyList(),
    selectedAudioTrack: Int = -1,
    selectedSubtitleTrack: Int = -1,
    animeTitle: String,
    episodes: List<EpisodeModel>,
    currentEpisodeIndex: Int,
    currentEpisode: EpisodeModel?,
    playbackState: PlaybackState = PlaybackState.IDLE,
    currentPosition: Double = 0.0,
    duration: Double = 0.0,
    isPaused: Boolean = true,
    volume: Int = 100,
    playbackSpeed: Double = 1.0,
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
    gamma: Float = 1f,
    subtitleDelay: Double = 0.0,
    audioDelay: Double = 0.0,
    aspectRatio: String = "-1",
    videoRotation: Int = 0,
    isHflip: Boolean = false,
    isVflip: Boolean = false,
    isMPVAvailable: Boolean = false,
    isOfflinePlayback: Boolean = false,
    hasVideoUrl: Boolean = false,
    isResolvingVideo: Boolean = false,
    resolutionStatusText: String = "",
    videoResolutionError: String? = null,
    videoErrorDiagnostic: PlayerScreen.ErrorDiagnostic? = null,
    isLive: Boolean = false,
    videoQualityResolution: Int? = null,
    videoQualityLabel: String? = null,
    isLoading: Boolean = true,
    hasNextQuality: Boolean = false,
    nextQualityLabel: String? = null,
    isAutoRetrying: Boolean = false,
    onLinkToTracker: () -> Unit = {},
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onRetryNext: () -> Unit = {},
    onRetryAll: () -> Unit = {},
    onNavigateEpisode: (Int) -> Unit,
    onTogglePlay: () -> Unit = {},
    onSeekTo: (Double) -> Unit = {},
    onSeekRelative: (Double) -> Unit = {},
    onSetVolume: (Int) -> Unit = {},
    onTakeScreenshot: () -> Unit = {},
) {
    // --- Player state ---
    // mpv's observed pause property is authoritative. Keeping a second
    // optimistic play state here caused the icon to disagree with playback
    // until the user manually toggled it.
    val isPlaying = !isPaused
    var isControlsVisible by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableLongStateOf(currentEpisode?.lastSecondSeen ?: 0L) }
    var totalSeconds by remember {
        mutableLongStateOf(currentEpisode?.totalSeconds?.takeIf { it > 0 } ?: 0L)
    }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    // Settings panel visibility
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedPanel by remember { mutableStateOf(false) }
    var showAudioPanel by remember { mutableStateOf(false) }
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showAspectRatioPanel by remember { mutableStateOf(false) }
    var showVideoFilterPanel by remember { mutableStateOf(false) }

    // Always update elapsed time from currentPosition when it changes,
    // regardless of whether duration is set yet.
    LaunchedEffect(currentPosition) {
        if (currentPosition > 0) {
            elapsedSeconds = currentPosition.toLong()
        }
    }
    // Update duration and seek fraction separately (duration changes less often)
    LaunchedEffect(duration, currentPosition) {
        if (duration > 0) {
            totalSeconds = duration.toLong()
            if (currentPosition > 0) {
                seekFraction = (currentPosition / duration).toFloat().coerceIn(0f, 1f)
            }
        } else {
            totalSeconds = 0L
            seekFraction = 0f
        }
    }

    // If episode changes, reset progress
    LaunchedEffect(currentEpisode?.id) {
        elapsedSeconds = currentEpisode?.lastSecondSeen ?: 0L
        totalSeconds = currentEpisode?.totalSeconds?.takeIf { it > 0 } ?: 0L
        seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
    }

    // Auto-hide controls after 3 seconds of playback
    LaunchedEffect(isPlaying, isControlsVisible) {
        if (isPlaying && isControlsVisible) {
            delay(3000L)
            isControlsVisible = false
        }
    }

    val toastHost = LocalToastHost.current

    // Mock simulation fallback (when mpv is not available and no real video loaded)
    // Wait 3 seconds for mpv to initialize before entering mock mode, since
    // mpv initialization (lib loading, handle creation, option config) can
    // take 1-2 seconds and isMPVAvailable may be false during that window.
    LaunchedEffect(isPlaying, playerViewModel?.playbackState?.value) {
        if (isPlaying && !isMPVAvailable && totalSeconds > 0) {
            // Give mpv a chance to initialize before entering mock mode
            delay(3000L)
            // Check again — mpv might have initialized during the delay
            if (isMPVAvailable) return@LaunchedEffect
            // Also check if a real video is loaded (playbackState would be PLAYING or LOADING)
            val state = playerViewModel?.playbackState?.value
            if (state == PlaybackState.LOADING || state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
                return@LaunchedEffect
            }
            while (true) {
                delay(1000L)
                elapsedSeconds = (elapsedSeconds + 1).coerceAtMost(totalSeconds)
                seekFraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
                if (elapsedSeconds >= totalSeconds) {
                    toastHost.show("Episode complete", ToastDuration.SHORT)
                    break
                }
            }
        }
    }

    // Keyboard shortcuts
    val canSeek = !isLive && totalSeconds > 0L
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val errorFocusRequester = remember { FocusRequester() }
    // Loading overlay (shown on top of video surface while buffering)
    val showLoadingOverlay = isLoading || isResolvingVideo

    // Show error state when video URL resolution failed, mpv reported an error,
    // or we are not still resolving it. Do NOT check playbackState == IDLE here
    // — mpv starts in IDLE while the video is loading.
    val videoResolutionFailed = currentEpisode != null && !isLoading && !isResolvingVideo &&
        (!hasVideoUrl || playbackState == PlaybackState.ERROR || videoResolutionError != null)
    if (!isMPVAvailable && episodes.isEmpty() && currentEpisode == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    animeTitle.ifBlank { "Episode not found" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Could not load episode. The source extension may need to be updated or reinstalled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(24.dp))
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
        }
        return
    }

    // Show video resolution failure state with actionable info + Retry button
    if (videoResolutionFailed) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black)
                .focusRequester(errorFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.A -> {
                            if (hasNextQuality && !isAutoRetrying) {
                                onRetryAll()
                                true
                            } else {
                                false
                            }
                        }
                        Key.R -> {
                            onRetry()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LaunchedEffect(Unit) {
                errorFocusRequester.requestFocus()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    animeTitle.ifBlank { "Unknown Anime" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(4.dp))
                if (currentEpisode != null) {
                    Text(
                        "Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    videoErrorDiagnostic?.title ?: "Could not resolve video URL",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    videoErrorDiagnostic?.description ?: "This extension's video API may differ from what the app expects. Try a different source.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 ${videoErrorDiagnostic?.suggestion ?: "Check logs (~/Library/Application Support/Anikku/logs/actions.log) for details."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4ECCA3).copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                videoErrorDiagnostic?.let { diag ->
                    Spacer(Modifier.height(12.dp))
                    // Category badge
                    val categoryLabel = when (diag.category) {
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.DNS -> "🌐 DNS Error"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.SSL -> "🔒 SSL Error"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.TIMEOUT -> "⏱ Timeout"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.FORBIDDEN -> "🚫 Blocked"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.NOT_FOUND -> "🔍 Not Found"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.SERVER_ERROR -> "⚠ Server Error"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.CONNECTION -> "🔌 Connection Error"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.EMPTY_RESULT -> "📭 Empty Result"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.EMBED_URL -> "📄 Embed Page"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.API_CHANGED -> "🔧 API Changed"
                        PlayerScreen.ErrorDiagnostic.ErrorCategory.OTHER -> "❓ Unknown"
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.White.copy(alpha = 0.06f),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                // Action buttons: Back, Retry, and (if available) Try Different Quality
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Primary row: Back + Retry
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Back button
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(24.dp))
                        // Retry button
                        Surface(
                            onClick = onRetry,
                            shape = MaterialTheme.shapes.medium,
                            color = Color(0xFF1A73E8), // Google Blue
                            tonalElevation = 0.dp,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Retry",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Secondary row: Try Different Quality (only if more qualities available)
                    if (hasNextQuality && !isAutoRetrying) {
                        Spacer(Modifier.height(12.dp))
                        // Manual: try the next single quality
                        Surface(
                            onClick = onRetryNext,
                            shape = MaterialTheme.shapes.small,
                            color = Color.White.copy(alpha = 0.08f),
                            tonalElevation = 0.dp,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = "Try different quality",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Try: ${nextQualityLabel ?: "next quality"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Auto: try all qualities automatically
                        Surface(
                            onClick = onRetryAll,
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFF1A73E8).copy(alpha = 0.15f),
                            tonalElevation = 0.dp,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                            ) {
                                Text(
                                    "▶",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1A73E8),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Try All Qualities",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1A73E8),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Press [A] for Auto-Try All",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.25f),
                        )
                    }

                    // Link to Tracker button
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onLinkToTracker,
                        shape = MaterialTheme.shapes.small,
                        color = Color(0xFF02A9FF).copy(alpha = 0.15f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        ) {
                            Text(
                                "🔗",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Link Anime to Tracker",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF02A9FF),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        // This effect is composed only with the normal player root, so its
        // FocusRequester is guaranteed to be attached before the request.
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                isControlsVisible = !isControlsVisible
            }
            .focusRequester(focusRequester)
            .focusable()
            // Handle shortcuts after focused descendants have had a chance to
            // consume their own text-editing keys. This keeps Space/arrows from
            // leaking out of a text field placed over the player in the future.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.Spacebar -> {
                        onTogglePlay()
                        isControlsVisible = true
                        true
                    }
                    event.key == Key.DirectionLeft && canSeek -> {
                        onSeekRelative(-10.0)
                        elapsedSeconds = (elapsedSeconds - 10).coerceAtLeast(0)
                        seekFraction = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                        true
                    }
                    event.key == Key.DirectionRight && canSeek -> {
                        onSeekRelative(10.0)
                        elapsedSeconds = (elapsedSeconds + 10).coerceAtMost(totalSeconds)
                        seekFraction = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                        true
                    }
                    event.key == Key.DirectionUp -> {
                        onSetVolume((volume + 5).coerceAtMost(200))
                        true
                    }
                    event.key == Key.DirectionDown -> {
                        onSetVolume((volume - 5).coerceAtLeast(0))
                        true
                    }
                    else -> false
                }
            },
    ) {
        // === Loading overlay (shown on top of video surface while buffering) ===
        if (showLoadingOverlay) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (resolutionStatusText.isNotBlank()) resolutionStatusText else "Loading episode...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    if (isResolvingVideo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This may take a few seconds — the source is fetching video streams",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.3f),
                        )
                    }
                    if (isAutoRetrying && videoQualityLabel != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Quality: ${videoQualityLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1A73E8).copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }


        // === MPV Video Surface (when available) ===
        if (isMPVAvailable && mpvHandle != null) {
            MPVVideoSurface(
                mpvHandle = mpvHandle,
                renderer = softwareRenderer,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // === Fallback: Video area placeholder ===
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (currentEpisode != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(animeTitle, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Episode ${String.format("%.0f", currentEpisode.episodeNumber)}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))
                        val mpvStatus = when {
                            !MPVLib.isAvailable && !isMPVAvailable -> "Install mpv: brew install mpv"
                            playbackState == PlaybackState.LOADING -> "Loading..."
                            playbackState == PlaybackState.BUFFERING -> "Buffering..."
                            else -> "Video area — mpv ready"
                        }
                        Text(mpvStatus, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.15f))
                    }
                } else {
                    Text("No episode selected", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }

        // === Gradient overlays for controls ===
        AnimatedVisibility(visible = isControlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                ),
            ))
        }
        AnimatedVisibility(visible = isControlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp).background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                ),
            ))
        }

        // === Top bar ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(animeTitle, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (currentEpisode != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Episode ${String.format("%.0f", currentEpisode.episodeNumber)} — ${currentEpisode.name}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (isOfflinePlayback) {
                                    Spacer(Modifier.width(8.dp))
                                    OfflineBadge()
                                }
                                videoQualityResolution?.let {
                                    Spacer(Modifier.width(6.dp))
                                    VideoQualityBadge(
                                        resolution = it,
                                        label = videoQualityLabel,
                                    )
                                }
                                val stateBadgeState = playbackState
                                if ((stateBadgeState != PlaybackState.IDLE && stateBadgeState != PlaybackState.PLAYING) || isLive) {
                                    Spacer(Modifier.width(6.dp))
                                    PlaybackStateBadge(
                                        playbackState = stateBadgeState,
                                        isLive = isLive,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        TransportIconButton(icon = Icons.Outlined.Settings, description = "Settings", onClick = { showSettingsMenu = true })
                        DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                            DropdownMenuItem(text = { Text("Playback Speed") }, onClick = { showSettingsMenu = false; showSpeedPanel = true })
                            DropdownMenuItem(text = { Text("Audio Track") }, onClick = { showSettingsMenu = false; showAudioPanel = true })
                            DropdownMenuItem(text = { Text("Subtitles") }, onClick = { showSettingsMenu = false; showSubtitlePanel = true })
                            DropdownMenuItem(text = { Text("Equalizer") }, onClick = { showSettingsMenu = false; showEqualizerPanel = true })
                            DropdownMenuItem(text = { Text("Aspect Ratio") }, onClick = { showSettingsMenu = false; showAspectRatioPanel = true })
                            DropdownMenuItem(text = { Text("Video Filters") }, onClick = { showSettingsMenu = false; showVideoFilterPanel = true })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            DropdownMenuItem(
                                text = { Text("🔗  Link to Tracker", fontWeight = FontWeight.Medium) },
                                onClick = { showSettingsMenu = false; onLinkToTracker() },
                            )
                        }
                    }
                    if (isMPVAvailable) {
                        TransportIconButton(icon = Icons.Outlined.CameraAlt, description = "Take screenshot", onClick = onTakeScreenshot)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, navigationIconContentColor = Color.White),
            )
        }

        // === Offline checkmark animation ===
        OfflineCheckmarkAnimation(
            isOfflinePlayback = isOfflinePlayback,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 80.dp),
        )

        // === Center play/pause ===
        AnimatedVisibility(visible = !isPlaying && isControlsVisible && !showLoadingOverlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = "Play", tint = Color.White, modifier = Modifier.fillMaxSize())
            }
        }

        // === Bottom controls ===
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerTransportControls(
                currentPositionSeconds = elapsedSeconds,
                totalDurationSeconds = totalSeconds,
                isPlaying = isPlaying,
                isLive = isLive,
                playbackState = playbackState,
                currentEpisodeIndex = currentEpisodeIndex,
                episodeCount = episodes.size,
                volume = volume,
                showVolume = isMPVAvailable,
                onTogglePlay = onTogglePlay,
                onSeek = { if (canSeek) seekFraction = it },
                onSeekEnd = { fraction ->
                    if (canSeek) {
                        val newSeconds = (fraction * totalSeconds).toLong()
                        elapsedSeconds = newSeconds
                        onSeekTo(newSeconds.toDouble())
                    }
                },
                onSeekRelative = { offset ->
                    if (canSeek) {
                        onSeekRelative(offset)
                        elapsedSeconds = (elapsedSeconds + offset.toLong()).coerceIn(0, totalSeconds)
                        seekFraction = (elapsedSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
                    }
                },
                onNavigateEpisode = onNavigateEpisode,
            )
        }

        // === Keyboard shortcut hint ===
        AnimatedVisibility(
            visible = isControlsVisible && !isPlaying,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (if (isMPVAvailable) 180 else 120).dp),
        ) {
            Text("SPACE play/pause · ←→ seek · ↑↓ volume", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
        }

        // === Settings panel overlays ===
        val isAnyPanelOpen = showSpeedPanel || showAudioPanel || showSubtitlePanel || showEqualizerPanel || showAspectRatioPanel || showVideoFilterPanel
        if (isAnyPanelOpen) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { showSpeedPanel = false; showAudioPanel = false; showSubtitlePanel = false; showEqualizerPanel = false; showAspectRatioPanel = false; showVideoFilterPanel = false })
        }

        AnimatedVisibility(visible = showSpeedPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerSpeedPanel(currentSpeed = playbackSpeed.toFloat(), onSpeedChange = { playerViewModel?.setSpeed(it.toDouble()) }, onDismiss = { showSpeedPanel = false })
        }
        AnimatedVisibility(visible = showAudioPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerAudioTrackPanel(tracks = audioTracks, currentTrackIndex = selectedAudioTrack, audioDelay = audioDelay, onTrackSelected = { playerViewModel?.selectAudioTrack(it) }, onDelayChange = { playerViewModel?.setAudioDelay(it) }, onDismiss = { showAudioPanel = false })
        }
        AnimatedVisibility(visible = showSubtitlePanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerSubtitleTrackPanel(tracks = subtitleTracks, currentTrackIndex = selectedSubtitleTrack, subtitleDelay = subtitleDelay, onTrackSelected = { playerViewModel?.selectSubtitleTrack(it) }, onDelayChange = { playerViewModel?.setSubtitleDelay(it) }, onDismiss = { showSubtitlePanel = false })
        }
        AnimatedVisibility(visible = showEqualizerPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerEqualizerPanel(brightness = brightness, contrast = contrast, saturation = saturation, gamma = gamma, onBrightnessChange = { playerViewModel?.setBrightness(it) }, onContrastChange = { playerViewModel?.setContrast(it) }, onSaturationChange = { playerViewModel?.setSaturation(it) }, onGammaChange = { playerViewModel?.setGamma(it) }, onReset = { playerViewModel?.resetEqualizer() }, onDismiss = { showEqualizerPanel = false })
        }
        AnimatedVisibility(visible = showAspectRatioPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerAspectRatioPanel(currentRatio = aspectRatio, onRatioChange = { playerViewModel?.setAspectRatio(it) }, onDismiss = { showAspectRatioPanel = false })
        }
        AnimatedVisibility(visible = showVideoFilterPanel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PlayerVideoFilterPanel(currentRotation = videoRotation, isHflip = isHflip, isVflip = isVflip, onRotationChange = { playerViewModel?.setVideoRotation(it) }, onToggleHflip = { playerViewModel?.toggleHflip() }, onToggleVflip = { playerViewModel?.toggleVflip() }, onDismiss = { showVideoFilterPanel = false })
        }
    }
}
