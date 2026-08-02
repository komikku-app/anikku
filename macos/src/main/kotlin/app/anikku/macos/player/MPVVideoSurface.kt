package app.anikku.macos.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Target render interval in milliseconds (~33 fps).
 */
private const val FRAME_INTERVAL_MS = 30L

/**
 * Compose Desktop video surface for mpv software-rendered frames.
 *
 * Uses [MPVSoftwareRenderer] to pull decoded frames from libmpv and
 * renders them onto a Compose [Image] composable for reliable display
 * on macOS.
 *
 * Rendering Flow:
 *   LaunchedEffect ─► [MPVSoftwareRenderer.render()]
 *                              │
 *                              ▼
 *                      BufferedImage
 *                              │
 *                              ▼
 *                    .toComposeImageBitmap()
 *                              │
 *                              ▼
 *                    Image(bitmap = ...)
 */
@Composable
fun MPVVideoSurface(
    mpvHandle: Pointer?,
    renderer: MPVSoftwareRenderer?,
    modifier: Modifier = Modifier,
) {
    val mpvAvailable = mpvHandle != null && MPVLib.isAvailable

    // Current rendered frame as a Compose ImageBitmap
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    // Frame counter to force recomposition even when bitmap reference is reused
    var frameCounter by remember { mutableStateOf(0L) }

    // Surface size for aspect-ratio-aware rendering
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    // Flag set when the first frame has been rendered
    var hasFirstFrame by remember { mutableStateOf(false) }

    // Periodic render loop: pull frames from the software renderer.
    // Rendering is performed on Dispatchers.IO to avoid blocking the Compose
    // UI thread; only the resulting ImageBitmap is posted back to the UI.
    LaunchedEffect(renderer, mpvAvailable) {
        if (renderer == null || !mpvAvailable) {
            if (renderer == null) logger.warn { "🎬 SURFACE: renderer is null — cannot render frames" }
            if (!mpvAvailable) logger.warn { "🎬 SURFACE: mpv not available — cannot render frames" }
            return@LaunchedEffect
        }

        // Track frames-per-second for diagnostic logging
        var frameCount = 0L
        var lastFpsLog = System.nanoTime()
        var loggedFirstFrameMessage = false
        var loggedWaitingMessage = false

        while (isActive) {
            val startTime = System.nanoTime()

            val bufferedImage = withContext(Dispatchers.IO) {
                renderer.render()
            }
            if (bufferedImage != null) {
                currentFrame = bufferedImage.toComposeImageBitmap()
                hasFirstFrame = true
                frameCounter++ // Force recomposition
                frameCount++

                if (!loggedFirstFrameMessage) {
                    loggedFirstFrameMessage = true
                    logger.info { "🎬 SURFACE: first frame received and displayed (${bufferedImage.width}x${bufferedImage.height})" }
                }

                // Log frame rate every 5 seconds
                val now = System.nanoTime()
                val elapsedSec = (now - lastFpsLog) / 1_000_000_000.0
                if (elapsedSec >= 5.0) {
                    val fps = (frameCount / elapsedSec).toInt()
                    logger.debug { "🎬 SURFACE: ~${fps} fps (${frameCount} frames in ${elapsedSec.toInt()}s)" }
                    frameCount = 0
                    lastFpsLog = now
                }
            } else {
                // No frame available yet — normal during initial buffering
                if (!loggedFirstFrameMessage && !loggedWaitingMessage) {
                    loggedWaitingMessage = true
                    logger.debug { "🎬 SURFACE: waiting for first frame (renderer returned null — normal during buffering)" }
                }
            }

            // Adaptive delay: aim for the target frame interval
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000L
            val sleepMs = (FRAME_INTERVAL_MS - elapsedMs).coerceAtLeast(0L)
            delay(sleepMs)
        }
    }

    // Clean up the bitmap reference on dispose
    DisposableEffect(renderer) {
        onDispose {
            currentFrame = null
            hasFirstFrame = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size -> surfaceSize = size },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = currentFrame
        if (bitmap != null && hasFirstFrame) {
            // Use Image composable for reliable frame display on macOS
            Image(
                bitmap = bitmap,
                contentDescription = "Video frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// MPV utility functions
// ---------------------------------------------------------------------------

/**
 * Toggle playback between play and pause.
 */
fun togglePause(handle: Pointer) {
    try {
        val isPaused = MPVLib.getPropertyFlag(handle, "pause", default = false)
        MPVLib.setPropertyString(handle, "pause", if (isPaused) "no" else "yes")
    } catch (e: Exception) {
        logger.warn(e) { "Failed to toggle pause" }
    }
}

/**
 * Seek relative to the current position.
 */
fun seekRelative(handle: Pointer, seconds: Double) {
    try {
        val currentPos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
        val newPos = (currentPos + seconds).coerceAtLeast(0.0)
        MPVLib.setPropertyDouble(handle, "time-pos", newPos)
    } catch (e: Exception) {
        logger.warn(e) { "Failed to seek" }
    }
}

/**
 * Adjust volume by the given delta.
 */
fun adjustVolume(handle: Pointer, delta: Int) {
    try {
        val currentVol = MPVLib.getPropertyInt(handle, "volume", 100)
        val newVol = (currentVol + delta).coerceIn(0, 200)
        MPVLib.setPropertyInt(handle, "volume", newVol)
    } catch (e: Exception) {
        logger.warn(e) { "Failed to adjust volume" }
    }
}
