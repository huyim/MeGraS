package org.megras.segmentation.type

import org.megras.segmentation.Bounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType

data class Interval<T : Number>(val low: T, val high: T)

abstract class OneDimensionalSegmentation : Segmentation {
    abstract var intervals: List<Interval<*>>

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is OneDimensionalSegmentation) return false
        if (this.bounds != rhs.bounds) return false
        return this.intervals == rhs.intervals
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (!this.bounds.contains(rhs.bounds)) return false
        if (rhs is OneDimensionalSegmentation) {
            // All rhs intervals are contained in some intervals
            return rhs.intervals.all { j ->
                this.intervals.any { i -> i.low <= j.low && j.high <= i.high }
            }
        }
        // TODO: compare to ThreeDimensionalSegmentation
        return false
    }
}

operator fun Number.compareTo(low: Number): Int {
    if (this is Int) return this.compareTo(low.toInt())
    if (this is Double) return this.compareTo(low.toDouble())
    if (this is Long) return this.compareTo(low.toLong())
    return this.toDouble().compareTo(low.toDouble())
}

private operator fun Number.plus(other: Number): Number {
    if (this is Int) return this.plus(other.toInt())
    if (this is Double) return this.plus(other.toDouble())
    if (this is Long) return this.plus(other.toLong())
    return this.toDouble().plus(other.toDouble())
}

abstract class TemporalSegmentation(final override var intervals: List<Interval<*>>) : OneDimensionalSegmentation() {
    override val segmentationClass: SegmentationClass = SegmentationClass.TIME
    override var bounds = Bounds(intervals.first().low, intervals.last().high)

    override fun getDefinition(): String = intervals.joinToString(",") { "${it.low}-${it.high}" }
}

class Time(intervals: List<Interval<Double>>) : TemporalSegmentation(intervals) {
    override val segmentationType = SegmentationType.TIME

    override fun translate(by: Bounds): Segmentation {
        if (by.dimensions == 1) {
            return Time(intervals.map { Interval(it.low.toDouble() + by.getMinX(), it.high.toDouble() + by.getMinX()) })
        }
        return this
    }

    fun getIntervalsToDiscard(): List<Interval<Double>> {
        val newIntervals = mutableListOf<Interval<Double>>()

        for (i in 0 until intervals.size - 1) {
            newIntervals.add(Interval(intervals[i].high.toDouble(), intervals[i + 1].low.toDouble()))
        }
        return newIntervals
    }
}

class Character(intervals: List<Interval<Int>>) : TemporalSegmentation(intervals) {
    override val segmentationType = SegmentationType.CHARACTER

    override fun translate(by: Bounds): Segmentation {
        if (by.dimensions == 1) {
            return Character(intervals.map { Interval(it.low.toInt() + by.getMinX().toInt(), it.high.toInt() + by.getMinX().toInt()) })
        }
        return this
    }
}

class Page(intervals: List<Interval<Int>>) : TemporalSegmentation(intervals) {
    override val segmentationType = SegmentationType.PAGE

    override fun translate(by: Bounds): Segmentation {
        if (by.dimensions == 1) {
            return Page(intervals.map { Interval(it.low.toInt() + by.getMinX().toInt(), it.high.toInt() + by.getMinX().toInt()) })
        }
        return this
    }
}

class Frequency(val interval: Interval<Int>) : TemporalSegmentation(listOf(interval)) {
    override val segmentationType: SegmentationType = SegmentationType.FREQUENCY

    init {
        require(interval.low <= interval.high) {
            throw IllegalArgumentException("Frequency band is not valid.")
        }
    }

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false
        return this.interval.low == rhs.interval.low && this.interval.high == rhs.interval.high
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false
        return this.interval.low <= rhs.interval.low && this.interval.high >= rhs.interval.high
    }

    override fun orthogonalTo(rhs: Segmentation): Boolean {
        return rhs !is Frequency
    }

    override fun getDefinition(): String = "$interval.low-$interval.high"
}