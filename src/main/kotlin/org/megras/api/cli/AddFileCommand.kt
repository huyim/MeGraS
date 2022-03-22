package org.megras.api.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectDescriptor
import org.megras.data.graph.Quad
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.graphstore.MutableQuadSet
import org.megras.id.IdUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class AddFileCommand(private val quads: MutableQuadSet, private val objectStore: FileSystemObjectStore) : CliktCommand(name = "add") {

    private val fileName: String by option("-f", "--File", help = "Path of file to be added")
        .required()

    override fun run() {

        val file = File(fileName)

        if (!file.exists() || !file.canRead()) {
            System.err.println("Cannot read file '${file.absolutePath}'")
            return
        }

        //store raw
        val descriptor = objectStore.store(file)
        val id = IdUtil.generateId(file)

        quads.add(Quad(id, MeGraS.RAW_ID.string, descriptor.id.id))
        quads.add(Quad(id, MeGraS.MIME_TYPE.string, descriptor.mimeType.mimeString))
        quads.add(Quad(id, MeGraS.MEDIA_TYPE.string, MediaType.mimeTypeMap[descriptor.mimeType]!!.name))

        //generate and store canonical
        val canonical = generateCanonicalRepresentation(objectStore, descriptor)
        quads.add(Quad(id, MeGraS.CANONICAL_ID.string, canonical.id.id))

        println("Added file '${file.absolutePath}' with id '${id}'")

    }

    private fun generateCanonicalRepresentation(objectStore: FileSystemObjectStore, rawDescriptor: StoredObjectDescriptor): StoredObjectDescriptor {

        return when (rawDescriptor.mimeType) {

            //images to keep
            MimeType.JPEG_I,
            MimeType.PNG -> rawDescriptor

            //images to transform
            MimeType.BMP,
            MimeType.GIF,
            MimeType.SVG,
            MimeType.TIFF -> {

                try {
                    val buffered = ImageIO.read(objectStore.get(rawDescriptor.id)!!.inputStream())
                    val outStream = ByteArrayOutputStream()
                    ImageIO.write(buffered, "PNG", outStream)

                    val buf = outStream.toByteArray()

                    val inStream = ByteArrayInputStream(buf)

                    val id = objectStore.idFromStream(inStream)

                    inStream.reset()

                    val descriptor = StoredObjectDescriptor(
                        id,
                        MimeType.PNG,
                        buf.size.toLong()
                    )

                    objectStore.store(inStream, descriptor)

                    //return
                    descriptor

                } catch (e: Exception) {
                    //TODO log
                    rawDescriptor
                }

            }

            //audio to keep
            MimeType.OGG,
            MimeType.MPEG_A,
            MimeType.AAC,
            MimeType.MP4_A -> rawDescriptor


            //audio to transform
            MimeType.ADP,
            MimeType.AIF,
            MimeType.AU,
            MimeType.MIDI,
            MimeType.WAV,
            MimeType.WAX,
            MimeType.WMA -> {
                TODO()
            }


            //raw and text types to keep
            MimeType.OCTET_STREAM,
            MimeType.CSS,
            MimeType.CSV,
            MimeType.HTML,
            MimeType.JS,
            MimeType.JSON,
            MimeType.TEXT -> rawDescriptor
        }

    }


}