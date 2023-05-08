package org.megras.segmentation.type

import de.javagl.obj.*
import de.sciss.shapeint.ShapeInterpolator
import org.megras.api.rest.RestErrorStatus
import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.awt.Shape
import java.awt.geom.Path2D
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class ThreeDimensionalSegmentation : Segmentation {
    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is ThreeDimensionalSegmentation) return false
        if (this.bounds != rhs.bounds) return false

        val timeBounds = this.bounds.getTBounds()
        for (t in timeBounds[0].toInt() .. timeBounds[0].toInt()) {
            val shape1 = this.slice(t.toDouble())
            val shape2 = rhs.slice(t.toDouble())
            if (shape1 != null && shape2 != null && !shape1.equivalentTo(shape2)) return false
        }
        return true
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is ThreeDimensionalSegmentation) return false
        if (!this.bounds.contains(rhs.bounds)) return false

        val timeBounds = rhs.bounds.getTBounds()
        for (t in timeBounds[0].toInt() .. timeBounds[0].toInt()) {
            val shape1 = this.slice(t.toDouble())
            val shape2 = rhs.slice(t.toDouble())
            if (shape1 != null && shape2 != null && !shape1.contains(shape2)) return false
        }
        return true
    }

    abstract fun slice(time: Double): TwoDimensionalSegmentation?
}

class Plane(val a: Double, val b: Double, val c: Double, val d: Double, val above: Boolean) :
    ThreeDimensionalSegmentation() {
    override val segmentationType = SegmentationType.PLANE
    override var segmentationClass = SegmentationClass.SPACE
    override var bounds: SegmentationBounds = SegmentationBounds()

    override fun equivalentTo(rhs: Segmentation): Boolean {
        return rhs is Plane &&
                this.a == rhs.a && this.b == rhs.b && this.c == rhs.c && this.d == rhs.d && this.above == rhs.above
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Plane) return false
        return this.a == rhs.a && this.b == rhs.b && this.c == rhs.c && this.above == rhs.above &&
                ((above && rhs.d <= this.d) || (!above && this.d <= rhs.d))
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Plane) return false
        return this.a != rhs.a || this.b != rhs.b || this.c != rhs.c || this.above == rhs.above ||
                (above && rhs.d <= this.d ) || (!above && this.d <= rhs.d)
    }

    override fun slice(time: Double): TwoDimensionalSegmentation? = null

    override fun getDefinition(): String = "$a,$b,$c,$d" + if (above) "1" else "0"
}

data class RotoscopePair(var time: Double, var space: TwoDimensionalSegmentation)

class Rotoscope(var rotoscopeList: List<RotoscopePair>) : ThreeDimensionalSegmentation() {
    override val segmentationType = SegmentationType.ROTOSCOPE
    override val segmentationClass = SegmentationClass.SPACETIME
    override lateinit var bounds: SegmentationBounds

    init {
        require(rotoscopeList.size >= 2) {
            throw IllegalArgumentException("Need at least two transition points.")
        }

        val timePoints = rotoscopeList.map { it.time }
        val sortedTimePoints = timePoints.sorted()
        require(timePoints == sortedTimePoints) {
            throw IllegalArgumentException("Need input sorted by increasing time points")
        }

        var minX = Double.MAX_VALUE
        var maxX = Double.MIN_VALUE
        var minY = Double.MAX_VALUE
        var maxY = Double.MIN_VALUE
        rotoscopeList.forEach { i ->
            val bounds = i.space.shape.bounds
            if (bounds.minX < minX) minX = bounds.minX
            if (bounds.maxX > maxX) maxX = bounds.maxX
            if (bounds.minY < minY) minY = bounds.minY
            if (bounds.maxY > maxY) maxY = bounds.maxY
        }
        bounds = SegmentationBounds(minX, maxX, minY, maxY, rotoscopeList.first().time, rotoscopeList.last().time)
    }

    override fun translate(by: SegmentationBounds): Segmentation {
        if (by.dimensions >= 2) {
            return Rotoscope(rotoscopeList.map { RotoscopePair(it.time + by.getMinT(), it.space.translate(by) as TwoDimensionalSegmentation) })
        }
        return this
    }

    override fun slice(time: Double): TwoDimensionalSegmentation? {
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

        val keepBounds = bounds
        return object: TwoDimensionalSegmentation() {
            override val segmentationType = null
            override var shape: Shape = newShape
            override var bounds: SegmentationBounds = keepBounds
            override fun getDefinition(): String = ""
        }
    }

    override fun getDefinition(): String = rotoscopeList.joinToString(";") {
        val shapeString = it.space.toString().removePrefix("segment/").replace("/", ",")
        "${it.time},${shapeString}"
    }
}

