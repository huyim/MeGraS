package org.megras.segmentation

import java.util.*

object SegmentationUtil {

    fun shouldSwap(first: SegmentationType, second: SegmentationType): Boolean {
        return (first != SegmentationType.TIME && second == SegmentationType.TIME) ||
            (first == SegmentationType.CHANNEL && second != SegmentationType.CHANNEL)
    }

    fun parseSegmentationType(name: String): SegmentationType? =
        try {
            SegmentationType.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            null //not found
        }

    fun parseSegmentation(segmentType: String, segmentDefinition: String): Segmentation? {

        val type = parseSegmentationType(segmentType)
        return when (type) {
            SegmentationType.RECT -> {
                val coords = segmentDefinition.split(",").mapNotNull {
                    it.trim().toDoubleOrNull()
                }

                if (coords.size == 4) {
                    Rect(coords[0], coords[1], coords[2], coords[3])
                } else {
                    null
                }
            }

            /**
             * (x,y),(x,y),...,(x,y)
             */
            SegmentationType.POLYGON -> {
                val points = parsePointPairs(segmentDefinition)
                val finalPoints = points.filterNotNull()

                if (finalPoints.size == points.size) {
                    Polygon(finalPoints)
                } else {
                    null
                }
            }
            SegmentationType.BEZIER -> {
                val points = parsePointPairs(segmentDefinition)
                val finalPoints = points.filterNotNull()

                if (finalPoints.size == points.size) {
                    BezierSpline(finalPoints)
                } else {
                    null
                }
            }
            SegmentationType.BSPLINE -> {
                val points = parsePointPairs(segmentDefinition)
                val finalPoints = points.filterNotNull()

                if (finalPoints.size == points.size) {
                    BSpline(finalPoints)
                } else {
                    null
                }
            }

            SegmentationType.PATH -> {
                SVGPath(segmentDefinition)
            }

            SegmentationType.MASK -> {
                var binaryString = ""
                if (segmentDefinition.matches(Regex("^[01]+$"))) {
                    binaryString = segmentDefinition
                } else {
                    /**
                    try {
                    val decoded = Base64.getDecoder().decode(definition)
                    binaryString = BigInteger(1, decoded).toString(2)
                    } catch (_: Exception) {}
                     **/
                }

                val mask = BitSet(binaryString.length)
                binaryString.forEachIndexed { i, b ->
                    if (b == '1') mask.set(i)
                }

                Mask(mask)
            }

            SegmentationType.HILBERT -> {
                val elements = segmentDefinition.split(",")
                val dimensions = elements[0].toIntOrNull()
                val order = elements[1].toIntOrNull()

                val ranges = mutableListOf<Interval<Long>>()
                elements.forEach { el ->
                    val range = el.split("-").map { it.toLong() }
                    when (range.size) {
                        1 -> ranges.add(Interval(range[0], range[0]))
                        2 -> ranges.add(Interval(range[0], range[1]))
                        else -> return null
                    }
                }

                ranges.removeAt(1) // order
                ranges.removeAt(0) // dimension

                if (dimensions != null && order != null) {
                    Hilbert(dimensions, order, ranges)
                } else {
                    null
                }
            }

            SegmentationType.CHANNEL -> {
                val channels = segmentDefinition.split(",")
                Channel(channels)
            }

            SegmentationType.FREQUENCY -> {
                val bounds = segmentDefinition.split(",").map { it.toIntOrNull() }

                if (bounds.size == 2 && bounds[0] != null && bounds[1] != null) {
                    Frequency(bounds[0]!!, bounds[1]!!)
                } else {
                    null
                }
            }

            SegmentationType.TIME -> {
                val elements = segmentDefinition.split(",")

                val intervals = mutableListOf<Interval<Long>>()
                elements.forEach { el ->
                    val range = el.split("-").map { it.trim().toLong() }
                    when (range.size) {
                        2 -> intervals.add(Interval(range[0], range[1]))
                        else -> return null
                    }
                }
                Time(intervals)
            }

            SegmentationType.PLANE -> {
                val params = segmentDefinition.split(",").mapNotNull {
                    it.trim().toDoubleOrNull()
                }

                if (params.size == 5) {
                    Plane(params[0], params[1], params[2], params[3], params[4] == 1.0)
                } else {
                    null
                }
            }

            /**
             * t0,(x,y),(x,y),...,(x,y),t1,(x,y),(x,y),...,(x,y),...,tn,(x,y),(x,y),...,(x,y)
             */
            SegmentationType.ROTOPOLYGON -> {
                buildRotoscopeSegment(SegmentationType.ROTOPOLYGON, segmentDefinition)
            }
            SegmentationType.ROTOBEZIER -> {
                buildRotoscopeSegment(SegmentationType.ROTOBEZIER, segmentDefinition)
            }
            SegmentationType.ROTOBSPLINE -> {
                buildRotoscopeSegment(SegmentationType.ROTOBSPLINE, segmentDefinition)
            }

            else -> null
        }
    }

    private fun parsePointPairs(input: String) : List<Pair<Double, Double>?> {
        return input.split("),").map { chunk ->
            val coords = chunk.replaceFirst("(", "").replace(")", "").split(",").map { it.toDoubleOrNull() }
            if (coords.any { it == null }) {
                null
            } else if (coords.size < 2) {
                null
            } else {
                coords[0]!! to coords[1]!!
            }
        }
    }

    private fun buildRotoscopeSegment(type: SegmentationType, input: String) : Rotoscope? {
        val parts = input.split(Regex("(?<=\\))(,)?(?=\\d)"))
        val rotoscopeList = mutableListOf<RotoscopePair>()

        parts.forEach { part ->
            val p = part.split(",(").flatMap { it.split("),")}.map { it.replace(")", "") }
            val timePoint = p[0].toDoubleOrNull()
            val vertices = p.subList(1, p.size).map { chunk ->
                val coords = chunk.split(",").map { it.toDoubleOrNull() }
                if (coords.any { it == null }) {
                    null
                } else if (coords.size < 2) {
                    null
                } else {
                    coords[0]!! to coords[1]!!
                }
            }
            val finalVertices = vertices.filterNotNull()
            if (timePoint != null && vertices.size == finalVertices.size) {
                rotoscopeList.add(RotoscopePair(timePoint, finalVertices))
            } else {
                return null
            }
        }
        return Rotoscope(type, rotoscopeList)
    }
}