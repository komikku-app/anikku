package app.anikku.macos.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    /** Whether the PiP window is currently visible. */
    var isPipVisible: Boolean = false
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
 * @param onClose Called when the user closes the PiP window.
 */
@Composable
fun PipWindow(
    pipHandler: MacOSPipHandler,
    renderer: MPVSoftwareRenderer?,
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
            PipVideoSurface(
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
