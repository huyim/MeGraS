package org.megras.data.graph

import org.megras.util.extensions.toBase64

data class Quad(val subject: String, val predicate: String, val `object`: String) {
    val id = "<$subject><$predicate><${`object`}>".toByteArray(Charsets.UTF_8).toBase64()
}
