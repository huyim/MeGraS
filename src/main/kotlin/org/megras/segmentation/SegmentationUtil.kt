package org.megras.segmentation

object SegmentationUtil {

    fun equivalent(first: Segmentation, second: Segmentation): Boolean {

        if (first.type == second.type) {
            return first == second
        }
        if (first.type.ordinal > second.type.ordinal) {
            return equivalent(second, first)
        }

        return when (first.segmentClass) {
            SegmentationClass.SPACE -> {
                first as SpaceSegmentation
                when (second.segmentClass) {
                    SegmentationClass.SPACE -> {
                        second as SpaceSegmentation
                        first.area == second.area
                    }
                    else -> false
                }
            }
            // all others can only be equivalent to their own type
            else -> false
        }
    }

    fun contains(source: Segmentation, target: Segmentation): Boolean {

        return when(source.type) {
            SegmentationType.RECT, SegmentationType.POLYGON, SegmentationType.PATH, SegmentationType.BEZIER, SegmentationType.BSPLINE -> {
                source as SpaceSegmentation
                when (target.type) {
                    SegmentationType.RECT, SegmentationType.POLYGON, SegmentationType.PATH, SegmentationType.BEZIER, SegmentationType.BSPLINE -> {
                        target as SpaceSegmentation
                        target.area.subtract(source.area)
                        target.area.isEmpty
                    }
                    else -> false
                }
            }
            SegmentationType.PLANE -> {
                source as Plane
                when (target.type) {
                    SegmentationType.PLANE -> {
                        target as Plane
                        source.a == target.a && source.b == target.b && source.c == target.c && source.above == target.above
                    }
                    else -> false
                }
            }
            SegmentationType.MASK -> {
                source as Mask
                when (target.type) {
                    SegmentationType.MASK -> {
                        target as Mask
                        if (source.mask.size != target.mask.size) {
                            false
                        } else {
                            for (i in 0 until source.mask.size) {
                                if (target.mask[i].compareTo(1) == 0 && source.mask[i].compareTo(0) == 0) {
                                    return false
                                }
                            }
                            true
                        }
                    }
                    else -> false
                }
            }
            SegmentationType.CHANNEL -> {
                source as Channel
                when (target.type) {
                    SegmentationType.CHANNEL -> {
                        target as Channel
                        target.selection.all { source.selection.contains(it) }
                    }
                    else -> false
                }
            }
            SegmentationType.TIME -> {
                val sourcePoints = (source as Time).getTimePointsToSegment()
                when (target.type) {
                    SegmentationType.TIME -> {
                        val targetPoints = (target as Time).getTimePointsToSegment()
                        targetPoints.all { sourcePoints.contains(it) }
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    fun overlaps(source: Segmentation, target: Segmentation): Boolean {
        if (source.segmentClass != target.segmentClass) return false

        return when (source.segmentClass) {
            SegmentationClass.SPACE -> {
                source as SpaceSegmentation
                target as SpaceSegmentation
                source.area.intersect(target.area)
                source.area.isEmpty
            }

            SegmentationClass.TIME -> {
                source as TimeSegmentation
                target as TimeSegmentation
                source.intersect(target)
            }

            SegmentationClass.REDUCE -> {
                source as ReduceSegmentation
                target as ReduceSegmentation
                source.intersect(target)
            }
            else -> false
        }
    }

    fun translate(segmentation: Segmentation, by: Segmentation): Segmentation {
        return when (by.segmentClass) {
            SegmentationClass.SPACE -> {
                by as SpaceSegmentation
                when (segmentation.segmentClass) {
                    SegmentationClass.SPACE -> {
                        (segmentation as SpaceSegmentation).move(by.area.bounds.minX, by.area.bounds.minY)
                    }
                    else -> segmentation
                }
            }
            SegmentationClass.TIME -> {
                by as Time
                (segmentation as Time).move(by.intervals[0].first)
            }
            else -> segmentation
        }
    }

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
                } else if (coords.size >= 6) {
                    Rect(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5])
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

                val mask = ByteArray(binaryString.length)
                binaryString.forEachIndexed { i, b -> mask[i] = b.code.toByte() }

                Mask(mask)
            }

            SegmentationType.HILBERT -> {
                val elements = segmentDefinition.split(",")
                val dimensions = elements[0].toIntOrNull()
                val order = elements[1].toIntOrNull()

                val ranges = mutableListOf<Pair<Long, Long>>()
                elements.forEach { el ->
                    val range = el.split("-").map { it.toLong() }
                    when (range.size) {
                        1 -> ranges.add(range[0] to range[0])
                        2 -> ranges.add(range[0] to range[1])
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

            SegmentationType.TIME -> {
                val timepoints = segmentDefinition.split(",").mapNotNull {
                    it.trim().toIntOrNull()
                }

                val intervals = mutableListOf<Pair<Int, Int>>()
                if (timepoints.size % 2 == 0) {
                    for (i in 0 until timepoints.size / 2) {
                        intervals.add(Pair(timepoints[i * 2], timepoints[i * 2 + 1]))
                    }
                    Time(intervals)
                } else {
                    null
                }
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