package app.anikku.macos.player

import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode

/**
 * Minimal animated-GIF writer built on javax.imageio (pure JVM, no native
 * deps). Writes [frames] with a per-frame delay in milliseconds and an
 * infinite loop.
 */
object GifSequenceWriter {

    fun write(target: File, frames: List<BufferedImage>, delayMs: Int) {
        require(frames.isNotEmpty()) { "No frames to write" }
        val writer: ImageWriter = ImageIO.getImageWritersBySuffix("gif").next()
        FileOutputStream(target).buffered().use { raw ->
            val output = ImageIO.createImageOutputStream(raw)
            output.use {
                writer.output = output
                writer.prepareWriteSequence(null)
                try {
                    val param = writer.defaultWriteParam
                    frames.forEachIndexed { index, frame ->
                        val metadata = writer.getDefaultImageMetadata(
                            ImageTypeSpecifier.createFromRenderedImage(frame),
                            param,
                        )
                        val delay = (delayMs / 10).coerceAtLeast(1) // GIF delay is in centiseconds
                        // The loop extension belongs on the first frame only.
                        writer.writeToSequence(
                            IIOImage(frame, null, withDelay(metadata, delay, index == 0)),
                            param,
                        )
                    }
                } finally {
                    writer.endWriteSequence()
                    writer.dispose()
                }
            }
        }
    }

    private fun withDelay(metadata: IIOMetadata, delayCentis: Int, includeLoop: Boolean): IIOMetadata {
        val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
        val gce = getNode(root, "GraphicControlExtension")
        gce.setAttribute("delayTime", delayCentis.toString())
        gce.setAttribute("disposalMethod", "none")
        if (includeLoop) {
            val appExts = getNode(root, "ApplicationExtensions")
            val appExt = IIOMetadataNode("ApplicationExtension")
            appExt.setAttribute("applicationID", "NETSCAPE")
            appExt.setAttribute("authenticationCode", "2.0")
            appExt.setUserObject(byteArrayOf(1, 0, 0)) // loop forever
            appExts.appendChild(appExt)
        }
        metadata.setFromTree("javax_imageio_gif_image_1.0", root)
        return metadata
    }

    private fun getNode(root: IIOMetadataNode, name: String): IIOMetadataNode {
        for (i in 0 until root.length) {
            val item = root.item(i)
            if (item.nodeName.equals(name, ignoreCase = true)) return item as IIOMetadataNode
        }
        val node = IIOMetadataNode(name)
        root.appendChild(node)
        return node
    }
}
