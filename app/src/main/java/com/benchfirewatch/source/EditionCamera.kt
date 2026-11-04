package com.benchfirewatch.source

import android.content.Context

/** Opens the USB-C thermal camera. */
object EditionCamera : CameraModule {
    override fun create(context: Context, emissivity: Float): ThermalSource =
        OpenThermalSource(context.applicationContext, emissivity)

    override fun notices(): String = OpenNotices.TEXT

    override fun shareTestReport(context: Context) {
        TestReport.share(context)
    }
}
