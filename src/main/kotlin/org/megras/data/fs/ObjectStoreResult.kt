package org.megras.data.fs

import java.io.File

data class ObjectStoreResult(val descriptor: StoredObjectDescriptor, private val file: File) {

    fun inputStream() = file.inputStream()

}
