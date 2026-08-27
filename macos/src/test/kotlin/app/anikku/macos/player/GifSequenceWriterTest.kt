package app.anikku.macos.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GifSequenceWriterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `writes an animated gif with the expected frame count and dimensions`() {
        val frames = listOf(
            solidFrame(64, 64, Color.RED),
            solidFrame(64, 64, Color.GREEN),
            solidFrame(64, 64, Color.BLUE),
        )
        val target = File(tempDir, "clip.gif")

        GifSequenceWriter.write(target, frames, delayMs = 167)

        assertTrue(target.isFile)
        assertTrue(target.length() > 0)
        val header = target.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", header)

        val reader = ImageIO.getImageReadersBySuffix("gif").next()
        reader.input = ImageIO.createImageInputStream(target)
        try {
            assertEquals(3, reader.getNumImages(true))
            val first = reader.read(0)
            assertEquals(64, first.width)
            assertEquals(64, first.height)
        } finally {
            reader.dispose()
        }
    }

    @Test
    fun `refuses to write an empty frame list`() {
        val target = File(tempDir, "empty.gif")
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            GifSequenceWriter.write(target, emptyList(), delayMs = 100)
        }
        assertTrue(!target.exists())
    }

    private fun solidFrame(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val rgb = color.rgb
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, rgb)
            }
        }
        return image
    }
}
