package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectId
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.graphstore.QuadSet
import org.megras.segmentation.ImageSegmenter
import org.megras.segmentation.SegmentationUtil
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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


        val rawId = quads.filter(
            setOf(ctx.pathParam("objectId")),
            setOf(MeGraS.CANONICAL_ID.string),
            null
        ).firstOrNull()?.`object`

        if (rawId == null) {
            ctx.status(404)
            ctx.result("not found")
            return
        }

        val osId = StoredObjectId.of(rawId)

        if (osId == null) {
            ctx.status(404)
            ctx.result("not found")
            return
        }

        val storedObject = objectStore.get(osId)

        if (storedObject == null) {
            ctx.status(404)
            ctx.result("not found")
            return
        }

        when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> TODO()
            MediaType.IMAGE -> { //TODO cache

                val img = ImageIO.read(storedObject.inputStream())
                val segment = ImageSegmenter.segment(img, segmentations.first())

                if (segment == null) {
                    ctx.status(403)
                    ctx.result("invalid segmentation")
                }

                val out = ByteArrayOutputStream()

                ImageIO.write(segment, "PNG", out)

                ctx.contentType(MimeType.PNG.mimeString)
                ctx.result(out.toByteArray())

            }
            MediaType.AUDIO -> TODO()
            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        }


        //TODO("Not yet implemented")

    }
}