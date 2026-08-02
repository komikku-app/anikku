package app.anikku.macos.player

import com.sun.jna.Callback
import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.lang.ref.Reference

private val logger = KotlinLogging.logger {}

/**
 * Software renderer for mpv video frames using the libmpv render API.
 *
 * Creates a [mpv_render_context] with `MPV_RENDER_API_TYPE_SW` and renders
 * video frames into a CPU-side RGBA buffer. The frames are then converted
 * to [BufferedImage] objects suitable for display in Compose Desktop via
 * [toComposeImageBitmap][androidx.compose.ui.graphics.toComposeImageBitmap].
 *
 * ## Usage
 *
 * ```kotlin
 * val renderer = MPVSoftwareRenderer(mpvHandle)
 * renderer.create()
 *
 * // When a new frame is needed:
 * val image: BufferedImage? = renderer.render()
 * ```
 *
 * ## Lifecycle
 *
 * 1. Call [create] after mpv_initialize to create the render context.
 * 2. Call [updateVideoSize] when [MPVLib.MPV_EVENT_VIDEO_RECONFIG] fires
 *    to allocate a properly-sized buffer.
 * 3. Call [render] periodically (e.g., every 40ms) to get the latest frame.
 * 4. Call [dispose] on player shutdown.
 *
 * @param mpvHandle The initialized mpv handle (must have `vo=libmpv` set).
 */
