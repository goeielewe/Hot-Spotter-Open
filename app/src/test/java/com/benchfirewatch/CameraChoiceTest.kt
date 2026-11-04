package com.benchfirewatch

import com.benchfirewatch.model.CameraChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraChoiceTest {
    @Test
    fun legacyUseCameraFlagBecomesTheUsbCamera() {
        assertEquals(CameraChoice.DEMO, CameraChoice.fromStored(null, legacyUseCamera = false))
        assertEquals(CameraChoice.USB, CameraChoice.fromStored(null, legacyUseCamera = true))
        assertEquals(CameraChoice.USB, CameraChoice.fromStored("", legacyUseCamera = true))
        assertEquals(CameraChoice.DEMO, CameraChoice.fromStored("", legacyUseCamera = false))
        assertEquals(CameraChoice.USB, CameraChoice.fromStored("usb", legacyUseCamera = false))
        assertEquals(CameraChoice.USB, CameraChoice.fromStored("auto", legacyUseCamera = false))
        assertEquals(CameraChoice.DEMO, CameraChoice.fromStored("demo", legacyUseCamera = true))
        assertEquals(CameraChoice.DEMO, CameraChoice.fromStored("nope", legacyUseCamera = true))
    }

    @Test
    fun usbCameraDescribesTheOpenCameras() {
        val blurb = CameraChoice.USB.blurb()
        assertTrue(blurb.contains("USB-C"))
        assertTrue(blurb.contains("P2 Pro"))
        assertEquals("USB-C Thermal", CameraChoice.USB.displayTitle())
        assertEquals("USB-C", CameraChoice.USB.displayShort())
        assertEquals("Demo (Simulated)", CameraChoice.DEMO.displayTitle())
    }
}
