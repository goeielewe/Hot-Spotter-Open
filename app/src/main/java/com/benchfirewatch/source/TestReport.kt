package com.benchfirewatch.source

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One raw frame plus USB ids for a beta tester to send back.
 * No account, no serial number, no location.
 */
object TestReport {
    private const val MAX_LOG = 80
    private val notes = ArrayDeque<String>()
    @Volatile var vendorId: Int = 0
    @Volatile var productId: Int = 0
    @Volatile var kind: String = "none"
    @Volatile var frame: ByteArray? = null
    @Volatile var descriptors: String = ""
    @Volatile var rawDescriptors: ByteArray? = null

    fun note(line: String) {
        synchronized(notes) {
            notes.addLast(line.take(180))
            while (notes.size > MAX_LOG) notes.removeFirst()
        }
    }

    fun remember(
        vendor: Int,
        product: Int,
        camera: String,
        sample: ByteArray?,
        descriptorSummary: String = "",
        raw: ByteArray? = null,
    ) {
        vendorId = vendor
        productId = product
        kind = camera
        frame = sample?.copyOf(minOf(sample.size, 256 * 1024))
        descriptors = descriptorSummary.take(2_000)
        rawDescriptors = raw?.copyOf(minOf(raw.size, 4_096))
        note("saw ${camera} ${hex(vendor)}:${hex(product)} bytes=${sample?.size ?: 0}")
    }

    fun share(context: Context) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "hot-spotter-report-$stamp.txt")
        file.writeText(text())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Hot Spotter camera report")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(send, "Send test report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun text(): String = buildString {
        appendLine("Hot Spotter camera report")
        appendLine("camera=$kind")
        appendLine("vendor=${hex(vendorId)}")
        appendLine("product=${hex(productId)}")
        appendLine("frame_bytes=${frame?.size ?: 0}")
        appendLine("descriptors=$descriptors")
        val raw = rawDescriptors
        if (raw != null && raw.isNotEmpty()) {
            appendLine("descriptor_hex:")
            appendLine(raw.joinToString("") { "%02x".format(it) })
        }
        appendLine("notes:")
        synchronized(notes) { notes.forEach { appendLine(it) } }
        val sample = frame
        if (sample != null) {
            appendLine("frame_hex:")
            appendLine(sample.joinToString("") { "%02x".format(it) })
        }
    }

    private fun hex(value: Int): String = "%04x".format(value)
}
