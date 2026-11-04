package com.benchfirewatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.benchfirewatch.alert.SelfTestReminder
import com.benchfirewatch.data.EulaStore
import com.benchfirewatch.data.SessionStore
import com.benchfirewatch.data.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (!EulaStore(context).hasAccepted()) return
        val settings = SettingsStore(context).load()
        SelfTestReminder.sync(context, settings.selfTestReminder)
        val session = SessionStore(context)
        if (settings.startOnBoot || (session.monitoring && !session.cleanStop)) {
            MonitorRuntime.boot(context)
        }
    }
}
