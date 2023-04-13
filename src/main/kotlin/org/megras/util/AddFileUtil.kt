package org.megras.util

import com.github.kokorin.jaffree.ffmpeg.ChannelInput
import com.github.kokorin.jaffree.ffmpeg.ChannelOutput
import com.github.kokorin.jaffree.ffmpeg.FFmpeg
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.megras.data.fs.FileSystemObjectStore
import org.megras.data.fs.StoredObjectDescriptor
import org.megras.data.fs.file.PseudoFile
import org.megras.data.graph.Quad
import org.megras.data.graph.StringValue
import org.megras.data.mime.MimeType
import org.megras.data.model.MediaType
import org.megras.data.schema.MeGraS
import org.megras.graphstore.MutableQuadSet
import org.megras.id.IdUtil
import org.megras.id.ObjectId
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object AddFileUtil {

    fun addFile(objectStore: FileSystemObjectStore, quads: MutableQuadSet, file: PseudoFile): ObjectId {

        //store raw
        val descriptor = objectStore.store(file)
        val oid = IdUtil.generateId(file)

        quads.add(Quad(oid, MeGraS.RAW_ID.uri, StringValue(descriptor.id.id)))
        quads.add(Quad(oid, MeGraS.MIME_TYPE.uri, StringValue(descriptor.mimeType.mimeString)))
        quads.add(Quad(oid, MeGraS.MEDIA_TYPE.uri, StringValue(MediaType.mimeTypeMap[descriptor.mimeType]!!.name)))
        quads.add(Quad(oid, MeGraS.FILE_NAME.uri, StringValue(file.name)))

        //generate and store canonical
        val canonical = generateCanonicalRepresentation(objectStore, descriptor)
        quads.add(Quad(oid, MeGraS.CANONICAL_ID.uri, StringValue(canonical.id.id)))

        return oid
    }


    private fun generateCanonicalRepresentation(
        objectStore: FileSystemObjectStore,
        rawDescriptor: StoredObjectDescriptor
    ): StoredObjectDescriptor {

        return when (rawDescriptor.mimeType) {

            //images to keep
            MimeType.PNG -> rawDescriptor

            //images to transform
            MimeType.JPEG_I,
            MimeType.BMP,
            MimeType.GIF,
            MimeType.SVG,
            MimeType.TIFF -> {

                try {
                    val buffered = ImageIO.read(objectStore.get(rawDescriptor.id)!!.inputStream())

                    val rgbaImage = BufferedImage(buffered.width, buffered.height, BufferedImage.TYPE_INT_ARGB)
                    val g = rgbaImage.createGraphics()
                    g.drawImage(buffered, 0, 0, null)
                    g.dispose()

                    val outStream = ByteArrayOutputStream()
                    ImageIO.write(rgbaImage, "PNG", outStream)

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
            MimeType.OGG_A,
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
            MimeType.WMA -> rawDescriptor

            MimeType.MKV,
            MimeType.WEBM,
            MimeType.MOV,
            MimeType.MP4,
            MimeType.AVI,
            MimeType.OGG -> {
                try {
                    val videoStream = objectStore.get(rawDescriptor.id)!!.byteChannel()
                    val outStream = SeekableInMemoryByteChannel()
                    FFmpeg.atPath()
                        .addInput(ChannelInput.fromChannel(videoStream))
                        .addArguments("-c:v", "libvpx-vp9")
                        .addArguments("-c:a", "libvorbis")
                        .setOverwriteOutput(true)
                        .addOutput(ChannelOutput.toChannel("", outStream).setFormat("matroska"))
                        .execute()

                    val buf = outStream.array()
                    val inStream = ByteArrayInputStream(buf)
                    val id = objectStore.idFromStream(inStream)

                    inStream.reset()

                    val descriptor = StoredObjectDescriptor(
                        id,
                        MimeType.MKV,
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

            //raw and text types to keep
            MimeType.OCTET_STREAM,
            MimeType.CSS,
            MimeType.CSV,
            MimeType.HTML,
            MimeType.JS,
            MimeType.JSON,
            MimeType.TEXT,
            MimeType.PDF,
            MimeType.OBJ -> rawDescriptor
        }

    }

}