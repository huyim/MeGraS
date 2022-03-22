package org.megras.segmentation

import java.awt.image.BufferedImage


object ImageSegmenter {

    fun segment(image: BufferedImage, segmentation: Segmentation): BufferedImage? = when(segmentation.type) {
        SegmentationType.RECT -> segmentRect(image, segmentation as Rect)
        SegmentationType.POLYGON -> segmentPolygon(image, segmentation as Polygon)
    }

    private fun segmentRect(image: BufferedImage, rect: Rect) : BufferedImage? {

        val boundRect = rect.clip(0.0, image.width.toDouble(), 0.0, image.height.toDouble())

        if (boundRect.width < 1 || boundRect.height < 1) {
            return null
        }

        val out = BufferedImage(boundRect.width.toInt(), boundRect.height.toInt(), image.type)
        val g = out.graphics
        g.drawImage(image, 0, 0, boundRect.width.toInt(), boundRect.height.toInt(), boundRect.xmin.toInt(), boundRect.ymin.toInt(), boundRect.xmax.toInt(), boundRect.ymax.toInt(), null)
        g.dispose()
        return out
    }


    fun segmentPolygon(image: BufferedImage, polygon: Polygon): BufferedImage {

        val boundingRect = polygon.boundingRect()
        val movedPolygon = polygon.move(boundingRect.xmin, boundingRect.ymin)

        val clip = java.awt.Polygon(movedPolygon.vertices.map { it.first.toInt() }.toIntArray(), movedPolygon.vertices.map { it.first.toInt() }.toIntArray(), polygon.vertices.size)
        val out = BufferedImage(boundingRect.width.toInt(), boundingRect.height.toInt(), image.type)
        val g = out.graphics
        g.clip = clip
        g.drawImage(image, -boundingRect.xmin.toInt(), -boundingRect.ymin.toInt(), null)
        g.dispose()
        return out
    }
}