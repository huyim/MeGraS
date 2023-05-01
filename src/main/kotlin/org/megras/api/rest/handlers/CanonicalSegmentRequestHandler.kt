package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.ObjectStoreResult
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
import org.megras.segmentation.SegmentationUtil
import org.megras.segmentation.media.*
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.type.Translatable
import org.megras.util.HashUtil
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

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
                nextSegmentation.translate(segmentation.bounds)
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

        if (lookInCache) {
            // check for exact path matches
            if (findPathInCache(ctx, currentPath, nextSegmentPath)) return

            // check for equivalent segmentations
            if (findEquivalentInCache(ctx, segmentation, currentPath, nextSegmentPath)) return
        }

        val storedObject = getStoredObjectInCache(ctx.pathParam("objectId"))

        val segment: ByteArray = when(MediaType.mimeTypeMap[storedObject.descriptor.mimeType]) {
            MediaType.TEXT -> TextSegmenter.segment(storedObject.inputStream(), segmentation)
            MediaType.IMAGE -> ImageSegmenter.segment(storedObject.inputStream(), segmentation)
            MediaType.AUDIO, MediaType.VIDEO -> AudioVideoSegmenter.segment(storedObject.byteChannel(), segmentation)
            MediaType.DOCUMENT -> DocumentSegmenter.segment(storedObject.inputStream(), segmentation)
            MediaType.MESH -> MeshSegmenter.segment(storedObject.inputStream(), segmentation)
            MediaType.UNKNOWN, null -> throw RestErrorStatus.unknownMediaType
        } ?: throw RestErrorStatus.invalidSegmentation

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
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_TYPE.uri, StringValue(segmentType)))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_DEFINITION.uri, StringValue(segmentDefinition)))
        quads.add(Quad(cacheObject, MeGraS.SEGMENT_BOUNDS.uri, StringValue(segmentation.bounds.toString())))
        quads.add(Quad(LocalQuadValue(currentPath), SchemaOrg.SAME_AS.uri, cacheObject))

        redirect(ctx, currentPath, nextSegmentPath)
    }

    private fun findPathInCache(ctx: Context, currentPath: String, nextSegmentPath: String?): Boolean {
        quads.filter(listOf(LocalQuadValue(currentPath)), listOf(SchemaOrg.SAME_AS.uri), null).forEach {
            val cached = it.`object` as LocalQuadValue
            redirect(ctx, cached.uri, nextSegmentPath)
            logger.info("found $currentPath in cache: ${cached.uri}")
            return true
        }
        return false
    }

    private fun findEquivalentInCache(ctx: Context, segmentation: Segmentation, currentPath: String, nextSegmentPath: String?): Boolean {
        quads.filter(
            null, listOf(MeGraS.SEGMENT_OF.uri), listOf(ObjectId(ctx.pathParam("objectId")))
        ).forEach { potentialMatch ->
            // go through all segments of the medium and check their bounds
            quads.filter(
                listOf(potentialMatch.subject), listOf(MeGraS.SEGMENT_BOUNDS.uri), listOf(StringValue(segmentation.bounds.toString()))
            ).forEach { _ ->
                // if the bounds match, check the segmentation
                val segmentTypeQuad = quads.filter(listOf(potentialMatch.subject), listOf(MeGraS.SEGMENT_TYPE.uri), null).firstOrNull()
                val potentialMatchType = segmentTypeQuad?.`object` as StringValue
                val segmentDefinitionQuad = quads.filter(listOf(potentialMatch.subject), listOf(MeGraS.SEGMENT_DEFINITION.uri), null).firstOrNull()
                val potentialMatchDefinition = segmentDefinitionQuad?.`object` as StringValue
                val potentialMatchSegmentation = SegmentationUtil.parseSegmentation(potentialMatchType.value, potentialMatchDefinition.value) ?: return@forEach

                if (segmentation.equivalentTo(potentialMatchSegmentation)) {
                    val cached = potentialMatch.subject as LocalQuadValue
                    quads.add(Quad(LocalQuadValue(currentPath), SchemaOrg.SAME_AS.uri, cached))
                    redirect(ctx, cached.uri, nextSegmentPath)
                    logger.info("found equivalent to $currentPath in cache: ${cached.uri}")
                    return true
                }
            }
        }
        return false
    }

    private fun getStoredObjectInCache(objectId: String): ObjectStoreResult {
        val canonicalId = quads.filter(
            setOf(ObjectId(objectId)),
            setOf(MeGraS.CANONICAL_ID.uri),
            null
        ).firstOrNull()?.`object` as? StringValue ?: throw RestErrorStatus.notFound
        val osId = StoredObjectId.of(canonicalId.value) ?: throw RestErrorStatus.notFound
        return objectStore.get(osId) ?: throw RestErrorStatus.notFound
    }

    private fun redirect(ctx: Context, currentPath: String, nextSegmentPath: String?) {
        if (nextSegmentPath != null) {
            ctx.redirect("/$currentPath/$nextSegmentPath")
        } else {
            ctx.redirect("/$currentPath")
        }
    }
}