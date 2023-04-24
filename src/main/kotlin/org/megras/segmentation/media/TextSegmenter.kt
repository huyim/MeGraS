package org.megras.segmentation.media

import org.megras.segmentation.type.Mask
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.SegmentationType
import org.megras.segmentation.type.Time
import java.io.InputStream


object TextSegmenter {

    fun segment(text: InputStream, segmentation: Segmentation): ByteArray? = try {
        when(segmentation.segmentationType) {
            SegmentationType.TIME -> segmentTime(text, segmentation as Time)
            SegmentationType.MASK -> segmentMask(text, segmentation as Mask)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentTime(text: InputStream, time: Time) : ByteArray? {
        val textBytes = text.readBytes()

        val indices = mutableListOf<Int>()
        time.intervals.forEach { i ->
            if (i.low < textBytes.size && i.high <= textBytes.size) {
                (i.low.toInt() until i.high.toInt()).forEach {j -> indices.add(j)}
            } else {
                return null
            }
        }

        return textBytes.sliceArray(indices)
    }

    private fun segmentMask(text: InputStream, mask: Mask): ByteArray? {
        val textBytes = text.readBytes()

        if (textBytes.size != mask.mask.size()) {
            return null
        }

        val indices = mutableListOf<Int>()
        for (i in 0 until mask.mask.size()) {
            if (mask.mask[i]) {
                indices.add(i)
            }
        }

        return textBytes.sliceArray(indices)
    }
}