package com.benchfirewatch.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.benchfirewatch.alert.AlarmCover
import com.benchfirewatch.alert.AlertNotifier
import com.benchfirewatch.alert.AlertOutbox
import com.benchfirewatch.alert.MonitorActions
import com.benchfirewatch.alert.NtfyAlert
import com.benchfirewatch.alert.NtfyMessage
import com.benchfirewatch.alert.RemoteAlert
import com.benchfirewatch.alert.SelfTestReminder
import com.benchfirewatch.alert.PlugEndpoint
import com.benchfirewatch.alert.PlugKind
import com.benchfirewatch.alert.PlugSecrets
import com.benchfirewatch.alert.PowerPlugs
import com.benchfirewatch.alert.ShellyGeneration
import com.benchfirewatch.alert.SirenGate
import com.benchfirewatch.alert.SirenPlayer
import com.benchfirewatch.audio.VentDetector
import com.benchfirewatch.engine.FlameWatch
import com.benchfirewatch.evidence.EvidenceFrames
import com.benchfirewatch.evidence.EvidenceRing
import com.benchfirewatch.evidence.GifClip
import com.benchfirewatch.evidence.OpenClip
import com.benchfirewatch.health.PhoneHealth
import com.benchfirewatch.health.PhoneHealthInput
import com.benchfirewatch.selftest.SelfTestInput
import com.benchfirewatch.selftest.SelfTestScore
import java.io.File
import java.util.TimeZone
import kotlin.math.max
import com.benchfirewatch.data.EventLog
import com.benchfirewatch.data.SessionFiles
import com.benchfirewatch.data.SessionStore
import com.benchfirewatch.data.SettingsStore
import com.benchfirewatch.data.StoredEvent
import com.benchfirewatch.log.BackgroundLog
import com.benchfirewatch.log.LoggedBlob
import com.benchfirewatch.log.alertLabel
import com.benchfirewatch.log.classifyBlob
import com.benchfirewatch.engine.Decision
import com.benchfirewatch.engine.TrackSnapshot
import com.benchfirewatch.engine.WatchEngine
import com.benchfirewatch.model.AppPrefs
import com.benchfirewatch.model.CameraChoice
import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.StartReason
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.cameraChoice
import com.benchfirewatch.model.faultOnStart
import com.benchfirewatch.model.TemperatureScale
import com.benchfirewatch.model.sanitized
import com.benchfirewatch.render.HotSpotFinder
import com.benchfirewatch.render.PreviewFrames
import com.benchfirewatch.source.CameraBattery
import com.benchfirewatch.source.CameraBatteryWatch
import com.benchfirewatch.source.EditionCamera
import com.benchfirewatch.source.FailedSource
import com.benchfirewatch.source.SimulatedThermalSource
import com.benchfirewatch.source.SourceState
import com.benchfirewatch.source.ThermalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsStore: SettingsStore
    private lateinit var session: SessionStore
    private lateinit var eventLog: EventLog
    private lateinit var siren: SirenPlayer
    private lateinit var notifier: AlertNotifier
    private lateinit var wakeLock: PowerManager.WakeLock

    private var prefs = AppPrefs()
    private var engine = WatchEngine(WatchSettings())
    private var source: ThermalSource? = null
    private var ticker: Job? = null
    @Volatile private var running = false
    private var postedNotificationKey = ""
    private var stoppingClean = false
    private var frameId = 0L
    private var argb: IntArray? = null
    private var width = 0
    private var height = 0
    private var contentLeft = 0f
    private var contentTop = 0f
    private var contentRight = 1f
    private var contentBottom = 1f
    private var hotX = Float.NaN
    private var hotY = Float.NaN
    private var hotC = Float.NaN
    private var displayNote = ""
    private var tracks: List<TrackSnapshot> = emptyList()
    private var status = MonitorStatus.OFF
    private var headline = "Watch is off"
    private var sirenOn = false
    private var silenced = false
    private var diagnostics = ""
    private var flameNote = ""
    private var sourceDetail = ""
    private var frameTimeMs = 0L
    private var lastFreshFrameMs = Long.MIN_VALUE
    private var hotWorkUntilMs: Long? = null
    private var hotWorkAnnounced = false
    private var cameraBattery = CameraBattery()
    private var batteryWarnLevel = 0
    private var lastBatteryPercent: Int? = null
    private val batteryLock = Any()
    private var lastClock = 0L
    private val storedEvents = ArrayDeque<StoredEvent>()
    private val remoteSent = HashSet<String>()
    private var diskLog: BackgroundLog? = null
    @Volatile private var logFileName = ""
    @Volatile private var lastFrameMax = Float.NaN
    private val engineLock = Any()
    private val outbox = AlertOutbox()
    private val evidence = EvidenceRing()
    private val openClips = ArrayList<OpenClip>()
    private val evidenceLock = Any()
    private val phoneHealth = PhoneHealth()
    private var healthNotes: List<String> = emptyList()
    private var remoteNote = ""
    private var shellyNote = ""
    private var shellyWarning = ""
    private var selfTestNote = ""
    private val ventLines = ArrayDeque<String>()
    private var ventBoostUntil = 0L
    private var lastVentLog = 0L
    private var lastShellyProbe = 0L
    @Volatile private var plugCutPending = false
    private var sceneRange = Float.NaN
    private var sceneChanging = false
    private var sceneSig = 0
    private var pumping = false
    private var ventArmed = false
    @Volatile private var ventStop = false
    /** A reboot may start the watch, but not the microphone, on current Android. */
    private var micDeferred = false
    private var ventThread: Thread? = null
    private var ventRecorder: AudioRecord? = null
    private var ventLevel = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        session = SessionStore(this)
        eventLog = EventLog(this)
        siren = SirenPlayer(this)
        notifier = AlertNotifier(this)
        notifier.ensureChannel()
        prefs = settingsStore.load()
        storedEvents.addAll(eventLog.load())
        loadOutbox()
        SelfTestReminder.sync(this, prefs.selfTestReminder)
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.hotspotter.open:monitor")
        wakeLock.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifier.ensureChannel()
        val openingKey = AlarmCover.notificationKey(status, headline, silenced)
        val openingCover = openingKey != postedNotificationKey && AlarmCover.fullScreenIntent(status, silenced)
        postedNotificationKey = openingKey
        promote(notifier.foreground(status, headline, silenced, openingCover))
        when (intent?.action) {
            MonitorActions.STOP -> {
                stopWatch()
                return START_NOT_STICKY
            }
            MonitorActions.ACK -> {
                if (running) acknowledge()
                return START_STICKY
            }
            MonitorActions.PREFS -> {
                if (running) reloadPrefs(resetIfScenarioChanged = true)
                return START_STICKY
            }
            MonitorActions.LOG_START -> {
                if (!running) begin(StartReason.USER)
                startDiskLog()
                return START_STICKY
            }
            MonitorActions.LOG_STOP -> {
                stopDiskLog()
                return START_STICKY
            }
            MonitorActions.LOG_MARK -> {
                val label = intent?.getStringExtra(MonitorActions.EXTRA_TEXT).orEmpty()
                diskLog?.offerMark(System.currentTimeMillis(), label)
                return START_STICKY
            }
            MonitorActions.LOG_REFERENCE -> {
                val temp = intent?.getFloatExtra(MonitorActions.EXTRA_TEMP, Float.NaN) ?: Float.NaN
                if (!temp.isNaN()) diskLog?.offerReference(System.currentTimeMillis(), temp.toDouble())
                return START_STICKY
            }
            MonitorActions.LOG_FLUSH -> {
                diskLog?.flush()
                return START_STICKY
            }
            MonitorActions.SIREN_TEST -> {
                playSirenTest()
                return START_STICKY
            }
            MonitorActions.REMOTE_TEST -> {
                prefs = PrefsBridge.pending ?: settingsStore.load()
                scope.launch(Dispatchers.IO) {
                    runRemoteTest()
                    stopIfIdle()
                }
                return START_STICKY
            }
            MonitorActions.SHELLY_TEST -> {
                prefs = PrefsBridge.pending ?: settingsStore.load()
                scope.launch(Dispatchers.IO) {
                    runShellyTest()
                    stopIfIdle()
                }
                return START_STICKY
            }
            MonitorActions.SELF_TEST -> {
                prefs = PrefsBridge.pending ?: settingsStore.load()
                scope.launch(Dispatchers.IO) {
                    runSelfTest()
                    stopIfIdle()
                }
                return START_STICKY
            }
            MonitorActions.HOT_WORK -> {
                if (!running) begin(StartReason.USER)
                applyHotWork(intent?.getIntExtra(MonitorActions.EXTRA_MINUTES, -1) ?: -1)
                return START_STICKY
            }
            MonitorActions.BOOT -> begin(StartReason.BOOT)
            MonitorActions.RECOVERY -> begin(StartReason.RECOVERY)
            MonitorActions.START -> {
                val dirty = session.monitoring && !session.cleanStop && !running
                begin(if (dirty) StartReason.RECOVERY else StartReason.USER)
            }
            else -> begin(if (session.monitoring && !session.cleanStop) StartReason.RECOVERY else StartReason.USER)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask is false, so a swipe leaves the foreground watch running.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ticker?.cancel()
        stopVent()
        stopDiskLog()
        source?.stop()
        siren.stop()
        notifier.restoreDnd()
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun begin(reason: StartReason) {
        if (running) {
            if (reason == StartReason.USER) {
                micDeferred = false
                reloadPrefs(resetIfScenarioChanged = true)
            }
            return
        }
        stoppingClean = false
        micDeferred = reason == StartReason.BOOT
        postedNotificationKey = ""
        prefs = PrefsBridge.pending ?: settingsStore.load()
        val fault = faultOnStart(reason, session.monitoring, session.cleanStop)
        val now = System.currentTimeMillis()
        session.markRunning(now)
        if (!wakeLock.isHeld) wakeLock.acquire()
        engine = WatchEngine(engineSettings(), prefs.zones)
        synchronized(engineLock) { engine.start(now) }
        lastClock = now
        running = true
        if (fault != null) {
            apply(synchronized(engineLock) { engine.raiseProcessFault(now, fault) })
        }
        startSource()
        syncVent()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && running) {
                delay(1000)
                val nowMs = System.currentTimeMillis()
                session.heartbeat(nowMs)
                if (ventBoostUntil != 0L && nowMs >= ventBoostUntil) {
                    ventBoostUntil = 0L
                    synchronized(engineLock) { engine.update(engineSettings(), prefs.zones) }
                }
                pumpOutbox(nowMs)
                probePlug(nowMs)
                pollPhone(nowMs)
                flushClips(nowMs)
                if (prefs.cameraChoice().hardware) {
                    val current = source?.state?.value
                    if (current !is SourceState.Live) {
                        apply(synchronized(engineLock) {
                            engine.onHealth(System.currentTimeMillis(), false, detailOf(current))
                        })
                    }
                }
                synchronized(engineLock) { engine.hotWorkActive(clock()) }
                noteHotWorkEnd()
                refreshNotification()
                publish()
            }
        }
        publish()
    }

    private fun reloadPrefs(resetIfScenarioChanged: Boolean) {
        val next = PrefsBridge.pending ?: settingsStore.load()
        val previous = prefs
        prefs = next
        synchronized(engineLock) { engine.update(engineSettings(), next.zones) }
        syncVent()
        source?.setEmissivity(next.emissivity.coerceIn(0.05f, 1f))
        source?.setDisplay(next.viewMode, next.palette)
        val cameraChanged = previous.cameraId != next.cameraId
        val scenarioChanged = previous.scenarioId != next.scenarioId
        val speedChanged = previous.demoSpeed != next.demoSpeed
        if (cameraChanged) {
            startSource()
            synchronized(engineLock) { engine.start(clock()) }
            noteHotWorkEnd()
        } else if (next.cameraChoice() == CameraChoice.DEMO && source is SimulatedThermalSource && (scenarioChanged || speedChanged)) {
            (source as SimulatedThermalSource).setScenario(next.scenarioId, next.demoSpeed)
            if (resetIfScenarioChanged && scenarioChanged) {
                synchronized(engineLock) { engine.start(clock()) }
                noteHotWorkEnd()
            }
        }
        diskLog?.setHz(next.logHz.toDouble())
        syncSpeaker()
        publish()
    }

    /**
     * Speaker only. Muting does not change the alarm status, the notification,
     * or the log. A test clip is not cancelled by a quiet frame.
     */
    private fun syncSpeaker() {
        if (SirenGate.shouldSound(sirenOn, prefs.sirenEnabled)) {
            siren.startAlarm()
            notifier.allowThroughDnd()
        } else {
            siren.stopAlarm()
            notifier.restoreDnd()
        }
    }

    private fun playSirenTest() {
        siren.playTest()
        scope.launch {
            delay(SirenGate.TEST_MS + 250)
            siren.stopTest()
            if (!running) {
                if (Build.VERSION.SDK_INT >= 24) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun startSource() {
        lastBatteryPercent = null
        batteryWarnLevel = 0
        cameraBattery = CameraBattery()
        source?.stop()
        val selected = prefs.cameraChoice()
        val opened: ThermalSource = when (selected) {
            CameraChoice.DEMO -> SimulatedThermalSource(prefs.scenarioId, prefs.demoSpeed)
            CameraChoice.USB -> EditionCamera.create(applicationContext, prefs.emissivity)
        }
        source = opened
        opened.setEmissivity(prefs.emissivity.coerceIn(0.05f, 1f))
        opened.setDisplay(prefs.viewMode, prefs.palette)
        if (opened is FailedSource) {
            apply(synchronized(engineLock) { engine.raiseProcessFault(clock(), opened.reason) })
        }
        opened.start(
            scope,
            onFrame = { frame -> onFrame(frame) },
            onClock = { clock -> onClock(clock) },
        )
    }

    private fun onFrame(frame: ThermalFrame) {
        if (!running) return
        lastClock = frame.timestampMs
        val preview = frame.preview ?: if (frame.displayNote.isNotBlank()) {
            PreviewFrames.standIn(frame, prefs.palette)
        } else {
            PreviewFrames.forDemo(frame, prefs.viewMode, prefs.palette)
        }
        argb = preview.argb
        width = preview.width
        height = preview.height
        contentLeft = preview.contentLeft
        contentTop = preview.contentTop
        contentRight = preview.contentRight
        contentBottom = preview.contentBottom
        val hot = HotSpotFinder.find(frame)
        hotX = hot?.x ?: Float.NaN
        hotY = hot?.y ?: Float.NaN
        hotC = hot?.celsius ?: Float.NaN
        displayNote = frame.displayNote.ifBlank { preview.note }
        frameId++
        lastFrameMax = hottest(frame.celsius)
        val decision = synchronized(engineLock) {
            if (!running) return
            engine.onFrame(frame)
        }
        apply(decision, fromFrame = true)
        if (!running) return
        recordSample(decision)
        noteScene(frame)
        offerEvidence(frame)
    }

    private fun onClock(clock: Long) {
        if (!running) return
        lastClock = clock
        val current = source?.state?.value
        if (current !is SourceState.Live) {
            val decision = synchronized(engineLock) {
                if (!running) return
                engine.onHealth(clock, sourceLive = false, detail = detailOf(current))
            }
            apply(decision)
        }
    }

    private fun acknowledge() {
        outbox.acknowledge()
        scope.launch(Dispatchers.IO) { persistOutbox() }
        apply(synchronized(engineLock) { engine.acknowledge(clock()) })
    }

    private fun apply(decision: Decision, fromFrame: Boolean = false) {
        synchronized(engineLock) {
            if (!running) return
            applyLocked(decision, fromFrame)
        }
    }

    private fun applyLocked(decision: Decision, fromFrame: Boolean) {
        status = decision.status
        headline = decision.headline
        sirenOn = decision.siren
        silenced = decision.silenced
        tracks = decision.tracks
        diagnostics = describe(decision.tracks)
        flameNote = decision.flameNote
        frameTimeMs = decision.timeMs
        lastFreshFrameMs = decision.lastFreshFrameMs
        hotWorkUntilMs = decision.hotWorkUntilMs
        sourceDetail = detailOf(source?.state?.value)
        noteHotWorkEnd()
        syncSpeaker()
        decision.events.forEach { event ->
            if (event.status == MonitorStatus.OK || event.status == MonitorStatus.SEARCHING || event.status == MonitorStatus.OFF) {
                return@forEach
            }
            val stored = eventLog.append(event.timeMs, event.status, event.message, event.frame)
            synchronized(storedEvents) {
                storedEvents.addFirst(stored)
                while (storedEvents.size > 80) storedEvents.removeLast()
            }
            val key = "${event.timeMs}:${event.status}:${event.message}"
            if (remoteSent.add(key) && (prefs.ntfyUrl.isNotBlank() || prefs.webhookUrl.isNotBlank())) {
                scope.launch(Dispatchers.IO) {
                    RemoteAlert.send(prefs.ntfyUrl, prefs.webhookUrl, event.status, event.message, event.timeMs, decision.siren)
                }
            }
            if (NtfyAlert.repeats(event.status)) {
                armRepeat(event.status, event.timeMs)
                cutCharger()
                beginClip(stored.id, event.timeMs)
            }
        }
        noteLog(decision, fromFrame)
        refreshNotification()
        publish()
    }

    private fun stopWatch() {
        if (hotWorkAnnounced) {
            hotWorkAnnounced = false
            hotWorkUntilMs = null
            pushSideEvent(MonitorStatus.OK, "Hot-work mode ended. Flame watch still on.")
        }
        stoppingClean = true
        running = false
        plugCutPending = false
        ticker?.cancel()
        stopVent()
        stopDiskLog()
        source?.stop()
        source = null
        session.markStopped()
        siren.stop()
        notifier.restoreDnd()
        if (wakeLock.isHeld) wakeLock.release()
        synchronized(engineLock) {
            engine.stop(clock())
            status = MonitorStatus.OFF
            headline = "Watch is off. The bench is not being monitored."
            sirenOn = false
            silenced = false
            tracks = emptyList()
            diagnostics = ""
            flameNote = ""
            cameraBattery = CameraBattery()
            batteryWarnLevel = 0
            lastBatteryPercent = null
            healthNotes = emptyList()
            postedNotificationKey = ""
            publish()
        }
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * Camera battery is its own warning. It does not change the watch status
     * and does not replace the camera-lost fault, which still comes from health.
     */
    private fun noteBattery() {
        val notices = synchronized(batteryLock) {
            if (!running) {
                cameraBattery = CameraBattery()
                batteryWarnLevel = 0
                return
            }
            val reading = source?.cameraBattery() ?: CameraBattery()
            cameraBattery = reading
            if (reading.placeholder) {
                batteryWarnLevel = 0
                return
            }
            val percent = reading.percent
            batteryWarnLevel = CameraBatteryWatch.warnLevel(percent)
            val found = CameraBatteryWatch.notices(lastBatteryPercent, percent)
            lastBatteryPercent = CameraBatteryWatch.remember(lastBatteryPercent, percent)
            found
        }
        if (notices.isEmpty()) return
        val now = System.currentTimeMillis()
        notices.forEach { notice ->
            val stored = eventLog.append(now, MonitorStatus.WARNING, notice.message, null)
            synchronized(storedEvents) {
                storedEvents.addFirst(stored)
                while (storedEvents.size > 80) storedEvents.removeLast()
            }
            val key = "battery:${notice.level}:$now:${notice.message}"
            if (remoteSent.add(key) && (prefs.ntfyUrl.isNotBlank() || prefs.webhookUrl.isNotBlank())) {
                scope.launch(Dispatchers.IO) {
                    RemoteAlert.send(
                        prefs.ntfyUrl,
                        prefs.webhookUrl,
                        MonitorStatus.WARNING,
                        notice.message,
                        now,
                        siren = false,
                    )
                }
            }
        }
    }

    private fun currentNotification(): android.app.Notification {
        val cover = AlarmCover.fullScreenIntent(status, silenced)
        return notifier.foreground(status, headline, silenced, cover)
    }

    private fun refreshNotification() {
        if (!running) return
        val key = AlarmCover.notificationKey(status, headline, silenced)
        if (key == postedNotificationKey) return
        postedNotificationKey = key
        promote(currentNotification())
    }

    private fun promote(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(AlertNotifier.NOTIFICATION_ID, notification, foregroundTypes())
        } else {
            startForeground(AlertNotifier.NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundTypes(): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (ventArmed && micAllowed()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun publish() {
        noteBattery()
        MonitorRuntime.publish(
            MonitorUiState(
                running = running,
                status = status,
                headline = headline,
                siren = sirenOn,
                silenced = silenced,
                frameId = frameId,
                argb = argb,
                width = width,
                height = height,
                contentLeft = contentLeft,
                contentTop = contentTop,
                contentRight = contentRight,
                contentBottom = contentBottom,
                hotX = hotX,
                hotY = hotY,
                hotC = hotC,
                displayNote = displayNote,
                cameraBattery = cameraBattery,
                batteryWarnLevel = batteryWarnLevel,
                tracks = tracks,
                zones = prefs.zones,
                sourceDetail = sourceDetail,
                diagnostics = diagnostics,
                flameNote = flameNote,
                prefs = prefs,
                events = synchronized(storedEvents) { storedEvents.toList() },
                logging = diskLog?.active == true,
                logSamples = diskLog?.sampleCount ?: 0,
                logElapsedSec = diskLog?.let { log ->
                    if (!log.active) 0 else ((System.currentTimeMillis() - log.startedAtMs) / 1000L).toInt()
                } ?: 0,
                logFileName = logFileName,
                logError = diskLog?.error.orEmpty(),
                healthNotes = healthNotes,
                remoteNote = remoteNote,
                shellyNote = shellyNote,
                shellyWarning = shellyWarning,
                selfTestNote = selfTestNote,
                ventDetections = synchronized(ventLines) { ventLines.toList() },
                frameTimeMs = frameTimeMs,
                lastFreshFrameMs = lastFreshFrameMs,
                hotWorkUntilMs = if (running) hotWorkUntilMs else null,
            ),
        )
    }

    /**
     * A new preset replaces the deadline. It is not written to disk, and [WatchEngine.start]
     * clears it, so a restarted watch does not resume the mode.
     */
    private fun applyHotWork(minutes: Int) {
        if (!running) return
        if (minutes != 5 && minutes != 15 && minutes != 30) {
            synchronized(engineLock) { engine.setHotWorkUntil(null) }
            hotWorkUntilMs = null
            if (hotWorkAnnounced) {
                hotWorkAnnounced = false
                pushSideEvent(MonitorStatus.OK, "Hot-work mode cancelled. Flame watch still on.")
            } else {
                publish()
            }
            return
        }
        val until = clock() + minutes * 60_000L
        synchronized(engineLock) { engine.setHotWorkUntil(until) }
        hotWorkUntilMs = until
        hotWorkAnnounced = true
        pushSideEvent(MonitorStatus.OK, "Hot-work mode for $minutes minutes. Flame watch still on.")
    }

    private fun noteHotWorkEnd() {
        val until = synchronized(engineLock) { engine.hotWorkUntil() }
        hotWorkUntilMs = until
        if (hotWorkAnnounced && until == null) {
            hotWorkAnnounced = false
            pushSideEvent(MonitorStatus.OK, "Hot-work mode ended. Flame watch still on.")
        }
    }

    private fun startDiskLog() {
        if (diskLog?.active == true) {
            diskLog?.setHz(prefs.logHz.toDouble())
            return
        }
        val started = System.currentTimeMillis()
        val log = BackgroundLog(
            openWriter = {
                val (file, writer) = SessionFiles.create(this, started)
                logFileName = file.name
                writer
            },
            sampleHz = prefs.logHz.toDouble(),
            onSnapshot = { frame ->
                val base = logFileName.removeSuffix(".csv")
                if (base.isNotBlank()) SessionFiles.writeSnapshot(this, base, frame, System.currentTimeMillis())
            },
        )
        diskLog = log
        log.start(started)
        publish()
    }

    private fun stopDiskLog() {
        val log = diskLog ?: return
        diskLog = null
        log.stop()
        publish()
    }

    private fun recordSample(decision: Decision) {
        val log = diskLog ?: return
        if (!log.active || lastFrameMax.isNaN()) return
        val cellMin = prefs.settings.sanitized().cellMinAreaFraction
        val blobs = decision.tracks.map { track ->
            LoggedBlob(
                id = track.id,
                maxC = track.maxC,
                areaFraction = track.areaFraction,
                classification = classifyBlob(
                    tool = track.tool,
                    inGrace = track.inGrace,
                    areaFraction = track.areaFraction,
                    cellMinAreaFraction = cellMin,
                    warnedOrAlarmed = track.warning || track.alarming,
                ),
                riseCPerSec = track.riseCPerSec,
            )
        }
        log.offerSample(
            System.currentTimeMillis(),
            lastFrameMax,
            alertLabel(decision.status),
            blobs,
            decision.events.map { it.message },
        )
    }

    private fun noteLog(decision: Decision, fromFrame: Boolean) {
        val log = diskLog ?: return
        if (!log.active) return
        if (prefs.logAlarmSnapshots) {
            decision.events.forEach { event ->
                if ((event.status == MonitorStatus.ALARM || event.status == MonitorStatus.FLAME) && event.frame != null) {
                    log.offerSnapshot(event.frame)
                }
            }
        }
        if (!fromFrame) {
            val max = lastFrameMax.takeIf { !it.isNaN() }
            decision.events.forEach { event ->
                log.offerEvent(System.currentTimeMillis(), alertLabel(decision.status), max, event.message)
            }
        }
    }

    private fun hottest(values: FloatArray): Float {
        var max = Float.NEGATIVE_INFINITY
        for (value in values) if (value > max) max = value
        return max
    }

    private fun clock(): Long = if (lastClock != 0L) lastClock else System.currentTimeMillis()

    private fun detailOf(state: SourceState?): String = when (state) {
        is SourceState.Live -> state.detail
        is SourceState.Searching -> state.detail
        is SourceState.Down -> state.detail
        SourceState.Idle, null -> if (prefs.cameraChoice().hardware) "Camera idle" else "Demo idle"
    }

    private fun describe(tracks: List<TrackSnapshot>): String {
        if (tracks.isEmpty()) return "No hot blobs"
        return tracks.joinToString("   ") { track ->
            val kind = when {
                track.alarming -> "ALARM"
                track.warning -> "warn"
                track.tool -> "iron"
                track.inGrace -> "grace"
                else -> "spot"
            }
            val rise = track.riseCPerSec?.let { TemperatureScale.formatRate(it, prefs.useFahrenheit) } ?: "n/a"
            val body = TemperatureScale.formatAbsolute(track.bodyC, prefs.useFahrenheit, 0)
            val max = TemperatureScale.formatAbsolute(track.maxC, prefs.useFahrenheit, 0)
            "$kind $body  max $max  ${signedTenth(track.areaFraction * 100f)}%  $rise"
        }
    }

    private fun signedTenth(value: Float): String {
        val rounded = kotlin.math.round(value * 10f) / 10f
        return if (rounded > 0f) "+$rounded" else rounded.toString()
    }

    private fun engineSettings(): WatchSettings {
        val base = prefs.settings.sanitized()
        val boost = prefs.ventListenerEnabled && System.currentTimeMillis() < ventBoostUntil
        val level = VentDetector.boostedSensitivity(base.flameSensitivity, boost)
        return if (level == base.flameSensitivity) base else base.copy(flameSensitivity = level)
    }

    private fun armRepeat(status: MonitorStatus, timeMs: Long) {
        if (!prefs.remoteAlertsEnabled) return
        val message = NtfyAlert.message(status, lastFrameMax, timeMs, prefs.ntfyTopic) ?: return
        outbox.enqueue(message, repeat = true, label = "alarm", nowMs = timeMs)
        scope.launch(Dispatchers.IO) { persistOutbox() }
    }

    private fun cutCharger() {
        val endpoint = plugEndpoint() ?: return
        if (plugCutPending) return
        plugCutPending = true
        scope.launch(Dispatchers.IO) {
            try {
                val result = PowerPlugs.cut(endpoint)
                if (result.confirmedOff) {
                    shellyNote = result.detail
                    shellyWarning = ""
                    val decision = synchronized(engineLock) { engine.clearPlugFault(clock()) }
                    apply(decision)
                } else {
                    shellyWarning = result.detail
                    val decision = synchronized(engineLock) { engine.raisePlugFault(clock(), result.detail) }
                    apply(decision)
                }
            } finally {
                plugCutPending = false
            }
        }
    }

    private fun plugEndpoint(): PlugEndpoint? {
        val kind = PlugKind.fromStored(prefs.plugKind, prefs.shellyEnabled)
        if (kind == PlugKind.NONE) return null
        val secret = when (kind) {
            PlugKind.TAPO, PlugKind.TASMOTA -> PlugSecrets.password(this)
            PlugKind.HOME_ASSISTANT -> PlugSecrets.token(this)
            else -> ""
        }
        return PlugEndpoint(
            kind = kind,
            address = prefs.plugHost.ifBlank { prefs.shellyHost },
            shellyGeneration = ShellyGeneration.fromStored(prefs.shellyGeneration),
            username = prefs.plugUsername,
            secret = secret,
            entityId = prefs.plugEntity,
        )
    }

    private fun beginClip(eventId: String, timeMs: Long) {
        if (!prefs.alarmClipsEnabled) return
        synchronized(evidenceLock) {
            if (openClips.size >= 2) return
            openClips.add(OpenClip(eventId, timeMs + EvidenceFrames.AFTER_MS, evidence.copy().toMutableList()))
        }
    }

    private fun noteScene(frame: ThermalFrame) {
        if (!prefs.cameraChoice().hardware) {
            sceneChanging = false
            sceneRange = Float.NaN
            return
        }
        val sig = EvidenceFrames.signature(frame.celsius)
        sceneChanging = sig != sceneSig
        sceneSig = sig
        sceneRange = EvidenceFrames.range(frame.celsius)
    }

    private fun offerEvidence(frame: ThermalFrame) {
        if (!prefs.alarmClipsEnabled) return
        val preview = frame.preview
        val sample = EvidenceFrames.sample(
            frame.celsius,
            frame.width,
            frame.height,
            frame.timestampMs,
            preview?.argb,
            preview?.width ?: 0,
            preview?.height ?: 0,
        )
        val done = ArrayList<OpenClip>()
        synchronized(evidenceLock) {
            evidence.add(sample)
            for (clip in openClips) {
                clip.frames.add(sample.copyBytes())
                if (frame.timestampMs >= clip.untilMs) done.add(clip)
            }
            if (done.isNotEmpty()) openClips.removeAll(done.toSet())
        }
        if (done.isNotEmpty()) scope.launch(Dispatchers.IO) { done.forEach { finishClip(it) } }
    }

    private fun flushClips(nowMs: Long) {
        val done = ArrayList<OpenClip>()
        synchronized(evidenceLock) {
            val iterator = openClips.iterator()
            while (iterator.hasNext()) {
                val clip = iterator.next()
                if (nowMs >= clip.untilMs + 1_500L) {
                    done.add(clip)
                    iterator.remove()
                }
            }
        }
        if (done.isNotEmpty()) scope.launch(Dispatchers.IO) { done.forEach { finishClip(it) } }
    }

    private fun finishClip(clip: OpenClip) {
        val gif = GifClip.encode(clip.frames) ?: return
        val dir = File(filesDir, "evidence")
        val file = GifClip.writeFile(dir, clip.eventId, gif)
        val removed = GifClip.prune(dir).toSet()
        eventLog.attachClip(clip.eventId, file.absolutePath, removed)
        synchronized(storedEvents) {
            val copy = storedEvents.map { event ->
                when {
                    event.id == clip.eventId -> event.copy(clipPath = file.absolutePath)
                    event.clipPath != null && event.clipPath in removed -> event.copy(clipPath = null)
                    else -> event
                }
            }
            storedEvents.clear()
            storedEvents.addAll(copy)
        }
        publish()
    }

    private fun pumpOutbox(nowMs: Long) {
        if (pumping) return
        val due = outbox.due(nowMs)
        if (due.isEmpty()) return
        pumping = true
        scope.launch(Dispatchers.IO) {
            try {
                due.forEach { item ->
                    val ok = NtfyAlert.post(NtfyMessage(item.url, item.body, item.title, item.priority))
                    val now = System.currentTimeMillis()
                    if (ok) {
                        outbox.markSent(item.id, now)
                        if (item.label == "test") remoteNote = "Test push sent."
                    } else {
                        outbox.markFailed(item.id, now)
                        if (item.label == "test") {
                            remoteNote = if (item.attempts + 1 >= 4) {
                                "Test push failed."
                            } else {
                                "Test push failed. It will be retried."
                            }
                        }
                    }
                }
                persistOutbox()
                publish()
            } finally {
                pumping = false
            }
        }
    }

    private fun probePlug(nowMs: Long) {
        val endpoint = plugEndpoint()
        if (!running || endpoint == null || !PowerPlugs.configured(endpoint)) {
            if (endpoint == null) shellyWarning = ""
            return
        }
        if (nowMs - lastShellyProbe < 30_000L) return
        lastShellyProbe = nowMs
        scope.launch(Dispatchers.IO) {
            if (PowerPlugs.confirmedOff(endpoint)) {
                shellyWarning = ""
                val decision = synchronized(engineLock) { engine.clearPlugFault(clock()) }
                apply(decision)
            } else if (PowerPlugs.reachable(endpoint)) {
                if (!shellyWarning.contains("confirm")) shellyWarning = ""
            } else if (shellyWarning.isBlank()) {
                shellyWarning = "The plug did not answer. Reserve its address on the router so it stays put."
            }
            publish()
        }
    }

    private fun pollPhone(nowMs: Long) {
        if (!running) return
        val (percent, charging, known) = readPhone()
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            getSystemService(PowerManager::class.java)?.currentThermalStatus ?: -1
        } else {
            -1
        }
        val hardware = prefs.cameraChoice().hardware && source?.state?.value is SourceState.Live
        val snap = phoneHealth.poll(
            PhoneHealthInput(
                batteryPercent = percent,
                charging = charging,
                chargingKnown = known,
                thermalStatus = thermal,
                sceneRangeC = sceneRange.takeIf { !it.isNaN() },
                framesChanging = sceneChanging,
                hardwareCamera = hardware,
                nowMs = nowMs,
            ),
        )
        healthNotes = snap.showing
        snap.fresh.forEach { notice ->
            pushSideEvent(MonitorStatus.WARNING, notice.message)
        }
    }

    private fun readPhone(): Triple<Int?, Boolean, Boolean> {
        val sticky = try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (_: Exception) {
            null
        } ?: return Triple(null, false, false)
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val state = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val known = state != -1
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        return Triple(percent, charging, known)
    }

    private fun pushSideEvent(status: MonitorStatus, message: String) {
        val stored = eventLog.append(System.currentTimeMillis(), status, message, null)
        synchronized(storedEvents) {
            storedEvents.addFirst(stored)
            while (storedEvents.size > 80) storedEvents.removeLast()
        }
        publish()
    }

    private fun runRemoteTest() {
        val message = NtfyAlert.testMessage(lastFrameMax, System.currentTimeMillis(), prefs.ntfyTopic)
        if (!prefs.remoteAlertsEnabled || message == null) {
            remoteNote = "Turn remote alerts on and set a topic, then try again."
            publish()
            return
        }
        val ok = NtfyAlert.post(message)
        if (ok) {
            remoteNote = "Test push sent."
        } else if (running) {
            outbox.enqueue(message, repeat = false, label = "test", nowMs = System.currentTimeMillis())
            persistOutbox()
            remoteNote = "Test push failed. It will be retried."
        } else {
            remoteNote = "Test push failed."
        }
        publish()
    }

    private fun runShellyTest() {
        val endpoint = plugEndpoint()
        if (endpoint == null || !PowerPlugs.configured(endpoint)) {
            shellyNote = "Pick a plug and enter its address, then try again."
            publish()
            return
        }
        val alarming = status == MonitorStatus.FLAME || status == MonitorStatus.ALARM || status == MonitorStatus.FAULT
        val result = PowerPlugs.test(endpoint, alarming)
        shellyNote = result.detail
        if (!result.confirmedOff) shellyWarning = result.detail
        publish()
    }

    private suspend fun runSelfTest() {
        val watchOn = running
        val heat = watchOn && !sceneRange.isNaN() && sceneRange >= 4f
        val flameOk = FlameWatch.selfCheck()
        val alarming = status == MonitorStatus.FLAME || status == MonitorStatus.ALARM || status == MonitorStatus.FAULT
        val sirenEnabled = prefs.sirenEnabled
        var sirenOk = alarming && sirenEnabled
        if (sirenEnabled && watchOn && !alarming) {
            try {
                siren.playTest()
                sirenOk = true
                delay(900)
                siren.stopTest()
                syncSpeaker()
            } catch (_: Exception) {
                sirenOk = false
            }
        }
        val remoteConfigured = prefs.remoteAlertsEnabled && NtfyAlert.url(NtfyAlert.DEFAULT_SERVER, prefs.ntfyTopic) != null
        var remoteOk = false
        if (remoteConfigured) {
            val message = NtfyAlert.testMessage(lastFrameMax, System.currentTimeMillis(), prefs.ntfyTopic)
            remoteOk = message != null && NtfyAlert.post(message)
            if (message != null && !remoteOk) {
                outbox.enqueue(message, repeat = false, label = "test", nowMs = System.currentTimeMillis())
                remoteNote = "Test push failed. It will be retried."
            } else if (remoteOk) {
                remoteNote = "Test push sent."
            }
        }
        val plugEndpoint = plugEndpoint()
        val plugConfigured = plugEndpoint != null && PowerPlugs.configured(plugEndpoint)
        var plugOff = false
        var plugOn = false
        if (plugConfigured && plugEndpoint != null) {
            val result = PowerPlugs.test(plugEndpoint, alarming)
            shellyNote = result.detail
            plugOff = result.confirmedOff
            plugOn = result.confirmedOn
            if (!result.confirmedOff) shellyWarning = result.detail
        }
        val report = SelfTestScore.score(
            SelfTestInput(
                watchRunning = watchOn,
                sawHeat = heat,
                flamePathOk = flameOk,
                sirenEnabled = sirenEnabled,
                sirenOk = sirenOk,
                alarmAlreadySounding = alarming,
                remoteConfigured = remoteConfigured,
                remoteOk = remoteOk,
                plugConfigured = plugConfigured,
                plugOffOk = plugOff,
                plugOnOk = plugOn,
                leftOffForAlarm = alarming && plugOff,
            ),
        )
        selfTestNote = report.summary()
        pushSideEvent(if (report.failed) MonitorStatus.WARNING else MonitorStatus.OK, report.summary())
    }

    private fun onVentHit() {
        val now = System.currentTimeMillis()
        ventBoostUntil = now + VentDetector.BOOST_MS
        synchronized(engineLock) { engine.update(engineSettings(), prefs.zones) }
        val line = NtfyAlert.formatTime(now, TimeZone.getDefault()) + " vent click"
        synchronized(ventLines) {
            ventLines.addFirst(line)
            while (ventLines.size > 8) ventLines.removeLast()
        }
        if (now - lastVentLog >= 5_000L) {
            lastVentLog = now
            pushSideEvent(
                MonitorStatus.WARNING,
                "Vent sound. The flame watch is one step more sensitive for about a minute. This is not a full alarm.",
            )
        } else {
            publish()
        }
    }

    private fun syncVent() {
        val want = running && !micDeferred && prefs.ventListenerEnabled && micAllowed()
        if (!want) {
            stopVent()
            return
        }
        if (ventThread != null && ventLevel == prefs.ventSensitivity) return
        stopVent()
        ventArmed = true
        ventLevel = prefs.ventSensitivity
        promote(currentNotification())
        startVentThread()
        if (!ventArmed) promote(currentNotification())
    }

    private fun startVentThread() {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            ventArmed = false
            return
        }
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(min, rate),
            )
        } catch (_: SecurityException) {
            ventArmed = false
            null
        }
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            ventArmed = false
            return
        }
        val detector = VentDetector(prefs.ventSensitivity)
        ventStop = false
        try {
            recorder.startRecording()
        } catch (_: Exception) {
            recorder.release()
            ventArmed = false
            return
        }
        ventRecorder = recorder
        val thread = Thread {
            val buf = ShortArray(VentDetector.hopSamples(rate))
            while (!ventStop) {
                val n = try {
                    recorder.read(buf, 0, buf.size)
                } catch (_: Exception) {
                    break
                }
                if (n < 0) break
                if (n > 0 && detector.push(buf, n, rate)) {
                    scope.launch { onVentHit() }
                }
            }
        }
        thread.name = "vent-listen"
        ventThread = thread
        thread.start()
    }

    private fun stopVent() {
        val dropMicrophone = ventArmed
        ventStop = true
        ventArmed = false
        ventLevel = -1
        val thread = ventThread
        ventThread = null
        val recorder = ventRecorder
        ventRecorder = null
        if (recorder != null) {
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
        }
        thread?.join(200)
        recorder?.release()
        if (ventBoostUntil != 0L) {
            ventBoostUntil = 0L
            if (running) synchronized(engineLock) { engine.update(engineSettings(), prefs.zones) }
        }
        if (dropMicrophone && running) promote(currentNotification())
    }

    private fun micAllowed(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopIfIdle() {
        if (running) return
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun loadOutbox() {
        val file = File(filesDir, "alert-outbox.txt")
        if (!file.isFile) return
        try {
            outbox.load(file.readText())
        } catch (_: Exception) {
        }
    }

    private fun persistOutbox() {
        try {
            File(filesDir, "alert-outbox.txt").writeText(outbox.encode())
        } catch (_: Exception) {
        }
    }
}
