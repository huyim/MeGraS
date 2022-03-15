package org.megras.segmentation

enum class SegmentationType {

    /**
     * Rectangular area or volume, specified by (xmin, ymin), (xmax, ymax)
     * or (xmin, ymin, zmin), (xmax, ymax, zmax)
     */
    RECT,

    /**
     * 2d polygon defined by a closed series of (x, y) points
     */
    POLYGON,


}