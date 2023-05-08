package org.megras.segmentation.type

import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType

abstract class ReductionalSegmentation : Segmentation {
    override val segmentationClass = SegmentationClass.REDUCE
    override var bounds: SegmentationBounds = SegmentationBounds()
}

abstract class ChannelSegmentation(val selection: List<String>) : ReductionalSegmentation() {

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is ChannelSegmentation) return false
        return this.contains(rhs) && rhs.contains(this)
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is ChannelSegmentation) return false
        return rhs.selection.all { this.selection.contains(it) }
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is ChannelSegmentation) return true
        return this.selection.intersect(rhs.selection.toSet()).isNotEmpty()
    }
}

class StreamChannel(selection: List<String>) : ChannelSegmentation(selection) {
    override val segmentationType: SegmentationType = SegmentationType.CHANNEL

    override fun getDefinition(): String = selection.joinToString(",")
}

class ColorChannel(selection: List<String>) : ChannelSegmentation(selection) {
    override val segmentationType: SegmentationType = SegmentationType.COLOR

    override fun getDefinition(): String = selection.joinToString(",")
}

class Frequency(val interval: Interval<Int>) : ReductionalSegmentation() {
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

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return true
        return this.interval.high >= rhs.interval.low && this.interval.low <= rhs.interval.high
    }

    override fun getDefinition(): String = "$interval.low-$interval.high"
}