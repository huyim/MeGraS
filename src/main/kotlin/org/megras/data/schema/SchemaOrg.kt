package org.megras.data.schema

import org.megras.data.HasString

enum class SchemaOrg(private val suffix: String): HasString {

    SAME_AS("sameAs")

    ;

    override val string: String
        get() = "${prefix}${suffix}"

    companion object {
        private const val prefix = "http://schema.org/"
    }

}