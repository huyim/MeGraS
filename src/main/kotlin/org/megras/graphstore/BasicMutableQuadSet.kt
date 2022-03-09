package org.megras.graphstore

import org.megras.data.graph.Quad

class BasicMutableQuadSet(private val quads: MutableSet<Quad>) : BasicQuadSet(quads), MutableQuadSet, MutableSet<Quad> by quads