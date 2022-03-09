package org.megras.graphstore

import org.megras.data.graph.Quad

open class BasicQuadSet(private val quads: Set<Quad>) : QuadSet, Set<Quad> by quads {

    constructor() : this(setOf())

    override fun getId(id: String): Quad? = quads.find { it.id == id }

    override fun filterSubject(subject: String): QuadSet = BasicQuadSet(quads.filter { it.subject == subject }.toSet())

    override fun filterPredicate(predicate: String): QuadSet = BasicQuadSet(quads.filter { it.predicate == predicate }.toSet())

    override fun filterObject(`object`: String): QuadSet = BasicQuadSet(quads.filter { it.`object` == `object` }.toSet())

    override fun filter(
        subjects: Collection<String>?,
        predicates: Collection<String>?,
        objects: Collection<String>?
    ): QuadSet {
        //if all attributes are unfiltered, do not filter
        if (subjects == null && predicates == null && objects == null) {
            return this
        }

        //if one attribute has no matches, return empty set
        if (subjects?.isEmpty() == true || predicates?.isEmpty() == true || objects?.isEmpty() == true) {
            return BasicQuadSet(setOf())
        }

        return BasicQuadSet(quads.filter {
            subjects?.contains(it.subject) ?: true &&
                    predicates?.contains(it.predicate) ?: true &&
                    objects?.contains(it.`object`) ?: true

        }.toSet())
    }

    override fun toMutable(): MutableQuadSet = BasicMutableQuadSet(quads.toMutableSet())

    override fun toSet(): Set<Quad> = quads

    override fun plus(other: QuadSet): QuadSet = BasicQuadSet(quads + other.toSet())


}