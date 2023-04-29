package org.megras.api.rest.handlers

import de.javagl.obj.ObjReader
import de.javagl.obj.ObjWriter
import io.javalin.http.Context
import org.apache.pdfbox.pdmodel.PDDocument
import org.megras.api.rest.GetRequestHandler
import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectDescriptor
import org.megras.data.fs.StoredObjectId
import org.megras.data.graph.LocalQuadValue
import org.megras.data.graph.Quad
import org.megras.data.graph.StringValue
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.data.schema.SchemaOrg
import org.megras.graphstore.MutableQuadSet
import org.megras.id.ObjectId
import org.megras.segmentation.*
import org.megras.segmentation.media.*
import org.megras.segmentation.type.Channel
import org.megras.segmentation.type.Translatable
import org.megras.util.HashUtil
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


class CanonicalSegmentRequestHandler(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * /{objectId}/segment/{segmentation}/{segmentDefinition}"
     */
    override fun get(ctx: Context) {

        val objectId = ctx.pathParam("objectId")
        val segmentType = ctx.pathParam("segmentation")
        val segmentDefinition = ctx.pathParam("segmentDefinition")
        var nextSegmentPath: String? = null
        val tail = ctx.pathParamMap()["tail"]

        val lookInCache = ctx.queryParam("nocache") == null

        val segmentation = SegmentationUtil.parseSegmentation(segmentType, segmentDefinition) ?: throw RestErrorStatus.invalidSegmentation
        val currentPath = "$objectId/$segmentation"

        // check for an additional segmentation
        if (ctx.pathParamMap().containsKey("nextSegmentation")) {
            val nextSegmentType = ctx.pathParam("nextSegmentation")
            val nextSegmentDefinition = ctx.pathParam("nextSegmentDefinition")
            nextSegmentPath = "segment/$nextSegmentType/$nextSegmentDefinition"

            val nextSegmentation = SegmentationUtil.parseSegmentation(nextSegmentType, nextSegmentDefinition) ?: throw RestErrorStatus.invalidSegmentation
            if (nextSegmentation is Translatable) {
                nextSegmentation.translate(segmentation)
            }

            // if two segmentations of the same type are not overlapping, no valid result can be computed
            if (!segmentation.intersects(nextSegmentation)) {
                throw RestErrorStatus.emptySegment
            }

            // if two segmentations are equivalent, discard the second one
            if (segmentation.equivalentTo(nextSegmentation)) {
                ctx.redirect("/$currentPath" + (if (tail != null) "/$tail" else ""))
                return
            }

            // if the first segmentation contains the second one, directly apply the second one
            if (segmentation.contains(nextSegmentation)) {
                ctx.redirect("/$objectId/$nextSegmentation" + (if (tail != null) "/$tail" else ""))
                return
            }

            // reorder based on the segmentation types
            if (SegmentationUtil.shouldSwap(segmentation.segmentationType, nextSegmentation.segmentationType)) {
                ctx.redirect("/$objectId/$nextSegmentPath/$segmentation" + (if (tail != null) "/$tail" else ""))
                return
            }
        }

        val canonicalId = quads.filter(
            setOf(ObjectId(ctx.pathParam("objectId"))),
            setOf(MeGraS.CANONICAL_ID.uri),
            null
        ).firstOrNull()?.`object` as? StringValue ?: throw RestErrorStatus.notFound
        val osId = StoredObjectId.of(canonicalId.value) ?: throw RestErrorStatus.notFound
        val storedObject = objectStore.get(osId) ?: throw RestErrorStatus.notFound

        //check cache
        if (lookInCache) {
            quads.filter(listOf(LocalQuadValue(currentPath)), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
                val cached = it.`object` as LocalQuadValue
                if (nextSegmentPath != null) {
                    ctx.redirect("/${cached.uri}/$nextSegmentPath")
                } else {
                    ctx.redirect("/${cached.uri}")
                }
                logger.info("found $currentPath in cache: ${cached.uri}")
                return
            }
        }

        val segment: ByteArray = when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> {
                val text = storedObject.inputStream()
                TextSegmenter.segment(text, segmentation) ?: throw RestErrorStatus.invalidSegmentation
            }

            MediaType.IMAGE -> {
                val img = ImageIO.read(storedObject.inputStream())

                val segment: BufferedImage?

                segment = when(segmentation.segmentationType) {
                    SegmentationType.CHANNEL -> {
                        ImageSegmenter.segmentChannel(img, segmentation as Channel)
                    }
                    else -> {
                        ImageSegmenter.segment(img, segmentation) ?: throw RestErrorStatus.invalidSegmentation
                    }
                }

                val out = ByteArrayOutputStream()
                ImageIO.write(segment, "PNG", out)
                out.toByteArray()
            }

            MediaType.AUDIO, MediaType.VIDEO -> {
                val segment = AudioVideoSegmenter.segment(storedObject.byteChannel(), segmentation) ?: throw RestErrorStatus.invalidSegmentation
                segment.array()
            }

            MediaType.DOCUMENT -> {
                val pdf = PDDocument.load(storedObject.inputStream())
                val segment = DocumentSegmenter.segment(pdf, segmentation) ?: throw RestErrorStatus.invalidSegmentation

                val out = ByteArrayOutputStream()
                segment.save(out)
                pdf.close()
                segment.close()
                out.toByteArray()
            }

            MediaType.MESH -> {
                val obj = ObjReader.read(storedObject.inputStream())
                val segment = MeshSegmenter.segment(obj, segmentation) ?: throw RestErrorStatus.invalidSegmentation

                val out = ByteArrayOutputStream()
                ObjWriter.write(segment, out)
                out.toByteArray()
            }

            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        }

        val inStream = ByteArrayInputStream(segment)

        val cachedObjectId = objectStore.idFromStream(inStream)
        val descriptor = StoredObjectDescriptor(
            cachedObjectId,
            storedObject.descriptor.mimeType,
            segment.size.toLong()
        )

        inStream.reset()
        objectStore.store(inStream, descriptor)

        val cacheId = HashUtil.hashToBase64("$segmentType/$segmentDefinition", HashUtil.HashType.MD5)
        val cacheObject = LocalQuadValue("$objectId/c/$cacheId")

        quads.add(Quad(cacheObject, MeGraS.CANONICAL_ID.uri, StringValue(descriptor.id.id)))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_OF.uri, ObjectId(objectId)))
        quads.add(Quad(LocalQuadValue(currentPath), SchemaOrg.SAME_AS.uri, cacheObject))

        if (nextSegmentPath != null) {
            ctx.redirect("/$currentPath/$nextSegmentPath")
        } else {
            ctx.redirect("/$currentPath")
        }
    }

    private fun findInQuad(hash: String, ctx: Context, nextSegment: String?): Boolean {
        quads.filter(null, listOf(SchemaOrg.SHA256.uri), listOf(StringValue(hash))).forEach {
            val cached = it.subject as LocalQuadValue
            if (nextSegment != null) {
                ctx.redirect("/${cached.uri}/${nextSegment}")
            } else {
                ctx.redirect("/${cached.uri}")
            }
            return true
        }
        return false
    }
}