data class Vertex(val x: Float, val y: Float) {
    fun almostEqual(other: Vertex): Boolean {
        val eps = 0.0001
        return abs(this.x - other.x) < eps && abs(this.y - other.y) < eps
    }
}

class MeshBody(private var obj: Obj) : ThreeDimensionalSegmentation() {
    override val segmentationType = SegmentationType.MESH
    override val segmentationClass = SegmentationClass.SPACETIME
    override lateinit var bounds: SegmentationBounds

    init {
        val vertices = mutableListOf<FloatTuple>()

        val b = floatArrayOf(
            Float.MAX_VALUE, Float.MIN_VALUE,
            Float.MAX_VALUE, Float.MIN_VALUE,
            Float.MAX_VALUE, Float.MIN_VALUE
        )

        // Compute bounds and collect vertices
        for (v in 0 until obj.numVertices) {
            val vertex = obj.getVertex(v)
            vertices.add(vertex)

            b[0] = min(vertex.x, b[0])
            b[1] = max(vertex.x, b[1])
            b[2] = min(vertex.y, b[2])
            b[3] = max(vertex.y, b[3])
            b[4] = min(vertex.z, b[4])
            b[5] = max(vertex.z, b[5])
        }

        bounds = SegmentationBounds(
            b[0].toDouble(), b[1].toDouble(),
            b[2].toDouble(), b[3].toDouble(),
            b[4].toDouble(), b[5].toDouble()
        )

        // Sort vertices ascending and keep track of their old and new index
        val sorter = ObjVertexSorter(vertices)
        val oldToNewIndex = sorter.getMapping()

        // Collect faces with the updated vertex indices and sort them by index sum
        val faces = mutableListOf<IntArray>()
        for (f in 0 until obj.numFaces) {
            val face = obj.getFace(f)
            val newFaceIndices = mutableListOf<Int>()
            for (v in 0 until face.numVertices) {
                val newFaceIndex = oldToNewIndex[face.getVertexIndex(v)] ?: throw RestErrorStatus.invalidSegmentation
                newFaceIndices.add(newFaceIndex)
            }
            faces.add(newFaceIndices.toIntArray())
        }
        faces.sortBy { it.sum() }

        // Add the next vertices and faces to a new obj object
        val sortedObj = Objs.create()
        sorter.sortedIndices.forEach { i -> sortedObj.addVertex(vertices[i]) }
        faces.forEach {face ->
            val newFace = ObjFaces.create(face, null, null)
            sortedObj.addFace(newFace)
        }
        obj = sortedObj
    }

    override fun translate(by: SegmentationBounds): Segmentation {
        val minX = by.getMinX().toFloat()
        val minY = by.getMinY().toFloat()
        val minT = by.getMinT().toFloat()

        val newObj = Objs.create()
        for (i in 0 until obj.numVertices) {
            val vertex = obj.getVertex(i)
            newObj.addVertex(vertex.x + minX, vertex.y + minY, vertex.z + minT)
        }
        for (j in 0 until obj.numFaces) {
            val face = obj.getFace(j)
            newObj.addFace(face)
        }
        return MeshBody(newObj)
    }

    override fun slice(time: Double): TwoDimensionalSegmentation? {
        val lines: MutableList<Pair<Vertex, Vertex>> = LinkedList()
        val z = time.toFloat()

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

        val keepBounds = bounds
        return object: TwoDimensionalSegmentation() {
            override var shape: Shape = path
            override var bounds: SegmentationBounds = keepBounds
            override val segmentationType = null
            override fun getDefinition(): String = ""
        }
    }

    private fun interpolate(v1: FloatTuple, v2: FloatTuple, t: Float): Vertex {
        val x = v1.x + (v2.x - v1.x) * t
        val y = v1.y + (v2.y - v1.y) * t
        return Vertex(x, y)
    }

    override fun getDefinition(): String {
        val outputStream = ByteArrayOutputStream()
        ObjWriter.write(obj, outputStream)
        val outputString = outputStream.toString(Charset.defaultCharset())
        return outputString.replace("\n", ",")
    }
}

class ObjVertexSorter(private val array: List<FloatTuple>) : Comparator<Int> {
    val sortedIndices = array.indices.sortedWith(this).toMutableList()

    fun getMapping(): HashMap<Int, Int> {
        val indexMap = hashMapOf<Int, Int>()
        sortedIndices.forEachIndexed { index, i -> indexMap[i] = index }
        return indexMap
    }

    override fun compare(index1: Int, index2: Int): Int {
        return compareValuesBy(array[index1], array[index2], { it.x }, { it.y }, { it.z })
    }
}