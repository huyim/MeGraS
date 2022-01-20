package org.megras.data.fs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.megras.data.mime.MimeType
import org.megras.util.HashUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class FileSystemObjectStore(objectStoreBase: String) {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val objectStoreBaseFile = File(objectStoreBase)

    init {
        objectStoreBaseFile.mkdirs()

        if (!objectStoreBaseFile.isDirectory) {
            logger.error("object store base '${objectStoreBaseFile.absoluteFile}' is not a directory")
        }
        if (!objectStoreBaseFile.canWrite()) {
            logger.error("object store base '${objectStoreBaseFile.absoluteFile}' is not writable")
        }
    }


    @Throws(FileNotFoundException::class, IOException::class)
    fun store(file: File): StoredObjectDescriptor {

        if (!file.exists()) {
            throw FileNotFoundException("input file '${file.absolutePath}' not found")
        }

        if (!file.canRead()) {
            throw IOException("cannot read file '${file.absolutePath}'")
        }

        val id = StoredObjectId(HashUtil.hashToBase32(file.inputStream()).dropLast(4)) //last four are always '='
        val descriptor = StoredObjectDescriptor(id, MimeType.mimeType(file.extension), file.length())

        store(file.inputStream(), descriptor)

        return descriptor

    }

    fun store(stream: InputStream, descriptor: StoredObjectDescriptor) {

        val target = storageFile(descriptor.id)
        target.parentFile.mkdirs()

        stream.copyTo(
            storageFile(descriptor.id).outputStream()
        )
        storeDescriptor(descriptor)
    }

    fun get(id: StoredObjectId): ObjectStoreResult? {
        val descriptorFile = descriptorFile(id)
        val target = storageFile(id)

        if (!descriptorFile.exists() || !descriptorFile.canRead()){
            return null
        }

        if (!target.exists() || !target.canRead()){
            return null
        }

        val descriptor = Json.decodeFromString(StoredObjectDescriptor.serializer(), descriptorFile.readText(Charsets.UTF_8))

        return ObjectStoreResult(descriptor, target)

    }

    private fun storeDescriptor(descriptor: StoredObjectDescriptor) {
        val target = descriptorFile(descriptor.id)
        target.writeText(descriptor.toJSON(), Charsets.UTF_8)
    }

    private fun storageFile(id: StoredObjectId): File = File(objectStoreBaseFile, "${id.id.substring(0, 2)}/${id.id.substring(2, 4)}/$id")

    private fun descriptorFile(id: StoredObjectId): File = File(storageFile(id).parentFile, "${id}.meta")

}