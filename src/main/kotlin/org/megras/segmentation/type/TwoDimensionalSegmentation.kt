package org.megras.segmentation.type

import org.apache.batik.parser.AWTPathProducer
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import org.megras.util.extensions.equalsEpsilon
import org.tinyspline.BSpline
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.io.StringReader
import kotlin.math.roundToInt

abstract class TwoDimensionalSegmentation : Segmentation, Translatable {
    abstract var shape: Shape
    abstract var area: Area

    override val segmentationClass: SegmentationClass
        get() = SegmentationClass.SPACE

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is TwoDimensionalSegmentation) return false
        return this.area == rhs.area
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is TwoDimensionalSegmentation) return false
        rhs.area.subtract(this.area)
        return rhs.area.isEmpty
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is TwoDimensionalSegmentation) return false
        this.area.intersect(rhs.area)
        return this.area.isEmpty
    }

    override fun translate(by: Segmentation) {
        if (by is TwoDimensionalSegmentation) {
            val transform = AffineTransform()
            transform.translate(by.area.bounds.minX, by.area.bounds.minY)
            this.shape = transform.createTransformedShape(shape)
        }
    }
}

class Rect(val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double) : TwoDimensionalSegmentation() {

    override val segmentationType: SegmentationType = SegmentationType.RECT
    val width: Double = xmax - xmin
    val height: Double = ymax - ymin
    override var shape: Shape = Rectangle2D.Double(xmin, ymin, width, height)
    override var area: Area = Area(shape)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Rect

        if (!xmin.equalsEpsilon(other.xmin)) return false
        if (!xmax.equalsEpsilon(other.xmax)) return false
        if (!ymin.equalsEpsilon(other.ymin)) return false
        if (!ymax.equalsEpsilon(other.ymax)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = xmin.hashCode()
        result = 31 * result + xmax.hashCode()
        result = 31 * result + ymin.hashCode()
        result = 31 * result + ymax.hashCode()
        return result
    }

    override fun toString(): String = "segment/rect/" + "$xmin,$xmax,$ymin,$ymax"
}

class Polygon(val points: List<Pair<Double, Double>>) : TwoDimensionalSegmentation() {

    override val segmentationType: SegmentationType = SegmentationType.POLYGON
    override var shape: Shape = java.awt.Polygon(
        points.map { it.first.roundToInt() }.toIntArray(),
        points.map { it.second.roundToInt() }.toIntArray(),
        points.size
    )
    override var area: Area = Area(shape)

    init {
        require(points.size > 2) {
            throw IllegalArgumentException("A polygon needs at least 3 vertices")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Polygon

        if (other.points.size != points.size) return false

        val start = points.indexOfFirst { it.equalsEpsilon(other.points.first()) }

        if (start == -1) return false

        return points.indices.all { other.points[it].equalsEpsilon(points[(it + start) % points.size]) }
    }

    override fun hashCode(): Int {
        return points.sortedBy { it.first }.sortedBy { it.second }.hashCode()
    }

    override fun toString(): String = "segment/polygon/" + points.joinToString(",") { "(${it.first},${it.second})" }
}

class SVGPath(path: String) : TwoDimensionalSegmentation() {

    override val segmentationType: SegmentationType = SegmentationType.PATH

    override var shape: Shape = AWTPathProducer.createShape(StringReader(path), 0)
    override var area: Area = Area(shape)

    override fun toString(): String = TODO()
}

class BezierSpline(val points: List<Pair<Double, Double>>) : TwoDimensionalSegmentation() {
    override val segmentationType: SegmentationType = SegmentationType.BEZIER
    override lateinit var shape: Shape
    override lateinit var area: Area

    init {
        val flattenedControlPoints = points.flatMap { listOf(it.first, it.second) }
        val nBeziers = (points.size - 1) / 3

        val path = Path2D.Double()
        path.moveTo(flattenedControlPoints[0], flattenedControlPoints[1])
        for (i in 0 until nBeziers) {
            path.curveTo(
                flattenedControlPoints[i * 6 + 2], flattenedControlPoints[i * 6 + 3],
                flattenedControlPoints[i * 6 + 4], flattenedControlPoints[i * 6 + 5],
                flattenedControlPoints[i * 6 + 6], flattenedControlPoints[i * 6 + 7]
            )
        }

        shape = path
        area = Area(shape)
    }

    override fun toString(): String = "segment/bezier/" + points.joinToString(",") { "(${it.first},${it.second})" }
}

class BSpline(val points: List<Pair<Double, Double>>) : TwoDimensionalSegmentation() {
    override val segmentationType: SegmentationType = SegmentationType.BSPLINE
    override lateinit var shape: Shape
    override lateinit var area: Area

    init {
        val degree: Long = 3

        // To close B-Splines, one can wrap p control points, where p is the curve degree
        // for details, see https://pages.mtu.edu/~shene/COURSES/cs3621/NOTES/spline/B-spline/bspline-curve-closed.html
        var spline = BSpline(points.size.toLong() + degree, 2, degree, BSpline.Type.Opened)
        val controlPointList = points.flatMap { listOf(it.first, it.second) }
        val repeatPoints = points.subList(0, 3).flatMap { listOf(it.first, it.second) }
        val closedPoints = controlPointList.plus(repeatPoints)
        spline.controlPoints = closedPoints
        spline = spline.toBeziers()

        val controlPoints = spline.controlPoints
        val nBeziers = controlPoints.size / spline.order.toInt() / spline.dimension.toInt()
        val pointsPerBezier = controlPoints.size / nBeziers

        val path = Path2D.Double()
        path.moveTo(controlPoints[0], controlPoints[1])
        for (i in 0 until nBeziers) {
            path.curveTo(
                controlPoints[i * pointsPerBezier + 2], controlPoints[i * pointsPerBezier + 3],
                controlPoints[i * pointsPerBezier + 4], controlPoints[i * pointsPerBezier + 5],
                controlPoints[i * pointsPerBezier + 6], controlPoints[i * pointsPerBezier + 7]
            )
        }

        shape = path
        area = Area(shape)
    }

    override fun toString(): String = "segment/bspline/" + points.joinToString(",") { "(${it.first},${it.second})" }
}