package org.megras.segmentation.type

import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.util.*


sealed interface Segmentation {
    val segmentationType: SegmentationType?
    val segmentationClass: SegmentationClass

    fun equivalentTo(rhs: Segmentation): Boolean
    fun contains(rhs: Segmentation): Boolean
    fun intersects(rhs: Segmentation): Boolean
    override fun toString(): String
}

interface Translatable {
    fun translate(by: Segmentation)
}

class Mask(val mask: BitSet) : Segmentation {
    override val segmentationType: SegmentationType = SegmentationType.MASK
    override val segmentationClass: SegmentationClass = SegmentationClass.SPACE
    override fun equivalentTo(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        return this.mask == rhs.mask
    }

    override fun contains(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        if (this.mask.size() != rhs.mask.size()) return false

        for (i in 0 until this.mask.size()) {
            if (rhs.mask[i] && !this.mask[i]) return false
        }
        return true
    }

    override fun intersects(rhs: Segmentation): Boolean {
        if (rhs !is Mask) return false
        return this.mask.intersects(rhs.mask)
    }

    override fun toString(): String = TODO()
}