package com.benchfirewatch.evidence

import java.io.ByteArrayOutputStream
import java.io.File

data class EvidenceSample(
    val timeMs: Long,
    val width: Int,
    val height: Int,
    val thermal: ByteArray,
    val visual: ByteArray?,
) {
    fun copyBytes(): EvidenceSample = copy(
        thermal = thermal.copyOf(),
        visual = visual?.copyOf(),
    )
}

class OpenClip(
    val eventId: String,
    val untilMs: Long,
    val frames: MutableList<EvidenceSample>,
)

/**
 * About ten seconds of downscaled frames. Adding a sample is a short copy.
 * Encoding the GIF happens later, off the frame that raised the alarm.
 */
class EvidenceRing(private val windowMs: Long = EvidenceFrames.WINDOW_MS) {
    private val samples = ArrayDeque<EvidenceSample>()

    fun add(sample: EvidenceSample) {
        samples.addLast(sample)
        val cutoff = sample.timeMs - windowMs
        while (samples.isNotEmpty() && samples.first().timeMs < cutoff) samples.removeFirst()
    }

    fun copy(): List<EvidenceSample> = samples.map { it.copyBytes() }

    fun size(): Int = samples.size
}

object EvidenceFrames {
    const val WINDOW_MS = 10_000L
    const val AFTER_MS = 10_000L
    const val KEEP = 50
    const val MAX_EDGE = 80

    fun sample(
        celsius: FloatArray,
        width: Int,
        height: Int,
        timeMs: Long,
        previewArgb: IntArray?,
        previewWidth: Int,
        previewHeight: Int,
    ): EvidenceSample {
        val (outW, outH) = fit(width, height)
        val thermal = ByteArray(outW * outH)
        for (y in 0 until outH) {
            val sy = (y * height / outH).coerceIn(0, height - 1)
            for (x in 0 until outW) {
                val sx = (x * width / outW).coerceIn(0, width - 1)
                thermal[y * outW + x] = heatIndex(celsius[sy * width + sx])
            }
        }
        val visual = if (previewArgb != null && previewWidth > 0 && previewHeight > 0 &&
            previewArgb.size >= previewWidth * previewHeight
        ) {
            val pixels = ByteArray(outW * outH)
            for (y in 0 until outH) {
                val sy = (y * previewHeight / outH).coerceIn(0, previewHeight - 1)
                for (x in 0 until outW) {
                    val sx = (x * previewWidth / outW).coerceIn(0, previewWidth - 1)
                    val argb = previewArgb[sy * previewWidth + sx]
                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF
                    val luma = (r * 3 + g * 6 + b) / 10
                    pixels[y * outW + x] = luma.toByte()
                }
            }
            pixels
        } else {
            null
        }
        return EvidenceSample(timeMs, outW, outH, thermal, visual)
    }

    fun range(celsius: FloatArray): Float {
        if (celsius.isEmpty()) return 0f
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (value in celsius) {
            if (value < min) min = value
            if (value > max) max = value
        }
        return max - min
    }

    fun signature(celsius: FloatArray): Int {
        var hash = 0
        var i = 0
        while (i < celsius.size) {
            hash = hash * 31 + celsius[i].toBits()
            i += 17
        }
        return hash
    }

    fun fit(width: Int, height: Int): Pair<Int, Int> {
        val edge = maxOf(width, height).coerceAtLeast(1)
        val scale = maxOf(1, edge / MAX_EDGE)
        val outW = maxOf(1, width / scale)
        val outH = maxOf(1, height / scale)
        return outW to outH
    }

    private fun heatIndex(celsius: Float): Byte {
        val t = ((celsius - 15f) / 135f).coerceIn(0f, 1f)
        return (t * 255f).toInt().toByte()
    }
}

