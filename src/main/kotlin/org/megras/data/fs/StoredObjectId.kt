package org.megras.data.fs

import kotlinx.serialization.Serializable
import org.megras.data.HasString

@JvmInline
@Serializable
value class StoredObjectId(val id: String) : HasString{

    init {
        require(id.length == 52) {"Invalid id"}
    }

    companion object {
        fun of(string: String): StoredObjectId? =
            try{
                StoredObjectId(string)
            } catch (e: IllegalArgumentException) {
                null
            }
    }

    override val string: String
        get() = id

    override fun toString(): String = id
}