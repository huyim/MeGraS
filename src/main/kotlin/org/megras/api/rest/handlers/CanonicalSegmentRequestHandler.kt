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
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.data.schema.SchemaOrg
import org.megras.graphstore.MutableQuadSet
import org.megras.id.ObjectId
import org.megras.segmentation.*
import org.megras.util.HashUtil
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioSystem


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
        val currentPath = "$objectId/segment/$segmentType/$segmentDefinition"

        val lookInCache = ctx.queryParam("nocache") == null

        val segmentation = SegmentationUtil.parseSegmentation(segmentType, segmentDefinition) ?: throw RestErrorStatus(403, "invalid segmentation")
        val segmentPath = "segment/$segmentType/$segmentDefinition"

        // check for an additional segmentation
        if (ctx.pathParamMap().containsKey("nextSegmentation")) {
            val nextSegmentType = ctx.pathParam("nextSegmentation")
            val nextSegmentDefinition = ctx.pathParam("nextSegmentDefinition")
            nextSegmentPath = "segment/$nextSegmentType/$nextSegmentDefinition"

            val nextSegmentation = SegmentationUtil.parseSegmentation(nextSegmentType, nextSegmentDefinition) ?: throw RestErrorStatus(403, "invalid segmentation")
            val translatedNextSegment = SegmentationUtil.translate(nextSegmentation, segmentation)

            // TODO: if two segmentations of the same type are not overlapping, no valid result can be computed

            // if two segmentations are equivalent, discard the second one
            if (SegmentationUtil.equivalent(segmentation, translatedNextSegment)) {
                ctx.redirect("/$currentPath" + (if (tail != null) "/$tail" else ""))
                return
            }

            // if the first segmentation contains the second one, directly apply the second one
            if (SegmentationUtil.contains(segmentation, translatedNextSegment)) {
                ctx.redirect("/$objectId/$translatedNextSegment" + (if (tail != null) "/$tail" else ""))
                return
            }

            // reorder based on the segmentation types
            if (SegmentationUtil.shouldSwap(segmentation.type, nextSegmentation.type)) {
                ctx.redirect("/$objectId/$nextSegmentPath/$segmentPath" + (if (tail != null) "/$tail" else ""))
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
                ctx.redirect("/${cached.uri}" + (nextSegmentPath ?: ""))
                logger.info("found $currentPath in cache: ${cached.uri}")
                return
            }
        }

        val segment: ByteArray = when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> {
                val text = storedObject.inputStream()
                TextSegmenter.segment(text, segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")
            }

            MediaType.IMAGE -> {
                val img = ImageIO.read(storedObject.inputStream())

                val segment: BufferedImage?
                var hash: String? = null

                when(segmentation.type) {
                    SegmentationType.CHANNEL -> {
                        segment = ImageSegmenter.segmentChannel(img, segmentation as Channel)
                    }

                    else -> {
                        val mask = ImageSegmenter.toBinary(img, segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")
                        hash = HashUtil.hashToBase64(mask.inputStream(), HashUtil.HashType.MD5)

                        if (findInQuad(hash, ctx, nextSegmentPath)) {
                            logger.info("found cached equivalent")
                            return
                        }

                        segment = ImageSegmenter.segment(img, mask)
                    }
                }

                val out = ByteArrayOutputStream()
                ImageIO.write(segment, "PNG", out)
                out.toByteArray()
            }

            MediaType.AUDIO -> {
                val segment = AudioSegmenter.segment(storedObject, segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")

                val out = ByteArrayOutputStream()
                AudioSystem.write(segment, AudioFileFormat.Type.WAVE, out)
                out.toByteArray()
            }

            MediaType.VIDEO -> {
                val segment = VideoSegmenter.segment(storedObject.byteChannel(), segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")
                segment.array()
            }

            MediaType.DOCUMENT -> {
                val pdf = PDDocument.load(storedObject.inputStream())
                val segment = DocumentSegmenter.segment(pdf, segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")

                pdf.close()
                val out = ByteArrayOutputStream()
                segment.save(out)
                segment.close()
                out.toByteArray()
            }

            MediaType.MESH -> {
                val obj = ObjReader.read(storedObject.inputStream())
                val segment = MeshSegmenter.segment(obj, segmentation) ?: throw RestErrorStatus(403, "Invalid segmentation")

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

//        if (!hash.isNullOrEmpty()) {
//            quads.add(Quad(LocalQuadValue(hash), SchemaOrg.SAME_AS.uri, cacheObject))
//        }

        ctx.redirect("/$currentPath" + (nextSegmentPath ?: ""))
    }

    private fun findInQuad(hash: String, ctx: Context, nextSegment: String?): Boolean {
        quads.filter(listOf(LocalQuadValue(hash)), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
            val cached = it.`object` as LocalQuadValue
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