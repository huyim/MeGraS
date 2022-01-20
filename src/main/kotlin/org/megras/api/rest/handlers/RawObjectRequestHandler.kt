package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectId

class RawObjectRequestHandler(private val objectStore: FileSystemObjectStore) {

    fun get(ctx: Context) {

        val id = StoredObjectId.of(ctx.pathParam("objectId"))

        if (id == null) {
            ctx.status(403)
            ctx.result("invalid id")
            return
        }

        val result = objectStore.get(id)
        if (result == null) {
            ctx.status(404)
            ctx.result("Not found")
        } else {
            ctx.seekableStream(result.inputStream(), result.descriptor.mimeType.mimeString)
        }

    }

}