package com.benchfirewatch.engine

import com.benchfirewatch.model.MonitorStatus
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.WatchSettings
import com.benchfirewatch.model.Zone
import com.benchfirewatch.model.ZoneKind
import com.benchfirewatch.model.sanitized
import kotlin.math.hypot
import kotlin.math.round

data class TrackSnapshot(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val bodyC: Float,
    val maxC: Float,
    val riseCPerSec: Float?,
    val areaFraction: Float,
    val tool: Boolean,
    val alarming: Boolean,
    val warning: Boolean,
    val inGrace: Boolean,
    val label: String,
    /** Drawn on the picture. Noise above the blob line is still tracked, but not boxed. */
    val candidate: Boolean = false,
)

data class WatchEvent(
    val timeMs: Long,
    val status: MonitorStatus,
    val message: String,
    val frame: ThermalFrame?,
)

data class Decision(
    val timeMs: Long,
    val status: MonitorStatus,
    val headline: String,
    val siren: Boolean,
    val silenced: Boolean,
    val tracks: List<TrackSnapshot>,
    val events: List<WatchEvent>,
    /** Shown under the status. Empty until the watch has started. */
    val flameNote: String = "",
    /** Frame clock of the last frame whose pixels changed. Display only. */
    val lastFreshFrameMs: Long = Long.MIN_VALUE,
    /** Hot-work deadline on the frame clock. Null when the mode is off. Display only. */
    val hotWorkUntilMs: Long? = null,
)

/**
 * Turns a stream of radiometric frames into OK / WARNING / ALARM / FAULT.
 *
 * Time is the frame clock, not wall time, so tests and the sped-up demo use
 * the same thresholds as a live camera.
 *
 * An iron or spot welder is a small, sharp, moving or brief hot blob and is
 * ignored by the cell tracker. A cell that appears where that tool just was
 * gets a cool-down grace and is alarmed only if it is not falling when the
 * grace ends. A cell warming with no tool nearby, or sitting above the zone
 * threshold, warns and then alarms. A cell-sized area climbing several degrees
 * a second, or simply very hot, alarms immediately.
 *
 * Open flame is separate. [FlameWatch] looks at the hottest raw pixels against
 * the bench behind them, so a lighter that only covers a few pixels and reads
 * near 100°C still becomes FLAME. Picture mode and rotation never reach this
 * class. The service calls [onFrame] whether or not the screen is on.
 */
