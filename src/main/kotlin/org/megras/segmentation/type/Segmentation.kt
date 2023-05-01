package org.megras.segmentation.type

import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType


sealed interface Segmentation {
    val segmentationType: SegmentationType?
    val segmentationClass: SegmentationClass
    var bounds: SegmentationBounds

    /**
     * Attempts to compare equivalence of this segmentation to another one.
     * In cases where the segmentations could be equivalent, but not enough information is available, `false` is returned.
     */
    fun equivalentTo(rhs: Segmentation): Boolean

    /**
     * Attempts to determine if another segmentation is contained in this one.
     * In cases where the segmentations could be containing, but not enough information is available, `false` is returned.
     */
    fun contains(rhs: Segmentation): Boolean

    /**
     * Attempts to determine if this segmentation intersects another one.
     * In cases where the segmentations could be intersecting, but not enough information is available, `false` is returned.
     */
    fun intersects(rhs: Segmentation): Boolean

    override fun toString(): String
}

interface Translatable {
    fun translate(by: SegmentationBounds)
}