package org.megras.data.fs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.megras.data.mime.MimeType

@Serializable
data class StoredObjectDescriptor(val id: StoredObjectId, val mimeType: MimeType, val length: Long) {

    fun toJSON() = Json.encodeToString(this)

}
