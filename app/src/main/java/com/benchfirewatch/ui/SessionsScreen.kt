package com.benchfirewatch.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.benchfirewatch.data.SessionFile
import com.benchfirewatch.data.SessionFiles
import com.benchfirewatch.data.StoredEvent
import com.benchfirewatch.service.MonitorRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(modifier: Modifier, events: List<StoredEvent>, logging: Boolean) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = page == 0, onClick = { page = 0 }, label = { Text("Events") })
            FilterChip(selected = page == 1, onClick = { page = 1 }, label = { Text("Sessions") })
        }
        if (page == 0) {
            EventsScreen(Modifier.weight(1f), events)
        } else {
            SessionsScreen(Modifier.weight(1f), logging)
        }
    }
}

@Composable
private fun SessionsScreen(modifier: Modifier, logging: Boolean) {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SessionFile?>(null) }
    val sessions = remember(generation, logging) { SessionFiles.list(context) }
    val open = selected?.let { name -> sessions.firstOrNull { it.csv.name == name } }

    LaunchedEffect(logging) {
        MonitorRuntime.flushLog(context)
        generation++
    }

    if (pendingDelete != null) {
        HoldPagerWhileShown()
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete This Log?") },
            text = { Text(target.csv.name) },
            confirmButton = {
                TextButton(onClick = {
                    SessionFiles.delete(target)
                    if (selected == target.csv.name) selected = null
                    pendingDelete = null
                    generation++
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (open != null) {
        SessionPreview(
            modifier = modifier,
            session = open,
            onBack = { selected = null },
            onShare = { shareSession(context, open) },
            onDelete = { pendingDelete = open },
        )
        return
    }

    if (sessions.isEmpty()) {
        Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No Log Sessions", color = chromePaper(), style = MaterialTheme.typography.headlineSmall)
            Text(
                "Turn on Logging on the watch screen. Each session is a CSV in this app's private documents folder. Share it from here when you want the thermocouple comparison.",
                color = chromeMuted(),
            )
        }
        return
    }

    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.csv.name }) { session ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(session.csv.name, color = chromePaper(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatWhen(session.modifiedMs)}  ·  ${formatSize(session.bytes)}  ·  ${session.images.size} alarm PNG",
                    color = chromeMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(onClick = { selected = session.csv.name }) { Text("View") }
                    TextButton(onClick = { shareSession(context, session) }) { Text("Share") }
                    TextButton(onClick = { pendingDelete = session }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun SessionPreview(
    modifier: Modifier,
    session: SessionFile,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val text = remember(session.csv.name, session.bytes) { SessionFiles.preview(session) }
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = onShare) { Text("Share") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
        Text(session.csv.name, color = chromePaper(), style = MaterialTheme.typography.titleMedium)
        Text(
            text,
            color = chromePaper(),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
        if (session.images.isNotEmpty()) {
            Text(
                "${session.images.size} alarm snapshot${if (session.images.size == 1) "" else "s"} will be included when you share.",
                color = chromeMuted(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Files stay in app storage until you delete them or uninstall.",
            color = chromeMuted(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun shareSession(context: android.content.Context, session: SessionFile) {
    val authority = "${context.packageName}.fileprovider"
    val uris = (listOf(session.csv) + session.images).map { file ->
        FileProvider.getUriForFile(context, authority, file)
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("session", uris.first())
    val chooser = Intent.createChooser(intent, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun formatWhen(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(ms))

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
