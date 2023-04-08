package org.megras.segmentation

object SegmentationUtil {

    private val typeToClass = mapOf(
        SegmentationType.TIME to SegmentationClass.TIME,
        SegmentationType.RECT to SegmentationClass.SPACE,
        SegmentationType.POLYGON to SegmentationClass.SPACE,
        SegmentationType.PATH to SegmentationClass.SPACE,
        SegmentationType.SPLINE to SegmentationClass.SPACE,
        SegmentationType.PLANE to SegmentationClass.SPACE,
        SegmentationType.RECT to SegmentationClass.SPACE,
        SegmentationType.CHANNEL to SegmentationClass.REDUCE,
    )

    fun equivalent(first: Segmentation, second: Segmentation): Boolean {

        if (first.type == second.type) {
            return first == second
        }
        if (first.type.ordinal > second.type.ordinal) {
            return equivalent(second, first)
        }

        return when (first.type) {
            SegmentationType.RECT -> {
                first as Rect
                when (second.type) {
                    SegmentationType.POLYGON -> {
                        second as Polygon
                        second.toRect() == first
                    }
                    SegmentationType.PATH -> {
                        second as SVGPath
                        first.toShape() == second.shape
                    }
                    else -> false
                }
            }
            SegmentationType.POLYGON -> {
                first as Polygon
                when (second.type) {
                    SegmentationType.PATH -> {
                        second as SVGPath
                        first.toShape() == second.shape
                    }
                    else -> false
                }
            }
            SegmentationType.PATH -> {
                first as SVGPath
                when (second.type) {
                    SegmentationType.SPLINE -> {
                        second as Spline
                        first.shape == second.path
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
            SegmentationType.RECT -> {
                source as Rect
                when (target.type) {
                    SegmentationType.RECT -> {
                        target as Rect
                        source.xmin <= target.xmin && source.xmax >= target.xmax &&
                                source.ymin <= target.ymin && source.ymax >= target.ymax
                    }
                    SegmentationType.POLYGON -> {
                        val targetBound = (target as Polygon).boundingRect()
                        source.xmin <= targetBound.xmin && source.xmax >= targetBound.xmax &&
                                source.ymin <= targetBound.ymin && source.ymax >= targetBound.ymax
                    }
                    SegmentationType.PATH -> {
                        val targetBound = (target as SVGPath).shape.bounds
                        source.xmin <= targetBound.minX && source.xmax >= targetBound.maxX &&
                                source.ymin <= targetBound.minY && source.ymax >= targetBound.maxX
                    }
                    SegmentationType.SPLINE -> {
                        val targetBound = (target as Spline).path.bounds
                        source.xmin <= targetBound.minX && source.xmax >= targetBound.maxX &&
                                source.ymin <= targetBound.minY && source.ymax >= targetBound.maxX
                    }
                    else -> false
                }
            }
            SegmentationType.POLYGON -> {
                source as Polygon
                when (target.type) {
                    SegmentationType.RECT -> {
                        target as Rect
                        val s = source.boundingRect()
                        s.xmin <= target.xmin && s.xmax >= target.xmax && s.ymin <= target.ymin && s.ymax >= target.ymax
                    }
                    SegmentationType.POLYGON -> {
                        target as Polygon
                        val sourceShape = source.toShape()
                        if (source.isConvex()) {
                            target.vertices.all { sourceShape.contains(it.first, it.second) }
                        } else {
                            var intersect = false
                            for (i in 0 until source.vertices.size) {
                                val p1 = source.vertices[i]
                                val p2 = source.vertices[(i + 1) % source.vertices.size]
                                for (j in 0 until target.vertices.size) {
                                    val p3 = target.vertices[i]
                                    val p4 = target.vertices[(i + 1) % target.vertices.size]
                                    intersect = intersect || doLinesIntersect(p1.first, p1.second, p2.first, p2.second, p3.first, p3.second, p4.first, p4.second)
                                    if (intersect) break
                                }
                            }
                            !intersect && sourceShape.contains(target.vertices[0].first, target.vertices[0].second)
                        }
                    }
                    SegmentationType.PATH -> {
                        target as SVGPath
                        TODO()
                    }
                    SegmentationType.SPLINE -> {
                        target as Polygon
                        TODO()
                    }
                    else -> false
                }
            }
            SegmentationType.PATH -> {
                source as SVGPath
                when (target.type) {
                    SegmentationType.SPLINE -> {
                        target as Polygon
                        TODO()
                    }
                    else -> false
                }
            }
            SegmentationType.SPLINE -> false
            SegmentationType.PLANE -> false
            SegmentationType.MASK -> false
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
        }
    }

    fun overlaps(source: Segmentation, target: Segmentation): Boolean {
        if (source.segmentClass != target.segmentClass) return false

        return when (source.segmentClass) {
            SegmentationClass.SPACE -> {
                val sourceArea = (source as SpaceSegmentation).toArea()
                val targetArea = (target as SpaceSegmentation).toArea()
                sourceArea.intersect(targetArea)
                sourceArea.isEmpty
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
        }
    }

    fun translate(segmentation: Segmentation, by: Segmentation): Segmentation {
        return when (by.type) {
            SegmentationType.RECT -> {
                by as Rect
                when (segmentation.type) {
                    SegmentationType.RECT -> (segmentation as Rect).move(by.xmin, by.ymin)
                    SegmentationType.POLYGON -> (segmentation as Polygon).move(by.xmin, by.ymin)
                    SegmentationType.PATH -> (segmentation as SVGPath).move(by.xmin, by.ymin)
                    SegmentationType.SPLINE -> (segmentation as Polygon).move(by.xmin, by.ymin)
                    else -> segmentation
                }
            }
            SegmentationType.POLYGON, SegmentationType.SPLINE -> {
                by as Polygon
                when (segmentation.type) {
                    SegmentationType.RECT -> (segmentation as Rect).move(by.xmin, by.xmin)
                    SegmentationType.POLYGON -> (segmentation as Polygon).move(by.xmin, by.ymin)
                    SegmentationType.PATH -> (segmentation as SVGPath).move(by.xmin, by.ymin)
                    SegmentationType.SPLINE -> (segmentation as Polygon).move(by.xmin, by.ymin)
                    else -> segmentation
                }
            }
            SegmentationType.TIME -> {
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

        return when (parseSegmentationType(segmentType)) {
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

                val points = segmentDefinition.split("),").map { chunk ->
                    val coords = chunk.replaceFirst("(", "").replace(")", "").split(",").map { it.toDoubleOrNull() }
                    if (coords.any { it == null }) {
                        null
                    } else if (coords.size < 2) {
                        null
                    } else {
                        coords[0]!! to coords[1]!!
                    }
                }

                val finalPoints = points.filterNotNull()

                if (finalPoints.size == points.size) {
                    Polygon(finalPoints)
                } else {
                    null
                }
            }

            SegmentationType.SPLINE -> {

                val points = segmentDefinition.split("),").map { chunk ->
                    val coords = chunk.replaceFirst("(", "").replace(")", "").split(",").map { it.toDoubleOrNull() }
                    if (coords.any { it == null }) {
                        null
                    } else if (coords.size < 2) {
                        null
                    } else {
                        coords[0]!! to coords[1]!!
                    }
                }

                val finalPoints = points.filterNotNull()

                if (finalPoints.size == points.size) {
                    Spline(finalPoints)
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

            else -> null
        }
    }

    private fun doLinesIntersect(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double): Boolean {
        // Calculate the slopes and intercepts of the two lines
        val m1 = (y2 - y1) / (x2 - x1)
        val b1 = y1 - m1 * x1
        val m2 = (y4 - y3) / (x4 - x3)
        val b2 = y3 - m2 * x3

        // Check if the lines are parallel (i.e., have the same slope)
        if (m1 == m2) {
            return false
        }

        // Calculate the x-coordinate of the intersection point
        val xIntersect = (b2 - b1) / (m1 - m2)

        // Check if the intersection point lies within the x-coordinates of the two line segments
        return xIntersect >= Math.min(x1, x2) && xIntersect <= Math.max(x1, x2) &&
                xIntersect >= Math.min(x3,x4) && xIntersect <= Math.max(x3, x4)
    }
}