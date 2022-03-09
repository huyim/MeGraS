package org.megras.graphstore

import org.megras.data.graph.Quad

interface MutableQuadSet : QuadSet, MutableSet<Quad> {
}