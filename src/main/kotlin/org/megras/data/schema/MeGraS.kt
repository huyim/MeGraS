package org.megras.data.schema

enum class MeGraS(private val suffix: String) {


    RAW_ID("rawId"),
    MEDIA_TYPE("mediaType"),
    MIME_TYPE("mimeType"),
    CANONICAL_ID("canonicalId")


    ;

    val string: String
    get() = "${prefix}${suffix}"

    companion object {
        private val prefix = "http://megras.org/schema#"
    }

}