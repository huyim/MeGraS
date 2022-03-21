package org.megras.api.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.graph.Quad
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.graphstore.MutableQuadSet
import org.megras.id.IdUtil
import java.io.File

class AddFileCommand(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) : CliktCommand(name = "add") {

    private val fileName: String by option("-f", "--File", help = "Path of file to be added")
        .required()

    override fun run() {

        val file = File(fileName)

        if (!file.exists() || !file.canRead()) {
            System.err.println("Cannot read file '${file.absolutePath}'")
            return
        }

        val descriptor = objectStore.store(file)
        val id = IdUtil.generateId(file)

        quads.add(Quad(id, MeGraS.RAW_ID.string, descriptor.id.id))
        quads.add(Quad(id, MeGraS.MIME_TYPE.string, descriptor.mimeType.mimeString))
        quads.add(Quad(id, MeGraS.MEDIA_TYPE.string, MediaType.mimeTypeMap[descriptor.mimeType]!!.name))

        println("Added file '${file.absolutePath}' with id '${id}'")

    }


}