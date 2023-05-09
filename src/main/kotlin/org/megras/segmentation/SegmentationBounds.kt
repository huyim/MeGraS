package org.megras.segmentation

import java.awt.Shape
import java.util.*


class SegmentationBounds {

    private var bounds = DoubleArray(6) { Double.NaN }
    var dimensions = 0

    constructor()

    constructor(dimensions: Int) {
        this.dimensions = dimensions
    }

    constructor(boundString: String) {
        bounds = boundString.split(",").map {
            if (it == "-") {
                Double.NaN
            } else {
                it.toDouble()
            }
        }.toDoubleArray()
    }

    constructor(shape: Shape) {
        this.bounds = doubleArrayOf(
            shape.bounds.minX.coerceAtLeast(0.0), shape.bounds.maxX.coerceAtLeast(0.0),
            shape.bounds.minY.coerceAtLeast(0.0), shape.bounds.maxY.coerceAtLeast(0.0),
            Double.NaN, Double.NaN)
        dimensions = 2
    }

    constructor(minT: Double, maxT: Double) {
        this.bounds = doubleArrayOf(
            Double.NaN, Double.NaN,
            Double.NaN, Double.NaN,
            minT.coerceAtLeast(0.0), maxT.coerceAtLeast(0.0))
        dimensions = 1
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double) {
        this.bounds = doubleArrayOf(
            minX.coerceAtLeast(0.0), maxX.coerceAtLeast(0.0),
            minY.coerceAtLeast(0.0), maxY.coerceAtLeast(0.0),
            Double.NaN, Double.NaN)
        dimensions = 2
    }

    constructor(minX: Double, maxX: Double, minY: Double, maxY: Double, minT: Double, maxT: Double) {
        this.bounds = doubleArrayOf(
            minX.coerceAtLeast(0.0), maxX.coerceAtLeast(0.0),
            minY.coerceAtLeast(0.0), maxY.coerceAtLeast(0.0),
            minT.coerceAtLeast(0.0), maxT.coerceAtLeast(0.0))
        dimensions = 3
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SegmentationBounds) return false
        return this.dimensions == other.dimensions && this.bounds.contentEquals(other.bounds)
    }

    fun contains(rhs: SegmentationBounds): Boolean {
        if (this.dimensions != rhs.dimensions) return false
        return when (dimensions) {
            1 -> this.bounds[4] <= rhs.bounds[4] && this.bounds[5] >= rhs.bounds[5]
            2 -> this.bounds[0] <= rhs.bounds[0] && this.bounds[1] >= rhs.bounds[1] &&
                    this.bounds[2] <= rhs.bounds[2] && this.bounds[3] >= rhs.bounds[3]
            3 -> this.bounds[0] <= rhs.bounds[0] && this.bounds[1] >= rhs.bounds[1] &&
                    this.bounds[2] <= rhs.bounds[2] && this.bounds[3] >= rhs.bounds[3] &&
                    this.bounds[4] <= rhs.bounds[4] && this.bounds[5] >= rhs.bounds[5]
            else -> false
        }
    }

    fun orthogonalTo(rhs: SegmentationBounds): Boolean {
        return !(
                    (!this.bounds[0].isNaN() && !rhs.bounds[0].isNaN()) ||
                    (!this.bounds[2].isNaN() && !rhs.bounds[2].isNaN()) ||
                    (!this.bounds[4].isNaN() && !rhs.bounds[4].isNaN())
                )
    }

    fun translate(by: SegmentationBounds) {
        val result = DoubleArray(6)
        Arrays.setAll(result) { i: Int -> this.bounds[i] + by.bounds[i] }
        bounds = result
    }

    fun getMinX(): Double = bounds[0]

    fun getMinY(): Double = bounds[2]

    fun getMinT(): Double = bounds[4]

    fun getXBounds(): DoubleArray = bounds.copyOfRange(0, 2)

    fun getYBounds(): DoubleArray = bounds.copyOfRange(2, 4)

    fun getTBounds(): DoubleArray = bounds.copyOfRange(4, 6)

    override fun toString() = bounds.map { if (it.isNaN()) {"-"} else {it} }.joinToString(",")
}