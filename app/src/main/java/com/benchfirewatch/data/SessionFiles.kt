package com.benchfirewatch.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.render.ThermalPalette
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SessionFile(
    val csv: File,
    val images: List<File>,
    val modifiedMs: Long,
    val bytes: Long,
)

/** CSV logs in app-specific external storage. No storage permission. */
object SessionFiles {
    private val stamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun directory(context: Context): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val root = if (external != null) File(external, "sessions") else File(context.filesDir, "sessions")
        root.mkdirs()
        return root
    }

    fun create(context: Context, nowMs: Long): Pair<File, Writer> {
        val name = "HotSpotter_${stamp.format(Instant.ofEpochMilli(nowMs))}.csv"
        val file = File(directory(context), name)
        val writer = OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8)
        return file to writer
    }

    fun writeSnapshot(context: Context, sessionBase: String, frame: ThermalFrame, nowMs: Long): File? {
        return try {
            val argb = ThermalPalette.render(frame)
            val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(argb, 0, frame.width, 0, 0, frame.width, frame.height)
            val scaled = Bitmap.createScaledBitmap(bitmap, frame.width * 4, frame.height * 4, false)
            val file = File(directory(context), "${sessionBase}_alarm_$nowMs.png")
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            if (scaled !== bitmap) scaled.recycle()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun list(context: Context): List<SessionFile> {
        val dir = directory(context)
        val csvs = dir.listFiles { file -> file.isFile && file.name.endsWith(".csv") }?.toList().orEmpty()
        return csvs.map { csv ->
            val base = csv.nameWithoutExtension
            val images = dir.listFiles { file ->
                file.isFile && file.name.startsWith(base) && file.name.endsWith(".png")
            }?.sortedBy { it.name }.orEmpty()
            SessionFile(csv, images, csv.lastModified(), csv.length() + images.sumOf { it.length() })
        }.sortedByDescending { it.modifiedMs }
    }

    fun delete(session: SessionFile) {
        session.images.forEach { it.delete() }
        session.csv.delete()
    }

    fun preview(session: SessionFile, maxLines: Int = 80): String {
        val lines = ArrayList<String>(maxLines)
        var more = false
        session.csv.bufferedReader().use { reader ->
            while (lines.size < maxLines) {
                val line = reader.readLine() ?: break
                lines.add(line)
            }
            more = reader.readLine() != null
        }
        return if (more) lines.joinToString("\n") + "\n…" else lines.joinToString("\n")
    }
}
