package org.megras.segmentation

import java.awt.Shape
import java.util.*


class SegmentationBounds {

    private var bounds = DoubleArray(6) { Double.NEGATIVE_INFINITY }
    var dimensions = 0

    constructor()

    constructor(dimensions: Int) {
        this.dimensions = dimensions
    }

    constructor(shape: Shape) {
        this.bounds = doubleArrayOf(
            shape.bounds.minX.coerceAtLeast(0.0), shape.bounds.maxX.coerceAtLeast(0.0),
            shape.bounds.minY.coerceAtLeast(0.0), shape.bounds.maxY.coerceAtLeast(0.0),
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        dimensions = 2
    }

    constructor(minZ: Double, maxZ: Double) {
        this.bounds = doubleArrayOf(
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            minZ.coerceAtLeast(0.0), maxZ.coerceAtLeast(0.0))
        dimensions = 1
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double) {
        this.bounds = doubleArrayOf(
            minX.coerceAtLeast(0.0), maxX.coerceAtLeast(0.0),
            minY.coerceAtLeast(0.0), maxY.coerceAtLeast(0.0),
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        dimensions = 2
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double, minZ: Double, maxZ: Double) {
        this.bounds = doubleArrayOf(
            minX.coerceAtLeast(0.0), maxX.coerceAtLeast(0.0),
            minY.coerceAtLeast(0.0), maxY.coerceAtLeast(0.0),
            minZ.coerceAtLeast(0.0), maxZ.coerceAtLeast(0.0))
        dimensions = 3
    }

    fun contains(rhs: SegmentationBounds): Boolean {
        return this.bounds[0] <= rhs.bounds[0] && this.bounds[1] >= rhs.bounds[1] &&
                this.bounds[2] <= rhs.bounds[2] && this.bounds[3] >= rhs.bounds[3] &&
                this.bounds[4] <= rhs.bounds[4] && this.bounds[5] >= rhs.bounds[5]
    }

    fun intersects(rhs: SegmentationBounds): Boolean {
        return (this.bounds[0] <= rhs.bounds[1] && this.bounds[1] >= rhs.bounds[0]) ||
                (this.bounds[2] <= rhs.bounds[3] && this.bounds[3] >= rhs.bounds[2]) ||
                (this.bounds[4] <= rhs.bounds[5] && this.bounds[5] >= rhs.bounds[4])
    }

    fun translate(by: SegmentationBounds) {
        val result = DoubleArray(6)
        Arrays.setAll(result) { i: Int -> this.bounds[i] + by.bounds[i] }
        bounds = result
    }

    fun getMinX(): Double = bounds[0]

    fun getMinY(): Double = bounds[2]

    fun getMinZ(): Double = bounds[4]

    fun getXBounds(): DoubleArray = bounds.copyOfRange(0, 2)

    fun getYBounds(): DoubleArray = bounds.copyOfRange(2, 4)

    fun getZBounds(): DoubleArray = bounds.copyOfRange(4, 6)

    override fun toString() = bounds.joinToString(",")
}