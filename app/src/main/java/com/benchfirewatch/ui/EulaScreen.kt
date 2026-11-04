package com.benchfirewatch.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Full-screen license. Accept is required before the rest of the app.
 * Review mode is the copy opened later from Settings or Help.
 */
@Composable
fun EulaScreen(
    review: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val loaded = remember { loadEula(context) }
    BackHandler(onBack = if (review) onClose else onDecline)
    // Shown before the main scaffold, on first launch and from Settings or Help.
    Scaffold(
        containerColor = chromeInk(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (review) "End-User License" else "Accept the License to Continue",
                color = chromePaper(),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (review) "You can read this any time. Closing it does not change your acceptance."
                else "Hot Spotter stays closed until you accept. Decline exits the app.",
                color = chromeMuted(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    loaded.getOrElse { "The license text could not be loaded. You cannot accept it until it is readable." },
                    color = chromePaper(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (review) {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Close")
                }
            } else {
                Button(
                    onClick = onAccept,
                    enabled = loaded.isSuccess,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Decline")
                }
            }
        }
    }
}

internal fun loadEula(context: Context): Result<String> = runCatching {
    context.assets.open("eula.txt").bufferedReader().use { it.readText().trim() }
}
