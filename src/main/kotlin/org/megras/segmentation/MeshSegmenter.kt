package org.megras.segmentation

import de.javagl.obj.Obj
import de.javagl.obj.Objs


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

            var keep = true
            for (v in 0 until face.numVertices) {
                val vertex = obj.getVertex(face.getVertexIndex(v))
                val isAbove = plane.a * vertex.x + plane.b * vertex.y + plane.c * vertex.z + plane.d >= 0
                if (isAbove != plane.above) keep = false
            }

            if (keep) {
                out.addFace(face)
            }
        }

        return out
    }
}