package org.megras.segmentation

import java.awt.Shape

class SegmentationBounds {

    private val bounds = DoubleArray(6)
    var dimensions = 0

    constructor()

    constructor(shape: Shape) {
        bounds[0] = shape.bounds.minX
        bounds[1] = shape.bounds.maxX
        bounds[2] = shape.bounds.minY
        bounds[3] = shape.bounds.maxY
        dimensions = 2
    }

    constructor(minX: Double, maxX: Double) {
        bounds[0] = minX
        bounds[1] = maxX
        dimensions = 1
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double) {
        bounds[0] = minX
        bounds[1] = maxX
        bounds[2] = minY
        bounds[3] = maxY
        dimensions = 2
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double, minZ: Double, maxZ: Double) {
        bounds[0] = minX
        bounds[1] = maxX
        bounds[2] = minY
        bounds[3] = maxY
        bounds[4] = minZ
        bounds[5] = maxZ
        dimensions = 2
    }

    fun getXBounds(): DoubleArray = bounds.copyOfRange(0, 2)

    fun getYBounds(): DoubleArray = bounds.copyOfRange(2, 4)

    fun getZBounds(): DoubleArray = bounds.copyOfRange(4, 6)

    override fun toString() = bounds.joinToString(",")
}