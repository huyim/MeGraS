package org.megras.segmentation.media

import org.apache.pdfbox.pdmodel.PDDocument
import org.megras.segmentation.type.Page
import org.megras.segmentation.type.Segmentation
import java.io.ByteArrayOutputStream
import java.io.InputStream

object DocumentSegmenter {

    fun segment(inputStream: InputStream, segmentation: Segmentation): ByteArray? = try {
        if (segmentation is Page) {
            val pdf = PDDocument.load(inputStream)
            (0 until pdf.numberOfPages).reversed().forEach { pageNumber ->
                val keep = segmentation.intervals.any { pageNumber >= it.low && pageNumber <= it.high }
                if (!keep) {
                    pdf.removePage(pageNumber)
                }
            }
            val out = ByteArrayOutputStream()
            pdf.save(out)
            pdf.close()
            out.toByteArray()
        }
        null
    } catch (e: Exception) {
        //TODO log
        null
    }
}