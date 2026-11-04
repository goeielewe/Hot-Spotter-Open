package com.benchfirewatch.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * The few status glyphs that are not in the core icon set. Kept local so the
 * release APK does not carry the whole extended icon library.
 */
object BenchIcons {
    val Flame: ImageVector
        get() = flame ?: materialIcon(name = "Flame") {
            materialPath { flamePath(this) }
        }.also { flame = it }

    val CameraOff: ImageVector
        get() = cameraOff ?: materialIcon(name = "CameraOff") {
            materialPath { cameraOffPath(this) }
        }.also { cameraOff = it }

    val Pause: ImageVector
        get() = pause ?: materialIcon(name = "Pause") {
            materialPath { pausePath(this) }
        }.also { pause = it }

    val Fullscreen: ImageVector
        get() = fullscreen ?: materialIcon(name = "Fullscreen") {
            materialPath { fullscreenPath(this) }
        }.also { fullscreen = it }

    val Rotate: ImageVector
        get() = rotate ?: materialIcon(name = "Rotate") {
            materialPath { rotatePath(this) }
        }.also { rotate = it }

    private var flame: ImageVector? = null
    private var cameraOff: ImageVector? = null
    private var pause: ImageVector? = null
    private var fullscreen: ImageVector? = null
    private var rotate: ImageVector? = null
}

private fun flamePath(path: PathBuilder) {
    path.moveTo(13.5f, 0.67f)
    path.reflectiveCurveToRelative(0.74f, 2.65f, 0.74f, 4.8f)
    path.curveToRelative(0f, 2.06f, -1.35f, 3.73f, -3.41f, 3.73f)
    path.curveToRelative(-2.07f, 0f, -3.63f, -1.67f, -3.63f, -3.73f)
    path.lineToRelative(0.03f, -0.36f)
    path.curveTo(5.21f, 7.51f, 4f, 10.62f, 4f, 14f)
    path.curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
    path.reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
    path.curveTo(20f, 8.61f, 17.41f, 3.8f, 13.5f, 0.67f)
    path.close()
    path.moveTo(11.71f, 19f)
    path.curveToRelative(-1.78f, 0f, -3.22f, -1.4f, -3.22f, -3.14f)
    path.curveToRelative(0f, -1.62f, 1.05f, -2.76f, 2.81f, -3.12f)
    path.curveToRelative(1.77f, -0.36f, 3.6f, -1.21f, 4.62f, -2.58f)
    path.curveToRelative(0.39f, 1.29f, 0.59f, 2.65f, 0.59f, 4.04f)
    path.curveToRelative(0f, 2.65f, -2.15f, 4.8f, -4.8f, 4.8f)
    path.close()
}

private fun cameraOffPath(path: PathBuilder) {
    path.moveTo(21f, 6.5f)
    path.lineToRelative(-4f, 4f)
    path.verticalLineTo(7f)
    path.curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
    path.horizontalLineTo(9.82f)
    path.lineTo(21f, 17.18f)
    path.verticalLineTo(6.5f)
    path.close()
    path.moveTo(3.27f, 2f)
    path.lineTo(2f, 3.27f)
    path.lineTo(4.73f, 6f)
    path.horizontalLineTo(4f)
    path.curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
    path.verticalLineToRelative(10f)
    path.curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
    path.horizontalLineToRelative(12f)
    path.curveToRelative(0.21f, 0f, 0.39f, -0.08f, 0.54f, -0.18f)
    path.lineTo(19.73f, 21f)
    path.lineTo(21f, 19.73f)
    path.lineTo(3.27f, 2f)
    path.close()
}

private fun pausePath(path: PathBuilder) {
    path.moveTo(6f, 19f)
    path.horizontalLineToRelative(4f)
    path.verticalLineTo(5f)
    path.horizontalLineTo(6f)
    path.verticalLineToRelative(14f)
    path.close()
    path.moveTo(14f, 5f)
    path.verticalLineToRelative(14f)
    path.horizontalLineToRelative(4f)
    path.verticalLineTo(5f)
    path.horizontalLineToRelative(-4f)
    path.close()
}

private fun fullscreenPath(path: PathBuilder) {
    path.moveTo(7f, 14f)
    path.horizontalLineTo(5f)
    path.verticalLineToRelative(5f)
    path.horizontalLineToRelative(5f)
    path.verticalLineToRelative(-2f)
    path.horizontalLineTo(7f)
    path.verticalLineToRelative(-3f)
    path.close()
    path.moveTo(5f, 10f)
    path.horizontalLineToRelative(2f)
    path.verticalLineTo(7f)
    path.horizontalLineToRelative(3f)
    path.verticalLineTo(5f)
    path.horizontalLineTo(5f)
    path.verticalLineToRelative(5f)
    path.close()
    path.moveTo(17f, 17f)
    path.horizontalLineToRelative(-3f)
    path.verticalLineToRelative(2f)
    path.horizontalLineToRelative(5f)
    path.verticalLineToRelative(-5f)
    path.horizontalLineToRelative(-2f)
    path.verticalLineToRelative(3f)
    path.close()
    path.moveTo(14f, 5f)
    path.verticalLineToRelative(2f)
    path.horizontalLineToRelative(3f)
    path.verticalLineToRelative(3f)
    path.horizontalLineToRelative(2f)
    path.verticalLineTo(5f)
    path.horizontalLineToRelative(-5f)
    path.close()
}

private fun rotatePath(path: PathBuilder) {
    path.moveTo(16.48f, 2.52f)
    path.curveToRelative(3.27f, 1.55f, 5.61f, 4.72f, 5.97f, 8.48f)
    path.horizontalLineToRelative(1.5f)
    path.curveTo(23.44f, 4.84f, 18.29f, 0f, 12f, 0f)
    path.lineToRelative(-0.66f, 0.03f)
    path.lineToRelative(3.81f, 3.81f)
    path.lineToRelative(1.33f, -1.32f)
    path.close()
    path.moveTo(10.23f, 1.75f)
    path.curveToRelative(-0.59f, -0.59f, -1.54f, -0.59f, -2.12f, 0f)
    path.lineTo(1.75f, 8.11f)
    path.curveToRelative(-0.59f, 0.59f, -0.59f, 1.54f, 0f, 2.12f)
    path.lineToRelative(12.02f, 12.02f)
    path.curveToRelative(0.59f, 0.59f, 1.54f, 0.59f, 2.12f, 0f)
    path.lineToRelative(6.36f, -6.36f)
    path.curveToRelative(0.59f, -0.59f, 0.59f, -1.54f, 0f, -2.12f)
    path.lineTo(10.23f, 1.75f)
    path.close()
    path.moveTo(14.83f, 21.19f)
    path.lineTo(2.81f, 9.17f)
    path.lineToRelative(6.36f, -6.36f)
    path.lineToRelative(12.02f, 12.02f)
    path.lineToRelative(-6.36f, 6.36f)
    path.close()
    path.moveTo(7.52f, 21.48f)
    path.curveTo(4.25f, 19.94f, 1.91f, 16.76f, 1.55f, 13f)
    path.horizontalLineTo(0.05f)
    path.curveTo(0.56f, 19.16f, 5.71f, 24f, 12f, 24f)
    path.lineToRelative(0.66f, -0.03f)
    path.lineToRelative(-3.81f, -3.81f)
    path.lineToRelative(-1.33f, 1.32f)
    path.close()
}
