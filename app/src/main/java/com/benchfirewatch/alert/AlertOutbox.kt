package com.benchfirewatch.alert

import java.util.UUID

data class OutboxItem(
    val id: String,
    val url: String,
    val body: String,
    val title: String,
    val priority: String,
    val repeat: Boolean,
    val label: String,
    val createdMs: Long,
    var attempts: Int = 0,
    var nextAtMs: Long = createdMs,
)

/**
 * One repeating alarm push, plus any one-shot tests. Nothing here touches the
 * network. The service drains [due] on a background thread.
 */
class AlertOutbox {
    private val items = ArrayList<OutboxItem>()

    @Synchronized
    fun enqueue(message: NtfyMessage, repeat: Boolean, label: String, nowMs: Long): OutboxItem {
        if (repeat) items.removeAll { it.repeat }
        val item = OutboxItem(
            id = UUID.randomUUID().toString(),
            url = message.url,
            body = message.body,
            title = message.title,
            priority = message.priority,
            repeat = repeat,
            label = label,
            createdMs = nowMs,
            nextAtMs = nowMs,
        )
        items.add(item)
        return item
    }

    @Synchronized
    fun due(nowMs: Long): List<OutboxItem> = items.filter { it.nextAtMs <= nowMs }.map { it.copy() }

    @Synchronized
    fun markSent(id: String, nowMs: Long, repeatMs: Long = NtfyAlert.REPEAT_MS) {
        val item = items.firstOrNull { it.id == id } ?: return
        if (item.repeat) {
            item.attempts = 0
            item.nextAtMs = nowMs + repeatMs
        } else {
            items.remove(item)
        }
    }

    @Synchronized
    fun markFailed(id: String, nowMs: Long) {
        val item = items.firstOrNull { it.id == id } ?: return
        item.attempts += 1
        if (!item.repeat && item.attempts >= TEST_ATTEMPTS) {
            items.remove(item)
            return
        }
        item.nextAtMs = nowMs + backoffMs(item.attempts)
    }

    /** Stops the repeating alarm. A test that is still queued is left alone. */
    @Synchronized
    fun acknowledge() {
        items.removeAll { it.repeat }
    }

    @Synchronized
    fun snapshot(): List<OutboxItem> = items.map { it.copy() }

    /** One item per line. Newlines inside the body are escaped. No JSON library. */
    @Synchronized
    fun encode(): String = items.joinToString("\n") { item ->
        listOf(
            item.id,
            item.url,
            escape(item.body),
            escape(item.title),
            item.priority,
            item.repeat.toString(),
            escape(item.label),
            item.createdMs.toString(),
            item.attempts.toString(),
            item.nextAtMs.toString(),
        ).joinToString("\t")
    }

    @Synchronized
    fun load(raw: String) {
        items.clear()
        if (raw.isBlank()) return
        raw.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size < 10 || parts[1].isBlank()) return@forEach
            items.add(
                OutboxItem(
                    id = parts[0].ifBlank { UUID.randomUUID().toString() },
                    url = parts[1],
                    body = unescape(parts[2]),
                    title = unescape(parts[3]),
                    priority = parts[4].ifBlank { NtfyAlert.PRIORITY_MAX },
                    repeat = parts[5] == "true",
                    label = unescape(parts[6]),
                    createdMs = parts[7].toLongOrNull() ?: 0L,
                    attempts = parts[8].toIntOrNull() ?: 0,
                    nextAtMs = parts[9].toLongOrNull() ?: 0L,
                ),
            )
        }
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", " ")

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(text[i + 1])
                }
                i += 2
            } else {
                out.append(text[i])
                i += 1
            }
        }
        return out.toString()
    }

    companion object {
        private const val TEST_ATTEMPTS = 4

        fun backoffMs(attempts: Int): Long = when {
            attempts <= 1 -> 5_000L
            attempts == 2 -> 15_000L
            attempts == 3 -> 30_000L
            else -> 60_000L
        }
    }
}
