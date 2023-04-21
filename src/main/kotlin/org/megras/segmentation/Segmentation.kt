package org.megras.segmentation

import org.apache.batik.parser.AWTPathProducer
import org.davidmoten.hilbert.HilbertCurve
import org.megras.util.extensions.equalsEpsilon
import org.tinyspline.BSpline
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.io.StringReader
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong


sealed interface Segmentation {
    val type: SegmentationType
    val segmentClass: SegmentationClass

    fun equivalentTo(rhs: Segmentation): Boolean
    fun contains(rhs: Segmentation): Boolean
    fun intersects(rhs: Segmentation): Boolean
}

interface Translatable {
    fun translate(by: Segmentation)
}

abstract class ShapeSegmentation : Segmentation, Translatable {
    abstract var shape: Shape
    abstract var area: Area

    override val segmentClass: SegmentationClass
        get() = SegmentationClass.SPACE

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is ShapeSegmentation) return false
        return this.area == rhs.area
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is ShapeSegmentation) return false
        rhs.area.subtract(this.area)
        return rhs.area.isEmpty
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is ShapeSegmentation) return false
        this.area.intersect(rhs.area)
        return this.area.isEmpty
    }

    override fun translate(by: Segmentation) {
        if (by is ShapeSegmentation) {
            val transform = AffineTransform()
            transform.translate(by.area.bounds.minX, by.area.bounds.minY)
            this.shape = transform.createTransformedShape(shape)
        }
    }
}

data class Interval<T>(val low: T, val high: T)

abstract class LineSegmentation : Segmentation {
    abstract var intervals: List<Interval<Long>>
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.TIME

    override fun equivalentTo(rhs: Segmentation): Boolean {
        return this.contains(rhs) && rhs.contains(this)
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is LineSegmentation) return false

        // All rhs intervals are contained in some intervals
        return rhs.intervals.all { j ->
            this.intervals.any { i -> i.low <= j.low && j.high <= i.high }
        }
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is LineSegmentation) return false

        // Any two intervals overlap
        return this.intervals.any { i ->
            rhs.intervals.any { j -> i.low <= j.high && j.low <= i.high }
        }
    }
}

interface ReduceSegmentation : Segmentation {
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.REDUCE
}

interface SpaceSegmentation : Segmentation {
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.SPACETIME
}

class Rect(val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double) : ShapeSegmentation() {

    override val type: SegmentationType = SegmentationType.RECT
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

class Polygon(val points: List<Pair<Double, Double>>) : ShapeSegmentation() {

    override val type: SegmentationType = SegmentationType.POLYGON
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

class SVGPath(path: String) : ShapeSegmentation() {

    override val type: SegmentationType = SegmentationType.PATH
    override var shape: Shape = AWTPathProducer.createShape(StringReader(path), 0)
    override var area: Area = Area(shape)
}

class BezierSpline(val points: List<Pair<Double, Double>>) : ShapeSegmentation() {
    override val type: SegmentationType = SegmentationType.BEZIER
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

class BSpline(val points: List<Pair<Double, Double>>) : ShapeSegmentation() {
    override val type: SegmentationType = SegmentationType.BSPLINE
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

class Mask(val mask: BitSet) : Segmentation {
    override val type: SegmentationType = SegmentationType.MASK
    override val segmentClass: SegmentationClass = SegmentationClass.SPACE
    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        return this.mask == rhs.mask
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        if (this.mask.size() != rhs.mask.size()) return false

        for (i in 0 until this.mask.size()) {
            if (rhs.mask[i] && !this.mask[i]) return false
        }
        return true
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        return this.mask.intersects(rhs.mask)
    }
}

class Hilbert(dimensions: Int, order: Int, override var intervals: List<Interval<Long>>) : LineSegmentation() {
    override val type: SegmentationType = SegmentationType.HILBERT
    override val segmentClass: SegmentationClass
    private val hilbertCurve = HilbertCurve.small().bits(order).dimensions(dimensions)
    private val dimensionSize = (2.0).pow(order) - 1

    var relativeTimestamp: Double? = null
        set(value) {
            field = value
        }

    init {
        segmentClass = when (dimensions) {
            2 -> SegmentationClass.SPACE
            3 -> SegmentationClass.SPACETIME
            else -> throw IllegalArgumentException("Dimension not supported.")
        }

        require(intervals.all { it.low <= it.high }) {
            throw IllegalArgumentException("Ranges are not valid.")
        }

        require(intervals.all { it.high <= hilbertCurve.maxIndex() }) {
            throw IllegalArgumentException("Range is out of bounds.")
        }
    }

