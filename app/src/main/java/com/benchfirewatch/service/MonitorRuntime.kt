package com.benchfirewatch.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.benchfirewatch.alert.MonitorActions
import com.benchfirewatch.data.StoredEvent
import com.benchfirewatch.engine.TrackSnapshot
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.Zone
import com.benchfirewatch.source.CameraBattery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MonitorUiState(
    val running: Boolean = false,
    val status: MonitorStatus = MonitorStatus.OFF,
    val headline: String = "Watch is off. The bench is not being monitored.",
    val siren: Boolean = false,
    val silenced: Boolean = false,
    val frameId: Long = 0,
    val argb: IntArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    val contentLeft: Float = 0f,
    val contentTop: Float = 0f,
    val contentRight: Float = 1f,
    val contentBottom: Float = 1f,
    val hotX: Float = Float.NaN,
    val hotY: Float = Float.NaN,
    val hotC: Float = Float.NaN,
    val displayNote: String = "",
    val cameraBattery: CameraBattery = CameraBattery(),
    val batteryWarnLevel: Int = 0,
    val tracks: List<TrackSnapshot> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val sourceDetail: String = "",
    val diagnostics: String = "",
    val flameNote: String = "",
    val prefs: AppPrefs = AppPrefs(),
    val events: List<StoredEvent> = emptyList(),
    val logging: Boolean = false,
    val logSamples: Int = 0,
    val logElapsedSec: Int = 0,
    val logFileName: String = "",
    val logError: String = "",
    val healthNotes: List<String> = emptyList(),
    val remoteNote: String = "",
    val shellyNote: String = "",
    val shellyWarning: String = "",
    val selfTestNote: String = "",
    val ventDetections: List<String> = emptyList(),
    /** Latest frame clock. Display only. */
    val frameTimeMs: Long = 0,
    /** Last frame whose pixels changed. Display only. */
    val lastFreshFrameMs: Long = Long.MIN_VALUE,
    /** Hot-work deadline on the frame clock. Null when off. Not saved. */
    val hotWorkUntilMs: Long? = null,
)

object MonitorRuntime {
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state

    fun publish(state: MonitorUiState) {
        _state.value = state
    }

    fun current(): MonitorUiState = _state.value

    fun start(context: Context) = send(context, MonitorActions.START)
    fun stop(context: Context) = send(context, MonitorActions.STOP)
    fun acknowledge(context: Context) = send(context, MonitorActions.ACK)

    fun testSiren(context: Context) = send(context, MonitorActions.SIREN_TEST)
    fun testRemote(context: Context) = send(context, MonitorActions.REMOTE_TEST)
    fun testPlug(context: Context) = send(context, MonitorActions.SHELLY_TEST)

    fun testShelly(context: Context) = testPlug(context)
    fun runSelfTest(context: Context) = send(context, MonitorActions.SELF_TEST)

    /** Starts a 5, 15, or 30 minute hot-work window. Null cancels. It is not saved. */
    fun setHotWork(context: Context, minutes: Int?) {
        if (minutes != null && minutes > 0 && !_state.value.running) {
            send(context, MonitorActions.START)
        }
        deliver(
            context,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorActions.HOT_WORK)
                .putExtra(MonitorActions.EXTRA_MINUTES, minutes ?: -1),
        )
    }
    fun recover(context: Context) = send(context, MonitorActions.RECOVERY)
    fun boot(context: Context) = send(context, MonitorActions.BOOT)

    fun updatePrefs(context: Context, prefs: AppPrefs) {
        PrefsBridge.publish(prefs)
        // Prefs are also on disk. The running service is told to reload; a stopped watch
        // picks them up on the next start.
        if (_state.value.running) send(context, MonitorActions.PREFS)
    }

    fun setLogging(context: Context, enabled: Boolean) {
        if (enabled) {
            if (!_state.value.running) send(context, MonitorActions.START)
            send(context, MonitorActions.LOG_START)
        } else if (_state.value.running || _state.value.logging) {
            send(context, MonitorActions.LOG_STOP)
        }
    }

    fun logReference(context: Context, celsius: Float) {
        if (!_state.value.logging) return
        deliver(
            context,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorActions.LOG_REFERENCE)
                .putExtra(MonitorActions.EXTRA_TEMP, celsius),
        )
    }

    fun logMark(context: Context, label: String) {
        if (!_state.value.logging || label.isBlank()) return
        deliver(
            context,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorActions.LOG_MARK)
                .putExtra(MonitorActions.EXTRA_TEXT, label),
        )
    }

    fun flushLog(context: Context) {
        if (!_state.value.logging) return
        send(context, MonitorActions.LOG_FLUSH)
    }

    private fun send(context: Context, action: String) {
        deliver(context, Intent(context, MonitorService::class.java).setAction(action))
    }

    private fun deliver(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

/** Latest prefs written by the UI, so the service sees them without a race on apply(). */
object PrefsBridge {
    @Volatile var pending: AppPrefs? = null
    fun publish(prefs: AppPrefs) {
        pending = prefs
    }
}
