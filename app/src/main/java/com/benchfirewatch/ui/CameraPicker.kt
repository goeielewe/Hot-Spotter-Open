package com.benchfirewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.CameraChoice
import com.benchfirewatch.model.cameraChoice
import com.benchfirewatch.model.withCamera

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraPicker(
    prefs: AppPrefs,
    compact: Boolean,
    onPrefs: (AppPrefs) -> Unit,
) {
    val selected = prefs.cameraChoice()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Camera", color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CameraChoice.entries.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onPrefs(prefs.withCamera(choice)) },
                    label = { Text(if (compact) choice.displayShort() else choice.displayTitle()) },
                )
            }
        }
        Text(selected.blurb(), color = chromeMuted(), style = MaterialTheme.typography.bodySmall)
    }
}
