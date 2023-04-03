package org.megras.api.rest.handlers

import de.javagl.obj.ObjReader
import de.javagl.obj.ObjWriter
import org.apache.pdfbox.pdmodel.PDDocument
import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.ObjectStoreResult
import org.megras.data.fs.StoredObjectDescriptor
import org.megras.data.graph.LocalQuadValue
import org.megras.data.graph.Quad
import org.megras.data.graph.StringValue
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


class CanonicalSegmentRequestHandler(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) {

    /**
     * /{objectId}/segment/{segmentation}/<segmentDefinition>"
     */
    fun get(objectId: String, storedObject: ObjectStoreResult, type: String, definition: String): ObjectStoreResult? {

        val segmentationTypes = SegmentationUtil.parseSegmentationType(type)

        if (segmentationTypes.any { it == null }) {
            throw RestErrorStatus(403, "invalid segmentation type")
        }

        val segmentations = SegmentationUtil.parseSegmentation(segmentationTypes.filterNotNull(), definition)

        if(segmentations.isEmpty()) {
            throw RestErrorStatus(403, "invalid segmentation definition")
        }

        val segment: ByteArray = when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> {
                val text = storedObject.inputStream()
                TextSegmenter.segment(text, segmentations.first())
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

//                        if (findInQuad(hash)) {
//                            println("found cached equivalent")
//                            return
//                        }

                        segment = ImageSegmenter.segment(img, mask)
                    }
                }

                val out = ByteArrayOutputStream()
                ImageIO.write(segment, "PNG", out)
                out.toByteArray()
            }

            MediaType.AUDIO -> {
                val segment = AudioSegmenter.segment(storedObject, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                val out = ByteArrayOutputStream()
                AudioSystem.write(segment, AudioFileFormat.Type.WAVE, out)
                out.toByteArray()
            }

            MediaType.VIDEO -> {
                val segment = VideoSegmenter.segment(storedObject.byteChannel(), segmentations.first())
                segment?.array()
            }

            MediaType.DOCUMENT -> {
                val pdf = PDDocument.load(storedObject.inputStream())
                val segment = DocumentSegmenter.segment(pdf, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                pdf.close()
                val out = ByteArrayOutputStream()
                segment.save(out)
                segment.close()
                out.toByteArray()
            }

            MediaType.MESH -> {
                val obj = ObjReader.read(storedObject.inputStream())
                val segment = MeshSegmenter.segment(obj, segmentations.first()) ?: throw RestErrorStatus(403, "Invalid segmentation")

                val out = ByteArrayOutputStream()
                ObjWriter.write(segment, out)
                out.toByteArray()
            }

            MediaType.UNKNOWN -> TODO()
            null -> TODO()
        } ?: throw RestErrorStatus(403, "Invalid segmentation")

        val inStream = ByteArrayInputStream(segment)

        val cachedObjectId = objectStore.idFromStream(inStream)
        val descriptor = StoredObjectDescriptor(
            cachedObjectId,
            storedObject.descriptor.mimeType,
            segment.size.toLong()
        )

        inStream.reset()
        objectStore.store(inStream, descriptor)

        val cacheId = HashUtil.hashToBase64("$type/$definition", HashUtil.HashType.MD5)
        val cacheObject = LocalQuadValue("${objectId}/c/$cacheId")

        quads.add(Quad(cacheObject, MeGraS.CANONICAL_ID.uri, StringValue(descriptor.id.id)))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_OF.uri, ObjectId(objectId)))
        quads.add(Quad(LocalQuadValue("$objectId/segment/$type/$definition"), SchemaOrg.SAME_AS.uri, cacheObject))

//        if (!hash.isNullOrEmpty()) {
//            quads.add(Quad(LocalQuadValue(hash), SchemaOrg.SAME_AS.uri, cacheObject))
//        }

        return objectStore.get(cachedObjectId)
    }

    fun findInQuad(value: String): String? {
        quads.filter(listOf(LocalQuadValue(value)), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
            val cached = it.`object` as LocalQuadValue
            return cached.uri
        }
        return null
    }

}