class WatchEngine(
    settings: WatchSettings,
    zones: List<Zone> = emptyList(),
) {
    companion object {
        /**
         * A first search, including the USB permission prompt, stays a banner
         * until this long, then one fault. A drop after frames uses the
         * reconnect grace instead.
         */
        const val STARTUP_SEARCH_FAULT_SEC = 30f
    }

    private var settings = settings.sanitized()
    private var zones = zones

    private val tracks = ArrayList<Track>()
    private val toolMemory = ArrayList<Memory>()
    private var nextId = 1
    private var started = false
    private var everLive = false
    private var downSince: Long? = null
    private var healthDetail = "Waiting for frames"
    private var live = false
    private var frozenSince: Long? = null
    private var lastSignature: Int? = null
    private var lastFreshFrameMs: Long = Long.MIN_VALUE
    private var hotWorkUntilMs: Long? = null
    private var lastFrame: ThermalFrame? = null
    private var faultActive = false
    private var faultLatched = false
    private var faultMessage = ""
    private var faultRecovered = false
    private var alarmLatched = false
    private var alarmMessage = ""
    private var silenced = false
    private var ackAt: Long? = null
    private var processFault = false
    private var plugFault = false
    private var warningMessage = ""
    private val flame = FlameWatch()
    private var flameLatched = false
    private var flameMessage = ""
    private var flameSeen = Long.MIN_VALUE
    private var flameAt: FlameHit? = null
    private var flameOk = false
    private var flameNote = ""
    private var rangeCeiling: Float? = null
    private var lastSelfCheck = Long.MIN_VALUE

    fun update(settings: WatchSettings, zones: List<Zone>) {
        this.settings = settings.sanitized()
        this.zones = zones
        flame.configure(this.settings.flameSensitivity)
        if (flameOk) refreshFlameNote()
    }

    /**
     * Hot-work ends at [untilMs] on the frame clock. Null cancels it.
     * [start] clears it. Nothing in the engine adds time on its own.
     */
    fun setHotWorkUntil(untilMs: Long?) {
        hotWorkUntilMs = untilMs
    }

    fun hotWorkUntil(): Long? = hotWorkUntilMs

    /** True only while [now] is still before the deadline. At the deadline it turns off. */
    fun hotWorkActive(now: Long): Boolean {
        val until = hotWorkUntilMs ?: return false
        if (now >= until) {
            hotWorkUntilMs = null
            return false
        }
        return true
    }

    fun start(now: Long) {
        tracks.clear()
        toolMemory.clear()
        started = true
        everLive = false
        downSince = now
        live = false
        frozenSince = null
        lastSignature = null
        lastFreshFrameMs = Long.MIN_VALUE
        hotWorkUntilMs = null
        faultActive = false
        faultLatched = false
        faultRecovered = false
        alarmLatched = false
        silenced = false
        ackAt = null
        processFault = false
        plugFault = false
        warningMessage = ""
        healthDetail = "Waiting for frames"
        flameLatched = false
        flameMessage = ""
        flameSeen = Long.MIN_VALUE
        flameAt = null
        rangeCeiling = null
        flame.reset()
        flame.configure(settings.flameSensitivity)
        lastSelfCheck = now
        flameOk = FlameWatch.selfCheck()
        if (!flameOk) {
            faultLatched = true
            faultActive = true
            processFault = true
            faultRecovered = false
            faultMessage = "Flame detector self-check failed"
            silenced = false
        }
        refreshFlameNote()
    }

    /**
     * The charger plug did not confirm it turned off. This stays latched while
     * the camera is live, and acknowledging it only silences until the rearm
     * time. [clearPlugFault] is the way it ends, once a later check sees off.
     */
    fun raisePlugFault(now: Long, message: String): Decision {
        if (!started) start(now)
        plugFault = true
        faultLatched = true
        faultActive = true
        faultRecovered = false
        processFault = false
        faultMessage = message
        silenced = false
        return decide(now, listOf(WatchEvent(now, MonitorStatus.FAULT, message, lastFrame?.copyFrame())))
    }

    fun clearPlugFault(now: Long): Decision {
        if (!plugFault) return decide(now, emptyList())
        plugFault = false
        if (!flameLatched && !alarmLatched && !processFault) {
            faultActive = false
            faultLatched = false
            faultRecovered = false
            faultMessage = ""
            silenced = false
            ackAt = null
        }
        return decide(now, emptyList())
    }

    fun raiseProcessFault(now: Long, message: String): Decision {
        if (!started) start(now)
        processFault = true
        faultLatched = true
        faultActive = true
        faultRecovered = false
        faultMessage = message
        silenced = false
        return decide(now, listOf(WatchEvent(now, MonitorStatus.FAULT, message, lastFrame?.copyFrame())))
    }

    fun onHealth(now: Long, sourceLive: Boolean, detail: String): Decision {
        live = sourceLive
        healthDetail = detail
        if (!started) return decide(now, emptyList())
        val events = ArrayList<WatchEvent>()
        if (sourceLive) {
            downSince = null
            if (faultActive && !processFault && !plugFault && !frozenNow(now)) {
                faultActive = false
                faultRecovered = true
            }
        } else {
            if (downSince == null) downSince = now
            val grace = if (everLive) {
                settings.reconnectGraceSec
            } else {
                settings.firstConnectGraceSec.coerceAtMost(STARTUP_SEARCH_FAULT_SEC)
            }
            val downSec = (now - (downSince ?: now)) / 1000f
            if (downSec >= grace) {
                latchFault(now, detail.ifBlank { "Thermal source is down" }, events)
            }
        }
        maybeRearm(now, events)
        return decide(now, events)
    }

    fun acknowledge(now: Long): Decision {
        val alarmStillHot = hotAlarm(now) || hotFlame(now)
        val cameraStillDown = faultActive && !processFault
        if (alarmStillHot || cameraStillDown) {
            silenced = true
            ackAt = now
        } else {
            silenced = false
            ackAt = null
            alarmLatched = false
            faultLatched = false
            faultActive = false
            faultRecovered = false
            processFault = false
            flameLatched = false
            flameAt = null
            tracks.forEach {
                it.alarmed = false
                it.warned = false
            }
        }
        return decide(now, emptyList())
    }

    /** Clears every latch. The next decision is OFF, with the cover closed. */
    fun stop(now: Long): Decision {
        tracks.clear()
        toolMemory.clear()
        started = false
        everLive = false
        downSince = null
        live = false
        frozenSince = null
        lastSignature = null
        lastFreshFrameMs = Long.MIN_VALUE
        hotWorkUntilMs = null
        faultActive = false
        faultLatched = false
        faultRecovered = false
        faultMessage = ""
        alarmLatched = false
        alarmMessage = ""
        silenced = false
        ackAt = null
        processFault = false
        plugFault = false
        warningMessage = ""
        flameLatched = false
        flameMessage = ""
        flameSeen = Long.MIN_VALUE
        flameAt = null
        healthDetail = "Watch is off"
        return decide(now, emptyList())
    }

    fun onFrame(frame: ThermalFrame): Decision {
        val now = frame.timestampMs
        if (!started) start(now)
        everLive = true
        live = true
        downSince = null
        lastFrame = frame
        val events = ArrayList<WatchEvent>()
        noteFrozen(frame, now, events)
        if (frame.rangeMaxC != null) rangeCeiling = frame.rangeMaxC
        maybeSelfCheck(now, events)
        refreshFlameNote()
        val hit = flame.onFrame(frame, zones, settings.flameWholeFrame)
        if (hit != null) latchFlame(now, hit, events)
        val blobs = extractBlobs(frame, settings)
        stepTracks(blobs, now, events)
        pruneMemory(now)
        maybeRearm(now, events)
        return decide(now, events)
    }

    private fun stepTracks(blobs: List<Blob>, now: Long, events: MutableList<WatchEvent>) {
        val usable = blobs.filter { zoneAt(it.cx, it.cy)?.kind != ZoneKind.IGNORE }
        val open = tracks.toMutableList()
        for (blob in usable.sortedByDescending { it.pixelCount }) {
            var best: Track? = null
            var bestD = settings.matchDistance
            for (track in open) {
                val d = hypot(track.cx - blob.cx, track.cy - blob.cy)
                if (d < bestD) {
                    bestD = d
                    best = track
                }
            }
            if (best != null) {
                open.remove(best)
                best.absorb(blob, now)
            } else {
                tracks.add(Track(nextId++, blob, now))
            }
        }
        val dead = ArrayList<Track>()
        for (track in open) {
            if ((now - track.lastSeen) / 1000f > settings.coastSec) dead.add(track)
        }
        tracks.removeAll(dead.toSet())

        for (track in tracks) {
            if (track.lastSeen != now) {
                if (!track.alarmed) track.candidate = false
                continue
            }
            evaluate(track, now, events)
        }
    }

    /** A box is for a blob the watch is following, not every patch above the background. */
    private fun isCandidate(track: Track, policy: Policy): Boolean {
        if (track.tool || track.inGrace || track.alarmed || track.warned) return true
        if (track.body >= policy.warn) return true
        return track.area >= settings.cellMinAreaFraction && track.body >= settings.selfHeatMinTempC
    }

    private fun evaluate(track: Track, now: Long, events: MutableList<WatchEvent>) {
        val zone = zoneAt(track.cx, track.cy)
        val policy = policyFor(zone)
        track.zoneName = zone?.name ?: "Bench"
        try {
            evaluateBody(track, now, events, policy)
        } finally {
            track.candidate = isCandidate(track, policy)
        }
    }

    private fun evaluateBody(track: Track, now: Long, events: MutableList<WatchEvent>, policy: Policy) {
        val speed = track.speed(now)
        val ageSec = (now - track.born) / 1000f
        val sharp = track.max - track.body
        val tool = isTool(track, speed, ageSec, sharp)
        val wasTool = track.tool
        track.tool = tool
        if (tool) {
            toolMemory.add(Memory(track.cx, track.cy, now))
            track.samples.clear()
            track.risingSince = null
            track.aboveWarnSince = null
            track.aboveAlarmSince = null
            track.runawaySince = null
            track.restingSince = null
            track.inGrace = false
            track.rise = null
            return
        }
        if (wasTool) track.samples.clear()
        track.samples.addLast(Sample(now, track.body))
        while (track.samples.isNotEmpty() && now - track.samples.first().t > 30_000) {
            track.samples.removeFirst()
        }

        val lastTool = latestToolNear(track.cx, track.cy, now)
        val graceMs = (policy.graceSec * 1000f).toLong()
        val inGrace = lastTool != null && now - lastTool < graceMs
        track.inGrace = inGrace
        val rateShort = slope(track.samples, now, settings.runawayWindowSec)
        val rateNow = slope(track.samples, now, (settings.runawayWindowSec * 0.6f).coerceAtLeast(0.4f))
        val rateMed = slope(track.samples, now, settings.selfHeatWindowSec)
        track.rise = rateMed ?: rateShort

        // A joint heats quickly while the iron is on it, and the blob's body
        // temperature jumps once when the tip pulls away. That is not runaway.
        // Runaway is a cell-sized rise that keeps going after the iron is gone.
        val ironJustLeft = lastTool != null && now - lastTool <= 1_500L
        val risingFast = rateShort != null && rateShort >= policy.runawayRate &&
            rateNow != null && rateNow >= policy.runawayRate * 0.5f
        if (!ironJustLeft && track.area >= settings.cellMinAreaFraction && risingFast) {
            if (track.runawaySince == null) track.runawaySince = now
            if (now - track.runawaySince!! >= (settings.runawaySustainSec * 1000f).toLong()) {
                alarm(track, now, "Rapid rise ${formatRate(rateShort!!)} on a cell-sized spot", events)
            }
        } else {
            track.runawaySince = null
        }

        if (track.area >= settings.cellMinAreaFraction && track.body >= policy.runawayTemp) {
            alarm(track, now, "Cell-sized spot at ${track.body.toInt()}°C", events)
        }

        // Hot-work quiets only the slow hot-blob path inside an iron holder or solder zone.
        // Runaway, a very hot cell, flame, and faults are handled elsewhere and stay on.
        val quietSlow = slowHeatSuppressed(track, now)

        if (!tool && track.max >= settings.toolAbsC && track.area <= settings.movingToolMaxAreaFraction) {
            if (track.restingSince == null) track.restingSince = now
            val rested = (now - track.restingSince!!) / 1000f
            if (!quietSlow && rested >= settings.restingHotAlarmSec) {
                alarm(track, now, "A very hot spot has been sitting still. Iron left down, or a joint that is not cooling.", events)
            } else if (!quietSlow && rested >= settings.restingHotWarnSec) {
                warn(track, now, "Hot spot staying put outside the iron holder", events)
            }
        } else {
            track.restingSince = null
        }

        if (inGrace) {
            track.risingSince = null
            track.aboveWarnSince = null
            return
        }

        val toolAssociated = lastTool != null &&
            now - lastTool < graceMs + (settings.coolingWatchSec * 1000f).toLong()
        if (toolAssociated && track.body >= policy.warn - 1f) {
            val cooling = rateMed != null && rateMed < settings.minCoolingSlope
            if (rateMed != null && !cooling && !quietSlow) {
                alarm(
                    track,
                    now,
                    "Soldered spot is still ${track.body.toInt()}°C and is not cooling",
                    events,
                )
            }
            if (cooling || rateMed == null) return
        }

        if (track.area < settings.cellMinAreaFraction) return

        val rising = rateMed != null &&
            rateMed >= policy.selfHeatRate &&
            track.body >= settings.selfHeatMinTempC
        if (rising) {
            if (track.risingSince == null) track.risingSince = now
        } else if (rateMed != null && rateMed < policy.selfHeatRate * 0.4f) {
            track.risingSince = null
        }

        if (track.body >= policy.warn) {
            if (track.aboveWarnSince == null) track.aboveWarnSince = now
        } else if (track.body < policy.warn - 2f) {
            track.aboveWarnSince = null
            if (track.warned && !track.alarmed) track.warned = false
        }

        if (track.body >= policy.alarm) {
            if (track.aboveAlarmSince == null) track.aboveAlarmSince = now
        } else if (track.body < policy.alarm - 2f) {
            track.aboveAlarmSince = null
        }

        val risingLong = track.risingSince != null &&
            now - track.risingSince!! >= (policy.selfHeatSustainSec * 1000f).toLong()
        val hotLong = track.aboveWarnSince != null &&
            now - track.aboveWarnSince!! >= (policy.warnSustainSec * 1000f).toLong()
        if (!quietSlow && (risingLong || hotLong) && !track.alarmed) {
            val why = if (hotLong) {
                "Holding ${track.body.toInt()}°C with no iron nearby"
            } else {
                "Warming ${formatRate(rateMed ?: 0f)} with no iron nearby"
            }
            warn(track, now, why, events)
        }

        if (!quietSlow && track.warned) {
            val sustainedAlarm = track.aboveAlarmSince != null &&
                now - track.aboveAlarmSince!! >= (policy.alarmHoldSec * 1000f).toLong()
            val notCooling = rateMed == null || rateMed >= settings.minCoolingSlope
            val stayedHot = track.aboveWarnSince != null &&
                now - track.aboveWarnSince!! >= (policy.escalateSec * 1000f).toLong() &&
                notCooling &&
                track.body >= policy.warn
            if (sustainedAlarm) {
                alarm(track, now, "Still ${track.body.toInt()}°C after the warning", events)
            } else if (stayedHot) {
                alarm(track, now, "Heat is holding at ${track.body.toInt()}°C and not falling", events)
            }
        }
    }

    private fun isTool(track: Track, speed: Float, ageSec: Float, sharp: Float): Boolean {
        val hotContrast = track.max >= track.body + settings.toolContrastC || track.max - 27f >= settings.toolContrastC
        val absHot = track.max >= settings.toolAbsC
        if (!hotContrast && !absHot) return false
        val small = track.area <= settings.toolMaxAreaFraction
        val compact = track.area <= settings.movingToolMaxAreaFraction
        val moving = speed >= settings.toolMinSpeed
        val young = ageSec <= settings.toolTransientSec
        if (small && absHot && (moving || young)) return true
        if (compact && moving && (absHot || hotContrast)) return true
        if (compact && young && sharp >= settings.toolSharpnessC && (absHot || hotContrast)) return true
        return false
    }

    private fun slowHeatSuppressed(track: Track, now: Long): Boolean {
        if (!hotWorkActive(now)) return false
        val kind = zoneAt(track.cx, track.cy)?.kind ?: return false
        return kind == ZoneKind.IGNORE || kind == ZoneKind.SOLDER
    }

    private fun warn(track: Track, now: Long, message: String, events: MutableList<WatchEvent>) {
        if (track.warned || track.alarmed || alarmLatched) return
        track.warned = true
        warningMessage = "${track.zoneName}: $message"
        events.add(WatchEvent(now, MonitorStatus.WARNING, warningMessage, lastFrame?.copyFrame()))
    }

    private fun alarm(track: Track, now: Long, message: String, events: MutableList<WatchEvent>) {
        val text = "${track.zoneName}: $message"
        if (!track.alarmed) {
            track.alarmed = true
            track.warned = true
            alarmLatched = true
            alarmMessage = text
            silenced = false
            events.add(WatchEvent(now, MonitorStatus.ALARM, text, lastFrame?.copyFrame()))
        }
    }

    private fun latchFault(now: Long, message: String, events: MutableList<WatchEvent>) {
        faultActive = true
        faultRecovered = false
        if (!faultLatched) {
            faultLatched = true
            faultMessage = message
            silenced = false
            processFault = false
            events.add(WatchEvent(now, MonitorStatus.FAULT, message, lastFrame?.copyFrame()))
        }
    }

    private fun noteFrozen(frame: ThermalFrame, now: Long, events: MutableList<WatchEvent>) {
        val sig = signature(frame)
        if (sig == lastSignature) {
            if (frozenSince == null) frozenSince = now
            val frozenFor = (now - frozenSince!!) / 1000f
            if (frozenFor >= settings.frozenFrameSec) {
                latchFault(now, "Camera blind. Frames are frozen.", events)
            }
        } else {
            lastSignature = sig
            frozenSince = null
            lastFreshFrameMs = now
            if (faultActive && faultMessage.startsWith("Camera blind")) {
                faultActive = false
                faultRecovered = true
            }
        }
    }

    private fun frozenNow(now: Long): Boolean {
        val since = frozenSince ?: return false
        return lastSignature != null && (now - since) / 1000f >= settings.frozenFrameSec
    }

    /** A blob that was alarming and is still in frame. Same window acknowledge uses. */
    private fun hotAlarm(now: Long): Boolean =
        tracks.any { it.alarmed && now - it.lastSeen < 1_500L }

    private fun hotFlame(now: Long): Boolean =
        flameLatched && now - flameSeen < 1_500L

    /**
     * Silence holds for the rearm delay, then the siren comes back only if the
     * cell is still hot or the camera is still down. A latch left over after
     * the bench cooled does not start the speaker again.
     */
    private fun maybeRearm(now: Long, events: MutableList<WatchEvent>) {
        val acked = ackAt ?: return
        if (!silenced) return
        if (now - acked < (settings.rearmAfterAckSec * 1000f).toLong()) return
        val flameHot = flameLatched && hotFlame(now)
        val alarmHot = alarmLatched && hotAlarm(now)
        val faultHot = faultLatched && faultActive && !processFault
        if (!flameHot && !alarmHot && !faultHot) return
        silenced = false
        val message = when {
            flameHot -> "Still a flame after acknowledge. $flameMessage"
            alarmHot -> "Still in alarm after acknowledge. $alarmMessage"
            else -> "Still in fault after acknowledge. $faultMessage"
        }
        val status = when {
            flameHot -> MonitorStatus.FLAME
            alarmHot -> MonitorStatus.ALARM
            else -> MonitorStatus.FAULT
        }
        events.add(WatchEvent(now, status, message, lastFrame?.copyFrame()))
    }

    private fun decide(now: Long, events: List<WatchEvent>): Decision {
        var status = MonitorStatus.OK
        var headline = "Bench is quiet"
        val warningTrack = tracks.firstOrNull { it.warned && !it.alarmed && it.lastSeen == lastFrame?.timestampMs }
        if (warningTrack != null) {
            status = MonitorStatus.WARNING
            headline = warningMessage.ifBlank {
                "${warningTrack.zoneName}: warm spot at ${warningTrack.body.toInt()}°C"
            }
        }
        if (!everLive && started) {
            status = MonitorStatus.SEARCHING
            headline = healthDetail
        }
        if (flameLatched) {
            status = MonitorStatus.FLAME
            val base = if (silenced) "Silenced — $flameMessage" else flameMessage
            headline = if (plugFault) "$base $faultMessage" else base
        } else if (alarmLatched) {
            status = MonitorStatus.ALARM
            val base = if (silenced) "Silenced — $alarmMessage" else alarmMessage
            headline = if (plugFault) "$base $faultMessage" else base
        } else if (faultLatched) {
            status = MonitorStatus.FAULT
            headline = when {
                silenced -> "Silenced — $faultMessage"
                faultRecovered && !faultActive -> "Camera is back. Acknowledge to clear the fault."
                else -> faultMessage
            }
        }
        if (!started && !alarmLatched && !faultLatched && !flameLatched) {
            status = MonitorStatus.OFF
            headline = "Watch is off"
        }
        hotWorkActive(now)
        val siren = (status == MonitorStatus.FLAME || status == MonitorStatus.ALARM || status == MonitorStatus.FAULT) && !silenced
        val boxes = ArrayList<TrackSnapshot>(tracks.size + 1)
        tracks.mapTo(boxes) { it.snapshot() }
        flameBox()?.let { boxes.add(it) }
        return Decision(
            timeMs = now,
            status = status,
            headline = headline,
            siren = siren,
            silenced = silenced,
            tracks = boxes,
            events = events,
            flameNote = if (started) flameNote else "",
            lastFreshFrameMs = lastFreshFrameMs,
            hotWorkUntilMs = hotWorkUntilMs,
        )
    }

    private fun latchFlame(now: Long, hit: FlameHit, events: MutableList<WatchEvent>) {
        flameSeen = now
        flameAt = hit
        val text = "Open flame, ${hit.maxC.toInt()}°C, ${hit.deltaC.toInt()}° above the bench"
        if (!flameLatched) {
            flameLatched = true
            flameMessage = text
            silenced = false
            events.add(WatchEvent(now, MonitorStatus.FLAME, text, lastFrame?.copyFrame()))
        }
    }

    private fun maybeSelfCheck(now: Long, events: MutableList<WatchEvent>) {
        if (now - lastSelfCheck < 60_000L) return
        lastSelfCheck = now
        if (FlameWatch.selfCheck()) {
            flameOk = true
        } else {
            flameOk = false
            refreshFlameNote()
            latchFault(now, "Flame detector self-check failed", events)
        }
    }

    private fun refreshFlameNote() {
        if (!flameOk) {
            flameNote = "Flame detector self-check failed"
            return
        }
        val ceiling = rangeCeiling?.let { " · ceiling ${it.toInt()}°C" }.orEmpty()
        flameNote = FlameWatch.describe(settings.flameSensitivity, settings.flameWholeFrame) + ceiling
    }

    private fun flameBox(): TrackSnapshot? {
        val hit = flameAt ?: return null
        if (!flameLatched) return null
        val frame = lastFrame ?: return null
        val nx = hit.x.toFloat() / frame.width
        val ny = hit.y.toFloat() / frame.height
        val padX = 3f / frame.width
        val padY = 3f / frame.height
        return TrackSnapshot(
            id = -1,
            left = (nx - padX).coerceIn(0f, 1f),
            top = (ny - padY).coerceIn(0f, 1f),
            right = (nx + padX).coerceIn(0f, 1f),
            bottom = (ny + padY).coerceIn(0f, 1f),
            bodyC = hit.maxC,
            maxC = hit.maxC,
            riseCPerSec = null,
            areaFraction = 4f / (frame.width * frame.height),
            tool = false,
            alarming = true,
            warning = false,
            inGrace = false,
            label = "Flame",
            candidate = true,
        )
    }

    private fun zoneAt(x: Float, y: Float): Zone? {
        val hits = zones.filter { it.contains(x, y) }
        return hits.firstOrNull { it.kind == ZoneKind.IGNORE }
            ?: hits.firstOrNull { it.kind == ZoneKind.CHARGE }
            ?: hits.firstOrNull { it.kind == ZoneKind.SOLDER }
    }

    private fun policyFor(zone: Zone?): Policy {
        val s = settings
        return when (zone?.kind) {
            ZoneKind.CHARGE -> Policy(
                warn = s.chargeWarnC,
                alarm = s.chargeAlarmC,
                runawayTemp = s.chargeRunawayC,
                runawayRate = s.runawayRateCPerSec,
                graceSec = s.chargeGraceSec,
                selfHeatRate = s.selfHeatRateCPerSec,
                selfHeatSustainSec = s.chargeSustainSec,
                warnSustainSec = s.chargeSustainSec,
                alarmHoldSec = s.chargeSustainSec,
                escalateSec = s.chargeEscalateSec,
            )
            ZoneKind.SOLDER -> Policy(
                warn = s.solderWarnC,
                alarm = s.solderAlarmC,
                runawayTemp = s.runawayTempC,
                runawayRate = s.runawayRateCPerSec,
                graceSec = s.solderGraceSec,
                selfHeatRate = s.solderSelfHeatRate,
                selfHeatSustainSec = s.selfHeatSustainSec * 1.3f,
                warnSustainSec = s.warnSustainSec * 1.4f,
                alarmHoldSec = s.alarmSustainSec * 1.3f,
                escalateSec = s.warningEscalateSec * 1.4f,
            )
            else -> Policy(
                warn = s.warnTempC,
                alarm = s.alarmTempC,
                runawayTemp = s.runawayTempC,
                runawayRate = s.runawayRateCPerSec,
                graceSec = s.solderGraceSec,
                selfHeatRate = s.selfHeatRateCPerSec,
                selfHeatSustainSec = s.selfHeatSustainSec,
                warnSustainSec = s.warnSustainSec,
                alarmHoldSec = s.alarmSustainSec,
                escalateSec = s.warningEscalateSec,
            )
        }
    }

    private fun latestToolNear(x: Float, y: Float, now: Long): Long? {
        var best: Long? = null
        for (memory in toolMemory) {
            if (hypot(memory.x - x, memory.y - y) > settings.toolProximity) continue
            if (best == null || memory.t > best) best = memory.t
        }
        return best?.takeIf { now - it <= (settings.memoryKeepSec * 1000f).toLong() }
    }

    private fun pruneMemory(now: Long) {
        val keep = (settings.memoryKeepSec * 1000f).toLong()
        toolMemory.removeAll { now - it.t > keep }
    }

    private fun slope(samples: ArrayDeque<Sample>, now: Long, windowSec: Float): Float? {
        val t0 = now - (windowSec * 1000f).toLong()
        var n = 0
        var sumT = 0.0
        var sumY = 0.0
        var sumTT = 0.0
        var sumTY = 0.0
        var first = Long.MAX_VALUE
        var last = Long.MIN_VALUE
        for (sample in samples) {
            if (sample.t < t0) continue
            val t = (sample.t - now) / 1000.0
            n++
            sumT += t
            sumY += sample.temp
            sumTT += t * t
            sumTY += t * sample.temp
            if (sample.t < first) first = sample.t
            if (sample.t > last) last = sample.t
        }
        val minN = if (windowSec <= 1.2f) 3 else 4
        if (n < minN) return null
        if (last - first < (windowSec * 450f).toLong()) return null
        val denom = n * sumTT - sumT * sumT
        if (kotlin.math.abs(denom) < 1e-6) return 0f
        return ((n * sumTY - sumT * sumY) / denom).toFloat()
    }

    private fun signature(frame: ThermalFrame): Int {
        var hash = 17
        val step = (frame.celsius.size / 48).coerceAtLeast(1)
        var i = 0
        while (i < frame.celsius.size) {
            hash = 31 * hash + frame.celsius[i].toRawBits()
            i += step
        }
        return hash
    }

    private class Track(
        val id: Int,
        blob: Blob,
        now: Long,
    ) {
        var cx = blob.cx
        var cy = blob.cy
        var left = blob.left
        var top = blob.top
        var right = blob.right
        var bottom = blob.bottom
        var area = blob.areaFraction
        var body = blob.bodyC
        var max = blob.maxC
        val born = now
        var lastSeen = now
        val positions = ArrayDeque<Pos>()
        val samples = ArrayDeque<Sample>()
        var risingSince: Long? = null
        var aboveWarnSince: Long? = null
        var aboveAlarmSince: Long? = null
        var runawaySince: Long? = null
        var restingSince: Long? = null
        var warned = false
        var alarmed = false
        var tool = false
        var inGrace = false
        var rise: Float? = null
        var zoneName = "Bench"
        var candidate = false

        init {
            positions.add(Pos(now, cx, cy))
        }

        fun absorb(blob: Blob, now: Long) {
            cx = cx * 0.4f + blob.cx * 0.6f
            cy = cy * 0.4f + blob.cy * 0.6f
            left = blob.left
            top = blob.top
            right = blob.right
            bottom = blob.bottom
            area = blob.areaFraction
            body = blob.bodyC
            max = blob.maxC
            lastSeen = now
            positions.addLast(Pos(now, cx, cy))
            while (positions.isNotEmpty() && now - positions.first().t > 2_000) positions.removeFirst()
        }

        fun speed(now: Long): Float {
            val recent = positions.filter { now - it.t <= 800 }
            if (recent.size < 2) return 0f
            val a = recent.first()
            val b = recent.last()
            val dt = (b.t - a.t) / 1000f
            if (dt < 0.2f) return 0f
            return hypot(b.x - a.x, b.y - a.y) / dt
        }

        fun snapshot() = TrackSnapshot(
            id = id,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            bodyC = body,
            maxC = max,
            riseCPerSec = rise,
            areaFraction = area,
            tool = tool,
            alarming = alarmed,
            warning = warned && !alarmed,
            inGrace = inGrace,
            label = zoneName,
            candidate = candidate,
        )
    }
}

private data class Sample(val t: Long, val temp: Float)
private data class Pos(val t: Long, val x: Float, val y: Float)
private data class Memory(val x: Float, val y: Float, val t: Long)

private data class Policy(
    val warn: Float,
    val alarm: Float,
    val runawayTemp: Float,
    val runawayRate: Float,
    val graceSec: Float,
    val selfHeatRate: Float,
    val selfHeatSustainSec: Float,
    val warnSustainSec: Float,
    val alarmHoldSec: Float,
    val escalateSec: Float,
)

private fun ThermalFrame.copyFrame(): ThermalFrame =
    ThermalFrame(timestampMs, width, height, celsius.copyOf(), label)

private fun formatRate(rate: Float): String {
    val rounded = round(rate * 10f) / 10f
    val sign = if (rounded >= 0f) "+" else ""
    return "$sign$rounded°C/s"
}
