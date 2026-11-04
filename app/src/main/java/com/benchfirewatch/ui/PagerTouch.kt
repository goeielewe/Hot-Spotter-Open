package com.benchfirewatch.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

class PagerModalLocks {
    var count by mutableIntStateOf(0)
}

val LocalPagerModalLocks = staticCompositionLocalOf { PagerModalLocks() }

@Composable
fun HoldPagerWhileShown() {
    val locks = LocalPagerModalLocks.current
    DisposableEffect(locks) {
        locks.count++
        onDispose { locks.count-- }
    }
}

private val eatLeftoverHorizontal = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
        return Offset(x = available.x, y = 0f)
    }
}

/** A drag that starts here scrolls this control and does not change pages. */
fun Modifier.keepSwipeOffPager(): Modifier = this
    .nestedScroll(eatLeftoverHorizontal)
    .pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalX = 0f
            var totalY = 0f
            var claim = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                totalX += change.position.x - change.previousPosition.x
                totalY += change.position.y - change.previousPosition.y
                if (!claim && PageSwipe.claimsHorizontal(totalX, totalY, slop)) claim = true
                if (claim) change.consume()
            }
        }
    }

/**
 * Horizontal drags that start in the system gesture insets are left for Back.
 * Button taps in that strip still go through.
 */
@Composable
fun Modifier.yieldSystemBackEdges(): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = WindowInsets.systemGestures.getLeft(density, direction)
    val right = WindowInsets.systemGestures.getRight(density, direction)
    val view = LocalView.current
    val originX = remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { coords ->
            originX.floatValue = coords.positionInWindow().x
        }
        .pointerInput(left, right) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val windowX = originX.floatValue + down.position.x
                val width = view.rootView.width.toFloat()
                val edge = PageSwipe.inSystemGestureZone(windowX, width, left.toFloat(), right.toFloat())
                if (!edge) return@awaitEachGesture
                var totalX = 0f
                var totalY = 0f
                var claim = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    totalX += change.position.x - change.previousPosition.x
                    totalY += change.position.y - change.previousPosition.y
                    if (!claim && PageSwipe.claimsHorizontal(totalX, totalY, slop)) claim = true
                    if (claim) change.consume()
                }
            }
        }
}

/** Keeps an overlay as the hit target so the page underneath does not see the drag. */
fun Modifier.absorbTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
fun PageDots(page: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == page
            val width by animateDpAsState(if (active) 16.dp else 4.dp, label = "page-dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(width)
                    .background(
                        if (active) chromePaper() else chromeMuted().copy(alpha = 0.45f),
                        CircleShape,
                    ),
            )
        }
    }
}