object GifClip {
    fun encode(frames: List<EvidenceSample>): ByteArray? {
        if (frames.isEmpty()) return null
        val first = frames.first()
        val usable = frames.filter { it.width == first.width && it.height == first.height && it.thermal.size == first.thermal.size }
        if (usable.isEmpty()) return null
        val sideBySide = usable.any { it.visual != null }
        val width = if (sideBySide) first.width * 2 else first.width
        val height = first.height
        val indexed = usable.map { frame ->
            if (!sideBySide) {
                frame.thermal
            } else {
                val row = ByteArray(width * height)
                val visual = frame.visual
                for (y in 0 until height) {
                    for (x in 0 until first.width) {
                        val i = y * first.width + x
                        row[y * width + x] = frame.thermal[i]
                        row[y * width + first.width + x] = visual?.getOrNull(i) ?: 0
                    }
                }
                row
            }
        }
        val delays = IntArray(usable.size) { index ->
            if (index == 0) {
                10
            } else {
                val delta = (usable[index].timeMs - usable[index - 1].timeMs).coerceIn(20L, 500L)
                (delta / 10L).toInt().coerceIn(2, 50)
            }
        }
        return GifEncoder.write(width, height, indexed, delays)
    }

    fun writeFile(dir: File, eventId: String, gif: ByteArray): File {
        dir.mkdirs()
        val file = File(dir, "$eventId.gif")
        file.writeBytes(gif)
        return file
    }

    /** Deletes the oldest GIFs past [EvidenceFrames.KEEP]. Returns the paths removed. */
    fun prune(dir: File, keep: Int = EvidenceFrames.KEEP): List<String> {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".gif") }
            ?.sortedBy { it.lastModified() }
            ?: return emptyList()
        val extra = files.size - keep
        if (extra <= 0) return emptyList()
        return files.take(extra).map { file ->
            val path = file.absolutePath
            file.delete()
            path
        }
    }
}

internal object GifEncoder {
    fun write(width: Int, height: Int, frames: List<ByteArray>, delayCs: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7)
        out.write(0)
        out.write(0)
        for (i in 0 until 256) {
            val (r, g, b) = iron(i)
            out.write(r)
            out.write(g)
            out.write(b)
        }
        out.write(0x21)
        out.write(0xFF)
        out.write(0x0B)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(0x03)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        frames.forEachIndexed { index, pixels ->
            val delay = delayCs.getOrElse(index) { 10 }
            out.write(0x21)
            out.write(0xF9)
            out.write(0x04)
            out.write(0x04)
            writeShort(out, delay)
            out.write(0)
            out.write(0)
            out.write(0x2C)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, width)
            writeShort(out, height)
            out.write(0)
            val lzw = lzw(pixels)
            out.write(8)
            var pos = 0
            while (pos < lzw.size) {
                val n = minOf(255, lzw.size - pos)
                out.write(n)
                out.write(lzw, pos, n)
                pos += n
            }
            out.write(0)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun iron(index: Int): Triple<Int, Int, Int> {
        val t = index / 255f
        val r = (255f * t).toInt().coerceIn(0, 255)
        val g = (255f * ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)).toInt()
        val b = (255f * ((t - 0.65f) / 0.35f).coerceIn(0f, 1f)).toInt()
        return Triple(r, g, b)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /**
     * GIF LZW, minimum code size 8. Matches the usual variable-width packing:
     * clear is 256, end is 257, and the width grows after the table fills a bit.
     */
    private fun lzw(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf()
        val clear = 256
        val eoi = 257
        var bits = 9
        var next = 258
        val dict = HashMap<Int, Int>(4096)
        val packed = BitPacker()
        packed.write(clear, bits)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val pixel = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or pixel
            val found = dict[key]
            if (found != null) {
                prefix = found
            } else {
                packed.write(prefix, bits)
                if (next == 4096) {
                    packed.write(clear, bits)
                    dict.clear()
                    next = 258
                    bits = 9
                } else {
                    dict[key] = next
                    next += 1
                    if (next > (1 shl bits) && bits < 12) bits += 1
                }
                prefix = pixel
            }
        }
        packed.write(prefix, bits)
        packed.write(eoi, bits)
        return packed.finish()
    }
}

private class BitPacker {
    private val out = ByteArrayOutputStream()
    private var buffer = 0
    private var count = 0

    fun write(code: Int, bits: Int) {
        buffer = buffer or (code shl count)
        count += bits
        while (count >= 8) {
            out.write(buffer and 0xFF)
            buffer = buffer ushr 8
            count -= 8
        }
    }

    fun finish(): ByteArray {
        if (count > 0) out.write(buffer and 0xFF)
        return out.toByteArray()
    }
}
