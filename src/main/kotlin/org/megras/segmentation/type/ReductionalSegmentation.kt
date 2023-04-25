package org.megras.segmentation.type

import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType

interface ReductionalSegmentation : Segmentation {
    override val segmentationClass: SegmentationClass
        get() = SegmentationClass.REDUCE
}

class Channel(val selection: List<String>) : ReductionalSegmentation {
    override val segmentationType: SegmentationType = SegmentationType.CHANNEL

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is Channel) return false
        return this.contains(rhs) && rhs.contains(this)
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Channel) return false
        return rhs.selection.all { this.selection.contains(it) }
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Channel) return false

        return this.selection.intersect(rhs.selection.toSet()).isNotEmpty()
    }

    override fun toString(): String = "segment/channel/" + selection.joinToString(",")
}

class Frequency(val low: Int, val high: Int) : ReductionalSegmentation {
    override val segmentationType: SegmentationType = SegmentationType.FREQUENCY

    init {
        require(low <= high) {
            throw IllegalArgumentException("Frequency band is not valid.")
        }
    }

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false
        return this.low == rhs.low && this.high == rhs.high
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false
        return this.low <= rhs.low && this.high >= rhs.high
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Frequency) return false
        return this.high >= rhs.low && this.low <= rhs.high
    }

    override fun toString(): String = "segment/frequency/$low-$high"
}