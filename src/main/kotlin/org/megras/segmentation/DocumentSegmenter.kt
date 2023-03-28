package org.megras.segmentation

import org.apache.pdfbox.pdmodel.PDDocument


object DocumentSegmenter {

    fun segment(pdf: PDDocument, segmentation: Segmentation): PDDocument? = try {
        when(segmentation.type) {
            SegmentationType.TIME -> segmentTime(pdf, segmentation as Time)
            SegmentationType.MASK -> segmentMask(pdf, segmentation as Mask)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentTime(pdf: PDDocument, time: Time) : PDDocument {

        time.getTimePointsToDiscard(0, pdf.numberOfPages).reversed().forEach { i -> pdf.removePage(i) }

        return pdf
    }

    private fun segmentMask(pdf: PDDocument, mask: Mask): PDDocument? {

        if (pdf.numberOfPages != mask.mask.size) {
            return null
        }

        for (i in pdf.numberOfPages - 1 downTo 0) {
            if (mask.mask[i].compareTo(0) == 0) {
                pdf.removePage(i)
            }
        }

        return pdf
    }
}