class MPVSoftwareRenderer(
    private val mpvHandle: Pointer,
) {

    private var renderContext: Pointer? = null

    /** Current video dimensions (0 = unknown). Set via [updateVideoSize]. */
    var videoWidth: Int = 0
        private set

    var videoHeight: Int = 0
        private set

    /** Current video stride in bytes. */
    var videoStride: Int = 0
        private set

    /** Native memory buffer for raw RGBA pixel data. */
    private var pixelBuffer: Memory? = null

    /** Reusable [BufferedImage] for frame output. Uses TYPE_INT_ARGB_PRE for Skia compatibility. */
    private var frameImage: BufferedImage? = null

    /** Reusable [IntArray] for bulk native → Java heap copy (reduces GC). */
    private var rawIntBuffer: IntArray? = null

    /** Reusable native memory for render params (avoids per-frame allocation). */
    private var sizeParams: Memory? = null
    /** size_t* — one scalar stride in bytes per row (not an array). */
    private var strideParam: Memory? = null
    private var formatParam: Memory? = null
    /** Must be [Memory] (not Pointer) to prevent GC from freeing native buffer while mpv reads it. */
    private var renderParams: Memory? = null
    /** Reusable non-blocking target time param (value = 0). */
    private val blockParam: Memory = Memory(4).also { it.setInt(0, 0) }

    /** Update callback registered with mpv — must be a field to prevent GC. */
    private var updateCallback: UpdateCallback? = null

    /** Whether the render context has been successfully created. */
    var isReady: Boolean = false
        private set

    /** Lock protecting mutable renderer state across threads. */
    private val lock = Any()

    /**
     * Serializes native render calls with context disposal. The render context
     * is snapshotted under [lock], so disposal also needs this second barrier
     * before calling mpv_render_context_free().
     */
    private val nativeRenderLock = Any()

    /** Frame counter for diagnostic logging (logged every 100 frames). */
    private var frameCount: Long = 0

    /** Whether we've already logged the first-frame milestone. */
    private var loggedFirstFrame: Boolean = false

    /**
     * Anchors the current frame's Memory buffers at the class level so the JVM JIT
     * compiler cannot perform dead-code elimination and garbage-collect them while
     * mpv's native render is still reading them.
     *
     * Without this volatile field, the JIT may determine that the sub-Memory
     * references captured in the RenderSnapshot (sizeParams, strideParam,
     * formatParam) are "dead" after destructuring — even though
     * mpv is actively dereferencing them via raw pointers in renderParams.
     * GC then frees those native buffers → mpv reads garbage → malloc heap
     * corruption → "memory corruption of free block" crash.
     */
    @Volatile
    private var inFlightSnapshot: RenderSnapshot? = null

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Create the software render context.
     * Must be called after [MPVLib.initialize] and before loading any file.
     *
     * @return true on success, false if the context could not be created.
     */
    fun create(): Boolean {
        synchronized(lock) {
            if (isReady) return true

            try {
                val ctx = MPVLib.renderContextCreate(mpvHandle)
                if (ctx != null) {
                    renderContext = ctx
                    // Register update callback — mpv calls this when a new frame is available.
                    // We poll via the Compose render loop, but the callback must exist
                    // for mpv's internal render state machine to advance correctly.
                    updateCallback = object : UpdateCallback {
                        override fun invoke(cbCtx: Pointer?) { /* no-op: polled externally */ }
                    }
                    MPVLib.renderContextSetUpdateCallback(ctx, updateCallback!!, null)
                    isReady = true
                    logger.info { "MPV software render context created (update callback registered)" }
                    return true
                } else {
                    logger.error { "Failed to create MPV render context" }
                    return false
                }
            } catch (e: Exception) {
                logger.error(e) { "Exception creating MPV render context" }
                return false
            }
        }
    }

    /**
     * Update the video dimensions. Must be called when
     * [MPVLib.MPV_EVENT_VIDEO_RECONFIG] is received or when the surface is
     * resized.
     *
     * Reallocates the pixel buffer if the size has changed.
     * Uses TYPE_INT_ARGB_PRE (premultiplied alpha) for Skia compatibility
     * on Compose Desktop — Skia requires proper alpha values.
     */
    fun updateVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == videoWidth && height == videoHeight) return

        synchronized(lock) {
            videoWidth = width
            videoHeight = height

            videoStride = width * 4

            // Allocate native pixel buffer (4 bytes per pixel).
            // EXTRA PADDING: allocate 8 extra rows to prevent SIMD writes
            // from going past buffer boundaries (mp_image_clear uses
            // NEON SIMD on ARM64 which can write up to 64 bytes past
            // the last pixel in a row).
            val paddedHeight = height + 8
            val newSize = videoStride * paddedHeight
            pixelBuffer = Memory(newSize.toLong())
            // Zero-initialize the buffer so mpv's read-modify-write ops
            // don't operate on garbage malloc data.
            pixelBuffer!!.clear()
            // TYPE_INT_ARGB with alpha=0xFF for fully opaque pixels.
            // CRITICAL: Discard the old IntArray so it is recreated with the
            // correct (new) dimensions. Without this, render() reuses the old
            // smaller array → nativeBuffer.read writes past the array bounds
            // → JVM heap corruption → G1 barrier SIGSEGV + malloc corruption.
            rawIntBuffer = null

            frameImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

            // Reuse render param memory; reallocate only when dimensions change.
            sizeParams = Memory(8).also { mem ->
                mem.setInt(0, width)
                mem.setInt(4, height)
            }
            // The software render API uses scalar values here:
            // SW_STRIDE points to one size_t (bytes per row), and SW_POINTER
            // points directly at the first pixel. These are not plane arrays.
            strideParam = Memory(8).also { mem ->
                mem.setLong(0, videoStride.toLong())
            }
            renderParams = buildRenderParamsForFormat(MPVLib.RENDER_FORMAT_BGR0)
        }

        logger.info { "Video surface updated: ${width}x${height}" }
    }

    /** Build the reusable render params memory for the given pixel format. */
    private fun buildRenderParamsForFormat(format: String): Memory {
        formatParam = Memory(5L).also { it.setString(0, format) }
        return MPVLib.buildRenderParams(
            MPVLib.RENDER_PARAM_SW_SIZE to sizeParams,
            MPVLib.RENDER_PARAM_SW_FORMAT to formatParam,
            MPVLib.RENDER_PARAM_SW_STRIDE to strideParam,
            MPVLib.RENDER_PARAM_SW_POINTER to pixelBuffer,
            MPVLib.RENDER_PARAM_BLOCK_FOR_TARGET_TIME to blockParam,
        )
    }

    /** Snapshot of renderer state captured under [lock] for a render call. */
    private data class RenderSnapshot(
        val context: Pointer,
        val buffer: Memory,
        val image: BufferedImage,
        val width: Int,
        val height: Int,
        val stride: Int,
        /** Held as Memory to prevent GC of native buffer during the render call. */
        val params: Memory,
        val intBuffer: IntArray,
        /** Parameter memory referenced by [params]. */
        val sizeParams: Memory?,
        val strideParam: Memory?,
        val formatParam: Memory?,
    )

    /**
     * Render the current video frame.
     *
     * Call this periodically (e.g., every 40ms for 25fps) to get the latest
     * decoded frame. If a frame is not yet available, returns null.
     *
     * @return A [BufferedImage] with the current frame, or null if no frame.
     */
    fun render(): BufferedImage? {
        // Acquire this before snapshotting the context. dispose() uses the same
        // lock, so it cannot free the context between snapshot and native render.
        synchronized(nativeRenderLock) {
        // Snapshot mutable state under the lock so render() and
        // updateVideoSize()/dispose() cannot race.
        val snapshot = synchronized(lock) {
            val ctx = renderContext
            if (ctx == null) {
                if (frameCount == 0L) logger.debug { "🎬 RENDER: renderContext is null — not yet created" }
                return null
            }
            val buffer = pixelBuffer
            if (buffer == null) {
                // This is the most common failure: updateVideoSize() hasn't been called yet
                // because VIDEO_RECONFIG hasn't fired or dwidth/dheight returned 0.
                if (frameCount == 0L) logger.warn { "🎬 RENDER: pixelBuffer is null — VIDEO_RECONFIG not yet received or dimensions not available" }
                return null
            }
            val image = frameImage
            if (image == null) {
                logger.error { "🎬 RENDER: frameImage is null — unexpected, should be allocated with pixelBuffer" }
                return null
            }
            val width = videoWidth
            val height = videoHeight
            val stride = videoStride
            val params = renderParams
            if (params == null) {
                if (frameCount == 0L) logger.warn { "🎬 RENDER: renderParams is null — updateVideoSize not yet called" }
                return null
            }
            if (width <= 0 || height <= 0) {
                if (frameCount == 0L) logger.warn { "🎬 RENDER: invalid video dimensions ${width}x${height}" }
                return null
            }
            // Recreate intBuffer if dimensions changed and the old array is too small.
            // Without this check, nativeBuffer.read writes past the array bounds
            // → JVM heap corruption → G1 barrier crash + malloc corruption.
            val requiredInts = height * stride / 4
            var ints = rawIntBuffer
            if (ints == null || ints.size < requiredInts) {
                ints = IntArray(requiredInts)
                rawIntBuffer = ints
            }
            val intBuffer = ints
            // Capture every Memory object referenced by renderParams so native
            // pointers remain valid for the complete render and copy operation.
            RenderSnapshot(
                context = ctx,
                buffer = buffer,
                image = image,
                width = width,
                height = height,
                stride = stride,
                params = params,
                intBuffer = intBuffer,
                sizeParams = sizeParams,
                strideParam = strideParam,
                formatParam = formatParam,
            )
        }

        // Anchor snapshot at class level to prevent JIT dead-code elimination
        // from freeing Memory objects during the native render call.
        inFlightSnapshot = snapshot
        try {
                val result = MPVLib.renderContextRender(snapshot.context, snapshot.params)

                if (result < 0) {
                    if (frameCount == 0L) {
                        val errorName = when (result) {
                            MPVLib.ERROR_INVALID_PARAMETER -> "INVALID_PARAMETER"
                            MPVLib.ERROR_UNINITIALIZED -> "UNINITIALIZED"
                            MPVLib.ERROR_NOMEM -> "NOMEM"
                            MPVLib.ERROR_UNSUPPORTED -> "UNSUPPORTED"
                            MPVLib.ERROR_NOT_IMPLEMENTED -> "NOT_IMPLEMENTED"
                            else -> "unknown($result)"
                        }
                        logger.warn { "🎬 RENDER: mpv_render_context_render returned error $errorName — no frame available yet. pixelBuffer=allocated, format=bgr0" }
                    }
                    return null
                }

                // Copy the native pixel data into the BufferedImage for display.
                copyBufferToImage(snapshot)

                // Keep Memory objects alive until after BOTH the native render call
                // AND the buffer copy complete. Placed AFTER copyBufferToImage because
                // it also reads snapshot.buffer (via nativeBuffer.read()).
                Reference.reachabilityFence(snapshot.buffer)
                Reference.reachabilityFence(snapshot.params)
                Reference.reachabilityFence(snapshot.sizeParams)
                Reference.reachabilityFence(snapshot.strideParam)
                Reference.reachabilityFence(snapshot.formatParam)

                frameCount++
                if (!loggedFirstFrame) {
                    loggedFirstFrame = true
                    logger.info { "🎬 RENDER: first frame rendered successfully! ${snapshot.width}x${snapshot.height}, format=bgr0" }
                    logger.info { "🎬 RENDER: ✅ VIDEO IS RENDERING" }
                }
                if (frameCount % 100L == 0L) {
                    logger.debug { "🎬 RENDER: frame $frameCount rendered (${snapshot.width}x${snapshot.height})" }
                }

                return snapshot.image
        } catch (e: Exception) {
            logger.debug { "Render frame failed (normal during buffering): ${e.message}" }
            return null
        } finally {
            // Release the anchor so the old snapshot can be GC'd before the next frame.
            inFlightSnapshot = null
        }
        }
    }

    /**
     * Copy raw BGR0 pixel data from a native [Memory] buffer into a
     * [BufferedImage] with TYPE_INT_ARGB.
     *
     * The mpv buffer has bytes B, G, R, A per pixel (bgra format).
     * BufferedImage.TYPE_INT_ARGB has layout 0xAARRGGBB.
     *
     * On little-endian: mpv bytes [B, G, R, A] → int 0xAARRGGBB
     * We need: int 0xFFRRGGBB (alpha=255 for opaque)
     *
     * We set the alpha byte to 0xFF (255) after the bulk copy.
     */
    private fun copyBufferToImage(snapshot: RenderSnapshot) {
        val nativeBuffer = snapshot.buffer
        val image = snapshot.image
        val width = snapshot.width
        val height = snapshot.height
        val stride = snapshot.stride
        val rawInts = snapshot.intBuffer
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        val intCount = height * stride / 4

        // Bulk native → JVM copy of raw pixel ints (0x00RRGGBB format from bgr0)
        nativeBuffer.read(0, rawInts, 0, intCount)

        // Copy row-by-row, setting alpha to 0xFF for each pixel
        if (stride == width * 4) {
            // Fast path: no padding
            for (i in 0 until width * height) {
                // rawInts[i] = 0x00RRGGBB, set alpha = 0xFF → 0xFFRRGGBB
                pixels[i] = rawInts[i] or 0xFF000000.toInt()
            }
        } else {
            // Slow path: copy each row separately
            for (y in 0 until height) {
                val srcPos = y * stride / 4
                val dstPos = y * width
                for (x in 0 until width) {
                    pixels[dstPos + x] = rawInts[srcPos + x] or 0xFF000000.toInt()
                }
            }
        }
    }

    /**
     * Dispose of the render context and free resources.
     */
    fun dispose() {
        // Wait for an in-flight native render/copy before freeing its context
        // and buffers. Without this barrier, shutdown can free the context while
        // the Compose render coroutine is still inside mpv_render_context_render().
        synchronized(nativeRenderLock) {
            synchronized(lock) {
                renderContext?.let { context ->
                    // Unregister before freeing so mpv cannot call back into a
                    // renderer that has released its Kotlin/JNA state.
                    MPVLib.renderContextSetUpdateCallback(context, null, null)
                    MPVLib.renderContextFree(context)
                }
                renderContext = null
                updateCallback = null
                pixelBuffer = null
                frameImage = null
                rawIntBuffer = null
                sizeParams = null
                strideParam = null
                formatParam = null
                renderParams = null
                inFlightSnapshot = null
                isReady = false
                videoWidth = 0
                videoHeight = 0
                videoStride = 0
            }
        }
        logger.info { "MPV software renderer disposed" }
    }
}

/**
 * JNA callback for mpv's render update notification.
 *
 * mpv calls this when a new video frame is available for rendering.
 * Since we poll periodically via the Compose render loop, the callback
 * itself is a no-op — its presence is what matters.
 */
private interface UpdateCallback : Callback {
    fun invoke(cbCtx: Pointer?)
}
