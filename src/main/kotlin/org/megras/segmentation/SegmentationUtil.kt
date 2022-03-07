package org.megras.segmentation

object SegmentationUtil {

    fun equivalent(first: Segmentation, second: Segmentation) : Boolean {

        if (first.type == second.type) {
            return first == second
        }
        if (first.type.ordinal > second.type.ordinal) {
            return equivalent(second, first)
        }

        return when(first.type) {
            SegmentationType.RECT -> {
                first as Rect
                when(second.type) {
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

}