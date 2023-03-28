package org.megras.segmentation

import org.megras.util.HashUtil
import org.tinyspline.BSpline
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt


object ImageSegmenter {

    fun toBinary(image: BufferedImage, segmentation: Segmentation) : String? = try {
        when(segmentation.type) {
            SegmentationType.RECT -> rectToBinary(image, segmentation as Rect)
            SegmentationType.POLYGON -> polygonToBinary(image, segmentation as Polygon)
            else -> TODO()
        }
    } catch (e: Exception) {
        null
    }

    fun segment(image: BufferedImage, segmentation: Segmentation) : BufferedImage? = try {
        when(segmentation.type) {
            SegmentationType.RECT -> segmentRect(image, segmentation as Rect)
            SegmentationType.POLYGON -> segmentPolygon(image, segmentation as Polygon)
            SegmentationType.SPLINE -> segmentSpline(image, segmentation as Spline)
            SegmentationType.PATH -> segmentPath(image, segmentation as Path)
            SegmentationType.MASK -> segmentMask(image, segmentation as Mask)
            SegmentationType.CHANNEL -> segmentChannel(image, segmentation as Channel)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun rectToBinary(image: BufferedImage, segmentation: Rect) : String {

        for (i in 0 until 10) {
            val y = i / image.height
            val x = i % image.width
        }
        return ""
    }

    private fun polygonToBinary(image: BufferedImage, segmentation: Polygon) : String {

        val clip = java.awt.Polygon(segmentation.vertices.map { it.first.roundToInt() }.toIntArray(), segmentation.vertices.map { it.second.roundToInt() }.toIntArray(), segmentation.vertices.size)
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics() //TODO replace clipping with mask alpha blending to get smooth edges
        g.clip = clip
        g.drawImage(image, 0, 0, null)
        g.dispose()

        val alpha = out.alphaRaster
        val mask = ByteArray(out.width * out.height)

        for (x in 0 until out.width) {
            for (y in 0 until out.height) {
                if (alpha.getSample(x, y, 0) == 0) {
                    mask[y * out.height + x] = 0
                }
                else {
                    mask[y * out.height + x] = 1
                }
            }
        }

        return HashUtil.hashToBase64(mask.inputStream(), HashUtil.HashType.MD5)
    }

    private fun segmentRect(image: BufferedImage, rect: Rect) : BufferedImage? {

        val boundRect = rect.clip(0.0, image.width.toDouble(), 0.0, image.height.toDouble())

        if (boundRect.width < 1 || boundRect.height < 1) {
            return null
        }

        val out = BufferedImage(boundRect.width.roundToInt(), boundRect.height.roundToInt(), image.type)
        val g = out.graphics
        g.drawImage(image, 0, 0, boundRect.width.roundToInt(), boundRect.height.roundToInt(), boundRect.xmin.roundToInt(), boundRect.ymin.roundToInt(), boundRect.xmax.roundToInt(), boundRect.ymax.roundToInt(), null)
        g.dispose()
        return out
    }


    private fun segmentPolygon(image: BufferedImage, polygon: Polygon): BufferedImage {

        val boundingRect = polygon.boundingRect()
        val movedPolygon = polygon.move(-boundingRect.xmin, -boundingRect.ymin)

        val clip = java.awt.Polygon(movedPolygon.vertices.map { it.first.roundToInt() }.toIntArray(), movedPolygon.vertices.map { it.second.roundToInt() }.toIntArray(), polygon.vertices.size)
        val out = BufferedImage(boundingRect.width.roundToInt(), boundingRect.height.roundToInt(), BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics() //TODO replace clipping with mask alpha blending to get smooth edges
        g.clip = clip
        g.drawImage(image, -boundingRect.xmin.roundToInt(), -boundingRect.ymin.roundToInt(), null)
        g.dispose()
        return out
    }

    private fun segmentPath(image: BufferedImage, path: Path): BufferedImage {

        val boundingRect = path.path.bounds
        val transformation = AffineTransform()
        transformation.translate(-boundingRect.minX, -boundingRect.minY)
        val movedPath = transformation.createTransformedShape(path.path)

        val out = BufferedImage(boundingRect.width, boundingRect.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.clip = movedPath
        g.drawImage(image, -boundingRect.minX.roundToInt(), -boundingRect.minY.roundToInt(), null)
        g.dispose()
        return out
    }

    private fun segmentSpline(image: BufferedImage, polygon: Spline): BufferedImage {

        var spline = BSpline(polygon.vertices.size.toLong(), 2, 3, BSpline.Type.Opened)
        spline.controlPoints = polygon.vertices.flatMap { listOf(it.first, it.second) }
        spline = spline.toBeziers()

//        val spline = BSpline.interpolateCubicNatural(polygon.vertices.flatMap { listOf(it.first, it.second) }, 2).toBeziers()

        val ctrlp = spline.controlPoints
        val order = spline.order.toInt()
        val dim = spline.dimension.toInt()
        val nBeziers = (ctrlp.size / dim) / order
        val path = Path2D.Double()
        path.moveTo(ctrlp[0], ctrlp[1])
        for (i in 0 until nBeziers) {
            path.curveTo(
                ctrlp[i * dim * order + 2], ctrlp[i * dim * order + 3],
                ctrlp[i * dim * order + 4], ctrlp[i * dim * order + 5],
                ctrlp[i * dim * order + 6], ctrlp[i * dim * order + 7]
            )
        }

        val boundingRect = path.bounds
        val transformation = AffineTransform()
        transformation.translate(-boundingRect.minX, -boundingRect.minY)
        val movedPath = transformation.createTransformedShape(path)

        val out = BufferedImage(boundingRect.width, boundingRect.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(image, -boundingRect.minX.roundToInt(), -boundingRect.minY.roundToInt(), null)
        g.color = Color.RED
        g.fill(movedPath)

        g.dispose()
        return out
    }

    private fun segmentMask(image: BufferedImage, mask: Mask): BufferedImage {

        if (image.width * image.height != mask.mask.size) {
            return image
        }

        var top = image.height / 2
        var bottom = top
        var left = image.width / 2
        var right = left

        for (i in 0 until mask.mask.size) {
            val y = i / image.height
            val x = i % image.width

            if (!mask.mask[y * image.height + x]) {
                image.setRGB(x, y, 0)
            } else {
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
        }

        val out = BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(image, -left, -top, null)
        g.dispose()

        return out
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