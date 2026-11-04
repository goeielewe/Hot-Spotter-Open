package com.benchfirewatch.render

import com.benchfirewatch.model.ThermalFrame

/**
 * Where the radiometric image sits inside the colorized picture, in 0..1 of that picture.
 * A full-frame mode (MSX, thermal, aligned visual) uses [FULL]. Picture-in-picture uses the inset.
 */
data class ContentRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun mapX(thermalX: Float): Float = left + thermalX.coerceIn(0f, 1f) * (right - left)

    fun mapY(thermalY: Float): Float = top + thermalY.coerceIn(0f, 1f) * (bottom - top)

    fun thermalX(imageX: Float): Float {
        val span = (right - left).coerceAtLeast(1e-4f)
        return ((imageX - left) / span).coerceIn(0f, 1f)
    }

    fun thermalY(imageY: Float): Float {
        val span = (bottom - top).coerceAtLeast(1e-4f)
        return ((imageY - top) / span).coerceIn(0f, 1f)
    }

    companion object {
        val FULL = ContentRect(0f, 0f, 1f, 1f)
    }
}

/** The picture is drawn at its own aspect ratio, flush to the width of the view. */
data class PlacedImage(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

object FrameGeometry {
    /** Fit the whole picture inside the view. Bars fill the leftover side. */
    fun place(viewWidth: Float, viewHeight: Float, imageW: Int, imageH: Int): PlacedImage? {
        if (imageW <= 0 || imageH <= 0 || viewWidth <= 0f || viewHeight <= 0f) return null
        val imageAspect = imageW.toFloat() / imageH
        val viewAspect = viewWidth / viewHeight
        return if (viewAspect > imageAspect) {
            val w = viewHeight * imageAspect
            PlacedImage((viewWidth - w) / 2f, 0f, w, viewHeight)
        } else {
            val h = viewWidth / imageAspect
            PlacedImage(0f, (viewHeight - h) / 2f, viewWidth, h)
        }
    }

    /**
     * Fill the view and crop the overflow, still at the picture's own aspect.
     * The result can start outside the view. Callers map boxes with the same rect.
     */
    fun cover(viewWidth: Float, viewHeight: Float, imageW: Int, imageH: Int): PlacedImage? {
        if (imageW <= 0 || imageH <= 0 || viewWidth <= 0f || viewHeight <= 0f) return null
        val imageAspect = imageW.toFloat() / imageH
        val viewAspect = viewWidth / viewHeight
        return if (viewAspect > imageAspect) {
            val h = viewWidth / imageAspect
            PlacedImage(0f, (viewHeight - h) / 2f, viewWidth, h)
        } else {
            val w = viewHeight * imageAspect
            PlacedImage((viewWidth - w) / 2f, 0f, w, viewHeight)
        }
    }

    /** Height of a view that is exactly [viewWidth] wide and matches the picture. No side bars. */
    fun flushHeight(viewWidth: Float, imageW: Int, imageH: Int): Float {
        if (imageW <= 0 || imageH <= 0 || viewWidth <= 0f) return 0f
        return viewWidth * imageH.toFloat() / imageW
    }
}

data class HotPoint(val x: Float, val y: Float, val celsius: Float)

object HotSpotFinder {
    fun find(frame: ThermalFrame): HotPoint? = find(frame.celsius, frame.width, frame.height)

    fun find(celsius: FloatArray, width: Int, height: Int): HotPoint? {
        if (width <= 0 || height <= 0 || celsius.size != width * height) return null
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (index in celsius.indices) {
            val value = celsius[index]
            if (value > bestValue) {
                bestValue = value
                best = index
            }
        }
        if (bestValue == Float.NEGATIVE_INFINITY) return null
        val x = best % width
        val y = best / width
        return HotPoint((x + 0.5f) / width, (y + 0.5f) / height, bestValue)
    }
}
