package app.anikku.macos.player

import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@EnabledOnOs(OS.MAC)
class MPVHighBitratePerformanceTest {
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    fun `high bitrate playback advances seeks and stays within heap budget`() {
        assumeTrue(commandAvailable("ffmpeg"), "ffmpeg is required for high-bitrate media generation")
        assumeTrue(MPVLib.initialize(), "Bundled libmpv must be loadable")

        val directory = Files.createTempDirectory("anikku-high-bitrate-").toFile().apply { deleteOnExit() }
        val media = directory.resolve("high-bitrate.ts").apply { deleteOnExit() }
        val generator = ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=duration=12:size=1920x1080:rate=60",
            "-an", "-c:v", "mpeg2video",
            "-b:v", "25M", "-minrate", "25M", "-maxrate", "25M", "-bufsize", "50M",
            "-f", "mpegts", media.absolutePath,
        ).redirectErrorStream(true).start()
        val generatorOutput = generator.inputStream.bufferedReader().readText()
        assumeTrue(generator.waitFor() == 0, "ffmpeg could not create performance media: $generatorOutput")

        val measuredBitrate = media.length() * 8.0 / 12.0
        assertTrue(measuredBitrate >= 15_000_000, "Generated bitrate was only $measuredBitrate bits/s")

        val handle = MPVLib.create()
        assertTrue(handle != null, "mpv_create returned null")
        val runtime = Runtime.getRuntime()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        try {
            MPVLib.setOptionString(handle, "vo", "null")
            MPVLib.setOptionString(handle, "ao", "null")
            // Match production: safe copy-mode hardware decoding yields system
            // memory frames and falls back to software when unsupported.
            MPVLib.setOptionString(handle, "hwdec", "auto-copy-safe")
            MPVLib.setOptionString(handle, "cache", "yes")
            MPVLib.setOptionString(handle, "demuxer-max-bytes", "157286400")
            MPVLib.setOptionString(handle, "pause", "no")
            assertTrue(MPVLib.initialize(handle) == 0)
            assertTrue(MPVLib.command(handle, "loadfile", "file://${media.absolutePath}", "replace") == 0)

            val started = waitForPosition(handle, minimum = 0.25, timeoutMillis = 15_000)
            assertTrue(
                started >= 0.25,
                "High-bitrate playback did not advance: position=$started, duration=${MPVLib.getPropertyDouble(handle, "duration", -1.0)}, paused=${MPVLib.getPropertyFlag(handle, "pause", true)}",
            )

            for (target in listOf(3.0, 8.0, 1.0)) {
                assertTrue(MPVLib.setPropertyDouble(handle, "time-pos", target) ?: -1 >= 0)
                val actual = waitForPosition(handle, minimum = target - 0.5, timeoutMillis = 5_000)
                assertTrue(actual >= target - 0.5, "Seek to $target stopped at $actual")
            }

            val heapAfter = runtime.totalMemory() - runtime.freeMemory()
            val heapGrowth = heapAfter - heapBefore
            assertTrue(heapGrowth < 256L * 1024L * 1024L, "Playback heap grew by $heapGrowth bytes")
        } finally {
            MPVLib.destroy(handle)
        }
    }

    private fun waitForPosition(handle: Pointer?, minimum: Double, timeoutMillis: Long): Double {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var position = -1.0
        while (System.nanoTime() < deadline) {
            drainEvents(handle)
            position = MPVLib.getPropertyDouble(handle, "time-pos", -1.0)
            if (position >= minimum) break
            Thread.sleep(100)
        }
        return position
    }

    private fun drainEvents(handle: Pointer?) {
        while (true) {
            val event = MPVLib.waitEvent(handle, 0.0) ?: break
            if (event.eventId == MPVLib.MPV_EVENT_NONE) break
        }
    }

    private fun commandAvailable(command: String): Boolean = runCatching {
        val process = ProcessBuilder("which", command).redirectErrorStream(true).start()
        process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
    }.getOrDefault(false)
}
