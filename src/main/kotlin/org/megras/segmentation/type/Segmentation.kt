package org.megras.segmentation.type

import org.megras.segmentation.SegmentationBounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType


sealed interface Segmentation {
    val segmentationType: SegmentationType?
    val segmentationClass: SegmentationClass
    var bounds: SegmentationBounds

    fun equivalentTo(rhs: Segmentation): Boolean
    fun contains(rhs: Segmentation): Boolean
    fun intersects(rhs: Segmentation): Boolean
    override fun toString(): String
}

interface Translatable {
    fun translate(by: Segmentation)
}