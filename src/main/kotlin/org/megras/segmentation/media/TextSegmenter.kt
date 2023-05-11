package org.megras.segmentation.media

import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.type.Time
import java.io.InputStream


object TextSegmenter {

    fun segment(text: InputStream, segmentation: Segmentation): ByteArray? = try {
        if (segmentation is Time) {
            val textBytes = text.readBytes()

            val indices = mutableListOf<Int>()
            segmentation.intervals.forEach { i ->
                if (i.low < textBytes.size && i.high <= textBytes.size) {
                    (i.low.toInt() until i.high.toInt()).forEach { j -> indices.add(j) }
                } else {
                    return null
                }
            }
            textBytes.sliceArray(indices)
        }
        null
    } catch (e: Exception) {
        //TODO log
        null
    }
}