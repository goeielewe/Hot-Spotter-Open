package com.benchfirewatch.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.benchfirewatch.R
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.ui.MainActivity

class AlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var liftedDnd = false
    private var previousFilter: Int? = null

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Hot Spotter", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ongoing thermal watch for Hot Spotter. Alarms use the siren, not this channel's sound."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun foreground(
        status: MonitorStatus,
        headline: String,
        silenced: Boolean = false,
        launchFullScreen: Boolean = false,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ack = PendingIntent.getService(
            context,
            2,
            Intent(context, com.benchfirewatch.service.MonitorService::class.java).setAction(MonitorActions.ACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(status.name)
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(headline))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val cover = AlarmCover.visible(status, silenced)
        if (cover) {
            builder.addAction(0, "Acknowledge", ack)
            builder.priority = NotificationCompat.PRIORITY_MAX
            if (launchFullScreen && fullScreenIntentAllowed(Build.VERSION.SDK_INT, canUseFullScreenIntent())) {
                val fullScreen = PendingIntent.getActivity(
                    context,
                    3,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.setFullScreenIntent(fullScreen, true)
            }
        } else {
            builder.priority = NotificationCompat.PRIORITY_LOW
        }
        return builder.build()
    }

    fun allowThroughDnd() {
        if (Build.VERSION.SDK_INT < 23) return
        if (!manager.isNotificationPolicyAccessGranted || liftedDnd) return
        previousFilter = manager.currentInterruptionFilter
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        liftedDnd = true
    }

    fun restoreDnd() {
        if (!liftedDnd) return
        if (Build.VERSION.SDK_INT >= 23 && manager.isNotificationPolicyAccessGranted) {
            previousFilter?.let { manager.setInterruptionFilter(it) }
        }
        liftedDnd = false
        previousFilter = null
    }

    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return manager.canUseFullScreenIntent()
    }

    companion object {
        const val CHANNEL = "com.hotspotter.open"
        const val NOTIFICATION_ID = 7101

        /** Android 14+ requires the user grant. Older releases may use the declared permission. */
        fun fullScreenIntentAllowed(sdkInt: Int, granted: Boolean): Boolean = sdkInt < 34 || granted
    }
}

fun Context.fullScreenAlarmsGranted(): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val manager = getSystemService(NotificationManager::class.java) ?: return false
    return manager.canUseFullScreenIntent()
}

object MonitorActions {
    const val START = "com.hotspotter.open.START"
    const val STOP = "com.hotspotter.open.STOP"
    const val ACK = "com.hotspotter.open.ACK"
    const val BOOT = "com.hotspotter.open.BOOT"
    const val RECOVERY = "com.hotspotter.open.RECOVERY"
    const val PREFS = "com.hotspotter.open.PREFS"
    const val LOG_START = "com.hotspotter.open.LOG_START"
    const val LOG_STOP = "com.hotspotter.open.LOG_STOP"
    const val LOG_MARK = "com.hotspotter.open.LOG_MARK"
    const val LOG_REFERENCE = "com.hotspotter.open.LOG_REFERENCE"
    const val LOG_FLUSH = "com.hotspotter.open.LOG_FLUSH"
    const val SIREN_TEST = "com.hotspotter.open.SIREN_TEST"
    const val REMOTE_TEST = "com.hotspotter.open.REMOTE_TEST"
    const val SHELLY_TEST = "com.hotspotter.open.SHELLY_TEST"
    const val SELF_TEST = "com.hotspotter.open.SELF_TEST"
    const val HOT_WORK = "com.hotspotter.open.HOT_WORK"
    const val EXTRA_TEXT = "text"
    const val EXTRA_TEMP = "temp"
    const val EXTRA_MINUTES = "minutes"
}
