package org.megras.data.schema

import org.megras.data.graph.URIValue

enum class MeGraS(suffix: String) {

    RAW_ID("rawId"),
    MEDIA_TYPE("mediaType"),
    MIME_TYPE("mimeType"),
    CANONICAL_ID("canonicalId"), //raw id of canonical representation
    FILE_NAME("fileName"),
    SEGMENT_OF("segmentOf"),
    QUERY_DISTANCE("queryDistance")
    ;

    companion object {
        private const val prefix = "http://megras.org/schema#"
    }

    val uri = URIValue(MeGraS.prefix, suffix)


}