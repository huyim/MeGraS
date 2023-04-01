package org.megras.segmentation

object SegmentationUtil {

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
                    SegmentationType.RECT -> false
                    SegmentationType.PATH -> TODO()
                    SegmentationType.SPLINE -> TODO()
                    SegmentationType.MASK -> TODO()
                    SegmentationType.CHANNEL -> TODO()
                    SegmentationType.TIME -> TODO()
                    SegmentationType.PLANE -> TODO()
                }
            }
            SegmentationType.POLYGON -> TODO()
            SegmentationType.PATH -> TODO()
            SegmentationType.SPLINE -> TODO()
            SegmentationType.MASK -> TODO()
            SegmentationType.CHANNEL -> TODO()
            SegmentationType.TIME -> TODO()
            SegmentationType.PLANE -> TODO()
        }


    }

    fun parseSegmentationType(name: String): List<SegmentationType?> =
        name.trim().split(",").map {
            try {
                SegmentationType.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null //not found
            }
        }

    fun parseSegmentation(type: SegmentationType, definition: String): Segmentation? = when(type) {
        SegmentationType.RECT -> {

            val coords = definition.split(",").mapNotNull {
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

            val points = definition.split("),").map { chunk ->
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

            val points = definition.split("),").map { chunk ->
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
            Path(definition)
        }

        SegmentationType.MASK -> {

            var binaryString = ""
            if (definition.matches(Regex("^[01]+$"))) {
                binaryString = definition
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
            val channels = definition.split(",")
            Channel(channels)
        }

        SegmentationType.TIME -> {
            val timepoints = definition.split(",").mapNotNull {
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
            val params = definition.split(",").mapNotNull {
                it.trim().toDoubleOrNull()
            }

            if (params.size == 5) {
                Plane(params[0], params[1], params[2], params[3], params[4] == 1.0)
            } else {
                null
            }
        }
    }


    fun parseSegmentation(types: List<SegmentationType>, definition: String): List<Segmentation> {

        if (types.isEmpty()) {
            return emptyList()
        }

        if (types.size == 1) {
            return listOfNotNull(parseSegmentation(types.first(), definition))
        }

        TODO()

    }


}