package com.benchfirewatch.data

import android.content.Context
import android.graphics.Bitmap
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.render.ThermalPalette
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StoredEvent(
    val id: String,
    val timeMs: Long,
    val status: MonitorStatus,
    val message: String,
    val imagePath: String?,
    val clipPath: String? = null,
)

class EventLog(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "events").apply { mkdirs() }
    private val index = File(dir, "index.json")

    @Synchronized
    fun load(): List<StoredEvent> {
        if (!index.exists()) return emptyList()
        return try {
            val array = JSONArray(index.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val status = runCatching { MonitorStatus.valueOf(o.getString("status")) }.getOrNull() ?: continue
                    add(
                        StoredEvent(
                            id = o.getString("id"),
                            timeMs = o.getLong("timeMs"),
                            status = status,
                            message = o.optString("message"),
                            imagePath = o.optString("imagePath").ifBlank { null },
                            clipPath = o.optString("clipPath").ifBlank { null },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun append(timeMs: Long, status: MonitorStatus, message: String, frame: ThermalFrame?): StoredEvent {
        val id = UUID.randomUUID().toString()
        val image = frame?.let { writeSnapshot(id, it) }
        val event = StoredEvent(id, timeMs, status, message, image)
        val all = (listOf(event) + load()).take(MAX)
        val dropped = load().drop(MAX - 1)
        dropped.forEach { old ->
            old.imagePath?.let { path -> File(path).delete() }
            old.clipPath?.let { path -> File(path).delete() }
        }
        writeAll(all)
        return event
    }

    @Synchronized
    fun attachClip(id: String, clipPath: String, clearPaths: Set<String> = emptySet()): List<StoredEvent> {
        val all = load().map { event ->
            when {
                event.id == id -> event.copy(clipPath = clipPath)
                event.clipPath != null && event.clipPath in clearPaths -> event.copy(clipPath = null)
                else -> event
            }
        }
        writeAll(all)
        return all
    }

    private fun writeAll(all: List<StoredEvent>) {
        index.writeText(JSONArray().apply {
            all.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("timeMs", e.timeMs)
                    put("status", e.status.name)
                    put("message", e.message)
                    put("imagePath", e.imagePath ?: "")
                    put("clipPath", e.clipPath ?: "")
                })
            }
        }.toString())
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun writeSnapshot(id: String, frame: ThermalFrame): String? {
        return try {
            val argb = ThermalPalette.render(frame)
            val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(argb, 0, frame.width, 0, 0, frame.width, frame.height)
            val scaled = Bitmap.createScaledBitmap(bitmap, frame.width * 4, frame.height * 4, false)
            val file = File(dir, "$id.png")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            if (scaled !== bitmap) scaled.recycle()
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX = 80
    }
}
