package com.benchfirewatch.render

/**
 * Display-only clockwise rotation of the colorized picture.
 * Thermal samples, zones, and alarms stay in the camera's own coordinates.
 * 0° is the camera's native orientation.
 */
enum class PictureRotation(val degrees: Int) {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270),
    ;

    fun next(): PictureRotation = fromDegrees(degrees + 90)

    /** Pixel size of the picture after this rotation. 90° and 270° swap the sides. */
    fun displayedSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        return if (this == DEG_90 || this == DEG_270) height to width else width to height
    }

    /**
     * A point in the unrotated picture, 0..1, to the same pixel after a clockwise turn.
     * (0, 0) is the top-left of each picture.
     */
    fun mapImage(x: Float, y: Float): Pair<Float, Float> = when (this) {
        DEG_0 -> x to y
        DEG_90 -> (1f - y) to x
        DEG_180 -> (1f - x) to (1f - y)
        DEG_270 -> y to (1f - x)
    }

    /** Inverse of [mapImage]. A touch on the rotated picture returns the unrotated pixel. */
    fun unmapImage(x: Float, y: Float): Pair<Float, Float> = when (this) {
        DEG_0 -> x to y
        DEG_90 -> y to (1f - x)
        DEG_180 -> (1f - x) to (1f - y)
        DEG_270 -> (1f - y) to x
    }

    companion object {
        fun fromDegrees(degrees: Int): PictureRotation = when (Math.floorMod(degrees, 360)) {
            90 -> DEG_90
            180 -> DEG_180
            270 -> DEG_270
            else -> DEG_0
        }
    }
}

data class DisplayPoint(val x: Float, val y: Float)

data class DisplayRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Maps between thermal coordinates (what detection stores) and the view.
 * Thermal 0..1 is the radiometric frame. [ContentRect] places that frame inside
 * the colorized picture. [PictureRotation] then turns the whole picture.
 */
object PictureMapping {
    fun thermalToView(
        thermalX: Float,
        thermalY: Float,
        dest: PlacedImage,
        content: ContentRect,
        rotation: PictureRotation,
    ): DisplayPoint {
        val (rx, ry) = rotation.mapImage(content.mapX(thermalX), content.mapY(thermalY))
        return DisplayPoint(dest.left + rx * dest.width, dest.top + ry * dest.height)
    }

    fun thermalRectToView(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        dest: PlacedImage,
        content: ContentRect,
        rotation: PictureRotation,
    ): DisplayRect {
        val corners = listOf(
            thermalToView(left, top, dest, content, rotation),
            thermalToView(right, top, dest, content, rotation),
            thermalToView(left, bottom, dest, content, rotation),
            thermalToView(right, bottom, dest, content, rotation),
        )
        return DisplayRect(
            left = corners.minOf { it.x },
            top = corners.minOf { it.y },
            right = corners.maxOf { it.x },
            bottom = corners.maxOf { it.y },
        )
    }

    /** A finger on the rotated picture, back to thermal 0..1. Points outside the picture clamp to its edge. */
    fun viewToThermal(
        viewX: Float,
        viewY: Float,
        dest: PlacedImage,
        content: ContentRect,
        rotation: PictureRotation,
    ): Pair<Float, Float> {
        if (dest.width <= 0f || dest.height <= 0f) return 0f to 0f
        val rx = ((viewX - dest.left) / dest.width).coerceIn(0f, 1f)
        val ry = ((viewY - dest.top) / dest.height).coerceIn(0f, 1f)
        val (ix, iy) = rotation.unmapImage(rx, ry)
        return content.thermalX(ix) to content.thermalY(iy)
    }
}
