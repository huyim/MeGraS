package org.megras.segmentation.type

import de.javagl.obj.FloatTuple
import de.javagl.obj.Obj
import de.javagl.obj.ObjReader
import de.sciss.shapeint.ShapeInterpolator
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.io.File
import java.util.*
import kotlin.math.abs

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

    override fun toString(): String = "segment/plane/$a,$b,$c,$d" + if (above) "1" else "0"
}

data class RotoscopePair(val time: Double, val space: TwoDimensionalSegmentation)

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

data class Vertex(val x: Float, val y: Float) {
    fun almostEqual(other: Vertex): Boolean {
        val eps = 0.0001
        return abs(this.x - other.x) < eps && abs(this.y - other.y) < eps
    }
}

class MeshBody(private val fileName: String) : ThreeDimensionalSegmentation {
    override val segmentationType = SegmentationType.MESH
    override val segmentationClass = SegmentationClass.SPACETIME
    private val obj: Obj = ObjReader.read(File("$fileName.obj").inputStream())

    override fun equivalentTo(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun intersects(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    fun slice(z: Float): TwoDimensionalSegmentation? {
        val lines: MutableList<Pair<Vertex, Vertex>> = LinkedList()

        // iterate all faces and all vertices
        for (f in 0 until obj.numFaces) {
            val face = obj.getFace(f)

            val intersections: MutableSet<Vertex> = mutableSetOf()
            for (v in 0 until face.numVertices) {
                val v1 = obj.getVertex(face.getVertexIndex(v))
                val v2 = obj.getVertex(face.getVertexIndex((v + 1) % face.numVertices))
                if (v1.z < z && v2.z < z || v1.z > z && v2.z > z) {
                    continue  // edge does not intersect z-coordinate
                }

                // compute (interpolate) intersection point and add to list
                val t = (z - v1.z) / (v2.z - v1.z)
                if (v1.z == z) {
                    intersections.add(Vertex(v1.x, v1.y))
                } else if (v2.z == z) {
                    intersections.add(Vertex(v2.x, v2.y))
                } else if (v1.z < z && v2.z > z) {
                    intersections.add(interpolate(v1, v2, t))
                } else if (v1.z > z && v2.z < z) {
                    intersections.add(interpolate(v2, v1, 1 - t))
                }
            }
            // fully intersected faces have two intersection points
            if (intersections.size == 2) {
                lines.add(Pair(intersections.first(), intersections.last()))
            }
        }

        if (lines.size == 0) {
            return null
        }

        // build path
        val path = Path2D.Float()

        // start with an arbitrary point
        var current = lines.first().first
        path.moveTo(current.x, current.y)

        while (lines.size > 0) {
            // test if the current point is connected to an unvisited point by a line
            var hasNext = false
            for (line in lines) {
                if (current.almostEqual(line.first)) {
                    current = line.second
                    hasNext = true
                } else if (current.almostEqual(line.second)) {
                    current = line.first
                    hasNext = true
                }
                // if yes, add it to the polygon and start next round
                if (hasNext) {
                    path.lineTo(current.x, current.y)
                    lines.remove(line)
                    break
                }
            }
            // if no, consider that polygon completed and start a new one
            if (!hasNext) {
                current = lines.first().first
                path.moveTo(current.x, current.y)
            }
        }

        return object: TwoDimensionalSegmentation() {
            override var shape: Shape = path
            override var area: Area = Area(shape)
            override val segmentationType = null
            override fun toString(): String = ""
        }
    }

    private fun interpolate(v1: FloatTuple, v2: FloatTuple, t: Float): Vertex {
        val x = v1.x + (v2.x - v1.x) * t
        val y = v1.y + (v2.y - v1.y) * t
        return Vertex(x, y)
    }

    override fun toString(): String = "segment/mesh/$fileName"
}