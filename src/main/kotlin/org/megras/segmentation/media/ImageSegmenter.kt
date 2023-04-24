package org.megras.segmentation.media

import org.megras.segmentation.*
import org.megras.segmentation.type.*
import java.awt.Color
import java.awt.Shape
import java.awt.image.BufferedImage
import java.util.*


object ImageSegmenter {

    fun toBinary(image: BufferedImage, segmentation: Segmentation): BitSet? = try {
        when (segmentation.segmentationType) {
            SegmentationType.RECT,
            SegmentationType.POLYGON,
            SegmentationType.PATH,
            SegmentationType.BEZIER,
            SegmentationType.BSPLINE,
            null -> generateMaskFromShape(image, (segmentation as TwoDimensionalSegmentation).shape)
            SegmentationType.HILBERT -> generateMaskFromHilbert(image, segmentation as Hilbert)
            SegmentationType.MASK -> (segmentation as Mask).mask
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun segment(image: BufferedImage, mask: BitSet, imageType: Int = BufferedImage.TYPE_4BYTE_ABGR): BufferedImage? {
        try {
            if (image.width * image.height != mask.size()) {
                return null
            }

            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (!mask[y * image.width + x]) {
                        image.setRGB(x, y, 0)
                    }
                }
            }

            val out = BufferedImage(image.width, image.height, imageType)
            val g = out.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()

            return out
        } catch (e: Exception) {
            //TODO log
            return null
        }
    }

    fun segmentAndCut(image: BufferedImage, mask: ByteArray, imageType: Int = BufferedImage.TYPE_4BYTE_ABGR): BufferedImage? {
        try {
            if (image.width * image.height != mask.size) {
                return null
            }

            var top = image.height
            var bottom = 0
            var left = image.width
            var right = 0

            for (y in 0 until image.height) {
                for (x in 0 until image.width) {

                    if (mask[y * image.width + x].compareTo(1) == 0) {
                        top = Math.min(top, y)
                        bottom = Math.max(bottom, y)
                        left = Math.min(left, x)
                        right = Math.max(right, x)
                    } else {
                        image.setRGB(x, y, 0)
                    }
                }
            }

            val out = BufferedImage(right - left, bottom - top, imageType)
            val g = out.createGraphics()
            g.drawImage(image, -left, -top, null)
            g.dispose()

            return out
        } catch (e: Exception) {
            //TODO log
            return null
        }
    }

    private fun generateMaskFromShape(inputImage: BufferedImage, clippingShape: Shape) : BitSet {
        val segmentedImage = BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_INT_ARGB)
        val g = segmentedImage.createGraphics() //TODO replace clipping with mask alpha blending to get smooth edges
        g.clip = clippingShape
        g.drawImage(inputImage, 0, 0, null)
        g.dispose()

        val alpha = segmentedImage.alphaRaster
        val mask = BitSet(segmentedImage.width * segmentedImage.height)

        for (y in 0 until segmentedImage.height) {
            for (x in 0 until segmentedImage.width) {
                if (alpha.getSample(x, y, 0) > 0) {
                    mask.set(y * segmentedImage.width + x)
                }
            }
        }
        return mask
    }

    fun generateMaskFromHilbert(image: BufferedImage, hilbert: Hilbert): BitSet {
        val mask = BitSet(image.width * image.height)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (hilbert.isIncluded(x.toDouble() / image.width, y.toDouble() / image.height)) {
                    mask.set(y * image.width + x)
                }
            }
        }
        return mask
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