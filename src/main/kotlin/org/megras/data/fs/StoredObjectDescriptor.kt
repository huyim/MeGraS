package org.megras.data.fs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.megras.data.mime.MimeType
import org.megras.segmentation.SegmentationBounds

internal val formatter = Json { allowSpecialFloatingPointValues = true }

@Serializable
data class StoredObjectDescriptor(val id: StoredObjectId, val mimeType: MimeType, val length: Long, val bounds: SegmentationBounds) {

    fun toJSON() = formatter.encodeToString(this)

}
