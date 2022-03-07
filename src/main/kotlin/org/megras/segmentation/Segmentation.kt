package org.megras.segmentation

import org.megras.util.extensions.equalsEpsilon
import java.lang.Double.max
import java.lang.Double.min

sealed class Segmentation(val type: SegmentationType)

data class Rect(val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double, val zmin: Double = Double.NEGATIVE_INFINITY, val zmax: Double = Double.POSITIVE_INFINITY) : Segmentation(SegmentationType.RECT) {
    constructor(x: Pair<Double, Double>, y: Pair<Double, Double>, z: Pair<Double, Double> = Pair(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) : this(x.first, x.second, y.first, y.second, z.first, z.second)
    constructor(min: Triple<Double, Double, Double>, max: Triple<Double, Double, Double>) : this(min.first, max.first, min.second, max.second, min.third, max.third)

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

    fun to2dPolygon() : Polygon = Polygon(
        listOf(
            xmin to ymin,
            xmax to ymin,
            xmax to ymax,
            xmin to ymax
        )
    )


}

data class Polygon(val vertices: List<Pair<Double, Double>>) : Segmentation(SegmentationType.POLYGON) {
    init {
        require(vertices.size > 2) {
            throw IllegalArgumentException ("A polygon needs at least 3 vertices")
        }
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
            ymax = max(xmax, it.second)
        }

        return Rect(xmin, xmax, ymin, ymax)

    }

    /**
     * Converts [Polygon] into equivalent 2d [Rect] in case it exists
     */
    fun toRect() : Rect? {

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


}