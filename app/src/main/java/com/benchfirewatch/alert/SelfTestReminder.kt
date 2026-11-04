package com.benchfirewatch.alert

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.benchfirewatch.R
import com.benchfirewatch.ui.MainActivity

object SelfTestReminder {
    const val ACTION = "com.hotspotter.open.SELF_TEST_REMINDER"
    private const val CHANNEL = "com.hotspotter.open.self_test"
    private const val NOTIFICATION_ID = 7102

    fun sync(context: Context, enabled: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(context)
        if (!enabled) {
            alarm.cancel(pending)
            return
        }
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Self-Test", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily reminder to run the Hot Spotter self-test."
            }
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Run a Hot Spotter Self-Test")
            .setContentText("Flick a lighter or hold something hot in view.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, SelfTestReminderReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            8,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class SelfTestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SelfTestReminder.ACTION) return
        SelfTestReminder.notify(context)
    }
}
