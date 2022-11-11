package org.megras.data.graph

data class Quad(val id: Long? = null, val subject: QuadValue, val predicate: QuadValue, val `object`: QuadValue) {
    constructor(subject: QuadValue, predicate: QuadValue, `object`: QuadValue) : this(null, subject, predicate, `object`)

}
