package app.anikku.macos.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.image.BufferedImage
import java.io.File
import java.util.Collections
import kotlin.concurrent.thread

/**
 * MPV render experiment — verifies the same software-render path used by the
 * macOS player, including VIDEO_RECONFIG sizing and BufferedImage conversion.
 *
 * Run:
 *   ./gradlew -p macos test --tests "app.anikku.macos.player.MPVRenderExperiment" --rerun
 */
@EnabledOnOs(OS.MAC)
class MPVRenderExperiment {

    @Test
    fun `production software renderer produces visible pixels`() {
        println("=".repeat(60))
        println("MPV SOFTWARE RENDERER EXPERIMENT")
        println("=".repeat(60))

        val loaded = MPVLib.initialize()
        assumeTrue(loaded, "libmpv not loadable — install with: brew install mpv")

        val handle = MPVLib.create() ?: run {
            assumeTrue(false, "mpv_create() returned null")
            return
        }

        var renderer: MPVSoftwareRenderer? = null
        try {
            // Match PlayerViewModel.configureMPV(): the SW API cannot consume
            // VideoToolbox GPU surfaces, so this test must use software decode.
            MPVLib.setOptionString(handle, "vo", "libmpv")
            MPVLib.setOptionString(handle, "hwdec", "no")
            MPVLib.setOptionString(handle, "cache", "no")
            MPVLib.setOptionString(handle, "keep-open", "yes")
            MPVLib.setOptionString(handle, "osd-level", "0")

            val initResult = MPVLib.initialize(handle)
            assertTrue(initResult == 0, "mpv_initialize failed: $initResult")

            renderer = MPVSoftwareRenderer(handle)
            assertTrue(renderer.create(), "software render context creation failed")
            MPVLib.requestEvent(handle, MPVLib.MPV_EVENT_VIDEO_RECONFIG, true)

            assertTrue(
                MPVLib.command(handle, "loadfile", "av://lavfi:testsrc2=size=320x240:rate=10:duration=10", "replace") == 0,
                "loadfile failed",
            )

            var lastImage: BufferedImage? = null
            val deadline = System.nanoTime() + 8_000_000_000L
            while (System.nanoTime() < deadline && lastImage == null) {
                // mpv's event queue must be pumped while using libmpv. This is
                // also what MPVEventLoop does in the real player.
                MPVLib.waitEvent(handle, 0.05)

                // Properties can become available just after VIDEO_RECONFIG,
                // so retry the lookup on every render tick rather than relying
                // on one event/property ordering.
                val width = listOf("dwidth", "video-params/w", "width")
                    .asSequence()
                    .map { MPVLib.getPropertyInt(handle, it, 0) }
                    .firstOrNull { it > 0 } ?: 0
                val height = listOf("dheight", "video-params/h", "height")
                    .asSequence()
                    .map { MPVLib.getPropertyInt(handle, it, 0) }
                    .firstOrNull { it > 0 } ?: 0
                if (width > 0 && height > 0) renderer.updateVideoSize(width, height)

                lastImage = renderer.render()
                Thread.sleep(30)
            }

            assertNotNull(lastImage, "renderer never returned a frame")
            val image = lastImage!!
            val pixels = IntArray(image.width * image.height)
            image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
            val hasVisibleContent = pixels.any { (it and 0x00FFFFFF) != 0 }
            println("Rendered ${image.width}x${image.height}; visible pixels=$hasVisibleContent")
            assertTrue(hasVisibleContent, "renderer returned an all-black image")

            for ((width, height) in listOf(160 to 90, 640 to 360, 321 to 181)) {
                renderer.updateVideoSize(width, height)
                val resized = renderer.render()
                assertNotNull(resized, "renderer returned no frame after resize to ${width}x$height")
                assertEquals(width, resized!!.width)
                assertEquals(height, resized.height)
            }

            val failures = Collections.synchronizedList(mutableListOf<Throwable>())
            val renderThread = thread(name = "mpv-render-dispose-test") {
                repeat(40) {
                    runCatching { renderer.render() }.exceptionOrNull()?.let(failures::add)
                }
            }
            repeat(20) { index ->
                val width = if (index % 2 == 0) 320 else 480
                val height = if (index % 2 == 0) 180 else 270
                renderer.updateVideoSize(width, height)
            }
            renderer.dispose()
            renderThread.join(5_000)
            assertFalse(renderThread.isAlive, "render thread did not finish after dispose")
            assertTrue(failures.isEmpty(), "render/resize/dispose raised: ${failures.firstOrNull()}")
            assertFalse(renderer.isReady)
            assertNull(renderer.render(), "disposed renderer must not return a frame")

        } finally {
            renderer?.dispose()
            MPVLib.destroy(handle)
        }
    }

