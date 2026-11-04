package com.benchfirewatch.source

/**
 * The measurement grid is the size of the first real frame. A later frame
 * with a different size is a sharpened picture, not temperatures, and is
 * refused. Detection, alarms, the crosshair, the max reading, and the CSV
 * stay on the first grid.
 */
class SensorGrid {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    fun accept(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        if (this.width == 0) {
            this.width = width
            this.height = height
            return true
        }
        return width == this.width && height == this.height
    }

    fun clear() {
        width = 0
        height = 0
    }
}
