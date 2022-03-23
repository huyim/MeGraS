package org.megras.data.schema

enum class MeGraS(private val suffix: String) {


    RAW_ID("rawId"),
    MEDIA_TYPE("mediaType"),
    MIME_TYPE("mimeType"),
    CANONICAL_ID("canonicalId"), //raw id of canonical representation
    CACHE("cache")


    ;

    val string: String
    get() = "${prefix}${suffix}"

    companion object {
        private const val prefix = "http://megras.org/schema#"
    }

}