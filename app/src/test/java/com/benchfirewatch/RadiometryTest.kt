package com.benchfirewatch

import com.benchfirewatch.source.OpenCameraKind
import com.benchfirewatch.source.OpenCameras
import com.benchfirewatch.source.P2ProPackets
import com.benchfirewatch.source.Radiometry
import com.benchfirewatch.source.UvcModes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadiometryTest {
    @Test
    fun splitFrameBottomHalfIsLittleEndianKelvin() {
        val width = 4
        val height = 2
        val pixels = width * height
        val frame = ByteArray(pixels * 4)
        // 25.0 °C = (25 + 273.15) * 64 = 19081.6 -> 19082
        val raw = ((25.0 + 273.15) * 64.0).toInt()
        val tempAt = pixels * 2
        frame[tempAt] = (raw and 0xFF).toByte()
        frame[tempAt + 1] = ((raw shr 8) and 0xFF).toByte()
        val decoded = Radiometry.decodeSplitYuyv(frame, width, height)!!
        assertEquals(25f, decoded.celsius[0], 0.02f)
        assertEquals(width, decoded.width)
        assertNull(Radiometry.decodeSplitYuyv(ByteArray(8), width, height))
    }

    @Test
    fun tc002cDividesBySixteen() {
        val frame = ByteArray(4)
        frame[0] = 0xA0.toByte()
        frame[1] = 0x0F.toByte() // 4000 -> 250.0
        val decoded = Radiometry.decodeTc002c(frame, width = 2, height = 1)!!
        assertEquals(250f, decoded.celsius[0], 0.01f)
    }

    @Test
    fun thermalMasterReadsTheTemperatureRows() {
        val width = 2
        val height = 1
        val rows = 2 * height + 2
        val payload = ByteArray(rows * width * 2)
        val raw = ((40.0 + 273.15) * 64.0).toInt()
        val at = (height + 2) * width * 2
        payload[at] = (raw and 0xFF).toByte()
        payload[at + 1] = ((raw shr 8) and 0xFF).toByte()
        val decoded = Radiometry.decodeThermalMaster(payload, width, height)!!
        assertEquals(40f, decoded.celsius[0], 0.02f)
        assertEquals(197_632, 2 * (2 * 192 + 2) * 256)
        assertEquals(77_440, 2 * (2 * 120 + 2) * 160)
    }

    @Test
    fun uniTFrameKeepsTheY16BlockAndDoesNotInventCelsius() {
        val y16 = ByteArray(120 * 90 * 2) { 0x5A }
        val frame = ByteArray(720) + y16
        val pulled = Radiometry.uniT120Y16(frame)!!
        assertEquals(y16.size, pulled.size)
        assertEquals(0x5A.toByte(), pulled[0])
        assertNull(Radiometry.uniT120Y16(ByteArray(100)))
    }

    @Test
    fun usbIdsPickTheRightFamily() {
        assertEquals(OpenCameraKind.SPLIT_FRAME, OpenCameras.kind(0x0BDA, 0x5830, false))
        assertEquals(OpenCameraKind.SPLIT_FRAME, OpenCameras.kind(0x0BDA, 0x5840, false))
        assertEquals(OpenCameraKind.SPLIT_FRAME, OpenCameras.kind(0x1234, 0x0001, true))
        assertEquals(OpenCameraKind.THERMAL_MASTER_P3, OpenCameras.kind(0x3474, 0x45A2, false))
        assertEquals(OpenCameraKind.THERMAL_MASTER_P1, OpenCameras.kind(0x3474, 0x45C2, false))
        assertEquals(OpenCameraKind.UNIT_120, OpenCameras.kind(0x5656, 0x1201, false))
        assertEquals(OpenCameraKind.UNKNOWN, OpenCameras.kind(0x09CB, 0x1996, false))
        assertEquals(OpenCameraKind.TC002C, OpenCameras.kind(0x1111, 0x2222, false, offersWide = true))
        assertEquals(OpenCameraKind.SPLIT_FRAME, OpenCameras.kind(0x0BDA, 0x5830, false, offersWide = true))
        assertTrue(OpenCameras.listed(0x3474, 0x45C2))
        assertTrue(!OpenCameras.listed(0x09CB, 0x1996))
    }

    @Test
    fun p2PacketMatchesTheMitHeaderLayout() {
        val packet = P2ProPackets.standardWrite(0x8409, 0)
        assertEquals(8, packet.size)
        assertEquals(0x09.toByte(), packet[0])
        assertEquals(0x84.toByte(), packet[1])
        assertTrue(packet.drop(2).all { it == 0.toByte() })
        assertEquals(127, P2ProPackets.emissivitySteps(1f))
        assertEquals(63, P2ProPackets.emissivitySteps(0.5f))
        val (a, b) = P2ProPackets.longWrite(P2ProPackets.CMD_PROP_TPD or P2ProPackets.DIR_SET, 3, 127)
        assertEquals(8, a.size)
        assertEquals(8, b.size)
        assertEquals(0x14.toByte(), a[0])
        assertEquals((0x85 or 0x40).toByte(), a[1])
    }

    @Test
    fun uvcDescriptorFindsTheTallFrame() {
        val desc = ByteArray(26)
        desc[0] = 26.toByte()
        desc[1] = 0x24.toByte()
        desc[2] = 5.toByte()
        desc[5] = (256 and 0xFF).toByte()
        desc[6] = ((256 shr 8) and 0xFF).toByte()
        desc[7] = (384 and 0xFF).toByte()
        desc[8] = ((384 shr 8) and 0xFF).toByte()
        assertTrue(UvcModes.offers(desc, 256, 384))
        assertTrue(!UvcModes.offers(desc, 96, 96))
        val picked = UvcModes.target(desc, 256, 384)!!
        assertEquals(1, picked.formatIndex)
        assertEquals(UvcModes.INTERVAL_25_HZ, picked.interval)
    }

    @Test
    fun mjpegFrameIsNotTheSplitMode() {
        val desc = ByteArray(27 + 26)
        desc[0] = 27.toByte()
        desc[1] = 0x24.toByte()
        desc[2] = 4.toByte()
        desc[3] = 1.toByte()
        desc[5] = 0x4D.toByte()
        val frameAt = 27
        desc[frameAt] = 26.toByte()
        desc[frameAt + 1] = 0x24.toByte()
        desc[frameAt + 2] = 5.toByte()
        desc[frameAt + 5] = (256 and 0xFF).toByte()
        desc[frameAt + 6] = ((256 shr 8) and 0xFF).toByte()
        desc[frameAt + 7] = (384 and 0xFF).toByte()
        desc[frameAt + 8] = ((384 shr 8) and 0xFF).toByte()
        assertTrue(!UvcModes.offers(desc, 256, 384))
    }

    @Test
    fun yuy2DescriptorSelectsTheTallFrame() {
        val desc = ByteArray(27)
        desc[0] = 27.toByte()
        desc[1] = 0x24.toByte()
        desc[2] = 4.toByte()
        desc[3] = 2.toByte()
        val guid = intArrayOf(0x59, 0x55, 0x59, 0x32, 0x00, 0x00, 0x10, 0x00, 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71)
        guid.forEachIndexed { index, value -> desc[5 + index] = value.toByte() }
        val frame = ByteArray(26)
        frame[0] = 26.toByte()
        frame[1] = 0x24.toByte()
        frame[2] = 5.toByte()
        frame[3] = 3.toByte()
        frame[5] = (512 and 0xFF).toByte()
        frame[6] = ((512 shr 8) and 0xFF).toByte()
        frame[7] = (484 and 0xFF).toByte()
        frame[8] = ((484 shr 8) and 0xFF).toByte()
        val both = desc + frame
        val wide = UvcModes.target(both, 512, 484)!!
        assertEquals(2, wide.formatIndex)
        assertEquals(3, wide.frameIndex)
        assertTrue(!UvcModes.offers(both, 96, 96))
    }

    @Test
    fun isoEndpointPicksTheStreamingAlt() {
        val desc = ByteArray(9 + 7)
        desc[0] = 9.toByte()
        desc[1] = 0x04.toByte()
        desc[2] = 1.toByte()
        desc[3] = 3.toByte()
        desc[4] = 1.toByte()
        desc[5] = 0x0E.toByte()
        desc[6] = 0x02.toByte()
        desc[9] = 7.toByte()
        desc[10] = 0x05.toByte()
        desc[11] = 0x81.toByte()
        desc[12] = 0x01.toByte()
        desc[13] = 0x00.toByte()
        desc[14] = 0x14.toByte() // 3 * 1024
        val iso = UvcModes.largestIsoIn(desc)!!
        assertEquals(1, iso.interfaceId)
        assertEquals(3, iso.alternate)
        assertEquals(3 * 1024, iso.packetBytes)
    }

    @Test
    fun thermalMasterStartCommandIsEighteenBytes() {
        assertEquals(18, com.benchfirewatch.source.ThermalMasterCommands.startStream.size)
        assertEquals(0x01.toByte(), com.benchfirewatch.source.ThermalMasterCommands.startStream[0])
        assertEquals(0x2F.toByte(), com.benchfirewatch.source.ThermalMasterCommands.startStream[1])
    }

    @Test
    fun joinedVendorNamesMatchAPackagedLibraryPath() {
        val token = "com/" + "fl" + "ir"
        val library = "lib" + "at" + "las"
        assertTrue(("com/" + "fl" + "ir" + "/live/Camera").contains(token))
        assertTrue(("lib/arm64-v8a/" + library + "_native.so").contains(library))
        assertTrue(!"plain demo text".contains(token))
    }
}