    fun isIncluded(vararg relativeCoords: Double): Boolean {

        // Translate to hilbert space
        val hilbertCoords = relativeCoords.map { (it * dimensionSize).roundToLong() }.toMutableList()

        if (relativeTimestamp != null) {
            hilbertCoords.add((relativeTimestamp!! * dimensionSize).roundToLong())
        }

        val hilbertIndex = hilbertCurve.index(*hilbertCoords.toLongArray())
        val found = intervals.find { i -> i.low <= hilbertIndex && hilbertIndex <= i.high }

        return found != null
    }
}

class Channel(val selection: List<String>) : ReduceSegmentation {
    override val type: SegmentationType = SegmentationType.CHANNEL

    override fun equivalentTo(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Channel) return false
        return rhs.selection.all { this.selection.contains(it) }
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Channel) return false

        return this.selection.intersect(rhs.selection.toSet()).isNotEmpty()
    }
}

class Frequency(val low: Int, val high: Int) : ReduceSegmentation {
    override val type: SegmentationType = SegmentationType.FREQUENCY

    init {
        require(low <= high) {
            throw IllegalArgumentException("Frequency band is not valid.")
        }
    }

    override fun equivalentTo(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false

        return this.high >= rhs.low && this.low <= rhs.high
    }
}

class Time(override var intervals: List<Interval<Long>>) : LineSegmentation(), Translatable {
    override val type: SegmentationType = SegmentationType.TIME

    override fun translate(by: Segmentation) {
        if (by is Time) {
            Time(intervals.map { Interval(it.low + by.intervals[0].low, it.high + by.intervals[0].low) })
        }
    }

    fun getIntervalsToDiscard(): List<Interval<Long>> {
        val newIntervals = mutableListOf<Interval<Long>>()

        for (i in 0 until intervals.size - 1) {
            newIntervals.add(Interval(intervals[i].high, intervals[i + 1].low))
        }
        return newIntervals
    }

    fun getTimePointsToDiscard(start: Long, end: Long): List<Long> {
        val discard = getIntervalsToDiscard()

        val res = mutableListOf<Long>()
        (start until intervals[0].low).forEach { i -> res.add(i) }
        discard.forEach { d -> (d.low + 1 until d.high).forEach { i -> res.add(i) } }
        (intervals.last().high + 1 until end).forEach { i -> res.add(i) }

        return res
    }

    override fun toString(): String = "segment/time/" + intervals.joinToString(",") { "${it.low},${it.high}" }
}

class Plane(val a: Double, val b: Double, val c: Double, val d: Double, val above: Boolean) : Segmentation {
    override val type: SegmentationType = SegmentationType.PLANE
    override var segmentClass: SegmentationClass = SegmentationClass.SPACE

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

data class RotoscopePair(val time: Double, val points: List<Pair<Double, Double>>)

class Rotoscope(segmentationType: SegmentationType, val rotoscopeList: List<RotoscopePair>) : SpaceSegmentation {
    override val type: SegmentationType = segmentationType
    override val segmentClass: SegmentationClass = SegmentationClass.SPACETIME

    init {
        require(rotoscopeList.size >= 2) {
            throw IllegalArgumentException("Need at least two transition points.")
        }

        val initialSize = rotoscopeList.first().points.size
        require(rotoscopeList.all { it.points.size == initialSize }) {
            throw IllegalArgumentException("Need same amount of points for each shape.")
        }

        val timePoints = rotoscopeList.map { it.time }
        val sortedTimePoints = timePoints.sorted()
        require(timePoints == sortedTimePoints) {
            throw IllegalArgumentException("Need input sorted by increasing time points")
        }
    }

    override fun equivalentTo(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun intersects(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    fun interpolate(time: Double): ShapeSegmentation? {
        if (time < rotoscopeList.first().time || time > rotoscopeList.last().time) return null

        var endIndex = rotoscopeList.indexOfFirst { it.time > time }
        if (endIndex == -1 && rotoscopeList.last().time == time) {
            endIndex = rotoscopeList.size - 1
        }
        val (endFrame, endPoints) = rotoscopeList[endIndex]

        val startIndex = endIndex - 1
        val (startFrame, startPoints) = rotoscopeList[startIndex]

        val t = (time - startFrame) / (endFrame - startFrame)

        val newPoints = mutableListOf<Pair<Double, Double>>()

        for (v in startPoints.indices) {
            val sp = startPoints[v]
            val ep = endPoints[v]

            val x = sp.first + t * (ep.first - sp.first)
            val y = sp.second + t * (ep.second - sp.second)

            newPoints.add(x to y)
        }
        return when (type) {
            SegmentationType.ROTOPOLYGON -> Polygon(newPoints)
            SegmentationType.ROTOBEZIER -> BezierSpline(newPoints)
            SegmentationType.ROTOBSPLINE -> BSpline(newPoints)
            else -> null
        }
    }
}