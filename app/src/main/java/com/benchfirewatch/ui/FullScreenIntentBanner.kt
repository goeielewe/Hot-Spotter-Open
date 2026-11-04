package com.benchfirewatch.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.benchfirewatch.alert.fullScreenAlarmsGranted

@Composable
fun FullScreenIntentBanner(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 34) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.fullScreenAlarmsGranted()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.fullScreenAlarmsGranted()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (granted) return
    Column(
        modifier.fillMaxWidth().padding(vertical = Space8),
        verticalArrangement = Arrangement.spacedBy(Space8),
    ) {
        Text(
            "Alarms cannot cover the lock screen until full-screen alerts are allowed. Until then they stay a heads-up notification.",
            color = WarnAmber,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow Full-Screen Alarms") }
    }
}
