package com.benchfirewatch.source

/**
 * Temperature frames for the open edition.
 *
 * Family A (P2 Pro, TC001, TOOLTOP T7, and any camera that offers 256×384 YUYV)
 * is the layout published by the MIT P2Pro-Viewer and the Apache-2.0
 * PyThermalCamera project: the bottom half is little-endian uint16 and
 * °C = raw / 64 − 273.15.
 *
 * TC002C uses the same scale with a 512×484 frame and a /16 step, from the
 * Apache-2.0 notes on that camera.
 *
 * Thermal Master P1/P3 is the Apache-2.0 frame map: two image rows, two
 * metadata rows, then the temperature rows, same 1/64 kelvin scale.
 *
 * UNI-T UTi120M is MIT. Its absolute scale needs the factory table stored on
 * the camera, so this decoder only checks the frame size and pulls the Y16
 * block. It does not invent Celsius.
 */
object Radiometry {
    const val KELVIN_STEP = 64.0

    fun rawToCelsius(raw: Int): Float = (raw / KELVIN_STEP - 273.15).toFloat()

    data class CelsiusFrame(val width: Int, val height: Int, val celsius: FloatArray)

    fun decodeSplitYuyv(frame: ByteArray, width: Int = 256, imageHeight: Int = 192): CelsiusFrame? {
        val pixels = width * imageHeight
        val need = pixels * 4
        if (frame.size < need) return null
        val out = FloatArray(pixels)
        var o = 0
        var i = pixels * 2
        while (o < pixels && i + 1 < frame.size) {
            val raw = (frame[i].toInt() and 0xFF) or ((frame[i + 1].toInt() and 0xFF) shl 8)
            out[o] = rawToCelsius(raw)
            o++
            i += 2
        }
        if (o != pixels) return null
        return CelsiusFrame(width, imageHeight, out)
    }

    /** TC002C: 512×484, temperatures are the uint16 values divided by 16. */
    fun decodeTc002c(frame: ByteArray, width: Int = 512, height: Int = 484): CelsiusFrame? {
        val pixels = width * height
        if (frame.size < pixels * 2) return null
        val out = FloatArray(pixels)
        var i = 0
        for (p in 0 until pixels) {
            val raw = (frame[i].toInt() and 0xFF) or ((frame[i + 1].toInt() and 0xFF) shl 8)
            out[p] = (raw / 16.0).toFloat()
            i += 2
        }
        return CelsiusFrame(width, height, out)
    }

    /**
     * P3 is 256×192 (197,632 byte payload). P1 is 160×120 (77,440).
     * Payload is little-endian uint16 after the 12-byte start marker is removed.
     */
    fun decodeThermalMaster(payload: ByteArray, width: Int, height: Int): CelsiusFrame? {
        val rows = 2 * height + 2
        val need = rows * width * 2
        if (payload.size < need) return null
        val thermalRow = height + 2
        val out = FloatArray(width * height)
        var o = 0
        for (y in 0 until height) {
            var i = (thermalRow + y) * width * 2
            for (x in 0 until width) {
                val raw = (payload[i].toInt() and 0xFF) or ((payload[i + 1].toInt() and 0xFF) shl 8)
                out[o++] = rawToCelsius(raw)
                i += 2
            }
        }
        return CelsiusFrame(width, height, out)
    }

    const val UNIT_HEADER = 720
    const val UNIT_WIDTH = 120
    const val UNIT_HEIGHT = 90

    /** True when the buffer is a UTi120-sized bulk frame. Celsius is not implied. */
    fun uniT120Y16(frame: ByteArray): ByteArray? {
        val pixels = UNIT_WIDTH * UNIT_HEIGHT
        val need = UNIT_HEADER + pixels * 2
        if (frame.size < need) return null
        return frame.copyOfRange(UNIT_HEADER, need)
    }
}

/**
 * Vendor control packets for the P2 Pro family.
 * Packet layout from LeoDJ/P2Pro-Viewer (MIT), Copyright (c) 2023 LeoDJ.
 * This is a Kotlin rewrite of the header packing, not a copy of the USB stack.
 */
object P2ProPackets {
    const val REQ_OUT = 0x41
    const val REQ_IN = 0xC1
    const val REQUEST_WRITE = 0x45
    const val REQUEST_READ = 0x44
    const val W_VALUE = 0x78
    const val INDEX_LONG_A = 0x9d00
    const val INDEX_LONG_B = 0x1d08
    const val INDEX_STD = 0x1d00
    const val CMD_PSEUDO_COLOR = 0x8409
    const val CMD_PROP_TPD = 0x8514
    const val DIR_SET = 0x4000
    const val PROP_EMISSIVITY = 3

