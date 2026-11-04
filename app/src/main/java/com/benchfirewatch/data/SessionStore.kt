package com.benchfirewatch.data

import android.content.Context

/** Whether a watch session was stopped on purpose. A kill leaves this dirty. */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var monitoring: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) { prefs.edit().putBoolean(KEY_MONITORING, value).apply() }

    var cleanStop: Boolean
        get() = prefs.getBoolean(KEY_CLEAN, true)
        set(value) { prefs.edit().putBoolean(KEY_CLEAN, value).apply() }

    var lastHeartbeatMs: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) { prefs.edit().putLong(KEY_HEARTBEAT, value).apply() }

    fun markRunning(now: Long) {
        prefs.edit()
            .putBoolean(KEY_MONITORING, true)
            .putBoolean(KEY_CLEAN, false)
            .putLong(KEY_HEARTBEAT, now)
            .apply()
    }

    fun heartbeat(now: Long) {
        prefs.edit().putLong(KEY_HEARTBEAT, now).apply()
    }

    fun hasRecord(): Boolean =
        prefs.contains(KEY_MONITORING) || prefs.contains(KEY_HEARTBEAT) || prefs.contains(KEY_CLEAN)

    fun markStopped() {
        prefs.edit()
            .putBoolean(KEY_MONITORING, false)
            .putBoolean(KEY_CLEAN, true)
            .apply()
    }

    companion object {
        private const val FILE = "com.hotspotter.open.session"
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_CLEAN = "clean_stop"
        private const val KEY_HEARTBEAT = "heartbeat"
    }
}
