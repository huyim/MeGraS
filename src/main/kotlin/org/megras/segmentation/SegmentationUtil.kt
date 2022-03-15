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
                }
            }
            SegmentationType.POLYGON -> TODO()
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
        SegmentationType.POLYGON -> TODO()
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