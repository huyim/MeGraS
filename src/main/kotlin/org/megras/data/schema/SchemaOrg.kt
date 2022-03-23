package org.megras.data.schema

enum class SchemaOrg(private val suffix: String) {

    SAME_AS("sameAs")

    ;

    val string: String
        get() = "${prefix}${suffix}"

    companion object {
        private const val prefix = "http://schema.org/"
    }

}