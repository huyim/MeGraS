package org.megras.data.fs.file

import io.javalin.http.UploadedFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

sealed interface PseudoFile {

    val name: String

    val extension: String
        get() = name.substringAfterLast('.')

    fun inputStream(): InputStream

    fun bytes(): ByteArray

    fun length(): Long

}

class FilePseudoFile(private val file: File) : PseudoFile {

    init {
        if (!file.exists() || !file.canRead()) {
            throw IllegalArgumentException("Cannot access file '${file.absolutePath}'")
        }
    }

    override val name: String = file.name

    override fun inputStream(): InputStream = file.inputStream()

    override fun bytes(): ByteArray = file.readBytes()

    override fun length(): Long = file.length()

}

fun PseudoFile(file: File) = FilePseudoFile(file)

class ByteArrayPseudoFile(private val buf: ByteArray, override val name: String) : PseudoFile {

    override fun inputStream(): InputStream = ByteArrayInputStream(buf)

    override fun bytes(): ByteArray = buf.clone()

    override fun length(): Long = buf.size.toLong()

}

fun PseudoFile(buf: ByteArray, name: String) = ByteArrayPseudoFile(buf, name)

fun PseudoFile(uploadedFile: UploadedFile) = ByteArrayPseudoFile(uploadedFile.content().readAllBytes(), uploadedFile.filename())