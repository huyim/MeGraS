package org.megras.data.fs

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class StoredObjectId(val id: String) {

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

    override fun toString(): String = id
}