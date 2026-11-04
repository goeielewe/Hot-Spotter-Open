package com.benchfirewatch.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.benchfirewatch.model.TemperatureScale
import com.benchfirewatch.render.ContentRect
import com.benchfirewatch.render.FrameGeometry
import com.benchfirewatch.render.PictureMapping
import com.benchfirewatch.render.PictureRotation
import com.benchfirewatch.render.PlacedImage
import com.benchfirewatch.service.MonitorUiState
import androidx.compose.foundation.layout.padding

/**
 * The colorized frame, zones, blob boxes, and hottest-point crosshair.
 * Both the watch preview and the full-screen view use this, so a box stays on
 * the same thermal pixel when the picture is letterboxed or rotated.
 * [rotation] turns the picture only. Temperatures and stored zones are not moved.
 */
@Composable
fun ThermalCanvas(
    state: MonitorUiState,
    modifier: Modifier = Modifier,
    cover: Boolean = false,
    rotation: PictureRotation = PictureRotation.DEG_0,
    after: DrawScope.(ContentRect, PlacedImage?) -> Unit = { _, _ -> },
) {
    val argb = state.argb
    val image = remember(state.frameId, argb, state.width, state.height) {
        if (argb == null || state.width <= 0 || state.height <= 0 || argb.size < state.width * state.height) {
            null
        } else {
            val bitmap = Bitmap.createBitmap(state.width, state.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(argb, 0, state.width, 0, 0, state.width, state.height)
            bitmap.asImageBitmap()
        }
    }
    val content = ContentRect(state.contentLeft, state.contentTop, state.contentRight, state.contentBottom)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (image == null) {
            Text(
                if (state.running) "Waiting for a thermal frame" else "Start the watch. Demo mode needs no camera.",
                color = chromeMuted(),
                modifier = Modifier.padding(24.dp),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val dest = drawWatchPicture(state, image, content, cover, rotation)
            after(content, dest)
        }
    }
}

private fun DrawScope.drawWatchPicture(
    state: MonitorUiState,
    image: ImageBitmap?,
    content: ContentRect,
    cover: Boolean,
    rotation: PictureRotation,
): PlacedImage? {
    val (imageW, imageH) = rotation.displayedSize(state.width, state.height)
    val dest = if (cover) {
        FrameGeometry.cover(size.width, size.height, imageW, imageH)
    } else {
        FrameGeometry.place(size.width, size.height, imageW, imageH)
    }
    if (image != null && dest != null) {
        drawRotated(image, dest, rotation)
    }
    fun map(thermalX: Float, thermalY: Float): Offset {
        val placed = dest ?: return Offset.Zero
        val point = PictureMapping.thermalToView(thermalX, thermalY, placed, content, rotation)
        return Offset(point.x, point.y)
    }
    fun drawBox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: androidx.compose.ui.graphics.Color,
        minSide: Float,
    ) {
        val placed = dest ?: return
        val box = PictureMapping.thermalRectToView(left, top, right, bottom, placed, content, rotation)
        drawRect(
            color,
            topLeft = Offset(box.left, box.top),
            size = Size(
                (box.right - box.left).coerceAtLeast(minSide),
                (box.bottom - box.top).coerceAtLeast(minSide),
            ),
            style = Stroke(width = 3f),
        )
    }
    state.zones.forEach { zone ->
        val normalized = zone.normalized()
        drawBox(
            normalized.left,
            normalized.top,
            normalized.right,
            normalized.bottom,
            zone.kind.color().copy(alpha = 0.85f),
            minSide = 1f,
        )
    }
    state.tracks.forEach { track ->
        if (!track.candidate) return@forEach
        val color = when {
            track.alarming -> AlarmRed
            track.warning -> WarnAmber
            track.tool -> SolderBlue
            track.inGrace -> ChargeGreen
            else -> Paper
        }
        drawBox(track.left, track.top, track.right, track.bottom, color, minSide = 4f)
    }
    if (!state.hotC.isNaN()) {
        val p = map(state.hotX, state.hotY)
        val arm = 14f
        drawLine(Paper, Offset(p.x - arm, p.y), Offset(p.x + arm, p.y), strokeWidth = 2.5f)
        drawLine(Paper, Offset(p.x, p.y - arm), Offset(p.x, p.y + arm), strokeWidth = 2.5f)
        drawContext.canvas.nativeCanvas.drawText(
            TemperatureScale.formatAbsolute(state.hotC, state.prefs.useFahrenheit),
            p.x + 16f,
            (p.y - 10f).coerceAtLeast(36f),
            hotPaint,
        )
    }
    return dest
}

private fun DrawScope.drawRotated(image: ImageBitmap, dest: PlacedImage, rotation: PictureRotation) {
    if (rotation == PictureRotation.DEG_0) {
        drawImage(
            image,
            dstOffset = IntOffset(dest.left.toInt(), dest.top.toInt()),
            dstSize = IntSize(dest.width.toInt().coerceAtLeast(1), dest.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low,
        )
        return
    }
    val quarter = rotation == PictureRotation.DEG_90 || rotation == PictureRotation.DEG_270
    val drawW = if (quarter) dest.height else dest.width
    val drawH = if (quarter) dest.width else dest.height
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.translate(dest.left + dest.width / 2f, dest.top + dest.height / 2f)
    canvas.rotate(rotation.degrees.toFloat())
    canvas.translate(-drawW / 2f, -drawH / 2f)
    canvas.drawBitmap(image.asAndroidBitmap(), null, RectF(0f, 0f, drawW, drawH), imagePaint)
    canvas.restore()
}

private val imagePaint = Paint().apply {
    isFilterBitmap = true
    isAntiAlias = true
}

private val hotPaint = Paint().apply {
    color = android.graphics.Color.WHITE
    textSize = 42f
    isAntiAlias = true
    setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
}

