package org.megras.segmentation.type

import org.davidmoten.hilbert.HilbertCurve
import org.megras.segmentation.Bounds
import org.megras.segmentation.SegmentationClass
import org.megras.segmentation.SegmentationType
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.pow
import kotlin.math.roundToLong

class Hilbert(val dimensions: Int, val order: Int, var intervals: List<Interval<*>>) : Segmentation {
    override val segmentationType: SegmentationType = SegmentationType.HILBERT
    override val segmentationClass: SegmentationClass = when (dimensions) {
        2 -> SegmentationClass.SPACE
        3 -> SegmentationClass.SPACETIME
        else -> throw IllegalArgumentException("Dimension not supported.")
    }
    override var bounds = Bounds(dimensions)

    override fun equivalentTo(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(rhs: Segmentation): Boolean {
        TODO("Not yet implemented")
    }

    private val hilbertCurve = HilbertCurve.small().bits(order).dimensions(dimensions)
    private val dimensionSize = (2.0).pow(order) - 1

    private var relativeTimestamp: Double? = null

    init {
        require(intervals.all { it.low <= it.high }) {
            throw IllegalArgumentException("Ranges are not valid.")
        }

        require(intervals.all { it.high <= hilbertCurve.maxIndex() }) {
            throw IllegalArgumentException("Range is out of bounds.")
        }
    }

    fun toImageMask(width: Int, height: Int, t: Double? = null): ImageMask {
        relativeTimestamp = t
        val mask = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (isIncluded(x.toDouble() / width, y.toDouble() / height)) {
                    mask.setRGB(x, y, Color.WHITE.rgb)
                }
            }
        }
        return ImageMask(mask)
    }

    private fun isIncluded(vararg relativeCoords: Double): Boolean {

        // Translate to hilbert space
        val hilbertCoords = relativeCoords.map { (it * dimensionSize).roundToLong() }.toMutableList()

        if (relativeTimestamp != null) {
            hilbertCoords.add((relativeTimestamp!! * dimensionSize).roundToLong())
        }

        val hilbertIndex = hilbertCurve.index(*hilbertCoords.toLongArray())
        val found = intervals.find { i -> i.low <= hilbertIndex && hilbertIndex <= i.high }

        return found != null
    }

    override fun getDefinition(): String = "${dimensions},${order}," + intervals.joinToString(",") {"${it.low}-${it.high}"}
}