package com.benchfirewatch.ui

/**
 * Which finger-down may move the Watch, Log, Settings, and Help pager.
 * The system back gesture keeps the screen edges. A drag that starts on the
 * camera, zone drawing, a slider, a horizontal chip row, or a dialog does not
 * change pages. Full screen is not a page. Back from any other page returns
 * to Watch.
 */
object AppPages {
    const val WATCH = 0
    const val LOG = 1
    const val SETTINGS = 2
    const val HELP = 3
    const val COUNT = 4
    val titles = listOf("Watch", "Log", "Settings", "Help")
}

enum class PagerTouch {
    OPEN,
    CAMERA,
    ZONES,
    SLIDER,
    CHIP_ROW,
    DIALOG,
}

object PageSwipe {
    fun selectedTab(currentPage: Int, targetPage: Int, scrollInProgress: Boolean): Int {
        val raw = if (scrollInProgress) targetPage else currentPage
        return raw.coerceIn(0, AppPages.COUNT - 1)
    }

    /** Null means Back should leave the app. */
    fun backPage(currentPage: Int): Int? {
        val page = currentPage.coerceIn(0, AppPages.COUNT - 1)
        return if (page == AppPages.WATCH) null else AppPages.WATCH
    }

    fun inSystemGestureZone(x: Float, width: Float, leftInset: Float, rightInset: Float): Boolean {
        if (width <= 0f) return false
        val left = leftInset.coerceAtLeast(0f)
        val right = rightInset.coerceAtLeast(0f)
        if (left == 0f && right == 0f) return false
        return x < left || x > width - right
    }

    fun claimsHorizontal(totalX: Float, totalY: Float, slop: Float): Boolean {
        if (slop < 0f) return false
        val dx = kotlin.math.abs(totalX)
        val dy = kotlin.math.abs(totalY)
        return dx > slop && dx > dy
    }

    fun allowsPaging(
        touch: PagerTouch,
        inSystemGesture: Boolean,
        fullScreen: Boolean,
        modalVisible: Boolean,
    ): Boolean {
        if (fullScreen || modalVisible || inSystemGesture) return false
        return touch == PagerTouch.OPEN
    }
}
