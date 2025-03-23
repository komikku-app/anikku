package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import eu.kanade.tachiyomi.ui.player.cast.CastMediaBuilder
import eu.kanade.tachiyomi.ui.player.cast.CastSessionListener
import eu.kanade.tachiyomi.ui.player.cast.components.BorderStyle
import eu.kanade.tachiyomi.ui.player.cast.components.SubtitleSettings
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import eu.kanade.tachiyomi.ui.player.settings.CastSubtitlePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.tail.TLMR
import java.util.LinkedList
import kotlin.coroutines.resume

class CastManager(
    private val activity: ComponentActivity,
    private val preferenceStore: PreferenceStore,
) {
    enum class CastState {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
    }

    data class CastDevice(
        val id: String,
        val name: String,
        val isConnected: Boolean = false,
        val isSelected: Boolean = false,
    )

    data class CastMedia(
        val title: String,
        val subtitle: String,
        val thumbnail: String?,
    )

    private val context = activity.applicationContext
    private val viewModel by lazy {
        when (activity) {
            is PlayerActivity -> {
                val factory = PlayerViewModelProviderFactory(activity)
                activity.viewModels<PlayerViewModel> { factory }.value
            }
            else -> null
        }
    }
    private val player by lazy { (activity as? PlayerActivity)?.player }
    private val playerPreferences: PlayerPreferences by lazy {
        viewModel?.playerPreferences ?: PlayerPreferences(preferenceStore)
    }
    private val autoplayEnabled = playerPreferences.autoplayEnabled().get()
    private val subtitlePreferences = CastSubtitlePreferences(preferenceStore)
    private val mediaBuilder by lazy {
        viewModel?.let { CastMediaBuilder(it, activity as PlayerActivity) }
    }
    private val mediaRouter by lazy {
        androidx.mediarouter.media.MediaRouter.getInstance(context)
    }

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private var castContext: CastContext? = null
    var castSession: CastSession? = null
    private var sessionListener: CastSessionListener? = null
    private var castProgressJob: Job? = null
    private var discoveryRetryJob: Job? = null
    private var mediaRouterCallback: androidx.mediarouter.media.MediaRouter.Callback? = null

    private val isCastApiAvailable: Boolean
        get() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private val mediaQueue = LinkedList<MediaQueueItem>()
    private var isLoadingMedia = false

    private val _queueItems = MutableStateFlow<List<MediaQueueItem>>(emptyList())
    val queueItems: StateFlow<List<MediaQueueItem>> = _queueItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastDevice>> = _availableDevices.asStateFlow()

    private val _currentMedia = MutableStateFlow<CastMedia?>(null)
    val currentMedia: StateFlow<CastMedia?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    init {
        initializeCast()
    }

    private fun initializeCast() {
        if (!isCastApiAvailable) return
        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
            sessionListener = CastSessionListener(this)
            registerSessionListener()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    // Session Management
    fun registerSessionListener() {
        sessionListener?.let { listener ->
            castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        }
    }

    fun unregisterSessionListener() {
        sessionListener?.let { listener ->
            castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    fun cleanup() {
        discoveryRetryJob?.cancel()
        mediaRouterCallback?.let { mediaRouter.removeCallback(it) }
        mediaRouterCallback = null
        unregisterSessionListener()
        castSession = null
    }

    // Session Callbacks
    fun onSessionConnected(session: CastSession) {
        castSession = session
        updateCastState(CastState.CONNECTED)
        startTrackingCastProgress()
        updateCurrentMedia()
        updateQueueItems()

        session.remoteMediaClient?.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                updateCurrentMedia()
                updateQueueItems()
            }

            override fun onMetadataUpdated() {
                updateCurrentMedia()
                updateQueueItems()
            }

            override fun onQueueStatusUpdated() {
                updateQueueItems()
            }

            override fun onPreloadStatusUpdated() {
                updateQueueItems()
            }
        })
    }

    fun onSessionEnded() {
        castProgressJob?.cancel()
        val lastPosition = getCurrentCastPosition()
        if (lastPosition > 0) viewModel?.updateCastProgress(lastPosition.toFloat() / 1000)
        castSession = null
        updateCastState(CastState.DISCONNECTED)
        viewModel?.resumeFromCast()
    }

    fun updateCastState(state: CastState) {
        _castState.value = state
        if (state == CastState.CONNECTED) player?.paused = true
        activity.invalidateOptionsMenu()
    }

    // Quality Selection
    fun handleQualitySelection() {
        viewModel?.hosterList?.filter { it.isNotEmpty() }
            ?.onEach { videos ->
                val hasQueueItems = (castSession?.remoteMediaClient?.mediaQueue?.itemCount ?: 0) > 0
                val hasMultipleQualities = videos.size > 1

                if (!hasQueueItems && !hasMultipleQualities) {
                    loadRemoteMedia()
                }
            }
            ?.launchIn(viewModel!!.viewModelScope)
    }

    fun removeQueueItem(itemId: Int) {
        castSession?.remoteMediaClient?.queueRemoveItem(itemId, null)?.setResultCallback { result ->
            if (result.status.isSuccess) {
                updateQueueItems()
            }
        }
    }

    fun moveQueueItem(itemId: Int, newIndex: Int) {
        castSession?.remoteMediaClient?.queueMoveItemToNewIndex(itemId, newIndex, null)?.setResultCallback { result ->
            if (result.status.isSuccess) {
                updateQueueItems()
            }
        }
    }

    // Media Loading & Progress Tracking
    @SuppressLint("SuspiciousIndentation")
    fun loadRemoteMedia() {
        if (!isCastApiAvailable || isLoadingMedia) return
        val remoteMediaClient = castSession?.remoteMediaClient ?: return
        if (mediaBuilder == null || viewModel == null) return

        activity.lifecycleScope.launch {
            try {
                isLoadingMedia = true
                _isLoading.value = true
                val selectedHosterIndex = viewModel!!.selectedHosterVideoIndex.value.first
                val selectedVideoIndex = viewModel!!.selectedHosterVideoIndex.value.second

                val hosterState = viewModel!!.hosterState.value.getOrNull(selectedHosterIndex) as? HosterState.Ready
                val video = hosterState?.videoList?.getOrNull(selectedVideoIndex) ?: return@launch

                val mediaInfo = mediaBuilder!!.buildMediaInfo(video) // Now the `Video` object passes directly
                val currentLocalPosition = (player?.timePos ?: 0).toLong()

                // Update queue before and after loading new medium
                updateQueueItems()
                if (remoteMediaClient.mediaQueue.itemCount > 0) {
                    // Optimization: Pre-construct queue item
                    val queueItem = MediaQueueItem.Builder(mediaInfo)
                        .setAutoplay(autoplayEnabled)
                        .build()

                    // Add to local queue
                    mediaQueue.add(queueItem)
                    remoteMediaClient.queueAppendItem(queueItem, null)
                    updateQueueItems()
                    showAddedToQueueToast()
                } else {
                    // First load: optimize metadata
                    remoteMediaClient.load(
                        MediaLoadRequestData.Builder()
                            .setMediaInfo(mediaInfo)
                            .setAutoplay(autoplayEnabled)
                            .setCurrentTime(currentLocalPosition * 1000)
                            .build(),
                    )
                    updateQueueItems()
                }
                _castState.value = CastState.CONNECTED
            } catch (e: Exception) {
                // _castState.value = CastState.DISCONNECTED
                logcat(LogPriority.ERROR) { "Error loading media: ${e.message}" }
                showLoadErrorToast()
            } finally {
                isLoadingMedia = false
                _isLoading.value = false
            }
        }
    }

    private fun showAddedToQueueToast() {
        activity.runOnUiThread {
            Toast.makeText(
                context,
                context.stringResource(TLMR.strings.cast_video_added_to_queue),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showLoadErrorToast() {
        activity.runOnUiThread {
            Toast.makeText(
                context,
                context.stringResource(TLMR.strings.cast_error_loading),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun startTrackingCastProgress() {
        castProgressJob?.cancel()
        castProgressJob = activity.lifecycleScope.launch {
            while (castSession?.isConnected == true) {
                val currentPosition = getCurrentCastPosition()
                viewModel?.updateCastProgress(currentPosition.toFloat() / 1000)
                delay(1000)
            }
        }
    }

    private fun getCurrentCastPosition(): Long {
        return castSession?.remoteMediaClient?.approximateStreamPosition ?: 0
    }

    fun maintainCastSessionBackground() {
        castSession?.let { session ->
            if (session.isConnected) {
                session.remoteMediaClient?.pause()
                _isPlaying.value = false
            }
        }
    }

    fun updateQueueItems(items: List<MediaQueueItem>? = null) {
        _queueItems.value = items ?: castSession?.remoteMediaClient?.mediaQueue?.let { queue ->
            (0 until queue.itemCount).mapNotNull { index ->
                queue.getItemAtIndex(index)
            }
        } ?: emptyList()
    }

    fun reset() {
        mediaQueue.clear()
        _queueItems.value = emptyList()
        _isLoading.value = false
        _castState.value = CastState.DISCONNECTED
        castProgressJob?.cancel()
        castSession = null
    }

    fun reconnect() {
        if (!isCastApiAvailable) return
        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
            castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected == true) {
                updateCastState(CastState.CONNECTED)
                startTrackingCastProgress()
                updateQueueItems()
                updateCurrentMedia()

                castSession?.remoteMediaClient?.registerCallback(
                    object : RemoteMediaClient.Callback() {
                        override fun onStatusUpdated() {
                            updateCurrentMedia()
                            updateQueueItems()
                        }

                        override fun onQueueStatusUpdated() {
                            updateQueueItems()
                        }

                        override fun onPreloadStatusUpdated() {
                            updateQueueItems()
                        }
                    },
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    fun startDeviceDiscovery() {
        if (!isCastApiAvailable) return
        discoveryRetryJob?.cancel()

        try {
            castContext?.let { castContext ->
                if (_castState.value != CastState.CONNECTED) {
                    _castState.value = CastState.CONNECTING
                }

                val currentSession = castContext.sessionManager.currentCastSession
                val selector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                    .addControlCategory(androidx.mediarouter.media.MediaControlIntent.CATEGORY_LIVE_VIDEO)
                    .addControlCategory(androidx.mediarouter.media.MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                    .build()

                mediaRouterCallback?.let {
                    mediaRouter.removeCallback(it)
                    mediaRouterCallback = null
                }

                mediaRouterCallback = object : androidx.mediarouter.media.MediaRouter.Callback() {
                    private var lastUpdate = 0L

                    override fun onRouteAdded(
                        router: androidx.mediarouter.media.MediaRouter,
                        route: androidx.mediarouter.media.MediaRouter.RouteInfo,
                    ) {
                        if (System.currentTimeMillis() - lastUpdate > 1000) {
                            lastUpdate = System.currentTimeMillis()
                            updateDevicesList(currentSession)
                        }
                    }

                    override fun onRouteRemoved(
                        router: androidx.mediarouter.media.MediaRouter,
                        route: androidx.mediarouter.media.MediaRouter.RouteInfo,
                    ) {
                        if (System.currentTimeMillis() - lastUpdate > 1000) {
                            lastUpdate = System.currentTimeMillis()
                            updateDevicesList(currentSession)
                        }
                    }

                    override fun onRouteChanged(
                        router: androidx.mediarouter.media.MediaRouter,
                        route: androidx.mediarouter.media.MediaRouter.RouteInfo,
                    ) {
                        if (System.currentTimeMillis() - lastUpdate > 1000) {
                            lastUpdate = System.currentTimeMillis()
                            updateDevicesList(currentSession)
                        }
                    }
                }.also { callback ->
                    mediaRouter.addCallback(
                        selector,
                        callback,
                        androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
                    )
                }

                updateDevicesList(currentSession)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error in startDeviceDiscovery: ${e.message}" }
            if (_castState.value != CastState.CONNECTED) {
                _castState.value = CastState.DISCONNECTED
            }
        }
    }

    private fun updateDevicesList(currentSession: CastSession?) {
        val connectedDeviceId = currentSession?.castDevice?.deviceId

        val newDevices = mediaRouter.routes
            .filter { !it.isDefault }
            .map { route ->
                CastDevice(
                    id = route.id,
                    name = route.name,
                    isConnected = route.id == connectedDeviceId,
                    isSelected = route.id == connectedDeviceId,
                )
            }
            .distinctBy { it.id }

        if (_availableDevices.value != newDevices) {
            _availableDevices.value = newDevices

            when {
                newDevices.any { it.isConnected } -> _castState.value = CastState.CONNECTED
                newDevices.isEmpty() && _castState.value != CastState.DISCONNECTED ->
                    _castState.value = CastState.DISCONNECTED
            }
        }
    }

    fun connectToDevice(deviceId: String) {
        try {
            val route = mediaRouter.routes.find { it.id == deviceId } ?: return
            if (route.id == castSession?.castDevice?.deviceId) return

            _availableDevices.value = _availableDevices.value.map { device ->
                device.copy(isSelected = device.id == deviceId)
            }

            activity.lifecycleScope.launch {
                try {
                    _castState.value = CastState.CONNECTING
                    mediaRouter.selectRoute(route)

                    var attempts = 0
                    while (attempts < 5) {
                        if (castSession?.isConnected == true) {
                            _castState.value = CastState.CONNECTED
                            return@launch
                        }
                        delay(15000)
                        attempts++
                    }

                    if (castSession?.isConnected != true) {
                        _castState.value = CastState.DISCONNECTED
                        showConnectionErrorToast()
                    }
                } catch (e: Exception) {
                    _availableDevices.value = _availableDevices.value.map { device ->
                        device.copy(isSelected = device.isConnected)
                    }
                    logcat(LogPriority.ERROR) { "Error connecting to device: ${e.message}" }
                    _castState.value = CastState.DISCONNECTED
                    showConnectionErrorToast()
                }
            }
        } catch (e: Exception) {
            _availableDevices.value = _availableDevices.value.map { device ->
                device.copy(isSelected = device.isConnected)
            }
            logcat(LogPriority.ERROR) { "Error in connectToDevice: ${e.message}" }
            _castState.value = CastState.DISCONNECTED
            showConnectionErrorToast()
        }
    }

    private fun showConnectionErrorToast() {
        activity.runOnUiThread {
            Toast.makeText(
                context,
                context.stringResource(TLMR.strings.cast_connection_error),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun stop() {
        castSession?.remoteMediaClient?.stop()
        _isPlaying.value = false
    }

    fun setVolume(volume: Float) {
        try {
            castSession?.let { session ->
                val newVolume = volume.coerceIn(0f, 1f)
                session.volume = newVolume.toDouble()
                _volume.value = newVolume
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private fun updateCurrentMedia() {
        castSession?.remoteMediaClient?.let { client ->
            val mediaInfo = client.mediaInfo
            val metadata = mediaInfo?.metadata

            _currentMedia.value = CastMedia(
                title = metadata?.getString(MediaMetadata.KEY_TITLE)
                    ?: viewModel?.mediaTitle?.value ?: "",
                subtitle = metadata?.getString(MediaMetadata.KEY_SUBTITLE)
                    ?: viewModel?.currentEpisode?.value?.name ?: "",
                thumbnail = metadata?.images?.firstOrNull()?.url?.toString()
                    ?: viewModel?.currentAnime?.value?.thumbnailUrl,
            )
            _isPlaying.value = !client.isPaused
            _volume.value = castSession?.volume?.toFloat() ?: 1f
        }
    }

    fun seekRelative(offset: Int) {
        castSession?.remoteMediaClient?.let { client ->
            val newPosition = client.approximateStreamPosition + (offset * 1000)
            client.seek(newPosition)
        }
    }

    fun nextVideo() {
        castSession?.remoteMediaClient?.let { client ->
            val queue = client.mediaQueue
            val currentItemId = client.currentItem?.itemId ?: return@let
            val currentIndex = (0 until queue.itemCount).find {
                queue.getItemAtIndex(it)?.itemId == currentItemId
            } ?: return@let

            if (currentIndex < queue.itemCount - 1) {
                client.queueJumpToItem(queue.getItemAtIndex(currentIndex + 1)?.itemId ?: return@let, null)
            }
        }
    }

    fun previousVideo() {
        castSession?.remoteMediaClient?.let { client ->
            val queue = client.mediaQueue
            val currentItemId = client.currentItem?.itemId ?: return@let
            val currentIndex = (0 until queue.itemCount).find {
                queue.getItemAtIndex(it)?.itemId == currentItemId
            } ?: return@let

            if (currentIndex > 0) {
                client.queueJumpToItem(queue.getItemAtIndex(currentIndex - 1)?.itemId ?: return@let, null)
            }
        }
    }

    fun endSession() {
        val mSessionManager = castContext!!.sessionManager
        mSessionManager.endCurrentSession(true)
        reset()
        _castState.value = CastState.DISCONNECTED
    }

    private var lastAppliedSettings: SubtitleSettings? = null
    private var subtitleSettingsJob: Job? = null

    fun applySubtitleSettings(settings: SubtitleSettings) {
        subtitleSettingsJob?.cancel()
        if (lastAppliedSettings == settings) {
            logcat(LogPriority.INFO) { "Subtitle settings unchanged, skipping update" }
            return
        }
        lastAppliedSettings = settings

        subtitleSettingsJob = activity.lifecycleScope.launch {
            try {
                val session = castSession ?: run {
                    castContext?.sessionManager?.currentCastSession?.also {
                        castSession = it
                    } ?: run {
                        logcat(LogPriority.ERROR) { "No active cast session found" }
                        null
                    }
                } ?: run {
                    logcat(LogPriority.ERROR) { "Failed to get cast session" }
                    return@launch
                }

                val client = session.remoteMediaClient ?: run {
                    logcat(LogPriority.ERROR) { "No remote media client available" }
                    return@launch
                }
                if (!client.hasMediaSession()) {
                    logcat(LogPriority.ERROR) { "No active media session found" }
                    return@launch
                }

                subtitlePreferences.saveTextTrackStyle(settings)

                val textTrackStyle = TextTrackStyle().apply {
                    fontScale = settings.fontSize.value / 20f
                    foregroundColor = settings.textColor.toArgb()
                    backgroundColor = settings.backgroundColor.toArgb()
                    windowColor = android.graphics.Color.TRANSPARENT

                    if (settings.shadowRadius.value > 0) {
                        edgeColor = android.graphics.Color.BLACK
                        edgeType = when {
                            settings.shadowRadius.value <= 3 -> TextTrackStyle.EDGE_TYPE_DEPRESSED
                            settings.shadowRadius.value <= 6 -> TextTrackStyle.EDGE_TYPE_DROP_SHADOW
                            else -> TextTrackStyle.EDGE_TYPE_OUTLINE
                        }
                    } else {
                        edgeType = TextTrackStyle.EDGE_TYPE_NONE
                    }

                    when (settings.fontFamily) {
                        FontFamily.Default -> setFontFamily("SANS_SERIF")
                        FontFamily.SansSerif -> setFontFamily("SANS_SERIF")
                        FontFamily.Serif -> setFontFamily("SERIF")
                        FontFamily.Monospace -> setFontFamily("MONOSPACE")
                        FontFamily.Cursive -> setFontFamily("CURSIVE")
                        else -> setFontFamily("SANS_SERIF")
                    }

                    fontStyle = TextTrackStyle.FONT_STYLE_NORMAL
                    fontGenericFamily = when (settings.fontFamily) {
                        FontFamily.SansSerif -> TextTrackStyle.FONT_FAMILY_SANS_SERIF
                        FontFamily.Serif -> TextTrackStyle.FONT_FAMILY_SERIF
                        FontFamily.Monospace -> TextTrackStyle.FONT_FAMILY_MONOSPACED_SANS_SERIF
                        FontFamily.Cursive -> TextTrackStyle.FONT_FAMILY_CURSIVE
                        else -> TextTrackStyle.FONT_FAMILY_SANS_SERIF
                    }

                    if (settings.borderStyle != BorderStyle.NONE) {
                        edgeType = when (settings.borderStyle) {
                            BorderStyle.OUTLINE -> TextTrackStyle.EDGE_TYPE_OUTLINE
                            BorderStyle.DROP_SHADOW -> TextTrackStyle.EDGE_TYPE_DROP_SHADOW
                            BorderStyle.RAISED -> TextTrackStyle.EDGE_TYPE_RAISED
                            BorderStyle.DEPRESSED -> TextTrackStyle.EDGE_TYPE_DEPRESSED
                            else -> TextTrackStyle.EDGE_TYPE_NONE
                        }
                    }
                }

                val wasPlaying = client.isPlaying
                val activeTrackIds = client.mediaStatus?.activeTrackIds ?: longArrayOf()

                val styleApplied = suspendCancellableCoroutine { continuation ->
                    val task = client.setTextTrackStyle(textTrackStyle)
                    task.setResultCallback { result ->
                        if (continuation.isActive) {
                            val success = result.status.isSuccess
                            continuation.resume(success)
                        }
                    }
                    continuation.invokeOnCancellation {
                        task.cancel()
                    }
                }

                if (!styleApplied) {
                    logcat(LogPriority.ERROR) { "Failed to apply text track style" }
                    return@launch
                }

                if (activeTrackIds.isNotEmpty()) {
                    val tracksDisabled = suspendCancellableCoroutine { continuation ->
                        val task = client.setActiveMediaTracks(longArrayOf())
                        task.setResultCallback { result ->
                            if (continuation.isActive) {
                                val success = result.status.isSuccess
                                continuation.resume(success)
                            }
                        }
                        continuation.invokeOnCancellation {
                            task.cancel()
                        }
                    }

                    if (!tracksDisabled) {
                        logcat(LogPriority.ERROR) { "Failed to disable tracks" }
                    } else {
                        delay(500)
                        val tracksEnabled = suspendCancellableCoroutine { continuation ->
                            val task = client.setActiveMediaTracks(activeTrackIds)
                            task.setResultCallback { result ->
                                if (continuation.isActive) {
                                    val success = result.status.isSuccess
                                    continuation.resume(success)
                                }
                            }
                            continuation.invokeOnCancellation {
                                task.cancel()
                            }
                        }

                        if (!tracksEnabled) {
                            logcat(LogPriority.ERROR) { "Failed to re-enable tracks" }
                        } else if (wasPlaying) {
                            delay(100)
                            client.play()
                            logcat(LogPriority.INFO) { "Playback resumed" }
                        }
                    }
                } else {
                    logcat(LogPriority.INFO) { "No active tracks found, skipping refresh" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error applying subtitle settings: ${e.message}" }
                e.printStackTrace()
            } finally {
                subtitleSettingsJob = null
            }
        }
    }

    fun getDefaultSubtitleSettings(): SubtitleSettings {
        return SubtitleSettings(
            fontSize = subtitlePreferences.fontSize().get().sp,
            textColor = Color(subtitlePreferences.textColor().get()),
            backgroundColor = Color(subtitlePreferences.backgroundColor().get()),
            shadowRadius = subtitlePreferences.shadowRadius().get().dp,
            fontFamily = subtitlePreferences.getFontFamily(),
            borderStyle = subtitlePreferences.getBorderStyle(),
        )
    }
}
