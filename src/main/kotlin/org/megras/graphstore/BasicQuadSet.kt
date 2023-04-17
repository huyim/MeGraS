package org.megras.graphstore

import org.megras.data.graph.Quad
import org.megras.data.graph.QuadValue
import org.megras.data.graph.StringValue
import org.megras.data.graph.VectorValue

open class BasicQuadSet(private val quads: Set<Quad>) : QuadSet, Set<Quad> by quads {

    constructor() : this(setOf())

    override fun getId(id: Long): Quad? = quads.find { it.id == id }

    override fun filterSubject(subject: QuadValue): QuadSet = BasicQuadSet(quads.filter { it.subject == subject }.toSet())

    override fun filterPredicate(predicate: QuadValue): QuadSet = BasicQuadSet(quads.filter { it.predicate == predicate }.toSet())

    override fun filterObject(`object`: QuadValue): QuadSet = BasicQuadSet(quads.filter { it.`object` == `object` }.toSet())

    override fun filter(
        subjects: Collection<QuadValue>?,
        predicates: Collection<QuadValue>?,
        objects: Collection<QuadValue>?
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
    override fun nearestNeighbor(predicate: QuadValue, `object`: VectorValue, count: Int, distance: Distance): QuadSet {
        TODO("Not yet implemented")
    }

    override fun textFilter(filterText: String): QuadSet = BasicQuadSet(quads.filter { it.`object` is StringValue && it.`object`.value.contains(filterText) }.toSet())


}