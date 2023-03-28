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

        val segmentationTypes = SegmentationUtil.parseSegmentationType(ctx.pathParam("segmentation"))

        if (segmentationTypes.any { it == null }) {
            throw RestErrorStatus(403, "invalid segmentation type")
        }

        val segmentations = SegmentationUtil.parseSegmentation(segmentationTypes.filterNotNull(), ctx.pathParam("segmentDefinition"))

        if(segmentations.isEmpty()) {
            throw RestErrorStatus(403, "invalid segmentation type")
        }


        val rawId = quads.filter(
            setOf(ObjectId(ctx.pathParam("objectId"))),
            setOf(MeGraS.CANONICAL_ID.uri),
            null
        ).firstOrNull()?.`object` as? StringValue ?: throw RestErrorStatus.notFound


        val osId = StoredObjectId.of(rawId.value) ?: throw RestErrorStatus.notFound

        val storedObject = objectStore.get(osId) ?: throw RestErrorStatus.notFound

        //check cache
        quads.filter(listOf(LocalQuadValue(ctx.path())), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
            val cached = it.`object` as LocalQuadValue
            ctx.redirect("/${cached.uri}", HttpStatus.TEMPORARY_REDIRECT)
            println("found in cache")
            return
        }

        when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> {
                val text = storedObject.inputStream()
                val segment = TextSegmenter.segment(text, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                storeSegment(segment, MimeType.TEXT, ctx)
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

                        if (findInQuad(hash, ctx)) {
                            println("found cached equivalent")
                            return
                        }

                        segment = ImageSegmenter.segment(img, mask)
                    }
                }

                val out = ByteArrayOutputStream()
                ImageIO.write(segment, "PNG", out)
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.PNG, ctx, hash)

            }

            MediaType.AUDIO -> {
                val segment = AudioSegmenter.segment(storedObject, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                val out = ByteArrayOutputStream()
                AudioSystem.write(segment, AudioFileFormat.Type.WAVE, out)
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.WAV, ctx)
            }

            MediaType.VIDEO -> TODO()

            MediaType.DOCUMENT -> {
                val pdf = PDDocument.load(storedObject.inputStream())
                val segment = DocumentSegmenter.segment(pdf, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                pdf.close()
                val out = ByteArrayOutputStream()
                segment.save(out)
                segment.close()
                val buf = out.toByteArray()

                storeSegment(buf, MimeType.PDF, ctx)
            }

            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        }


        //TODO("Not yet implemented")

    }

    private fun findInQuad(hash: String, ctx: Context): Boolean {
        quads.filter(listOf(LocalQuadValue(hash)), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
            val cached = it.`object` as LocalQuadValue
            ctx.redirect("/${cached.uri}")
            return true
        }
        return false
    }

    private fun storeSegment(segment: ByteArray, mimeType: MimeType, ctx: Context, hash: String? = null) {
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

        quads.add(Quad(cacheObject, MeGraS.RAW_ID.uri, StringValue(descriptor.id.id)))
        quads.add(Quad(LocalQuadValue(ctx.path()), SchemaOrg.SAME_AS.uri, cacheObject))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_OF.uri, ObjectId(objectId)))

        if (!hash.isNullOrEmpty()) {
            quads.add(Quad(LocalQuadValue(hash), SchemaOrg.SAME_AS.uri, cacheObject))
        }

        ctx.redirect("/${cacheObject.uri}")
    }
}