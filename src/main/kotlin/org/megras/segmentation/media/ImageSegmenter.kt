package org.megras.segmentation.media

import org.megras.segmentation.type.Channel
import org.megras.segmentation.type.Hilbert
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.type.TwoDimensionalSegmentation
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min


object ImageSegmenter {

    fun segment(image: BufferedImage, segmentation: Segmentation, imageType: Int = BufferedImage.TYPE_4BYTE_ABGR): BufferedImage? {
        if (segmentation is TwoDimensionalSegmentation) return segmentShape(image, segmentation, imageType)
        if (segmentation is Hilbert) return segmentHilbert(image, segmentation, imageType)
        return null
    }

    private fun segmentShape(image: BufferedImage, segmentation: TwoDimensionalSegmentation, imageType: Int): BufferedImage? {
        return try {
            val xBounds = segmentation.getXBounds()
            val yBounds = segmentation.getYBounds()

            val transform = AffineTransform()
            transform.translate(-xBounds.low, -yBounds.low)
            val movedShape = transform.createTransformedShape(segmentation.shape)

            val out = BufferedImage((xBounds.high - xBounds.low).toInt(), (yBounds.high - yBounds.low).toInt(), imageType)
            val g = out.createGraphics()
            g.clip(movedShape)
            g.drawImage(image, -xBounds.low.toInt(), -yBounds.low.toInt(), null)
            g.dispose()

            out
        } catch (e: Exception) {
            //TODO log
            null
        }
    }

    private fun segmentHilbert(image: BufferedImage, segmentation: Hilbert, imageType: Int): BufferedImage? {
        return try {

            var top = image.height
            var bottom = 0
            var left = image.width
            var right = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (segmentation.isIncluded(x.toDouble() / image.width, y.toDouble() / image.height)) {
                        top = min(top, y)
                        bottom = max(bottom, y)
                        left = min(left, x)
                        right = max(right, x)
                    } else {
                        image.setRGB(x, y, 0)
                    }
                }
            }

            val out = BufferedImage(right - left, bottom - top, imageType)
            val g = out.createGraphics()
            g.drawImage(image, -left, -top, null)
            g.dispose()

            out
        } catch (e: Exception) {
            //TODO log
            null
        }
    }

    fun segmentChannel(image: BufferedImage, channel: Channel): BufferedImage {
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