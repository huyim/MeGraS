package org.megras.api.rest.handlers

import io.javalin.http.Context
import org.megras.api.rest.PostRequestHandler
import org.megras.api.rest.RestErrorStatus
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.file.PseudoFile
import org.megras.graphstore.MutableQuadSet
import org.megras.util.AddFileUtil

class AddFileRequestHandler(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) :
    PostRequestHandler {

    override fun post(ctx: Context) {

        val files = ctx.uploadedFiles()

        if (files.isEmpty()) {
            throw RestErrorStatus(400, "no file")
        }

        val ids = files.associate { uploadedFile ->
            uploadedFile.filename to AddFileUtil.addFile(objectStore, quads, PseudoFile(uploadedFile))
        }

        ctx.json(ids)

    }
}