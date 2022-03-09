package org.megras.graphstore

import org.megras.data.graph.Quad

interface QuadSet : Set<Quad> {

    /**
     * returns the [Quad] with the specified id in case it is contained within the [QuadSet]
     */
    fun getId(id: String): Quad?

    /**
     * returns a [QuadSet] only containing the [Quad]s with a specified subject
     */
    fun filterSubject(subject: String): QuadSet

    /**
     * returns a [QuadSet] only containing the [Quad]s with a specified predicate
     */
    fun filterPredicate(predicate: String): QuadSet

    /**
     * returns a [QuadSet] only containing the [Quad]s with a specified object
     */
    fun filterObject(`object`: String): QuadSet

}