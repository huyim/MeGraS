package org.megras.segmentation

import org.apache.batik.parser.AWTPathProducer
import org.davidmoten.hilbert.HilbertCurve
import org.davidmoten.hilbert.SmallHilbertCurve
import org.megras.util.extensions.equalsEpsilon
import org.tinyspline.BSpline
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.io.StringReader
import java.lang.Double.max
import java.lang.Double.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong


sealed interface Segmentation {
    val type: SegmentationType
    val segmentClass: SegmentationClass
}

abstract class SpaceSegmentation : Segmentation {
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.SPACE

    lateinit var shape: Shape

    lateinit var area: Area

    fun move(dx: Double, dy: Double): SpaceSegmentation {
        val transform = AffineTransform()
        transform.translate(dx, dy)
        this.shape = transform.createTransformedShape(shape)
        return this
    }
}

abstract class TimeSegmentation : Segmentation {
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.TIME

    abstract fun intersect(rhs: TimeSegmentation): Boolean
}

abstract class ReduceSegmentation : Segmentation {
    override val segmentClass: SegmentationClass
        get() = SegmentationClass.REDUCE

    abstract fun intersect(rhs: ReduceSegmentation): Boolean
}

class Rect(
    val xmin: Double,
    val xmax: Double,
    val ymin: Double,
    val ymax: Double,
    val zmin: Double = Double.NEGATIVE_INFINITY,
    val zmax: Double = Double.POSITIVE_INFINITY
) :
    SpaceSegmentation() {

    override val type: SegmentationType = SegmentationType.RECT

    constructor(x: Pair<Double, Double>, y: Pair<Double, Double>, z: Pair<Double, Double> = Pair(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) : this(x.first, x.second, y.first, y.second, z.first, z.second)
    constructor(min: Triple<Double, Double, Double>, max: Triple<Double, Double, Double>) : this(min.first, max.first, min.second, max.second, min.third, max.third)

    init {
        shape = Rectangle2D.Double(xmin, ymin, width, height)
        area = Area(shape)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Rect

        if (!xmin.equalsEpsilon(other.xmin)) return false
        if (!xmax.equalsEpsilon(other.xmax)) return false
        if (!ymin.equalsEpsilon(other.ymin)) return false
        if (!ymax.equalsEpsilon(other.ymax)) return false
        if (!zmin.equalsEpsilon(other.zmin)) return false
        if (!zmax.equalsEpsilon(other.zmax)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = xmin.hashCode()
        result = 31 * result + xmax.hashCode()
        result = 31 * result + ymin.hashCode()
        result = 31 * result + ymax.hashCode()
        result = 31 * result + zmin.hashCode()
        result = 31 * result + zmax.hashCode()
        return result
    }

    override fun toString(): String = "segment/rect/" + "$xmin,$xmax,$ymin,$ymax"

    fun to2dPolygon(): Polygon = Polygon(
        listOf(
            xmin to ymin,
            xmax to ymin,
            xmax to ymax,
            xmin to ymax
        )
    )

    val width: Double
        get() = xmax - xmin

    val height: Double
        get() = ymax - ymin


    fun clip(
        xmin: Double,
        xmax: Double,
        ymin: Double,
        ymax: Double,
        zmin: Double = Double.NEGATIVE_INFINITY,
        zmax: Double = Double.POSITIVE_INFINITY
    ): Rect = Rect(
        max(this.xmin, xmin), min(this.xmax, xmax),
        max(this.ymin, ymin), min(this.ymax, ymax),
        max(this.zmin, zmin), min(this.zmax, zmax)
    )
}

class Polygon(val points: List<Pair<Double, Double>>) : SpaceSegmentation() {

    override val type: SegmentationType = SegmentationType.POLYGON

    init {
        require(points.size > 2) {
            throw IllegalArgumentException("A polygon needs at least 3 vertices")
        }
        shape = java.awt.Polygon(points.map { it.first.roundToInt() }.toIntArray(),
            points.map { it.second.roundToInt() }.toIntArray(),
            points.size
        )
        area = Area(shape)
    }

    /**
     * Returns 2d bounding [Rect]
     */
    fun boundingRect(): Rect {
        var xmin = points.first().first
        var ymin = points.first().second
        var xmax = xmin
        var ymax = ymin

        points.forEach {
            xmin = min(xmin, it.first)
            xmax = max(xmax, it.first)
            ymin = min(ymin, it.second)
            ymax = max(ymax, it.second)
        }

        return Rect(xmin, xmax, ymin, ymax)
    }

    /**
     * Converts [Polygon] into equivalent 2d [Rect] in case it exists
     */
    fun toRect(): Rect? {

        val verts = this.points.toSet()

        if (verts.size != 4) {
            return null
        }

        val bounding = this.boundingRect()

        if (this == bounding.to2dPolygon()) {
            return bounding
        }

        return null

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

class SVGPath(path: String) : SpaceSegmentation() {

    override val type: SegmentationType = SegmentationType.PATH

    init {
        shape = AWTPathProducer.createShape(StringReader(path), 0)
        area = Area(shape)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SVGPath
        if (this.shape.bounds != other.shape.bounds) return false
        return false
    }

    override fun hashCode(): Int {
        return shape.hashCode()
    }
}

class BezierSpline(points: List<Pair<Double, Double>>) : SpaceSegmentation() {
    override val type: SegmentationType = SegmentationType.BEZIER

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
}

class BSpline(points: List<Pair<Double, Double>>) : SpaceSegmentation() {
    override val type: SegmentationType = SegmentationType.BSPLINE

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
}

class Mask(val mask: ByteArray) : Segmentation {
    override val type: SegmentationType = SegmentationType.MASK
    override val segmentClass: SegmentationClass = SegmentationClass.SPACE
}

class Hilbert(dimensions: Int, private val order: Int, private val ranges: List<Pair<Long, Long>>) : Segmentation {
    override val type: SegmentationType = SegmentationType.HILBERT
    override val segmentClass: SegmentationClass = SegmentationClass.SPACE

    private val hilbertCurve: SmallHilbertCurve
    var relativeTimestamp: Double? = null
        set(value) {
            field = value
        }

    init {
        hilbertCurve = HilbertCurve.small().bits(order).dimensions(dimensions)
    }

    fun isIncluded(vararg relativeCoords: Double): Boolean {

        // Translate to hilbert space
        val dim = (2.0).pow(order) - 1
        val hilbertCoords = relativeCoords.map { (it * dim).roundToLong() }.toMutableList()

        if (relativeTimestamp != null) {
            hilbertCoords.add((relativeTimestamp!! * dim).roundToLong())
        }

        val hilbertIndex = hilbertCurve.index(*hilbertCoords.toLongArray())
        val found = ranges.find { r -> r.first <= hilbertIndex && hilbertIndex <= r.second }

        relativeTimestamp = null
        return found != null
    }
}

class Channel(val selection: List<String>) : ReduceSegmentation() {
    override val type: SegmentationType = SegmentationType.CHANNEL

    override fun intersect(rhs: ReduceSegmentation): Boolean {
        if (rhs !is Channel) return false

        return selection.intersect(rhs.selection).isNotEmpty()
    }
}

class Time(val intervals: List<Pair<Int, Int>>) : TimeSegmentation() {

    override val type: SegmentationType = SegmentationType.TIME

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Time
        return this.getTimePointsToSegment() == other.getTimePointsToSegment()
    }

    override fun hashCode(): Int {
        return intervals.sortedBy { it.first }.hashCode()
    }

    override fun toString(): String = "segment/time/" + intervals.joinToString(",") { "${it.first},${it.second}" }

    override fun intersect(rhs: TimeSegmentation): Boolean {
        if (rhs !is Time) return false

        return getTimePointsToSegment().intersect(rhs.getTimePointsToSegment()).isNotEmpty()
    }

    fun move(dt: Int): Time = Time(intervals.map { it.first + dt to it.second + dt })

    fun getTimePointsToSegment(): List<Int> {
        return intervals.flatMap { i -> (i.first until i.second).map { j -> j } }
    }

    fun getTimePointsToDiscard(start: Int, end: Int): List<Int> {
        val keep = getTimePointsToSegment().sorted()
        var sweeper = 0

        val res = mutableListOf<Int>()
        for (i in start until end) {
            if (sweeper < keep.size && keep[sweeper] == i) {
                sweeper++
            } else {
                res.add(i)
            }
        }
        return res
    }
}

class Plane(val a: Double, val b: Double, val c: Double, val d: Double, val above: Boolean) : Segmentation {
    override val type: SegmentationType = SegmentationType.PLANE
    override val segmentClass: SegmentationClass = SegmentationClass.SPACE
}

data class RotoscopePair(val time: Double, val points: List<Pair<Double, Double>>)

class Rotoscope(segmentationType: SegmentationType, val rotoscopeList: List<RotoscopePair>) : Segmentation {
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

    fun interpolate(time: Double): SpaceSegmentation? {
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