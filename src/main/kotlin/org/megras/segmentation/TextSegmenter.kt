package org.megras.segmentation

import java.io.InputStream


object TextSegmenter {

    fun segment(text: InputStream, segmentation: Segmentation): ByteArray? = try {
        when(segmentation.type) {
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
            if (i.first < textBytes.size && i.second <= textBytes.size) {
                (i.first until i.second).forEach {j -> indices.add(j)}
            } else {
                return null
            }
        }

        return textBytes.sliceArray(indices)
    }

    private fun segmentMask(text: InputStream, mask: Mask): ByteArray? {
        val textBytes = text.readBytes()

        if (textBytes.size != mask.mask.size) {
            return null
        }

        val indices = mutableListOf<Int>()
        for (i in mask.mask.indices) {
            if (mask.mask[i].compareTo(1) == 0) {
                indices.add(i)
            }
        }

        return textBytes.sliceArray(indices)
    }
}