    fun standardWrite(cmd: Int, param: Int = 0): ByteArray {
        val out = ByteArray(8)
        out[0] = (cmd and 0xFF).toByte()
        out[1] = ((cmd shr 8) and 0xFF).toByte()
        out[2] = (param and 0xFF).toByte()
        out[3] = ((param shr 8) and 0xFF).toByte()
        out[4] = ((param shr 16) and 0xFF).toByte()
        out[5] = ((param shr 24) and 0xFF).toByte()
        return out
    }

    fun longWrite(cmd: Int, p1: Int, p2: Int, p3: Int = 0, p4: Int = 0): Pair<ByteArray, ByteArray> {
        val a = ByteArray(8)
        a[0] = (cmd and 0xFF).toByte()
        a[1] = ((cmd shr 8) and 0xFF).toByte()
        a[2] = ((p1 shr 8) and 0xFF).toByte()
        a[3] = (p1 and 0xFF).toByte()
        a[4] = ((p2 shr 24) and 0xFF).toByte()
        a[5] = ((p2 shr 16) and 0xFF).toByte()
        a[6] = ((p2 shr 8) and 0xFF).toByte()
        a[7] = (p2 and 0xFF).toByte()
        val b = ByteArray(8)
        writeBe32(b, 0, p3)
        writeBe32(b, 4, p4)
        return a to b
    }

    /** Emissivity 0.05..1 mapped onto the camera's 0..127 steps. */
    fun emissivitySteps(emissivity: Float): Int {
        val clamped = emissivity.coerceIn(0.05f, 1f)
        return (clamped * 127f).toInt().coerceIn(1, 127)
    }

    private fun writeBe32(dest: ByteArray, at: Int, value: Int) {
        dest[at] = ((value shr 24) and 0xFF).toByte()
        dest[at + 1] = ((value shr 16) and 0xFF).toByte()
        dest[at + 2] = ((value shr 8) and 0xFF).toByte()
        dest[at + 3] = (value and 0xFF).toByte()
    }
}

/** Which open-edition camera a USB identity is. */
enum class OpenCameraKind {
    SPLIT_FRAME,
    TC002C,
    THERMAL_MASTER_P3,
    THERMAL_MASTER_P1,
    UNIT_120,
    UNKNOWN,
}

object OpenCameras {
    const val REALTEK = 0x0BDA
    const val P2_OR_TC001 = 0x5830
    const val TOOLTOP_T7 = 0x5840
    const val THERMAL_MASTER = 0x3474
    const val P3 = 0x45A2
    const val P1 = 0x45C2
    const val UNIT = 0x5656
    const val UTI120 = 0x1201

    fun kind(
        vendorId: Int,
        productId: Int,
        offersSplitFrame: Boolean,
        offersWide: Boolean = false,
    ): OpenCameraKind = when {
        vendorId == THERMAL_MASTER && productId == P3 -> OpenCameraKind.THERMAL_MASTER_P3
        vendorId == THERMAL_MASTER && productId == P1 -> OpenCameraKind.THERMAL_MASTER_P1
        vendorId == UNIT && productId == UTI120 -> OpenCameraKind.UNIT_120
        vendorId == REALTEK && (productId == P2_OR_TC001 || productId == TOOLTOP_T7) -> OpenCameraKind.SPLIT_FRAME
        offersWide && vendorId != THERMAL_MASTER && vendorId != UNIT -> OpenCameraKind.TC002C
        offersSplitFrame -> OpenCameraKind.SPLIT_FRAME
        else -> OpenCameraKind.UNKNOWN
    }

    fun listed(vendorId: Int, productId: Int): Boolean =
        kind(vendorId, productId, offersSplitFrame = false) != OpenCameraKind.UNKNOWN
}

/**
 * Precomputed Thermal Master commands from the Apache-2.0 P3 protocol notes
 * (jvdillon/p3-ir-camera). Each one is 18 bytes and already includes its CRC.
 * The serial-number read is intentionally absent.
 */
object ThermalMasterCommands {
    val startStream: ByteArray = hex("012f81000000000000000000010000004930")
    val gainLow: ByteArray = hex("012f41000000000000000000000000003c3a")
    val gainHigh: ByteArray = hex("012f41000100000000000000000000004939")
    val shutter: ByteArray = hex("01364300000000000000000000000000cd0b")

    private fun hex(text: String): ByteArray {
        val out = ByteArray(text.length / 2)
        var i = 0
        while (i < out.size) {
            out[i] = text.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            i++
        }
        return out
    }
}
