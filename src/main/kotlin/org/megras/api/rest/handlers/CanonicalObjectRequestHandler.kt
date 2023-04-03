package org.megras.api.rest.handlers

import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.ObjectStoreResult
import org.megras.data.fs.StoredObjectId
import org.megras.data.graph.StringValue
import org.megras.data.schema.MeGraS
import org.megras.graphstore.QuadSet
import org.megras.id.ObjectId

class CanonicalObjectRequestHandler(private val quads: QuadSet, private val objectStore: FileSystemObjectStore) {

    fun get(objectId: String): ObjectStoreResult {

        val canonicalId = quads.filter(
            setOf(ObjectId(objectId)),
            setOf(MeGraS.CANONICAL_ID.uri),
            null
        ).firstOrNull()?.`object` as? StringValue ?: throw RestErrorStatus.notFound

        val osId = StoredObjectId.of(canonicalId.value) ?: throw RestErrorStatus.notFound

        return objectStore.get(osId) ?: throw RestErrorStatus.notFound
    }
}