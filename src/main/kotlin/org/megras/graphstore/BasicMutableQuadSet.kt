package org.megras.graphstore

import org.megras.data.graph.Quad

class BasicMutableQuadSet(private val quads: MutableSet<Quad>) : BasicQuadSet(quads), MutableQuadSet, MutableSet<Quad> by quads {

    constructor() : this(mutableSetOf())

    override fun toSet(): MutableSet<Quad> = this.quads

    override fun toMutable(): MutableQuadSet = this

    override fun plus(other: QuadSet): MutableQuadSet = BasicMutableQuadSet((this.quads + other.toSet()).toMutableSet())

    override fun contains(element: Quad): Boolean = quads.contains(element)

    override fun containsAll(elements: Collection<Quad>): Boolean = quads.containsAll(elements)

    override fun isEmpty(): Boolean = quads.isEmpty()

    override fun iterator(): MutableIterator<Quad> = quads.iterator()

    override val size: Int
        get() = quads.size
}