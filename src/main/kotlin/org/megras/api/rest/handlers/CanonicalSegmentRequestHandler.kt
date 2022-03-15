package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.graphstore.QuadSet
import org.megras.segmentation.SegmentationUtil

class CanonicalSegmentRequestHandler(private val quads: QuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

    /**
     * /{objectId}/segment/{segmentation}/<segmentDefinition>"
     */
    override fun get(ctx: Context) {

        val objectId = ctx.pathParam("objectId")

        val segmentationTypes = SegmentationUtil.parseSegmentationType(ctx.pathParam("segmentation"))

        if (segmentationTypes.any { it == null }) {
            ctx.status(403)
            ctx.result("invalid segmentation type")
            return
        }

        val segmentations = SegmentationUtil.parseSegmentation(segmentationTypes.filterNotNull(), ctx.pathParam("segmentDefinition"))

        if(segmentations.isEmpty()) {
            ctx.status(403)
            ctx.result("invalid segmentation")
            return
        }




        TODO("Not yet implemented")

    }
}