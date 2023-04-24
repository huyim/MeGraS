package org.megras.segmentation.media

import org.apache.pdfbox.pdmodel.PDDocument
import org.megras.segmentation.type.Mask
import org.megras.segmentation.type.Page
import org.megras.segmentation.type.Segmentation
import org.megras.segmentation.SegmentationType


object DocumentSegmenter {

    fun segment(pdf: PDDocument, segmentation: Segmentation): PDDocument? = try {
        when(segmentation.segmentationType) {
            SegmentationType.TIME -> segmentPage(pdf, segmentation as Page)
            SegmentationType.MASK -> segmentMask(pdf, segmentation as Mask)
            else -> null
        }
    } catch (e: Exception) {
        //TODO log
        null
    }

    private fun segmentPage(pdf: PDDocument, time: Page) : PDDocument {
        (0 until pdf.numberOfPages).reversed().forEach { pageNumber ->
            val keep = time.intervals.any { pageNumber >= it.low && pageNumber <= it.high }
            if (!keep) {
                pdf.removePage(pageNumber)
            }
        }

        return pdf
    }

    private fun segmentMask(pdf: PDDocument, mask: Mask): PDDocument? {

        if (pdf.numberOfPages != mask.mask.size()) {
            return null
        }

        for (i in pdf.numberOfPages - 1 downTo 0) {
            if (!mask.mask[i]) {
                pdf.removePage(i)
            }
        }

        return pdf
    }
}