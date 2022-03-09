package org.megras.graphstore

import org.megras.data.graph.Quad

open class BasicQuadSet(private val quads: Set<Quad>) : QuadSet, Set<Quad> by quads {

    override fun getId(id: String): Quad? = quads.find { it.id == id }

    override fun filterSubject(subject: String): QuadSet = BasicQuadSet(quads.filter { it.subject == subject }.toSet())

    override fun filterPredicate(predicate: String): QuadSet = BasicQuadSet(quads.filter { it.predicate == predicate }.toSet())

    override fun filterObject(`object`: String): QuadSet = BasicQuadSet(quads.filter { it.`object` == `object` }.toSet())

}