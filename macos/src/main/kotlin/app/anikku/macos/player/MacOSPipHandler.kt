package app.anikku.macos.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collect

/**
 * Picture-in-Picture handler — a secondary always-on-top floating window.
 *
 * The PiP window renders the SAME decoded video as the main player by
 * subscribing to [MPVSoftwareRenderer.frames]. The main surface keeps polling
 * the renderer, so there is never more than one thread inside mpv's render
 * context; the PiP window only receives pixel-copied snapshots.
 *
 * ## Usage
 *
 * ```kotlin
 * val pipHandler = remember { MacOSPipHandler() }
 * pipHandler.togglePip(title, renderer)
 *
 * // At the top level of the player's composition:
 * PipWindow(pipHandler, renderer, onClose = {})
 * ```
 */
class MacOSPipHandler {

    /**
     * Whether the PiP window is currently visible.
     *
     * Backed by Compose state (not a plain field) so [PipWindow]'s
     * `if (pipHandler.isPipVisible)` read invalidates composition when the
     * player toggles it — otherwise the icon flipped but the window never
     * entered composition.
     */
    var isPipVisible: Boolean by mutableStateOf(false)
        private set

    /** The current PiP window title. */
    var pipTitle: String = ""
        private set

    /** The renderer whose frames the PiP window displays. */
    private var renderer: MPVSoftwareRenderer? = null

    /**
     * Open the PiP window.
     *
     * @param title The title/description for the PiP window.
     * @param renderer The software renderer to mirror.
     * @return true if the PiP window was opened.
     */
    fun openPipWindow(title: String, renderer: MPVSoftwareRenderer?): Boolean {
        pipTitle = title
        this.renderer = renderer
        isPipVisible = true
        return true
    }

    /** Close the PiP window. */
    fun closePipWindow() {
        isPipVisible = false
        renderer = null
    }

    /** Toggle PiP window visibility. */
    fun togglePip(title: String, renderer: MPVSoftwareRenderer?): Boolean {
        if (isPipVisible) {
            closePipWindow()
            return false
        }
        return openPipWindow(title, renderer)
    }
}

/**
 * Displays the shared renderer's latest frame without touching the renderer
 * itself (subscribes to [MPVSoftwareRenderer.frames]).
 */
@Composable
fun PipVideoSurface(
    renderer: MPVSoftwareRenderer?,
    modifier: Modifier = Modifier,
) {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(renderer) {
        val source = renderer ?: return@LaunchedEffect
        source.frames.collect { frame ->
            currentFrame = frame
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        currentFrame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "PiP video frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Composable that renders the PiP window when [pipHandler] requests one.
 *
 * Must be placed at the top level of the player's composition tree (alongside
 * the main Window). The PiP window is a secondary always-on-top window.
 *
 * @param pipHandler The PiP handler controlling visibility.
 * @param renderer The software renderer whose frames are mirrored.
 * @param viewModel The active player — drives the PiP controls (play/pause,
 * seek, mute) so the window is fully controllable while another app is front.
 * @param onClose Called when the user closes the PiP window.
 */
@Composable
fun PipWindow(
    pipHandler: MacOSPipHandler,
    renderer: MPVSoftwareRenderer?,
    viewModel: PlayerViewModel? = null,
    onClose: () -> Unit,
) {
    if (pipHandler.isPipVisible) {
        val pipWindowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(320.dp, 240.dp),
        )

        Window(
            onCloseRequest = {
                pipHandler.closePipWindow()
                onClose()
            },
            title = pipHandler.pipTitle,
            state = pipWindowState,
            alwaysOnTop = true,
        ) {
            Column(Modifier.fillMaxSize()) {
                PipVideoSurface(
                    renderer = renderer,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (viewModel != null) {
                    PipControls(viewModel)
                }
            }
        }
    }
}

/**
 * Bottom control bar for the PiP window — play/pause, a seek slider and mute.
 * Reads the player's live flows directly (same process), so it keeps working
 * when the main window is in the background.
 */
@Composable
private fun PipControls(viewModel: PlayerViewModel) {
    val isPaused by viewModel.isPaused.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val volume by viewModel.volume.collectAsState()
    var isMuted by remember { mutableStateOf(false) }
    var lastVolume by remember { mutableIntStateOf(100) }

    Surface(color = Color(0xFF141414)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { viewModel.seekTo(it.toDouble()) },
                enabled = duration > 0,
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    formatTime(position) + " / " + formatTime(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (isMuted) {
                            viewModel.setVolume(lastVolume.coerceIn(0, 200))
                            isMuted = false
                        } else {
                            lastVolume = volume
                            viewModel.setVolume(0)
                            isMuted = true
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (isMuted || volume == 0) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
