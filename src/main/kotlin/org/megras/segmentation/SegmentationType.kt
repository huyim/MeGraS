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

    /**
     * SVG path
     */
    PATH,

    SPLINE,

    /**
     * Binary mask for each pixel
     */
    MASK,

    CHANNEL,

    TIME,

    /**
     * 3D plane specified as a,b,c,d,above
     * such that the plane is defined as ax + by + cz + d = 0 and 'above' indicates which side of the plane to keep
     */
    PLANE

}