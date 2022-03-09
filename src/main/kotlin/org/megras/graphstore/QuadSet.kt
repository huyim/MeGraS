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

    /**
     * returns a [QuadSet] only containing subjects, predicates, and objects specified in the supplied collections.
     * null serves as 'any' selector
     */
    fun filter(subjects: Collection<String>?, predicates: Collection<String>?, objects: Collection<String>?): QuadSet

    fun toMutable(): MutableQuadSet

    fun toSet(): Set<Quad>

    operator fun plus(other: QuadSet): QuadSet

}