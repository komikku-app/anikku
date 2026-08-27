package app.anikku.macos.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import java.awt.image.BufferedImage

/**
 * Ring buffer of the last N seconds of decoded video frames, fed from
 * [MPVSoftwareRenderer.frames].
 *
 * Frames are downscaled to [maxWidth] and throttled to [fps], so a 5-second
 * clip costs a few MB of memory instead of holding ~40 full-resolution
 * bitmaps. [snap] returns the buffered frames in chronological order.
 */
class GifClipRecorder(
    val maxWidth: Int = 480,
    val fps: Int = 6,
    val seconds: Int = 5,
) {
    val maxFrames: Int get() = fps * seconds

    private val ring = ArrayDeque<BufferedImage>()
    private var lastCaptureNanos = 0L

    /** Feed a frame (call at the renderer's rate — throttles internally). */
    fun onFrame(bitmap: ImageBitmap) {
        val now = System.nanoTime()
        if (now - lastCaptureNanos < 1_000_000_000L / fps) return
        lastCaptureNanos = now
        val pixelMap = bitmap.toPixelMap()
        feedDownscaled(pixelMap.buffer, pixelMap.width, pixelMap.height)
    }

    /**
     * Downscale + buffer an ARGB frame (internal seam for headless tests).
     * @see downscaleArgb
     */
    internal fun feedDownscaled(buffer: IntArray, width: Int, height: Int) {
        val frame = downscaleArgb(buffer, width, height, maxWidth) ?: return
        if (ring.size == maxFrames) ring.removeFirst()
        ring.addLast(frame)
    }

    /** Frames in chronological order; empty if nothing has been captured yet. */
    fun snap(): List<BufferedImage> = ring.toList()

    fun size(): Int = ring.size
}

/**
 * Downscale an ARGB int buffer to fit [maxWidth] by box-averaging, returning
 * a [BufferedImage]. Returns null for degenerate inputs. Purely functional —
 * unit-testable without Compose or mpv.
 */
internal fun downscaleArgb(
    buffer: IntArray,
    inWidth: Int,
    inHeight: Int,
    maxWidth: Int,
): BufferedImage? {
    if (inWidth <= 0 || inHeight <= 0 || buffer.size < inWidth * inHeight) return null
    if (inWidth <= maxWidth) {
        val image = BufferedImage(inWidth, inHeight, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, inWidth, inHeight, buffer, 0, inWidth)
        return image
    }
    val stride = inWidth / maxWidth
    val outWidth = inWidth / stride
    val outHeight = inHeight / stride
    val image = BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB)
    val area = stride * stride
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (dy in 0 until stride) {
                var index = (y * stride + dy) * inWidth + x * stride
                for (dx in 0 until stride) {
                    val argb = buffer[index++]
                    a += argb ushr 24 and 0xFF
                    r += argb ushr 16 and 0xFF
                    g += argb ushr 8 and 0xFF
                    b += argb and 0xFF
                }
            }
            val n = area
            val pixel = (((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)).toInt()
            image.setRGB(x, y, pixel)
        }
    }
    return image
}
