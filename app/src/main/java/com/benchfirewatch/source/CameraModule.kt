package com.benchfirewatch.source

import android.content.Context

/**
 * The one door from the app to a camera. [EditionCamera] opens the USB camera.
 */
interface CameraModule {
    fun create(context: Context, emissivity: Float): ThermalSource
    fun notices(): String
    fun shareTestReport(context: Context)
}
