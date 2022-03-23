package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectDescriptor
import org.megras.data.fs.StoredObjectId
import org.megras.data.graph.Quad
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.data.schema.SchemaOrg
import org.megras.graphstore.MutableQuadSet
import org.megras.graphstore.QuadSet
import org.megras.segmentation.ImageSegmenter
import org.megras.segmentation.SegmentationUtil
import org.megras.util.HashUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class CanonicalSegmentRequestHandler(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

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

        //check cache
        quads.filter(listOf(ctx.path()), listOf(SchemaOrg.SAME_AS.string), null).forEach {
            val cached = it.`object`
            ctx.redirect("/$cached")
            return
        }

        //TODO check cache for equivalent segmentations



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

                val buf = out.toByteArray()

                ctx.contentType(MimeType.PNG.mimeString)
                ctx.result(buf)


                val inStream = ByteArrayInputStream(buf)

                val cachedObjectId = objectStore.idFromStream(inStream)
                val descriptor = StoredObjectDescriptor(
                    cachedObjectId,
                    MimeType.PNG,
                    buf.size.toLong()
                )

                inStream.reset()
                objectStore.store(inStream, descriptor)

                val cacheId = HashUtil.hashToBase64("${ctx.pathParam("segmentation")}/${ctx.pathParam("segmentDefinition")}")
                val cacheObject = "$objectId/c/$cacheId"

                quads.add(Quad(cacheObject, MeGraS.RAW_ID.string, descriptor.id.id))
                quads.add(Quad(ctx.path(), SchemaOrg.SAME_AS.string, cacheObject))

            }
            MediaType.AUDIO -> TODO()
            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        }


        //TODO("Not yet implemented")

    }
}