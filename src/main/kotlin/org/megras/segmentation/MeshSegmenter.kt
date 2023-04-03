package org.megras.segmentation

import de.javagl.obj.*


object MeshSegmenter {

    fun segment(obj: Obj, segmentation: Segmentation): Obj? = try {
        when(segmentation.type) {
            SegmentationType.PLANE -> segmentPlane(obj, segmentation as Plane)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentPlane(obj: Obj, plane: Plane): Obj? {

        val out = Objs.create()

        (0 until obj.numVertices).forEach { v -> out.addVertex(obj.getVertex(v)) }
        (0 until obj.numNormals).forEach { v -> out.addNormal(obj.getNormal(v)) }

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
                    out.addVertex(x, y, z)
                    keepVertices.add(out.numVertices - 1)
                }
            }

            if (keepVertices.isNotEmpty()) {
                if (faceVertices == keepVertices) {
                    out.addFace(face)
                } else {
                    out.addFace(*keepVertices.toIntArray())
                }
            }
        }

        return out
    }
}