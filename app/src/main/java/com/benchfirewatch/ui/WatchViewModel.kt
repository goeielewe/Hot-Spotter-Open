package com.benchfirewatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.benchfirewatch.alert.SelfTestReminder
import com.benchfirewatch.data.EventLog
import com.benchfirewatch.data.SettingsStore
import com.benchfirewatch.data.StoredEvent
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.service.MonitorRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WatchViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val log = EventLog(app)

    private val _prefs = MutableStateFlow(store.load())
    val prefs: StateFlow<AppPrefs> = _prefs

    private val _diskEvents = MutableStateFlow(log.load())
    val diskEvents: StateFlow<List<StoredEvent>> = _diskEvents

    val runtime = MonitorRuntime.state

    fun save(prefs: AppPrefs) {
        val reminderChanged = _prefs.value.selfTestReminder != prefs.selfTestReminder
        _prefs.value = prefs
        store.save(prefs)
        if (reminderChanged) SelfTestReminder.sync(getApplication(), prefs.selfTestReminder)
        MonitorRuntime.updatePrefs(getApplication(), prefs)
    }

    fun update(block: (AppPrefs) -> AppPrefs) {
        save(block(_prefs.value))
    }

    fun reloadDisk() {
        _prefs.value = store.load()
        _diskEvents.value = log.load()
    }
}
