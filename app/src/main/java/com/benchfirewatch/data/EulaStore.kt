package com.benchfirewatch.data

import android.content.Context
import com.benchfirewatch.eula.Eula

/** The EULA version this install has accepted. Zero means none. */
class EulaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun acceptedVersion(): Int = prefs.getInt(KEY, 0)

    fun hasAccepted(currentVersion: Int = Eula.VERSION): Boolean =
        !Eula.needsAcceptance(acceptedVersion(), currentVersion)

    fun accept(version: Int = Eula.VERSION) {
        prefs.edit().putInt(KEY, version).apply()
    }

    companion object {
        private const val FILE = "com.hotspotter.open"
        private const val KEY = "eula_version"
    }
}
