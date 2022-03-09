package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.GetRequestHandler
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectId
import org.megras.data.schema.MeGraS
import org.megras.graphstore.QuadSet

class CanonicalObjectRequestHandler(private val quads: QuadSet, private val objectStore: FileSystemObjectStore) : GetRequestHandler {

    override fun get(ctx: Context) {

        val id = ctx.pathParam("objectId")

        val rawId = quads.filter(setOf(id), setOf(MeGraS.RAW_ID.string), null).firstOrNull()?.`object`

        if (rawId == null) {
            ctx.status(404)
            ctx.result("not found")
            return
        }

        val osId = StoredObjectId.of(rawId)

        if (osId == null) {
            ctx.status(403)
            ctx.result("invalid id")
            return
        }

        val result = objectStore.get(osId)

        if (result == null) {
            ctx.status(404)
            ctx.result("Not found")
        } else {
            ctx.seekableStream(result.inputStream(), result.descriptor.mimeType.mimeString)
        }
    }


}