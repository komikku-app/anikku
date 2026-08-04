package app.anikku.macos.player

import app.anikku.macos.platform.logging.CrashReporter
import com.sun.jna.Pointer
import eu.kanade.tachiyomi.animesource.model.Track
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Central player state and control model.
 *
 * Manages playback lifecycle, position tracking, audio/subtitle tracks,
 * and episode navigation. Communicates with mpv via [MPVLib] and
 * processes events via [MPVEventLoop].
 */
class PlayerViewModel(
    private val torrentStreamer: TorrentStreamingCoordinator = TorrentStreamingCoordinator(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Current mpv handle (null when not initialized). */
    private var mpvHandle: Pointer? = null

    /** Expose mpv handle reactively for the video surface composable. */
    private val _handle = MutableStateFlow<Pointer?>(null)
    val handle: StateFlow<Pointer?> = _handle.asStateFlow()

    /** Software renderer for pulling decoded frames. */
    private var softwareRenderer: MPVSoftwareRenderer? = null

    /** Expose renderer reactively for the video surface composable. */
    private val _renderer = MutableStateFlow<MPVSoftwareRenderer?>(null)
    val renderer: StateFlow<MPVSoftwareRenderer?> = _renderer.asStateFlow()

    /** Event loop for processing mpv events. */
    private var eventLoop: MPVEventLoop? = null

    /** Tracks the periodic position-update coroutine for cleanup on shutdown. */
    private var positionUpdateJob: Job? = null

    /** Collectors attached to the current MPV event loop. */
    private var propertyChangesJob: Job? = null
    private var eventsJob: Job? = null

    /** Asynchronous torrent startup for the current magnet request. */
    private var magnetLoadJob: Job? = null

    /** Current video URL being played. */
    private var currentUrl: String? = null

    /** Playlist entry associated with the current load, used to reject stale END_FILE events. */
    @Volatile
    private var activePlaylistEntryId: Long? = null

    /** External subtitle tracks supplied by the source for the current load. */
    @Volatile
    private var activeExternalSubtitleTracks: List<Track> = emptyList()

    /** URLs already handed to mpv for the current load. */
    private val loadedExternalSubtitleUrls = mutableSetOf<String>()

    /** Serializes external subtitle bookkeeping across the event and load threads. */
    private val subtitleLock = Any()

    /** Delayed track discovery/selection job for the current load. */
    private var subtitleLoadJob: Job? = null

    /** Prevents asynchronous track events from overriding the user's selection. */
    @Volatile
    private var subtitleDefaultApplied = false

    /** Active native TorrServer or WebTorrent session. */
    private var torrentStream: TorrentStreamingResult.Success? = null

    /**
     * Token to guard against stale magnet-load coroutines firing after
     * the user switches to a different episode.
     */
    @Volatile
    private var magnetLoadToken: Any? = null

    // -------------------------------------------------------------------------
    // Observable state
    // -------------------------------------------------------------------------

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0.0)
    val currentPosition: StateFlow<Double> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _isPaused = MutableStateFlow(true)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0)
    val playbackSpeed: StateFlow<Double> = _playbackSpeed.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow(-1)
    val selectedAudioTrack: StateFlow<Int> = _selectedAudioTrack.asStateFlow()

    private val _selectedSubtitleTrack = MutableStateFlow(-1)
    val selectedSubtitleTrack: StateFlow<Int> = _selectedSubtitleTrack.asStateFlow()

    // -------------------------------------------------------------------------
    // Video equalizer state
    // -------------------------------------------------------------------------

    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(1f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _saturation = MutableStateFlow(1f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _gamma = MutableStateFlow(1f)
    val gamma: StateFlow<Float> = _gamma.asStateFlow()

    /** Whether mpv is available on this system. */
    val isMPVAvailable: Boolean get() = MPVLib.isAvailable

    // -------------------------------------------------------------------------
    // Initialization & Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialize the mpv core and event loop.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun initialize(): Boolean {
        if (mpvHandle != null) return true

        val mpvLoaded = MPVLib.initialize()
        if (!mpvLoaded) {
            _playbackState.value = PlaybackState.ERROR
            logger.warn { "MPV not available — playback is disabled" }
            CrashReporter.logEvent("MPV init failed", "libmpv could not be loaded")
            return false
        }

        try {
            val handle = MPVLib.create()
            if (handle == null) {
                logger.error { "🚀 MPV_CORE: mpv_create() returned null — cannot create mpv instance. " +
                    "This is likely caused by a locale issue (LC_NUMERIC not \"C\"). " +
                    "Check ~/Library/Logs/Anikku/ for details." }
                _handle.value = null
                mpvHandle = null
                _playbackState.value = PlaybackState.ERROR
                CrashReporter.logEvent("MPV handle null", "mpv_create returned null — locale issue")
                return false
            }
            mpvHandle = handle
            _handle.value = handle
            logger.info { "🚀 MPV_CORE: mpv handle created (${Pointer.nativeValue(handle)})" }

            // Configure mpv options BEFORE mpv_initialize
            val configOk = configureMPV(handle)

            if (!configOk) {
                logger.error { "🚀 MPV_CORE: mpv_set_option failed — mpv may have locale issues" }
                _handle.value = null
                mpvHandle = null
                MPVLib.destroy(handle)
                _playbackState.value = PlaybackState.ERROR
                return false
            }

            // Set network timeout so mpv doesn't hang forever on unreachable servers
            setNetworkTimeout(handle)

            val initResult = MPVLib.initialize(handle)
            if (initResult == null || initResult < 0) {
                logger.error { "🚀 MPV_CORE: mpv_initialize failed with code: $initResult" }
                _handle.value = null
                mpvHandle = null
                MPVLib.destroy(handle)
                _playbackState.value = PlaybackState.ERROR
                CrashReporter.logEvent("MPV init failed", "mpv_initialize returned $initResult")
                return false
            }
            logger.info { "🚀 MPV_CORE: mpv initialized successfully (vo=libmpv, hwdec=auto-copy-safe)" }

            // Create software render context
            val renderer = MPVSoftwareRenderer(handle)
            if (renderer.create()) {
                softwareRenderer = renderer
                _renderer.value = renderer
                logger.info { "🚀 MPV_RENDER: software render context created (MPV_RENDER_API_TYPE_SW)" }
            } else {
                logger.warn { "🚀 MPV_RENDER: software render context creation FAILED — video will not render" }
            }

            // Request VIDEO_RECONFIG events explicitly as a belt-and-suspenders measure.
            MPVLib.requestEvent(handle, MPVLib.MPV_EVENT_VIDEO_RECONFIG, true)

            // Request mpv log messages at verbose level via the correct API.
            // mpv_request_log_messages() is the proper way to receive MPV_EVENT_LOG_MESSAGE
            // events — mpv_request_event(MPV_EVENT_LOG_MESSAGE) alone does NOT work.
            // The msg-level option (set in configureMPV before mpv_initialize) controls
            // which modules and severity levels actually generate log events.
            MPVLib.requestLogMessages(handle, "v")

            // Start event loop
            val loop = MPVEventLoop(handle)
            eventLoop = loop
            loop.observeProperty("time-pos")
            loop.observeProperty("duration")
            loop.observeProperty("pause", MPVLib.FORMAT_FLAG)
            loop.observeProperty("paused-for-cache", MPVLib.FORMAT_FLAG)
            loop.observeProperty("cache-buffering-state", MPVLib.FORMAT_INT64)
            loop.observeProperty("volume")
            // Track metadata changes asynchronously after FILE_LOADED and sub-add.
            // Observing this is what makes external subtitles appear reliably in
            // the selector instead of relying on a one-time manual refresh.
            loop.observeProperty("track-list", MPVLib.FORMAT_NODE_ARRAY)
            loop.start()

            // Listen for property changes
            propertyChangesJob = scope.launch {
                loop.propertyChanges.collect { change ->
                    when (change.name) {
                        "time-pos" -> updatePosition()
                        "duration" -> updateDuration()
                        "pause", "paused-for-cache", "cache-buffering-state" -> updatePauseState()
                        "volume" -> updateVolume()
                        "track-list" -> {
                            refreshTracks()
                            val token = loadToken
                            if (!subtitleDefaultApplied && token != null) {
                                scheduleSubtitleSelection(handle, token)
                            }
                        }
                    }
                }
            }

            // Listen for events
            eventsJob = scope.launch {
                loop.events.collect { event ->
                    when (event.eventId) {
                        MPVLib.MPV_EVENT_START_FILE -> {
                            activePlaylistEntryId = event.playlistEntryId
                            logger.debug { "🎬 VIDEO_START: playlist entry=${event.playlistEntryId}" }
                        }
                        MPVLib.MPV_EVENT_FILE_LOADED -> {
                            // FILE_LOADED means mpv accepted the file, not necessarily
                            // that it is currently playing. Read the real pause/cache
                            // properties instead of forcing the UI to PLAYING.
                            updatePauseState()
                            updateDuration()
                            updateRendererVideoSize(handle)
                            refreshTracks()
                            val token = loadToken
                            if (token != null) {
                                addExternalSubtitleTracks(handle, activeExternalSubtitleTracks, token)
                                scheduleSubtitleSelection(handle, token)
                            }
                            // Resume from a saved position. Seeking immediately
                            // after FILE_LOADED can race the demuxer; give mpv a
                            // short settle delay, then apply the absolute seek.
                            val resumeAt = pendingStartPosition
                            if (resumeAt > 0.0) {
                                logger.info { "🎬 VIDEO_FILE: resuming at ${resumeAt}s" }
                                scope.launch {
                                    delay(RESUME_SEEK_DELAY_MS)
                                    if (loadToken !== token || mpvHandle !== handle) return@launch
                                    try {
                                        MPVLib.setPropertyDouble(handle, "time-pos", resumeAt)
                                        pendingStartPosition = 0.0
                                        logger.info { "🎬 VIDEO_FILE: resume seek to ${resumeAt}s applied" }
                                    } catch (e: Exception) {
                                        logger.warn(e) { "🎬 VIDEO_FILE: resume seek failed" }
                                        pendingStartPosition = 0.0
                                    }
                                }
                            }
                            // Keep the timeout alive until mpv actually starts;
                            // FILE_LOADED can arrive while the demuxer is still buffering.
                            logger.info { "🎬 VIDEO_FILE: file loaded into mpv — synchronized playback state" }
                        }
                        MPVLib.MPV_EVENT_END_FILE -> {
                            val state = _playbackState.value
                            if (shouldIgnoreEndFileDuringLoad(
                                    state = state,
                                    loadInProgress = loadInProgress,
                                    activePlaylistEntryId = activePlaylistEntryId,
                                    eventPlaylistEntryId = event.playlistEntryId,
                                )
                            ) {
                                logger.debug {
                                    "🎬 VIDEO_END: ignoring stale/unowned entry=${event.playlistEntryId}, " +
                                        "active=$activePlaylistEntryId"
                                }
                                return@collect
                            }
                            val position = MPVLib.getPropertyDouble(handle, "time-pos", -1.0)
                            val endReason = event.endFileReason
                            if (state == PlaybackState.LOADING && loadInProgress &&
                                endReason == MPVLib.END_FILE_REASON_ERROR
                            ) {
                                loadInProgress = false
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                                _playbackState.value = PlaybackState.ERROR
                                logger.warn { "🎬 VIDEO_END: mpv reported a fatal end-file error (error=${event.endFileError})" }
                            } else if (
                                state == PlaybackState.LOADING &&
                                loadInProgress &&
                                endReason in setOf(
                                    MPVLib.END_FILE_REASON_STOP,
                                    MPVLib.END_FILE_REASON_QUIT,
                                    MPVLib.END_FILE_REASON_REDIRECT,
                                )
                            ) {
                                // STOP/QUIT/REDIRECT are non-fatal lifecycle reasons.
                                // Do not leave the startup timeout armed: a late
                                // event from the replaced file must not turn the
                                // next episode into a false ERROR.
                                loadInProgress = false
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                                _playbackState.value = if (endReason == MPVLib.END_FILE_REASON_QUIT) {
                                    PlaybackState.IDLE
                                } else {
                                    PlaybackState.ENDED
                                }
                                logger.info { "🎬 VIDEO_END: non-fatal end-file reason=$endReason" }
                            } else if (state == PlaybackState.LOADING && loadInProgress) {
                                val started = position >= 0.0 &&
                                    !MPVLib.getPropertyFlag(handle, "pause", default = true)
                                if (started) {
                                    loadInProgress = false
                                    loadTimeoutJob?.cancel()
                                    loadTimeoutJob = null
                                } else {
                                    // mpv_event.error is not the end-file
                                    // reason payload. Leave failure classification
                                    // to the guarded startup timeout instead of
                                    // risking a stale END_FILE hiding a healthy
                                    // stream behind the retry screen.
                                    logger.warn { "🎬 VIDEO_END: load ended before playback started; waiting for guarded timeout (eventError=${event.error})" }
                                }
                            } else if (state == PlaybackState.SEEKING) {
                                // A seek can produce an END_FILE-like transition
                                // for some network demuxers; the playback-restart
                                // event/property update will restore PLAYING.
                                logger.debug { "🎬 VIDEO_END: ignoring end event during seek (position=$position)" }
                            } else {
                                loadInProgress = false
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                                _playbackState.value = when (endReason) {
                                    MPVLib.END_FILE_REASON_ERROR -> PlaybackState.ERROR
                                    MPVLib.END_FILE_REASON_QUIT -> PlaybackState.IDLE
                                    else -> PlaybackState.ENDED
                                }
                                logger.info { "🎬 VIDEO_FILE: playback ended (reason=$endReason, error=${event.endFileError})" }
                            }
                        }
                        MPVLib.MPV_EVENT_VIDEO_RECONFIG -> {
                            updateRendererVideoSize(handle)
                        }
                        MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                            // This event is emitted after loading, seeking, and
                            // buffering recovery. The pause/cache properties are
                            // authoritative for the visible control state.
                            updatePauseState()
                            updateRendererVideoSize(handle)
                            // updatePauseState cancels the startup timeout only
                            // after confirming mpv is actually running. Do not
                            // cancel it unconditionally: a stale restart event
                            // must not strand a new load in LOADING forever.
                            logger.info { "🎬 VIDEO_RESTART: playback restarted — synchronized playback state" }
                        }
                        MPVLib.MPV_EVENT_SEEK -> {
                            // A seek during playback must never make the original
                            // load timeout turn a healthy stream into ERROR. The
                            // timeout only applies while the initial load is still
                            // genuinely waiting for playback to start.
                            if (_playbackState.value != PlaybackState.LOADING) {
                                loadTimeoutJob?.cancel()
                                loadTimeoutJob = null
                            }
                            _playbackState.value = PlaybackState.SEEKING
                            logger.info { "🎬 VIDEO_SEEK: mpv seek event — position=${currentPosition.value}" }
                        }
                        MPVLib.MPV_EVENT_SHUTDOWN -> {
                            _playbackState.value = PlaybackState.IDLE
                        }
                    }
                }
            }

            // Periodic position updates — always update regardless of playback state
            positionUpdateJob = scope.launch {
                while (isActive) {
                    delay(250) // 250ms for smoother position tracking
                    mpvHandle?.let { handle ->
                        updatePosition()
                        updateDuration()
                        // Retry metadata discovery until the decoder exposes
                        // dimensions. This covers VIDEO_RECONFIG/FILE_LOADED
                        // ordering races that would otherwise leave the SW
                        // renderer permanently without a pixel buffer.
                        if (softwareRenderer?.videoWidth == 0 || softwareRenderer?.videoHeight == 0) {
                            updateRendererVideoSize(handle)
                        }
                    }
                }
            }

            _playbackState.value = PlaybackState.IDLE
            logger.info { "🚀 PLAYER_READY: mpv player fully initialized and awaiting video" }
            return true
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize MPV player" }
            CrashReporter.logError("PlayerInit", e.message ?: "", e)
            _playbackState.value = PlaybackState.ERROR
            return false
        }
    }

    /**
     * Configure mpv options before initialization.
     */
    private fun configureMPV(handle: Pointer): Boolean {
        var allOk = true
        val criticalOptions = listOf(
            "vo" to "libmpv",
            // Copy-mode hardware decoding returns decoded frames to system RAM,
            // which is compatible with MPV_RENDER_API_TYPE_SW. mpv automatically
            // falls back to software decoding when no safe copy decoder supports
            // the stream.
            "hwdec" to "auto-copy-safe",
            "cache" to "yes",
            "cache-secs" to "30",
            "demuxer-max-bytes" to "150M",
            "demuxer-max-back-bytes" to "50M",
        )
        val nonCriticalOptions = listOf(
            "audio-file-auto" to "no",
            "ytdl" to "yes",
            "ytdl-format" to "bestvideo+bestaudio/best",
            "ytdl-raw-options" to "format-sort=+size:codec:h264:avc1:res:br,fragment-retries=10",
            "sub-auto" to "fuzzy",
            "sub-file-auto" to "no",
            "osd-level" to "0",
            // Use keep-open=no to prevent mpv from staying on the last frame and
            // instead allow proper cleanup and state transitions.
            "keep-open" to "no",
            "screenshot-format" to "png",
            "screenshot-template" to "anikku-screenshot-%n",
            // Verbose logging for video/render modules to capture render API errors.
            // Targets vo (output), libmpv (render context), video (decoding) at verbose level.
            // The processLogMessage filter further restricts what we actually print.
            // MUST be set before mpv_initialize to take effect.
            "msg-level" to "vo=v:libmpv=v:video=v",
        )

        for ((name, value) in criticalOptions) {
            try {
                val result = MPVLib.setOptionString(handle, name, value)
                if (result == null || result < 0) {
                    logger.error { "Failed to set critical mpv option: $name=$value (error: $result)" }
                    allOk = false
                }
            } catch (e: Exception) {
                logger.error(e) { "Exception setting mpv option: $name=$value" }
                allOk = false
            }
        }

        for ((name, value) in nonCriticalOptions) {
            try {
                MPVLib.setOptionString(handle, name, value)
            } catch (e: Exception) {
                logger.debug(e) { "Non-critical mpv option failed: $name=$value" }
            }
        }

        if (!allOk) {
            CrashReporter.logEvent("MPV config failed", "Critical options could not be set")
        }

        return allOk
    }

    /**
     * Set a network timeout on mpv so it doesn't hang forever when a server
     * is unreachable, blocked by Cloudflare, or the URL is not a valid stream.
     */
    private fun setNetworkTimeout(handle: Pointer) {
        try {
            // stream-timeout: seconds to wait for network I/O (default = 0 = infinite)
            // Use 30s — long enough for slow CDNs but short enough to avoid hanging.
            val result = MPVLib.setOptionString(handle, "stream-timeout", "30")
            if (result != null && result < 0) {
                logger.debug { "stream-timeout option not supported by this mpv build (non-fatal)" }
            } else {
                logger.info { "🎬 MPV_CONFIG: stream-timeout set to 30s" }
            }

            val result3 = MPVLib.setOptionString(handle, "demuxer-readahead-secs", "20")
            if (result3 != null && result3 < 0) {
                logger.debug { "demuxer-readahead-secs option not supported (non-fatal)" }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Failed to set network timeout options (non-fatal)" }
        }
    }

    /**
     * Shut down the player, clean up mpv resources.
     */
    fun shutdown() {
        logger.info { "🎬 PLAYER_SHUTDOWN: shutting down mpv player..." }
        val nativeJobs = listOfNotNull(
            loadTimeoutJob,
            positionUpdateJob,
            propertyChangesJob,
            eventsJob,
            subtitleLoadJob,
        )
        nativeJobs.forEach { it.cancel() }
        // These jobs may be inside short JNA calls. Wait for them to leave the
        // current mpv handle before its native memory is destroyed.
        runBlocking { nativeJobs.forEach { it.join() } }
        loadTimeoutJob = null
        loadInProgress = false
        positionUpdateJob = null
        propertyChangesJob = null
        eventsJob = null
        magnetLoadJob?.cancel()
        magnetLoadJob = null
        magnetLoadToken = null
        loadToken = null
        activePlaylistEntryId = null
        eventLoop?.stop()
        subtitleLoadJob = null
        synchronized(subtitleLock) {
            activeExternalSubtitleTracks = emptyList()
            loadedExternalSubtitleUrls.clear()
        }
        softwareRenderer?.dispose()
        _renderer.value = null
        softwareRenderer = null
        mpvHandle?.let { handle ->
            try {
                MPVLib.command(handle, "quit")
            } catch (_: Exception) { }
            MPVLib.destroy(handle)
        }
        _handle.value = null
        mpvHandle = null
        eventLoop = null
        currentUrl = null

        // Clean up torrent server/process if running.
        torrentStream?.let { stream ->
            torrentStreamer.stop(stream)
            torrentStream = null
            logger.info { "🧲 MAGNET_SHUTDOWN: torrent stream process terminated" }
        }

        _playbackState.value = PlaybackState.IDLE
        _currentPosition.value = 0.0
        _duration.value = 0.0
        _isPaused.value = true
        logger.info { "🎬 PLAYER_SHUTDOWN: mpv player shut down complete" }
        CrashReporter.logEvent("Player shutdown")
    }

    // -------------------------------------------------------------------------
    // Playback Controls
    // -------------------------------------------------------------------------

    /** Tracks the load timeout coroutine so it can be cancelled on success/error. */
    private var loadTimeoutJob: Job? = null

    @Volatile
    private var loadToken: Any? = null

    /** True only while the current load is waiting for its first playback start. */
    @Volatile
    private var loadInProgress = false

    /**
     * Resume position (seconds) to seek to once the current file has loaded,
     * or 0.0 when the episode should start from the beginning. Cleared when
     * the seek is applied or a new load starts.
     */
    @Volatile
    private var pendingStartPosition = 0.0

    /**
     * Load and play a video URL with optional HTTP headers and source-provided
     * external subtitle tracks.
     *
     * Subtitles are intentionally added after FILE_LOADED. mpv cannot attach
     * an external subtitle to the replaced file before its demuxer has created
     * the new track list, and doing so would race episode navigation.
     *
     * @param startPosition Optional resume position in seconds; the player
     *                      seeks there once the file has loaded (0 = start).
     */
    fun loadEpisode(
        url: String,
        headers: Map<String, String>? = null,
        subtitleTracks: List<Track> = emptyList(),
        startPosition: Double = 0.0,
    ) = loadEpisodeInternal(url, headers, subtitleTracks, retainMagnetProcess = false, startPosition = startPosition)

    private fun loadEpisodeInternal(
        url: String,
        headers: Map<String, String>?,
        subtitleTracks: List<Track>,
        retainMagnetProcess: Boolean,
        startPosition: Double = 0.0,
    ) {
        if (!retainMagnetProcess) cancelMagnetPlayback()

        // ── Magnet link handling ────────────────────────────────────
        if (url.startsWith("magnet:")) {
            loadMagnetEpisode(url, headers, subtitleTracks)
            return
        }

        val handle = mpvHandle ?: run {
            logger.warn { "🎬 VIDEO_LOAD: Cannot load episode: mpv not initialized" }
            _playbackState.value = PlaybackState.ERROR
            CrashReporter.logEvent("Video load failed", "mpv not initialized, url=$url")
            return
        }

        currentUrl = url

        val token = Any()
        loadToken = token
        pendingStartPosition = startPosition.coerceAtLeast(0.0)
        activePlaylistEntryId = null
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        subtitleDefaultApplied = false
        subtitleLoadJob?.cancel()
        synchronized(subtitleLock) {
            activeExternalSubtitleTracks = subtitleTracks
                .filter { it.url.isNotBlank() }
                .distinctBy { it.url }
            loadedExternalSubtitleUrls.clear()
        }

        _playbackState.value = PlaybackState.LOADING
        _currentPosition.value = 0.0
        _duration.value = 0.0
        loadInProgress = true
        logger.info { "🎬 VIDEO_LOAD: loading episode into mpv: $url" }
        CrashReporter.logEvent("Video loading", "url=$url")

        try {
            val userAgent = headers?.entries?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: MPVLib.DEFAULT_USER_AGENT

            if (headers.isNullOrEmpty()) {
                val clearResult = MPVLib.setPropertyString(handle, "http-header-fields", "")
                if (clearResult != null && clearResult < 0) {
                    logger.debug { "Failed to clear http-header-fields (error $clearResult) — non-fatal" }
                }
            } else {
                val httpHeaderFields = headers.entries
                    .filter { !it.key.equals("User-Agent", ignoreCase = true) }
                    .joinToString(",") { (name, value) ->
                        val escapedValue = value.replace(",", "\\,")
                        "$name: $escapedValue"
                    }
                val headerResult = MPVLib.setPropertyString(handle, "http-header-fields", httpHeaderFields)
                if (headerResult != null && headerResult < 0) {
                    logger.warn { "Failed to set http-header-fields (error $headerResult) — headers may not be sent" }
                }
            }
            val uaResult = MPVLib.setPropertyString(handle, "user-agent", userAgent)
            if (uaResult != null && uaResult < 0) {
                logger.warn { "Failed to set user-agent property (error $uaResult) — User-Agent may not be sent" }
            }

            // Dynamically set ytdl-format based on URL type
            val ytdlFormat = if (url.contains(".mpd", ignoreCase = true) ||
                url.contains("dash", ignoreCase = true) ||
                url.contains("manifest", ignoreCase = true)
            ) {
                "bestvideo+bestaudio/best"
            } else {
                "best"
            }
            val formatResult = MPVLib.setPropertyString(handle, "ytdl-format", ytdlFormat)
            if (formatResult != null && formatResult < 0) {
                logger.debug { "Failed to set ytdl-format=$ytdlFormat (error $formatResult) — non-fatal" }
            } else if (ytdlFormat != "best") {
                logger.info { "🎬 VIDEO_LOAD: set ytdl-format=$ytdlFormat for DASH stream" }
            }

            logger.info { "🎬 VIDEO_LOAD: set http-header-fields and user-agent properties" }
            val loadResult = MPVLib.command(handle, "loadfile", url, "replace")
            if (loadResult != 0) {
                throw IllegalStateException("mpv loadfile failed with code $loadResult")
            }
            activePlaylistEntryId = MPVLib.getPropertyInt(handle, "playlist-entry-id", -1)
                .takeIf { it >= 0 }
                ?.toLong()
            logger.info { "🎬 VIDEO_LOAD: loadfile command sent successfully" }

            // Start a timeout: if the file doesn't load within 30 seconds,
            // transition to ERROR to prevent getting stuck at LOADING.
            loadTimeoutJob?.cancel()
            loadTimeoutJob = scope.launch {
                delay(LOAD_TIMEOUT_MS)
                if (loadToken !== token) return@launch

                val state = _playbackState.value
                // Only fail a load that is still waiting for playback to
                // start. In particular, never treat SEEKING/BUFFERING or an
                // already-audible stream as a failed URL: mpv can keep its
                // audio clock alive while video data catches up.
                val position = MPVLib.getPropertyDouble(handle, "time-pos", -1.0)
                val paused = MPVLib.getPropertyFlag(handle, "pause", default = true)
                val playbackHasStarted = position >= 0.0 && !paused
                if (state == PlaybackState.LOADING && loadInProgress && !playbackHasStarted) {
                    _playbackState.value = PlaybackState.ERROR
                    loadInProgress = false
                    logger.warn { "🎬 VIDEO_TIMEOUT: file did not start within ${LOAD_TIMEOUT_MS / 1000}s" +
                        " — server may be unreachable, blocked, or URL not a playable stream" }
                    CrashReporter.logEvent("Video timeout", "url=$url, state=$state, position=$position, paused=$paused" )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load episode: $url" }
            CrashReporter.logError("VideoLoad", "Failed to load $url", e)
            loadInProgress = false
            loadTimeoutJob?.cancel()
            loadTimeoutJob = null
            _playbackState.value = PlaybackState.ERROR
        }
    }

    // ── Magnet Link Handling ──────────────────────────────────────

    private fun cancelMagnetPlayback() {
        magnetLoadToken = null
        magnetLoadJob?.cancel()
        magnetLoadJob = null
        torrentStream?.let(torrentStreamer::stop)
        torrentStream = null
    }

    private fun loadMagnetEpisode(
        magnetUrl: String,
        headers: Map<String, String>? = null,
        subtitleTracks: List<Track> = emptyList(),
    ) {
        logger.info { "🧲 MAGNET: Starting torrent stream: ${magnetUrl.take(80)}..." }
        _playbackState.value = PlaybackState.LOADING

        val token = Any()
        magnetLoadToken = token

        torrentStream?.let(torrentStreamer::stop)
        torrentStream = null

        magnetLoadJob?.cancel()
        magnetLoadJob = scope.launch {
            try {
                val result = torrentStreamer.start(magnetUrl)
                if (magnetLoadToken !== token) {
                    if (result is TorrentStreamingResult.Success) torrentStreamer.stop(result)
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    when (result) {
                        is TorrentStreamingResult.Success -> {
                            torrentStream = result
                            logger.info { "🧲 MAGNET: ${result.backend} server ready at ${result.httpUrl}" }
                            if (magnetLoadToken !== token) {
                                torrentStreamer.stop(result)
                                return@withContext
                            }
                            loadEpisodeInternal(
                                result.httpUrl,
                                headers,
                                subtitleTracks,
                                retainMagnetProcess = true,
                            )
                        }
                        is TorrentStreamingResult.Failure -> {
                            if (magnetLoadToken !== token) return@withContext
                            _playbackState.value = PlaybackState.ERROR
                            logger.warn { "🧲 MAGNET: Failed to start torrent stream: ${result.message}" }
                            CrashReporter.logEvent("Magnet stream failed", result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                if (magnetLoadToken !== token) return@launch
                withContext(Dispatchers.Main) {
                    _playbackState.value = PlaybackState.ERROR
                    logger.error(e) { "🧲 MAGNET: Unexpected error streaming torrent" }
                    CrashReporter.logError("MagnetError", e.message ?: "", e)
                }
            }
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 30_000L // 30 seconds for slower CDNs
        private const val MAX_TRACK_LIST_ENTRIES = 256
        private const val SUBTITLE_DISCOVERY_ATTEMPTS = 15
        private const val SUBTITLE_DISCOVERY_DELAY_MS = 200L
        private const val RESUME_SEEK_DELAY_MS = 800L // let the demuxer settle before resume-seek
        private const val DEFAULT_SUBTITLE_LANGUAGE = "eng"

        /**
         * During replacement, mpv can deliver an END_FILE for the previous
         * playlist entry after the next episode is already loading. Only an
         * END_FILE owned by the active entry may complete/fail that new load.
         */
        internal fun shouldIgnoreEndFileDuringLoad(
            state: PlaybackState,
            loadInProgress: Boolean,
            activePlaylistEntryId: Long?,
            eventPlaylistEntryId: Long?,
        ): Boolean = state == PlaybackState.LOADING && loadInProgress &&
            (activePlaylistEntryId == null || eventPlaylistEntryId == null ||
                activePlaylistEntryId != eventPlaylistEntryId)

        /** Pure selection rule shared by tests and the native-track flow. */
        internal fun chooseDefaultSubtitleTrack(
            tracks: List<TrackInfo>,
            preferredLanguage: String = DEFAULT_SUBTITLE_LANGUAGE,
        ): Int {
            val preferred = normalizeSubtitleLanguage(preferredLanguage)
            return tracks.firstOrNull { normalizeSubtitleLanguage(it.language) == preferred }?.id
                ?: tracks.firstOrNull()?.id
                ?: -1
        }

        private fun normalizeSubtitleLanguage(language: String): String {
            val value = language.trim().lowercase().substringBefore('-').substringBefore('_')
            return when (value) {
                "en", "eng", "english" -> "eng"
                "ja", "jpn", "japanese" -> "jpn"
                "zh", "zho", "chi", "chinese" -> "zho"
                "ko", "kor", "korean" -> "kor"
                else -> value
            }
        }
    }

    /**
     * Toggle between play and pause.
     */
    fun togglePause() {
        val handle = mpvHandle ?: return
        try {
            val paused = MPVLib.getPropertyFlag(handle, "pause", default = true)
            // The observed mpv `pause` property is the single source of truth.
            // Do not optimistically mutate _isPaused here: the command can be
            // rejected or delayed while buffering, which used to desynchronize
            // the button from the actual player.
            MPVLib.setPropertyString(handle, "pause", if (paused) "no" else "yes")
        } catch (e: Exception) {
            logger.warn(e) { "Failed to toggle pause" }
        }
    }

    /**
     * Seek to an absolute position in seconds.
     */
    fun seekTo(seconds: Double) {
        val handle = mpvHandle ?: return
        try {
            MPVLib.setPropertyDouble(handle, "time-pos", seconds.coerceAtLeast(0.0))
        } catch (e: Exception) {
            logger.warn(e) { "Failed to seek to $seconds" }
        }
    }

    /**
     * Seek relative to the current position.
     */
    fun seekRelative(offset: Double) {
        seekTo(currentPosition.value + offset)
    }

    /**
     * Set volume (0–200).
     */
    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 200)
        _volume.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyInt(handle, "volume", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set volume on mpv" }
            }
        }
    }

    /**
     * Set playback speed (0.25–4.0).
     */
    fun setSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        _playbackSpeed.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "speed", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set speed on mpv" }
            }
        }
    }

    /**
     * Toggle fullscreen mode.
     */
    fun toggleFullscreen() {
        val handle = mpvHandle ?: return
        try {
            val fs = MPVLib.getPropertyFlag(handle, "fullscreen", default = false)
            MPVLib.setPropertyString(handle, "fullscreen", if (fs) "no" else "yes")
            _isFullscreen.value = !fs
        } catch (e: Exception) {
            _isFullscreen.value = !_isFullscreen.value
        }
    }

    /**
     * Take a screenshot of the current video frame (subtitles included) and
     * save it to ~/Pictures/Anikku. Returns the absolute path of the saved
     * file, or null on failure.
     */
    fun takeScreenshot(): String? {
        val handle = mpvHandle ?: return null
        return try {
            val dir = java.io.File(System.getProperty("user.home"), "Pictures/Anikku")
            runCatching { if (!dir.exists()) dir.mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val file = java.io.File(dir, "Anikku-$stamp.png")
            // "subtitles" mode captures video + rendered subtitles. The file is
            // written synchronously, so reporting the real path is safe.
            val result = MPVLib.command(handle, "screenshot-to-file", file.absolutePath, "subtitles")
            if (result == 0 && file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            logger.warn(e) { "Failed to take screenshot" }
            null
        }
    }

    // -------------------------------------------------------------------------
    // Track Management
    // -------------------------------------------------------------------------

    /**
     * Reload track lists from mpv.
     *
     * Use the read-only indexed track-list rather than sub/audio indexes. The
     * latter are not guaranteed to equal mpv's actual track IDs, especially
     * after external tracks are added.
     */
    fun refreshTracks() {
        val handle = mpvHandle ?: return

        try {
            val nativeTracks = buildList {
                for (index in 0 until MAX_TRACK_LIST_ENTRIES) {
                    val type = MPVLib.getPropertyString(handle, "track-list/$index/type") ?: break
                    val id = MPVLib.getPropertyInt(handle, "track-list/$index/id", -1)
                    if (id < 0) continue
                    add(
                        TrackInfo(
                            id = id,
                            title = MPVLib.getPropertyString(handle, "track-list/$index/title")
                                ?: "Track $id",
                            language = MPVLib.getPropertyString(handle, "track-list/$index/lang")
                                ?: "unknown",
                            codec = MPVLib.getPropertyString(handle, "track-list/$index/codec") ?: "",
                            external = MPVLib.getPropertyFlag(handle, "track-list/$index/external", false),
                            type = type,
                        ),
                    )
                }
            }

            _audioTracks.value = nativeTracks.filter { it.type == "audio" }
            _subtitleTracks.value = nativeTracks.filter { it.type == "sub" }
            _selectedAudioTrack.value = MPVLib.getPropertyInt(handle, "aid", -1)
            _selectedSubtitleTrack.value = MPVLib.getPropertyInt(handle, "sid", -1)
        } catch (e: Exception) {
            logger.debug(e) { "Failed to parse track list" }
        }
    }

    /** Add source-provided subtitle URLs after the current media is loaded. */
    private fun addExternalSubtitleTracks(handle: Pointer, tracks: List<Track>, token: Any) {
        if (loadToken !== token || tracks.isEmpty()) return

        for (track in tracks) {
            val shouldAdd = synchronized(subtitleLock) {
                if (loadedExternalSubtitleUrls.contains(track.url)) {
                    false
                } else {
                    loadedExternalSubtitleUrls += track.url
                    true
                }
            }
            if (!shouldAdd) continue

            val language = track.lang.trim().ifBlank { "und" }
            val result = MPVLib.command(
                handle,
                "sub-add",
                track.url,
                "auto",
                "External ($language)",
                language,
            )
            if (result < 0) {
                synchronized(subtitleLock) { loadedExternalSubtitleUrls.remove(track.url) }
                logger.warn { "🎬 SUBTITLE: failed to add external track (${result}): ${track.url.take(120)}" }
            } else {
                logger.info { "🎬 SUBTITLE: queued external ${language} track" }
            }
        }
    }

    /**
     * Wait for embedded/external tracks to appear, then enable the preferred
     * language or the first available subtitle. This mirrors Android's
     * onFinishLoadingTracks behavior while accounting for asynchronous sub-add.
     */
    private fun scheduleSubtitleSelection(handle: Pointer, token: Any) {
        subtitleLoadJob?.cancel()
        subtitleLoadJob = scope.launch {
            repeat(SUBTITLE_DISCOVERY_ATTEMPTS) { attempt ->
                delay(SUBTITLE_DISCOVERY_DELAY_MS)
                if (loadToken !== token || mpvHandle !== handle) return@launch

                refreshTracks()
                val expectedExternalCount = synchronized(subtitleLock) {
                    activeExternalSubtitleTracks.size
                }
                val externalReady = expectedExternalCount == 0 ||
                    _subtitleTracks.value.count { it.external } >= expectedExternalCount
                if (externalReady || attempt == SUBTITLE_DISCOVERY_ATTEMPTS - 1) {
                    selectDefaultSubtitleTrack()
                    return@launch
                }
            }
        }
    }

    /** Select English when available, otherwise the first subtitle track. */
    private fun selectDefaultSubtitleTrack() {
        if (subtitleDefaultApplied) return
        val tracks = _subtitleTracks.value
        if (tracks.isEmpty()) return

        val selected = chooseDefaultSubtitleTrack(tracks)
        if (selected >= 0) {
            setSubtitleTrack(selected)
            subtitleDefaultApplied = true
            logger.info { "🎬 SUBTITLE: enabled default track id=$selected" }
        }
    }

    /**
     * Select an audio track by its mpv ID.
     */
    fun selectAudioTrack(trackId: Int) {
        val handle = mpvHandle ?: return
        try {
            MPVLib.setPropertyInt(handle, "aid", trackId)
            _selectedAudioTrack.value = trackId
        } catch (e: Exception) {
            logger.warn(e) { "Failed to select audio track $trackId" }
        }
    }

    /**
     * Select a subtitle track by its mpv ID. A user selection is sticky and
     * cannot be overwritten by the asynchronous default-track discovery.
     */
    fun selectSubtitleTrack(trackId: Int) {
        if (setSubtitleTrack(trackId)) {
            subtitleDefaultApplied = true
        }
    }

    /**
     * Attach a downloaded subtitle file (from SubtitleFetcher) as an external
     * track and auto-select the best available language. Returns true when the
     * track was added to mpv.
     *
     * Mirrors [addExternalSubtitleTracks] but for a local file path produced by
     * the subtitle service, so it works mid-playback (user picks a candidate
     * from the subtitle dropdown) and at load time (auto-fetch).
     */
    fun addDownloadedSubtitleFile(file: java.io.File, title: String, language: String): Boolean {
        val handle = mpvHandle ?: return false
        if (!file.isFile) {
            logger.warn { "🎬 SUBTITLE: downloaded subtitle file missing: ${file.path}" }
            return false
        }
        val shouldAdd = synchronized(subtitleLock) {
            if (loadedExternalSubtitleUrls.contains(file.path)) {
                false
            } else {
                loadedExternalSubtitleUrls += file.path
                true
            }
        }
        if (!shouldAdd) return false

        val lang = language.trim().ifBlank { "und" }
        val result = MPVLib.command(
            handle,
            "sub-add",
            file.absolutePath,
            "auto",
            title,
            lang,
        )
        if (result < 0) {
            synchronized(subtitleLock) { loadedExternalSubtitleUrls.remove(file.path) }
            logger.warn { "🎬 SUBTITLE: failed to add downloaded subtitle ($result): $title" }
            return false
        }
        logger.info { "🎬 SUBTITLE: added downloaded subtitle '$title' ($lang)" }
        refreshTracks()
        // Do not stomp an explicit user selection; only auto-select when the
        // user hasn't picked a track yet for this load.
        if (!subtitleDefaultApplied) {
            selectDefaultSubtitleTrack()
        }
        return true
    }

    // ---- Learned subtitle offset (smart-offset) ---------------------------

    /**
     * Per-anime subtitle delay offsets, learned from the user's manual delay
     * adjustments. Persisted to the preference store so offsets survive app
     * restarts. Keyed by [animeId].
     */
    private fun learnedOffsetKey(animeId: Long): String = "player_subtitle_offset_anime_$animeId"

    /** Remember a manual subtitle delay for [animeId] and apply it to mpv. */
    fun setSubtitleDelayForAnime(animeId: Long, delay: Double) {
        val clamped = delay.coerceIn(-10.0, 10.0)
        if (clamped != 0.0) {
            runCatching {
                preferenceStore()?.let { store ->
                    store.getFloat(learnedOffsetKey(animeId), 0.0f).set(clamped.toFloat())
                }
            }
        }
        setSubtitleDelay(clamped)
    }

    /** Apply a previously learned offset for [animeId] when a new episode loads. */
    fun applyLearnedSubtitleOffset(animeId: Long) {
        val saved = runCatching {
            preferenceStore()?.getFloat(learnedOffsetKey(animeId), 0.0f)?.get() ?: 0.0f
        }.getOrDefault(0.0f)
        if (saved != 0.0f) {
            logger.info { "🎬 SUBTITLE: applying learned offset ${saved}s for anime $animeId" }
            setSubtitleDelay(saved.toDouble())
        }
    }

    // ---- Subtitle appearance (font size / position) ------------------------

    /** Set the subtitle font size (mpv sub-font-size, 20-160). */
    fun setSubtitleFontSize(size: Float) {
        val handle = mpvHandle ?: return
        runCatching {
            MPVLib.setPropertyString(handle, "sub-font-size", size.toInt().coerceIn(20, 160).toString())
        }.onFailure { e -> logger.warn(e) { "Failed to set sub-font-size" } }
    }

    /** Set the subtitle vertical position (mpv sub-pos, 0-150). */
    fun setSubtitlePosition(position: Int) {
        val handle = mpvHandle ?: return
        runCatching {
            MPVLib.setPropertyString(handle, "sub-pos", position.coerceIn(0, 150).toString())
        }.onFailure { e -> logger.warn(e) { "Failed to set sub-pos" } }
    }

    /** Apply the persisted subtitle appearance when an episode loads. */
    fun applySubtitleAppearance(fontSize: Float, position: Int) {
        setSubtitleFontSize(fontSize)
        setSubtitlePosition(position)
    }

    private fun preferenceStore(): app.anikku.macos.platform.preference.MacOSPreferenceStore? =
        runCatching {
            org.koin.core.context.GlobalContext.get()
                .get<app.anikku.macos.platform.preference.MacOSPreferenceStore>()
        }.getOrNull()

    /** Apply a track selection internally without marking it as a user choice. */
    private fun setSubtitleTrack(trackId: Int): Boolean {
        val handle = mpvHandle ?: return false
        try {
            // mpv represents the disabled state as the string value "no";
            // passing -1 through FORMAT_INT64 is not portable across libmpv
            // versions and can leave the old subtitle visible.
            val result = if (trackId < 0) {
                MPVLib.setPropertyString(handle, "sid", "no")
            } else {
                MPVLib.setPropertyInt(handle, "sid", trackId)
            }
            if (result != null && result >= 0) {
                _selectedSubtitleTrack.value = trackId
                return true
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to select subtitle track $trackId" }
        }
        return false
    }

    /**
     * Disable subtitles.
     */
    fun disableSubtitles() {
        selectSubtitleTrack(-1)
    }

    private val _subtitleDelay = MutableStateFlow(0.0)
    val subtitleDelay: StateFlow<Double> = _subtitleDelay.asStateFlow()

    private val _audioDelay = MutableStateFlow(0.0)
    val audioDelay: StateFlow<Double> = _audioDelay.asStateFlow()

    // -------------------------------------------------------------------------
    // Aspect ratio & video filters
    // -------------------------------------------------------------------------

    private val _aspectRatio = MutableStateFlow("-1")
    val aspectRatio: StateFlow<String> = _aspectRatio.asStateFlow()

    private val _videoRotation = MutableStateFlow(0)
    val videoRotation: StateFlow<Int> = _videoRotation.asStateFlow()

    private val _isHflip = MutableStateFlow(false)
    val isHflip: StateFlow<Boolean> = _isHflip.asStateFlow()

    private val _isVflip = MutableStateFlow(false)
    val isVflip: StateFlow<Boolean> = _isVflip.asStateFlow()

    // -------------------------------------------------------------------------
    // Video equalizer controls
    // -------------------------------------------------------------------------

    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        _brightness.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "brightness", (clamped * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set brightness" }
            }
        }
    }

    fun setContrast(value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        _contrast.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "contrast", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set contrast" }
            }
        }
    }

    fun setSaturation(value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        _saturation.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "saturation", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set saturation" }
            }
        }
    }

    fun setGamma(value: Float) {
        val clamped = value.coerceIn(0.1f, 2f)
        _gamma.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "gamma", ((clamped - 1f) * 100).toDouble())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set gamma" }
            }
        }
    }

    fun resetEqualizer() {
        setBrightness(0f)
        setContrast(1f)
        setSaturation(1f)
        setGamma(1f)
    }

    fun setSubtitleDelay(delay: Double) {
        val clamped = delay.coerceIn(-10.0, 10.0)
        _subtitleDelay.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "sub-delay", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set subtitle delay" }
            }
        }
    }

    fun setAudioDelay(delay: Double) {
        val clamped = delay.coerceIn(-10.0, 10.0)
        _audioDelay.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyDouble(handle, "audio-delay", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set audio delay" }
            }
        }
    }

    fun setAspectRatio(ratio: String) {
        _aspectRatio.value = ratio
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyString(handle, "video-aspect", ratio)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set aspect ratio" }
            }
        }
    }

    fun setVideoRotation(degrees: Int) {
        val clamped = when (degrees) {
            90 -> 90; 180 -> 180; 270 -> 270; else -> 0
        }
        _videoRotation.value = clamped
        val handle = mpvHandle
        if (handle != null) {
            try {
                MPVLib.setPropertyInt(handle, "video-rotate", clamped)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to set video rotation" }
            }
        }
    }

    fun toggleHflip() {
        _isHflip.value = !_isHflip.value
        applyVideoFilters()
    }

    fun toggleVflip() {
        _isVflip.value = !_isVflip.value
        applyVideoFilters()
    }

    private fun applyVideoFilters() {
        val handle = mpvHandle
        if (handle != null) {
            try {
                val filters = buildList {
                    if (_isHflip.value) add("hflip")
                    if (_isVflip.value) add("vflip")
                }
                MPVLib.setPropertyString(handle, "vf", filters.joinToString(","))
            } catch (e: Exception) {
                logger.warn(e) { "Failed to apply video filters" }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal state updates
    // -------------------------------------------------------------------------

    /**
     * Allocate the software-render target from the best dimensions currently
     * exposed by mpv. VIDEO_RECONFIG can race FILE_LOADED on libmpv, so this is
     * intentionally safe to call from both events; it is a no-op until mpv has
     * usable video dimensions.
     */
    private fun updateRendererVideoSize(handle: Pointer) {
        val width = listOf("dwidth", "video-params/w", "width")
            .asSequence()
            .map { MPVLib.getPropertyInt(handle, it, 0) }
            .firstOrNull { it > 0 } ?: 0
        val height = listOf("dheight", "video-params/h", "height")
            .asSequence()
            .map { MPVLib.getPropertyInt(handle, it, 0) }
            .firstOrNull { it > 0 } ?: 0

        if (width > 0 && height > 0) {
            softwareRenderer?.updateVideoSize(width, height)
            logger.info { "🎬 VIDEO_SIZE: software render target ${width}x${height}" }
        } else {
            logger.debug { "🎬 VIDEO_SIZE: dimensions not available yet (d=${width}x${height})" }
        }
    }

    private fun updatePosition() {
        val handle = mpvHandle ?: return
        try {
            val pos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
            _currentPosition.value = pos.coerceAtLeast(0.0)
        } catch (_: Exception) { }
    }

    private fun updateDuration() {
        val handle = mpvHandle ?: return
        try {
            val dur = MPVLib.getPropertyDouble(handle, "duration", 0.0)
            if (dur > 0) {
                _duration.value = dur
            }
        } catch (_: Exception) { }
    }

    private fun updatePauseState() {
        val handle = mpvHandle ?: return
        try {
            val paused = MPVLib.getPropertyFlag(handle, "pause", default = true)
            val pausedForCache = MPVLib.getPropertyFlag(handle, "paused-for-cache", default = false)
            _isPaused.value = paused

            // Once mpv reports that it is actually running, the initial-load
            // timeout is no longer allowed to change the UI to ERROR later.
            if (!paused && !pausedForCache) {
                loadInProgress = false
                loadTimeoutJob?.cancel()
                loadTimeoutJob = null
            }

            _playbackState.value = when {
                pausedForCache -> PlaybackState.BUFFERING
                // During the initial load, mpv may briefly expose pause=yes
                // while it prepares the demuxer. Keep the loading state so the
                // guarded timeout can still report a genuinely stuck load and
                // the UI does not claim the user paused the video.
                loadInProgress -> PlaybackState.LOADING
                paused -> PlaybackState.PAUSED
                else -> PlaybackState.PLAYING
            }
        } catch (_: Exception) { }
    }

    private fun updateVolume() {
        val handle = mpvHandle ?: return
        try {
            _volume.value = MPVLib.getPropertyInt(handle, "volume", 100)
        } catch (_: Exception) { }
    }
}

/**
 * Information about an audio or subtitle track.
 */
data class TrackInfo(
    val id: Int,
    val title: String,
    val language: String,
    val codec: String = "",
    val external: Boolean = false,
    val type: String = "sub",
)
