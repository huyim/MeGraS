package org.megras.segmentation.type

import org.davidmoten.hilbert.HilbertCurve
import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.roundToLong

data class Interval<T : Number>(val low: T, val high: T)

abstract class OneDimensionalSegmentation : Segmentation {
    abstract val intervals: List<Interval<*>>

    override fun equivalentTo(rhs: Segmentation): Boolean {
        return this.contains(rhs) && rhs.contains(this)
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is OneDimensionalSegmentation) return false

        // All rhs intervals are contained in some intervals
        return rhs.intervals.all { j ->
            this.intervals.any { i -> i.low <= j.low && j.high <= i.high }
        }
    }
}

private operator fun Number.compareTo(low: Number): Int {
    if (this is Int) return this.compareTo(low.toInt())
    if (this is Double) return this.compareTo(low.toDouble())
    if (this is Long) return this.compareTo(low.toLong())
    return this.toDouble().compareTo(low.toDouble())
}

class Hilbert(val dimensions: Int, val order: Int, override var intervals: List<Interval<Long>>) : OneDimensionalSegmentation() {
    override val segmentationType: SegmentationType = SegmentationType.HILBERT
    override val segmentationClass: SegmentationClass = when (dimensions) {
        2 -> SegmentationClass.SPACE
        3 -> SegmentationClass.SPACETIME
        else -> throw IllegalArgumentException("Dimension not supported.")
    }
    override var bounds = SegmentationBounds(dimensions)

    private val hilbertCurve = HilbertCurve.small().bits(order).dimensions(dimensions)
    private val dimensionSize = (2.0).pow(order) - 1

    private var relativeTimestamp: Double? = null

    init {
        require(intervals.all { it.low <= it.high }) {
            throw IllegalArgumentException("Ranges are not valid.")
        }

        require(intervals.all { it.high <= hilbertCurve.maxIndex() }) {
            throw IllegalArgumentException("Range is out of bounds.")
        }
    }

    fun toImageMask(width: Int, height: Int, t: Double? = null): ImageMask {
        relativeTimestamp = t
        val mask = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (isIncluded(x.toDouble() / width, y.toDouble() / height)) {
                    mask.setRGB(x, y, Color.WHITE.rgb)
                }
            }
        }
        return ImageMask(mask)
    }

    private fun isIncluded(vararg relativeCoords: Double): Boolean {

        // Translate to hilbert space
        val hilbertCoords = relativeCoords.map { (it * dimensionSize).roundToLong() }.toMutableList()

        if (relativeTimestamp != null) {
            hilbertCoords.add((relativeTimestamp!! * dimensionSize).roundToLong())
        }

        val hilbertIndex = hilbertCurve.index(*hilbertCoords.toLongArray())
        val found = intervals.find { i -> i.low <= hilbertIndex && hilbertIndex <= i.high }

        return found != null
    }

    override fun toString(): String = "segment/hilbert/${dimensions},${order}," + intervals.joinToString(",") {"${it.low}-${it.high}"}
}

class Time(override var intervals: List<Interval<Double>>) : OneDimensionalSegmentation(), Translatable {
    override val segmentationType: SegmentationType = SegmentationType.TIME
    override val segmentationClass = SegmentationClass.TIME
    override var bounds = SegmentationBounds(intervals.first().low, intervals.last().high)

    override fun translate(by: SegmentationBounds) {
        if (by.dimensions == 1) {
            intervals = intervals.map { Interval(it.low + by.getMinX(), it.high + by.getMinX()) }
            bounds.translate(by)
        }
    }

    fun getIntervalsToDiscard(): List<Interval<Double>> {
        val newIntervals = mutableListOf<Interval<Double>>()

        for (i in 0 until intervals.size - 1) {
            newIntervals.add(Interval(intervals[i].high, intervals[i + 1].low))
        }
        return newIntervals
    }

    override fun toString(): String = "segment/time/" + intervals.joinToString(",") { "${it.low}-${it.high}" }
}

class Character(override var intervals: List<Interval<Int>>) : OneDimensionalSegmentation(), Translatable {
    override val segmentationType = SegmentationType.CHARACTER
    override val segmentationClass = SegmentationClass.TIME
    override var bounds = SegmentationBounds(intervals.first().low.toDouble(), intervals.last().high.toDouble())

    override fun translate(by: SegmentationBounds) {
        if (by.dimensions == 1) {
            intervals = intervals.map { Interval(it.low + by.getMinX().toInt(), it.high + by.getMinX().toInt()) }
            bounds.translate(by)
        }
    }

    override fun toString(): String = "segment/character/" + intervals.joinToString(",") { "${it.low}-${it.high}" }
}

class Page(override var intervals: List<Interval<Int>>) : OneDimensionalSegmentation(), Translatable {
    override val segmentationType = SegmentationType.PAGE
    override val segmentationClass = SegmentationClass.TIME
    override var bounds = SegmentationBounds(intervals.first().low.toDouble(), intervals.last().high.toDouble())

    override fun translate(by: SegmentationBounds) {
        if (by.dimensions == 1) {
            intervals = intervals.map { Interval(it.low + by.getMinX().toInt(), it.high + by.getMinX().toInt()) }
            bounds.translate(by)
        }
    }

    override fun toString(): String = "segment/page/" + intervals.joinToString(",") { "${it.low}-${it.high}" }
}