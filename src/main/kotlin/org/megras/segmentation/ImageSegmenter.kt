package org.megras.segmentation

import java.awt.image.BufferedImage
import kotlin.math.roundToInt


object ImageSegmenter {

    fun segment(image: BufferedImage, segmentation: Segmentation): BufferedImage? = try {
        when(segmentation.type) {
            SegmentationType.RECT -> segmentRect(image, segmentation as Rect)
            SegmentationType.POLYGON -> segmentPolygon(image, segmentation as Polygon)
        }
    } catch (e: Exception) {
        //TODO log
        null
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
}