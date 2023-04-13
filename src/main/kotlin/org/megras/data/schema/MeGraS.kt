package org.megras.data.schema

import org.megras.data.graph.URIValue

enum class MeGraS(suffix: String) {

    RAW_ID("rawId"),
    MEDIA_TYPE("mediaType"),
    RAW_MIME_TYPE("rawMimeType"),
    CANONICAL_ID("canonicalId"), //raw id of canonical representation
    CANONICAL_MIME_TYPE("canonicalMimeType"),
    FILE_NAME("fileName"),
    SEGMENT_OF("segmentOf")
    ;

    companion object {
        private const val prefix = "http://megras.org/schema#"
    }

    val uri = URIValue(MeGraS.prefix, suffix)


}