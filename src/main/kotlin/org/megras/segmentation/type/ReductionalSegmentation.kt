package org.megras.segmentation.type

import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType

abstract class ReductionalSegmentation(val selection: List<String>) : Segmentation {
    override val segmentationClass = SegmentationClass.REDUCE
    override var bounds: SegmentationBounds = SegmentationBounds()

    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is ReductionalSegmentation) return false
        return this.contains(rhs) && rhs.contains(this)
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is ReductionalSegmentation) return false
        return rhs.selection.all { this.selection.contains(it) }
    }

    override fun orthogonalTo(rhs: Segmentation): Boolean {
        return rhs !is ReductionalSegmentation
    }
}

class StreamChannel(selection: List<String>) : ReductionalSegmentation(selection) {
    override val segmentationType: SegmentationType = SegmentationType.CHANNEL

    override fun getDefinition(): String = selection.joinToString(",")
}

class ColorChannel(selection: List<String>) : ReductionalSegmentation(selection) {
    override val segmentationType: SegmentationType = SegmentationType.COLOR

    override fun getDefinition(): String = selection.joinToString(",")
}