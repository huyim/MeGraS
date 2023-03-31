package org.megras.api.rest.handlers

import io.javalin.http.Context
import io.javalin.http.HttpStatus
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
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioSystem


class CanonicalSegmentRequestHandler(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

    /**
     * /{objectId}/segment/{segmentation}/<segmentDefinition>"
     */
    override fun get(ctx: Context) {

        val lookInCache = ctx.queryParam("nocache") == null

        val nextSegment = checkIfNextSegment(ctx.path())

        val segmentationTypes = SegmentationUtil.parseSegmentationType(ctx.pathParam("segmentation"))

        if (segmentationTypes.any { it == null }) {
            throw RestErrorStatus(403, "invalid segmentation type")
        }

        val segmentations = SegmentationUtil.parseSegmentation(segmentationTypes.filterNotNull(), ctx.pathParam("segmentDefinition"))

        if(segmentations.isEmpty()) {
            throw RestErrorStatus(403, "invalid segmentation type")
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
            quads.filter(listOf(LocalQuadValue(ctx.path())), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
                val cached = it.`object` as LocalQuadValue
                ctx.redirect("/${cached.uri}")
                println("found in cache")
                return
            }
        }

        when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> {
                val text = storedObject.inputStream()
                val segment = TextSegmenter.segment(text, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                storeSegment(segment, MimeType.TEXT, ctx, nextSegment)
            }

            MediaType.IMAGE -> {
                val img = ImageIO.read(storedObject.inputStream())

                val segment: BufferedImage?
                var hash: String? = null

                when(segmentations.first().type) {
                    SegmentationType.CHANNEL -> {
                        segment = ImageSegmenter.segmentChannel(img, segmentations.first() as Channel)
                    }

                    else -> {
                        val mask = ImageSegmenter.toBinary(img, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")
                        hash = HashUtil.hashToBase64(mask.inputStream(), HashUtil.HashType.MD5)

                        if (findInQuad(hash, ctx, nextSegment)) {
                            println("found cached equivalent")
                            return
                        }

                        segment = ImageSegmenter.segment(img, mask)
                    }
                }

                val out = ByteArrayOutputStream()
                ImageIO.write(segment, "PNG", out)
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.PNG, ctx, nextSegment, hash)

            }

            MediaType.AUDIO -> {
                val segment = AudioSegmenter.segment(storedObject, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                val out = ByteArrayOutputStream()
                AudioSystem.write(segment, AudioFileFormat.Type.WAVE, out)
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.WAV, ctx, nextSegment)
            }

            MediaType.VIDEO -> {

                val segment = VideoSegmenter.segment(storedObject.byteChannel(), segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                storeSegment(segment.array(), MimeType.OGG, ctx, nextSegment)
            }

            MediaType.DOCUMENT -> {
                val pdf = PDDocument.load(storedObject.inputStream())
                val segment = DocumentSegmenter.segment(pdf, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                pdf.close()
                val out = ByteArrayOutputStream()
                segment.save(out)
                segment.close()
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.PDF, ctx, nextSegment)
            }

            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        }


        //TODO("Not yet implemented")

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

    private fun storeSegment(segment: ByteArray, mimeType: MimeType, ctx: Context, nextSegment: String?, hash: String? = null) {
        val objectId = ctx.pathParam("objectId")

        val inStream = ByteArrayInputStream(segment)

        val cachedObjectId = objectStore.idFromStream(inStream)
        val descriptor = StoredObjectDescriptor(
            cachedObjectId,
            mimeType,
            segment.size.toLong()
        )

        inStream.reset()
        objectStore.store(inStream, descriptor)

        val cacheId = HashUtil.hashToBase64("${ctx.pathParam("segmentation")}/${ctx.pathParam("segmentDefinition")}", HashUtil.HashType.MD5)
        val cacheObject = LocalQuadValue("$objectId/c/$cacheId")

        quads.add(Quad(cacheObject, MeGraS.CANONICAL_ID.uri, StringValue(descriptor.id.id)))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_OF.uri, ObjectId(objectId)))

        if (!hash.isNullOrEmpty()) {
            quads.add(Quad(LocalQuadValue(hash), SchemaOrg.SAME_AS.uri, cacheObject))
        }

        if (nextSegment != null) {
            quads.add(Quad(LocalQuadValue(ctx.path().replace("/$nextSegment", "")), SchemaOrg.SAME_AS.uri, cacheObject))
            ctx.redirect("/${cacheObject.uri}/${nextSegment}")
        } else {
            quads.add(Quad(LocalQuadValue(ctx.path()), SchemaOrg.SAME_AS.uri, cacheObject))
            ctx.redirect("/${cacheObject.uri}")
        }
    }

    private fun checkIfNextSegment(path: String): String? {
        val match = Regex("/.+/segment/.+/.+/(segment/.+)").find(path) ?: return null

        val (nextSegment) = match.destructured
        return nextSegment
    }
}