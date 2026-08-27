package app.anikku.macos.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class GifClipRecorderTest {

    @Test
    fun `downscaleArgb box-averages a 4k buffer down to the max width`() {
        val inWidth = 1920
        val inHeight = 1080
        val buffer = IntArray(inWidth * inHeight)
        for (y in 0 until inHeight) {
            for (x in 0 until inWidth) {
                buffer[y * inWidth + x] = argb(255, x % 256, y % 256, 128)
            }
        }

        val out = downscaleArgb(buffer, inWidth, inHeight, maxWidth = 480)

        assertEquals(480, out!!.width)
        assertEquals(270, out.height)
        assertEquals(BufferedImage.TYPE_INT_ARGB, out.type)
        // A box of four 4x4 blocks: x=0..3 (red 0..3), y=0..3 (green 0..3).
        val sample = out.getRGB(1, 1)
        assertEquals(128, sample and 0xFF)
        assertNull(downscaleArgb(IntArray(3), 2, 2, 480)) // too few pixels for 2x2
        assertNull(downscaleArgb(IntArray(0), 0, 0, 480))
    }

    @Test
    fun `keeps only the last maxFrames frames in chronological order`() {
        val recorder = GifClipRecorder(maxWidth = 64, fps = 6, seconds = 5)
        assertEquals(30, recorder.maxFrames)

        for (i in 1..40) {
            recorder.feedDownscaled(solidArgb(i), 64, 64)
        }

        assertEquals(30, recorder.size())
        val snap = recorder.snap()
        assertEquals(30, snap.size)
        // Chronological: the first retained frame is #11, the last is #40.
        assertEquals(argb(255, 11, 11, 11), snap.first().getRGB(0, 0))
        assertEquals(argb(255, 40, 40, 40), snap.last().getRGB(0, 0))
        // Earlier frames are gone.
        assertNull(recorder.snap().firstOrNull { it.getRGB(0, 0) == argb(255, 10, 10, 10) })
    }

    @Test
    fun `snap is empty before any frames arrive`() {
        val recorder = GifClipRecorder()
        assertEquals(0, recorder.size())
        assertEquals(emptyList<BufferedImage>(), recorder.snap())
    }

    private fun solidArgb(value: Int): IntArray {
        val buffer = IntArray(64 * 64)
        for (i in buffer.indices) buffer[i] = argb(255, value, value, value)
        return buffer
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
