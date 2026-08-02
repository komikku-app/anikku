package app.anikku.macos.player

import com.sun.jna.Pointer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MPV Playback Test — verifies actual video file playback via MPVLib.
 *
 * Requires a test video at /tmp/anikku_test/test_video.mp4.
 * Generate with:
 *   ffmpeg -f lavfi -i testsrc=duration=10:size=320x240:rate=1 \
 *          -pix_fmt yuv420p /tmp/anikku_test/test_video.mp4 -y
 *
 * Run: ./macos/gradlew -p macos test --tests "app.anikku.macos.player.MPVPlaybackTest"
 */
@EnabledOnOs(OS.MAC)
class MPVPlaybackTest {

    private val testVideoPath = "/tmp/anikku_test/test_video.mp4"

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `playback - local mp4 file plays and advances`() {
        println("=".repeat(60))
        println("MPV PLAYBACK TEST")
        println("=".repeat(60))

        // ── Step 1: Verify test video exists ───────────────────────────────
        val testFile = File(testVideoPath)
        assumeTrue(testFile.exists(), "Test video not found at $testVideoPath")
        println("[1] Test video found: ${testFile.absolutePath} (${testFile.length()} bytes)")

        // ── Step 2: Load libmpv ─────────────────────────────────────────────
        println("[2] Initializing MPVLib...")
        val loaded = MPVLib.initialize()
        assumeTrue(loaded, "libmpv not loadable — install with: brew install mpv")
        println("  ✅ libmpv loaded (version: ${MPVLib.getVersion()})")

        // ── Step 3: Create and configure mpv handle ────────────────────────
        println("[3] Creating mpv handle...")
        val handle = MPVLib.create()
        assertTrue(handle != null, "mpv_create() returned null")
        println("  ✅ handle: $handle")

        try {
            // Use dummy video output for a headless test, otherwise mpv blocks
            // waiting for a render context when vo=libmpv is set.
            MPVLib.setOptionString(handle, "vo", "null")
            MPVLib.setOptionString(handle, "cache", "yes")
            MPVLib.setOptionString(handle, "cache-secs", "10")
            MPVLib.setOptionString(handle, "osd-level", "0")
            MPVLib.setOptionString(handle, "keep-open", "yes")
            MPVLib.setOptionString(handle, "msg-level", "all=v")
            MPVLib.setOptionString(handle, "pause", "no")
            println("  Options set OK")

            val initResult = MPVLib.initialize(handle)
            assertTrue(initResult != null && initResult == 0, "mpv_initialize failed with code: $initResult")
            println("  ✅ mpv initialized")

            // ── Step 4: Load local video file ──────────────────────────────────
            println("[4] Loading local video file...")
            val fileUrl = "file://${testFile.absolutePath}"
            val loadResult = MPVLib.command(handle, "loadfile", fileUrl, "replace")
            println("  loadfile: $loadResult (0=success)")
            assertTrue(loadResult == 0, "loadfile command failed with code: $loadResult")

            // ── Step 5: Wait for playback to start and advance ─────────────────
            println("[5] Waiting for playback to start...")
            var timePos = 0.0
            var attempts = 0
            val maxAttempts = 30
            while (timePos <= 0.0 && attempts < maxAttempts) {
                Thread.sleep(500)

                drainEvents(handle)

                timePos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
                attempts++
                println("    attempt $attempts: time-pos = $timePos")
            }

            val duration = MPVLib.getPropertyDouble(handle, "duration", 0.0)
            println("  ✅ Playback started, time-pos = $timePos, duration = $duration after ${attempts * 500}ms")
            assertTrue(duration > 0.0, "File was not demuxed/decoded — duration remained 0")
            assertTrue(timePos > 0.0, "Video did not start playing — time-pos remained 0")

            // ── Step 6: Verify playback advances ──────────────────────────────
            println("[6] Verifying playback advances...")
            Thread.sleep(2000)
            drainEvents(handle)

            val laterTimePos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
            println("  time-pos after 2s: $laterTimePos")
            assertTrue(laterTimePos > timePos, "Playback did not advance — time-pos did not increase")

            // ── Step 7: Verify the same absolute seek operation used by the UI ──
            println("[7] Seeking to 7 seconds...")
            val seekResult = MPVLib.setPropertyDouble(handle, "time-pos", 7.0)
            assertTrue(seekResult != null && seekResult >= 0, "Absolute seek command failed: $seekResult")
            var seekedTimePos = 0.0
            attempts = 0
            while (seekedTimePos < 6.5 && attempts < 20) {
                Thread.sleep(100)
                drainEvents(handle)
                seekedTimePos = MPVLib.getPropertyDouble(handle, "time-pos", 0.0)
                attempts++
            }
            println("  time-pos after seek: $seekedTimePos")
            assertTrue(seekedTimePos >= 6.5, "Playback did not seek to the requested position")

            println()
            println("  ✅✅✅ LOCAL VIDEO PLAYBACK AND SEEK WORK!")
            println("  MPVLib can load, play, advance, and seek local MP4 files on macOS.")

        } finally {
            println()
            println("[cleanup] Destroying mpv handle...")
            MPVLib.destroy(handle)
            println("  ✅ mpv handle destroyed")
        }

        println()
        println("=".repeat(60))
        println("PLAYBACK TEST COMPLETE")
        println("=".repeat(60))
    }

    /**
     * Drain mpv's event queue to prevent it from filling up and deadlocking
     * the player while we poll for playback state.
     */
    private fun drainEvents(handle: Pointer?) {
        while (true) {
            val ev = MPVLib.waitEvent(handle, 0.0) ?: break
            if (ev.eventId == MPVLib.MPV_EVENT_NONE) break
        }
    }
}
