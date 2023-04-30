package org.megras.segmentation.media

import de.javagl.obj.*
import org.megras.segmentation.type.Plane
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.SegmentationType
import java.io.ByteArrayOutputStream
import java.io.InputStream


object MeshSegmenter {

    fun segment(inputStream: InputStream, segmentation: Segmentation): ByteArray? = try {
        when(segmentation.segmentationType) {
            SegmentationType.PLANE -> segmentPlane(inputStream, segmentation as Plane)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentPlane(inputStream: InputStream, plane: Plane): ByteArray? {
        val obj = ObjReader.read(inputStream)
        val segmentedObj = Objs.create()

        (0 until obj.numVertices).forEach { v -> segmentedObj.addVertex(obj.getVertex(v)) }
        (0 until obj.numNormals).forEach { v -> segmentedObj.addNormal(obj.getNormal(v)) }

        for (f in 0 until obj.numFaces) {
            val face = obj.getFace(f)

            val faceVertices = mutableListOf<Int>()
            val keepVertices = mutableListOf<Int>()
            for (v in 0 until face.numVertices) {
                faceVertices.add(face.getVertexIndex(v))
                val v1 = obj.getVertex(face.getVertexIndex(v))
                val v2 = obj.getVertex(face.getVertexIndex((v + 1) % face.numVertices))

                val d1 = (plane.a * v1.x + plane.b * v1.y + plane.c * v1.z + plane.d).toFloat()
                val d2 = (plane.a * v2.x + plane.b * v2.y + plane.c * v2.z + plane.d).toFloat()

                if (d1 >= 0 == plane.above) {
                    keepVertices.add(face.getVertexIndex(v))
                }

                if (d1 * d2 < 0) {
                    val t = -d1 / (d2 - d1)
                    val x = v1.x + t * (v2.x - v1.x)
                    val y = v1.y + t * (v2.y - v1.y)
                    val z = v1.z + t * (v2.z - v1.z)
                    segmentedObj.addVertex(x, y, z)
                    keepVertices.add(segmentedObj.numVertices - 1)
                }
            }

            if (keepVertices.isNotEmpty()) {
                if (faceVertices == keepVertices) {
                    segmentedObj.addFace(face)
                } else {
                    segmentedObj.addFace(*keepVertices.toIntArray())
                }
            }
        }

        val out = ByteArrayOutputStream()
        ObjWriter.write(segmentedObj, out)
        return out.toByteArray()
    }
}