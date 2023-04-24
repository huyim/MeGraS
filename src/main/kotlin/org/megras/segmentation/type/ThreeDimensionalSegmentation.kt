package org.megras.segmentation.type

import de.sciss.shapeint.ShapeInterpolator
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.awt.Shape
import java.awt.geom.Area

interface ThreeDimensionalSegmentation : Segmentation
class Plane(val a: Double, val b: Double, val c: Double, val d: Double, val above: Boolean) :
    ThreeDimensionalSegmentation {
    override val segmentationType = SegmentationType.PLANE
    override var segmentationClass = SegmentationClass.SPACE

    override fun equivalentTo(rhs: Segmentation): Boolean {
        return rhs is Plane && this == rhs
    }

    override fun contains(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun intersects(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }
}

class Rotoscope(var rotoscopeList: List<RotoscopePair>) : ThreeDimensionalSegmentation, Translatable {
    override val segmentationType = SegmentationType.ROTOSCOPE
    override val segmentationClass = SegmentationClass.SPACETIME

    init {
        require(rotoscopeList.size >= 2) {
            throw IllegalArgumentException("Need at least two transition points.")
        }

        val timePoints = rotoscopeList.map { it.time }
        val sortedTimePoints = timePoints.sorted()
        require(timePoints == sortedTimePoints) {
            throw IllegalArgumentException("Need input sorted by increasing time points")
        }
    }

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs is Rotoscope) {
            // First and last times need to match
            val ft1 = this.rotoscopeList.first().time
            val ft2 = rhs.rotoscopeList.first().time
            val lt1 = this.rotoscopeList.last().time
            val lt2 = rhs.rotoscopeList.last().time
            if (ft1 != ft2 || lt1 != lt2) {
                return false
            }
            for (i in ft1.toInt() .. lt1.toInt()) {
                val shape1 = this.interpolate(i.toDouble())
                val shape2 = rhs.interpolate(i.toDouble())
                if ((shape1 != shape2) ||
                    (shape1 != null && shape2 != null && !shape1.equivalentTo(shape2))) {
                    return false
                }
            }
            return true
        }
        TODO("comparison to space filling curve")
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs is Rotoscope) {
            // Times need to be contained
            val ft1 = this.rotoscopeList.first().time
            val ft2 = rhs.rotoscopeList.first().time
            val lt1 = this.rotoscopeList.last().time
            val lt2 = rhs.rotoscopeList.last().time
            if (ft1 > ft2 || lt1 < lt2) {
                return false
            }
            for (i in ft2.toInt() .. lt2.toInt()) {
                val shape1 = this.interpolate(i.toDouble())
                val shape2 = rhs.interpolate(i.toDouble())
                if (shape1 != null && shape2 != null && !shape1.contains(shape2)) {
                    return false
                }
            }
            return true
        }
        TODO("comparison to space filling curve")
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs is Rotoscope) {
            // Times need to overlap
            val ft1 = this.rotoscopeList.first().time
            val ft2 = rhs.rotoscopeList.first().time
            val lt1 = this.rotoscopeList.last().time
            val lt2 = rhs.rotoscopeList.last().time
            if (ft2 > lt1 || ft1 > lt2) {
                return false
            }
            for (i in ft1.toInt() .. lt1.toInt()) {
                val shape1 = this.interpolate(i.toDouble())
                val shape2 = rhs.interpolate(i.toDouble())
                if (shape1 != null && shape2 != null && shape1.intersects(shape2)) {
                    return true
                }
            }
            return false
        }
        TODO("comparison to space filling curve")
    }

    override fun translate(by: Segmentation) {
        if (by is Rotoscope) {
            val shift = by.rotoscopeList[0].time
            rotoscopeList = rotoscopeList.map { RotoscopePair(it.time + shift, it.space) }
        }
    }

    override fun toString(): String = "segment/rotoscope/" + rotoscopeList.joinToString(";") {
        val shapeString = it.space.toString().removePrefix("segment/").replace("/", ",")
        "${it.time},${shapeString})"
    }

    fun interpolate(time: Double): TwoDimensionalSegmentation? {
        if (time < rotoscopeList.first().time || time > rotoscopeList.last().time) return null

        var endIndex = rotoscopeList.indexOfFirst { it.time > time }
        if (endIndex == -1 && rotoscopeList.last().time == time) {
            endIndex = rotoscopeList.size - 1
        }
        val (endFrame, endShape) = rotoscopeList[endIndex]

        val startIndex = endIndex - 1
        val (startFrame, startShape) = rotoscopeList[startIndex]

        val t = (time - startFrame) / (endFrame - startFrame)
        val newShape = ShapeInterpolator().evaluate(startShape.shape, endShape.shape, t.toFloat())

        return object: TwoDimensionalSegmentation() {
            override var shape: Shape = newShape
            override var area: Area = Area(shape)
            override val segmentationType = null
            override fun toString(): String = ""
        }
    }
}

data class RotoscopePair(val time: Double, val space: TwoDimensionalSegmentation)