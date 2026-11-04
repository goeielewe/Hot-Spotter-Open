package com.benchfirewatch.source

/**
 * Finds uncompressed UVC frame sizes in a configuration descriptor.
 * The layout is the public USB Video Class spec: class-specific interface
 * descriptors (type 0x24), frame subtype 5, width at byte 5, height at byte 7.
 */
object UvcModes {
    private const val CS_INTERFACE = 0x24
    private const val INTERFACE = 0x04
    private const val ENDPOINT = 0x05
    private const val VS_FORMAT = 4
    private const val VS_FRAME = 5
    private const val VIDEO_CLASS = 0x0E
    private const val VIDEO_STREAMING = 0x02
    const val INTERVAL_25_HZ = 400_000
    private val YUY2 = intArrayOf(
        0x59, 0x55, 0x59, 0x32, 0x00, 0x00, 0x10, 0x00,
        0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71,
    )

    data class StreamTarget(val formatIndex: Int, val frameIndex: Int, val interval: Int)

    data class IsoIn(val interfaceId: Int, val alternate: Int, val packetBytes: Int)

    fun offers(descriptors: ByteArray, width: Int, height: Int): Boolean =
        target(descriptors, width, height) != null

    /**
     * Uncompressed YUY2 frame, when the descriptor says so.
     * A frame with no format descriptor yet still counts, which is how a
     * short test descriptor is recognized. MJPEG and other guids do not.
     */
    fun target(descriptors: ByteArray, width: Int, height: Int): StreamTarget? {
        var i = 0
        var formatIndex = 1
        var yuy2: Boolean? = null
        while (i + 1 < descriptors.size) {
            val length = descriptors[i].toInt() and 0xFF
            if (length < 2) break
            val type = descriptors[i + 1].toInt() and 0xFF
            if (type == CS_INTERFACE && i + 2 < descriptors.size) {
                val subtype = descriptors[i + 2].toInt() and 0xFF
                if (subtype == VS_FORMAT && length >= 21 && i + 20 < descriptors.size) {
                    formatIndex = descriptors[i + 3].toInt() and 0xFF
                    yuy2 = isYuy2(descriptors, i + 5)
                }
                if (subtype == VS_FRAME && length >= 9 && i + 8 < descriptors.size && yuy2 != false) {
                    val w = le16(descriptors, i + 5)
                    val h = le16(descriptors, i + 7)
                    if (w == width && h == height) {
                        val frameIndex = descriptors[i + 3].toInt() and 0xFF
                        val interval = if (length >= 25 && i + 24 < descriptors.size) {
                            le32(descriptors, i + 21).takeIf { it > 0 } ?: INTERVAL_25_HZ
                        } else {
                            INTERVAL_25_HZ
                        }
                        return StreamTarget(formatIndex.coerceAtLeast(1), frameIndex.coerceAtLeast(1), interval)
                    }
                }
            }
            i += length
        }
        return null
    }

    /** Largest isochronous IN endpoint on a video-streaming interface. */
    fun largestIsoIn(descriptors: ByteArray): IsoIn? {
        var i = 0
        var interfaceId = -1
        var alternate = 0
        var streaming = false
        var best: IsoIn? = null
        while (i + 1 < descriptors.size) {
            val length = descriptors[i].toInt() and 0xFF
            if (length < 2) break
            val type = descriptors[i + 1].toInt() and 0xFF
            if (type == INTERFACE && length >= 9 && i + 8 < descriptors.size) {
                interfaceId = descriptors[i + 2].toInt() and 0xFF
                alternate = descriptors[i + 3].toInt() and 0xFF
                val clazz = descriptors[i + 5].toInt() and 0xFF
                val sub = descriptors[i + 6].toInt() and 0xFF
                streaming = clazz == VIDEO_CLASS && sub == VIDEO_STREAMING
            } else if (streaming && type == ENDPOINT && length >= 7 && i + 6 < descriptors.size) {
                val address = descriptors[i + 2].toInt() and 0xFF
                val attributes = descriptors[i + 3].toInt() and 0xFF
                val inbound = address and 0x80 != 0
                val iso = attributes and 0x03 == 1
                if (inbound && iso) {
                    val raw = le16(descriptors, i + 4)
                    val size = raw and 0x07FF
                    val extra = (raw shr 11) and 0x03
                    val bytes = size * (extra + 1)
                    val current = best
                    if (bytes > 0 && (current == null || bytes > current.packetBytes)) {
                        best = IsoIn(interfaceId, alternate, bytes)
                    }
                }
            }
            i += length
        }
        return best
    }

    private fun isYuy2(bytes: ByteArray, at: Int): Boolean {
        if (at + 15 >= bytes.size) return false
        for (n in YUY2.indices) {
            if ((bytes[at + n].toInt() and 0xFF) != YUY2[n]) return false
        }
        return true
    }

    private fun le16(bytes: ByteArray, at: Int): Int {
        if (at + 1 >= bytes.size) return -1
        return (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    }

    private fun le32(bytes: ByteArray, at: Int): Int {
        if (at + 3 >= bytes.size) return -1
        return (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
    }
}
