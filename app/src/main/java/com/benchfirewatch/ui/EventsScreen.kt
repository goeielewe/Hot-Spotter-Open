package com.benchfirewatch.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.benchfirewatch.data.StoredEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventsScreen(modifier: Modifier, events: List<StoredEvent>) {
    if (events.isEmpty()) {
        Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No Events Yet", color = chromePaper(), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Warnings, alarms, and faults land here with a thermal snapshot. A quiet log means the watch has not seen a hot spot or a camera fault since it was cleared.",
                color = chromeMuted(),
            )
        }
        return
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(events, key = { it.id }) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun EventRow(event: StoredEvent) {
    val style = statusStyle(event.status.name)
    Row(
        Modifier.fillMaxWidth().background(chromePanel(), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(style.icon, contentDescription = style.wording, tint = style.color, modifier = Modifier.size(24.dp))
        val path = event.imagePath
        val image = remember(path) {
            path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Thermal Snapshot",
                modifier = Modifier.width(96.dp),
                contentScale = ContentScale.FillWidth,
                filterQuality = FilterQuality.None,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(style.wording, color = style.color, style = MaterialTheme.typography.titleMedium)
            Text(event.status.name, color = style.color, style = MaterialTheme.typography.labelMedium)
            Text(formatTime(event.timeMs), color = chromeMuted(), style = MaterialTheme.typography.labelMedium)
            Text(event.message, color = chromePaper())
            val clip = event.clipPath
            if (!clip.isNullOrBlank()) {
                val context = LocalContext.current
                TextButton(onClick = {
                    val file = File(clip)
                    if (!file.isFile) return@TextButton
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val view = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "image/gif")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(view) }
                }) { Text("Clip") }
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(ms))
