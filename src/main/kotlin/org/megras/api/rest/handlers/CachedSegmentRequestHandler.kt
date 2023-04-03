package org.megras.api.rest.handlers

import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.ObjectStoreResult
import org.megras.data.fs.StoredObjectId
import org.megras.data.graph.LocalQuadValue
import org.megras.data.graph.StringValue
import org.megras.data.schema.MeGraS
import org.megras.graphstore.QuadSet

class CachedSegmentRequestHandler(private val quads: QuadSet, private val objectStore: FileSystemObjectStore) {

    // /{objectId}/c/{segmentId}
    fun get(path: String): ObjectStoreResult {

        val canonicalId = quads.filter(
            setOf(LocalQuadValue(path)),
            setOf(MeGraS.CANONICAL_ID.uri),
            null
        ).firstOrNull()?.`object` as? StringValue ?: throw RestErrorStatus.notFound

        val osId = StoredObjectId.of(canonicalId.value) ?: throw RestErrorStatus.notFound

        return objectStore.get(osId) ?: throw RestErrorStatus.notFound
    }
}