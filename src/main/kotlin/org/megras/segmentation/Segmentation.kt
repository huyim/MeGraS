package org.megras.segmentation

import org.apache.batik.parser.AWTPathProducer
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
import kotlin.math.roundToInt


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

class Polygon(val vertices: List<Pair<Double, Double>>) : SpaceSegmentation() {

    override val type: SegmentationType = SegmentationType.POLYGON

    init {
        require(vertices.size > 2) {
            throw IllegalArgumentException("A polygon needs at least 3 vertices")
        }
        shape = java.awt.Polygon(vertices.map { it.first.roundToInt() }.toIntArray(),
            vertices.map { it.second.roundToInt() }.toIntArray(),
            vertices.size
        )
        area = Area(shape)
    }

    fun isConvex(): Boolean {
        if (vertices.size < 4) return true
        var sign = false
        val n: Int = vertices.size
        for (i in 0 until n) {
            val dx1: Double = vertices[(i + 2) % n].first - vertices[(i + 1) % n].first
            val dy1: Double = vertices[(i + 2) % n].second - vertices[(i + 1) % n].second
            val dx2: Double = vertices[i].first - vertices[(i + 1) % n].first
            val dy2: Double = vertices[i].second - vertices[(i + 1) % n].second
            val zcrossproduct = dx1 * dy2 - dy1 * dx2
            if (i == 0) sign = zcrossproduct > 0 else if (sign != zcrossproduct > 0) return false
        }
        return true
    }

    /**
     * Returns 2d bounding [Rect]
     */
    fun boundingRect(): Rect {
        var xmin = vertices.first().first
        var ymin = vertices.first().second
        var xmax = xmin
        var ymax = ymin

        vertices.forEach {
            xmin = min(xmin, it.first)
            xmax = max(xmax, it.first)
            ymin = min(ymin, it.second)
            ymax = max(ymax, it.second)
        }

        return Rect(xmin, xmax, ymin, ymax)
    }

    val xmin: Double = vertices.minOf { it.first }

    val ymin: Double = vertices.minOf { it.second }

    /**
     * Converts [Polygon] into equivalent 2d [Rect] in case it exists
     */
    fun toRect(): Rect? {

        val verts = this.vertices.toSet()

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

        if (other.vertices.size != vertices.size) return false

        val start = vertices.indexOfFirst { it.equalsEpsilon(other.vertices.first()) }

        if (start == -1) return false

        return vertices.indices.all { other.vertices[it].equalsEpsilon(vertices[(it + start) % vertices.size]) }

    }

    override fun hashCode(): Int {
        return vertices.sortedBy { it.first }.sortedBy { it.second }.hashCode()
    }

    override fun toString(): String = "segment/polygon/" + vertices.joinToString(",") { "(${it.first},${it.second})" }
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

class Spline(controlPoints: List<Pair<Double, Double>>) : SpaceSegmentation() {
    override val type: SegmentationType = SegmentationType.SPLINE

    init {
        var spline = BSpline(controlPoints.size.toLong(), 2, 3, BSpline.Type.Opened)
        spline.controlPoints = controlPoints.flatMap { listOf(it.first, it.second) }
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

        shape = path
        area = Area(shape)
    }
}

class Mask(val mask: ByteArray) : Segmentation {
    override val type: SegmentationType = SegmentationType.MASK
    override val segmentClass: SegmentationClass = SegmentationClass.SPACE
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

class SpaceTime() : Segmentation {
    override val type: SegmentationType = SegmentationType.SPACETIME
    override val segmentClass: SegmentationClass = SegmentationClass.SPACETIME
}