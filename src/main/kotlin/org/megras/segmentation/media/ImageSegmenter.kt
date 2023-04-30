package org.megras.segmentation.media

import org.megras.segmentation.type.Channel
import org.megras.segmentation.type.Hilbert
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.type.TwoDimensionalSegmentation
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO


object ImageSegmenter {

    fun segment(inputStream: InputStream, segmentation: Segmentation, imageType: Int = BufferedImage.TYPE_4BYTE_ABGR): ByteArray? {
        val image = ImageIO.read(inputStream)
        val segmentedImage = segment(image, segmentation, imageType)

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(segmentedImage, "PNG", outputStream)

        return outputStream.toByteArray()
    }

    fun segment(image: BufferedImage, segmentation: Segmentation, imageType: Int = BufferedImage.TYPE_4BYTE_ABGR): BufferedImage? =
        when (segmentation) {
            is TwoDimensionalSegmentation -> segmentShape(image, segmentation, imageType)
            is Hilbert -> segmentHilbert(image, segmentation, imageType)
            is Channel -> segmentChannel(image, segmentation)
            else -> null
        }

    private fun segmentShape(image: BufferedImage, segmentation: TwoDimensionalSegmentation, imageType: Int): BufferedImage? {
        return try {
            val xBounds = segmentation.bounds.getXBounds()
            val yBounds = segmentation.bounds.getYBounds()

            val transform = AffineTransform()
            transform.translate(-xBounds[0], -yBounds[0])
            val movedShape = transform.createTransformedShape(segmentation.shape)

            val out = BufferedImage((xBounds[1] - xBounds[0]).toInt(), (yBounds[1] - yBounds[0]).toInt(), imageType)
            val g = out.createGraphics()
            g.clip(movedShape)
            g.drawImage(image, -xBounds[0].toInt(), -yBounds[0].toInt(), null)
            g.dispose()

            out
        } catch (e: Exception) {
            //TODO log
            null
        }
    }

    private fun segmentHilbert(image: BufferedImage, segmentation: Hilbert, imageType: Int): BufferedImage? {
        val mask = segmentation.toImageMask(image.width, image.height)
        return segmentShape(image, mask, imageType)
    }

    private fun segmentChannel(image: BufferedImage, channel: Channel): BufferedImage {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val rgb = image.getRGB(x, y)
                var red = rgb and 0x00ff0000 shr 16
                var green = rgb and 0x0000ff00 shr 8
                var blue = rgb and 0x000000ff

                if (!channel.selection.contains("red")) red = 0
                if (!channel.selection.contains("green")) green = 0
                if (!channel.selection.contains("blue")) blue = 0

                image.setRGB(x, y, Color(red, green, blue).rgb)
            }
        }
        return image
    }
}