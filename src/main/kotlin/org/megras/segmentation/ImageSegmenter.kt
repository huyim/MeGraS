package org.megras.segmentation

import java.awt.Color
import java.awt.Shape
import java.awt.image.BufferedImage


object ImageSegmenter {

    fun toBinary(image: BufferedImage, segmentation: Segmentation): ByteArray? = try {
        when (segmentation.type) {
            SegmentationType.RECT,
            SegmentationType.POLYGON,
            SegmentationType.PATH,
            SegmentationType.BEZIER,
            SegmentationType.BSPLINE -> generateMaskFromShape(image, (segmentation as SpaceSegmentation).shape)
            SegmentationType.HILBERT -> generateMaskFromHilbert(image, segmentation as Hilbert)
            SegmentationType.MASK -> (segmentation as Mask).mask
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun toBinary(image: BufferedImage, segmentation: Segmentation, relativeFrame: Double): ByteArray? = try {
        when (segmentation.type) {
            SegmentationType.HILBERT -> generateMaskFromHilbert(image, segmentation as Hilbert, relativeFrame)
            else -> toBinary(image, segmentation)
        }
    } catch (e: Exception) {
        null
    }

    fun segment(image: BufferedImage, mask: ByteArray, imageType: Int = BufferedImage.TYPE_INT_ARGB): BufferedImage? {
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

    private fun generateMaskFromShape(inputImage: BufferedImage, clippingShape: Shape) : ByteArray {
        val segmentedImage = BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_INT_ARGB)
        val g = segmentedImage.createGraphics() //TODO replace clipping with mask alpha blending to get smooth edges
        g.clip = clippingShape
        g.drawImage(inputImage, 0, 0, null)
        g.dispose()

        val alpha = segmentedImage.alphaRaster
        val mask = ByteArray(segmentedImage.width * segmentedImage.height)

        for (y in 0 until segmentedImage.height) {
            for (x in 0 until segmentedImage.width) {
                if (alpha.getSample(x, y, 0) == 0) {
                    mask[y * segmentedImage.width + x] = 0
                }
                else {
                    mask[y * segmentedImage.width + x] = 1
                }
            }
        }
        return mask
    }

    fun generateMaskFromHilbert(image: BufferedImage, hilbert: Hilbert, relativeFrame: Double = -1.0): ByteArray? {
        val mask = ByteArray(image.width * image.height)

        var isRelevant = false
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {

                val isIncluded = if (relativeFrame == -1.0) {
                    hilbert.isIncluded(x.toDouble() / image.width, y.toDouble() / image.height)
                } else {
                    hilbert.isIncluded(x.toDouble() / image.width, y.toDouble() / image.height, relativeFrame)
                }

                if (isIncluded) {
                    isRelevant = true
                    mask[y * image.width + x] = 1
                } else {
                    mask[y * image.width + x] = 0
                }
            }
        }
        return if (isRelevant) {
            mask
        } else {
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