    @Test
    fun `software renderer can be repeatedly created and disposed`() {
        assumeTrue(MPVLib.initialize(), "libmpv not loadable — install with: brew install mpv")

        repeat(3) {
            val handle = MPVLib.create() ?: run {
                assumeTrue(false, "mpv_create() returned null")
                return
            }
            var renderer: MPVSoftwareRenderer? = null
            try {
                MPVLib.setOptionString(handle, "vo", "libmpv")
                MPVLib.setOptionString(handle, "hwdec", "no")
                assertEquals(0, MPVLib.initialize(handle))
                renderer = MPVSoftwareRenderer(handle)
                assertTrue(renderer.create())
                renderer.updateVideoSize(64, 64)
                renderer.dispose()
                renderer.dispose()
                assertFalse(renderer.isReady)
            } finally {
                renderer?.dispose()
                MPVLib.destroy(handle)
            }
        }
    }

    @Test
    fun `player view model can be repeatedly initialized and shut down`() {
        assumeTrue(MPVLib.initialize(), "libmpv not loadable — install with: brew install mpv")

        repeat(3) {
            val viewModel = PlayerViewModel()
            assertTrue(viewModel.initialize(), "PlayerViewModel initialization failed on cycle $it")
            assertNotNull(viewModel.handle.value)
            assertNotNull(viewModel.renderer.value)

            viewModel.shutdown()
            viewModel.shutdown()

            assertEquals(PlaybackState.IDLE, viewModel.playbackState.value)
            assertNull(viewModel.handle.value)
            assertNull(viewModel.renderer.value)
            assertEquals(0.0, viewModel.currentPosition.value)
            assertEquals(0.0, viewModel.duration.value)
        }
    }

    @Test
    fun `production player view model plays renders and seeks synthetic media`() {
        assumeTrue(MPVLib.initialize(), "libmpv not loadable — install with: brew install mpv")
        val media = File.createTempFile("anikku-player-", ".mp4")
        val ffmpeg = ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=duration=10:size=320x240:rate=30",
            "-pix_fmt", "yuv420p", media.absolutePath,
        ).redirectErrorStream(true).start()
        val ffmpegOutput = ffmpeg.inputStream.bufferedReader().readText()
        assumeTrue(ffmpeg.waitFor() == 0, "ffmpeg could not create seekable media: $ffmpegOutput")
        val viewModel = PlayerViewModel()
        try {
            assertTrue(viewModel.initialize())
            // libmpv expects an RFC 8089 file URL with an empty authority.
            viewModel.loadEpisode("file://${media.absolutePath}")

            val playbackDeadline = System.nanoTime() + 8_000_000_000L
            while (
                System.nanoTime() < playbackDeadline &&
                (viewModel.currentPosition.value <= 0.0 || viewModel.duration.value <= 0.0)
            ) {
                Thread.sleep(50)
            }
            assertTrue(viewModel.currentPosition.value > 0.0, "production player position never advanced")
            assertTrue(viewModel.duration.value >= 9.5, "production player duration was ${viewModel.duration.value}")
            assertTrue(
                viewModel.playbackState.value in setOf(PlaybackState.PLAYING, PlaybackState.BUFFERING),
                "unexpected production playback state: ${viewModel.playbackState.value}",
            )

            var image: BufferedImage? = null
            val renderDeadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < renderDeadline && image == null) {
                image = viewModel.renderer.value?.render()
                Thread.sleep(30)
            }
            assertNotNull(image, "production renderer did not produce a frame")

            viewModel.seekTo(7.0)
            val seekDeadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < seekDeadline && viewModel.currentPosition.value < 6.5) {
                Thread.sleep(50)
            }
            assertTrue(
                viewModel.currentPosition.value >= 6.5,
                "production player did not reach requested seek position: ${viewModel.currentPosition.value}",
            )
        } finally {
            viewModel.shutdown()
            media.delete()
        }
    }
}
