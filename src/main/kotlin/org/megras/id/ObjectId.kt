package org.megras.id

import org.megras.data.HasString

@JvmInline
value class ObjectId(val id: String) : HasString {

    override val string: String
        get() = id

    override fun toString(): String = this.id
}