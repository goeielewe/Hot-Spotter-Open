package com.benchfirewatch.data

import android.content.Context

/** Last release notes the user has already been shown, stored beside the other prefs. */
class VersionSeen(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun seenCode(): Int = prefs.getInt(KEY, 0)

    fun markSeen(code: Int) {
        prefs.edit().putInt(KEY, code).apply()
    }

    /** True when this install already had 1.0.0 settings or a watch session. */
    fun hadPriorData(): Boolean = SettingsStore(app).hasSavedPrefs() || SessionStore(app).hasRecord()

    companion object {
        private const val FILE = "com.hotspotter.open"
        private const val KEY = "seen_version_code"
    }
}
