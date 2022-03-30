package org.megras.data.schema

import org.megras.data.HasString

enum class MeGraS(private val suffix: String) : HasString {


    RAW_ID("rawId"),
    MEDIA_TYPE("mediaType"),
    MIME_TYPE("mimeType"),
    CANONICAL_ID("canonicalId"), //raw id of canonical representation
    FILE_NAME("fileName")


    ;

    override val string: String
    get() = "${prefix}${suffix}"

    companion object {
        private const val prefix = "http://megras.org/schema#"
    }

}