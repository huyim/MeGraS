package org.megras.data.graph

import org.megras.data.HasString
import org.megras.util.extensions.toBase64

data class Quad(val subject: String, val predicate: String, val `object`: String) {

    constructor(subject: HasString, predicate: HasString, `object`: HasString) : this(subject.string, predicate.string, `object`.string)

    val id = "<$subject><$predicate><${`object`}>".toByteArray(Charsets.UTF_8).toBase64().replace("\\s".toRegex(), "")
}
