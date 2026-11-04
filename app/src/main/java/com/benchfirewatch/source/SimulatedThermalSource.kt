package com.benchfirewatch.source

import com.benchfirewatch.engine.Frames
import com.benchfirewatch.model.ThermalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SimulatedThermalSource(
    scenarioId: String,
    private var speed: Float,
) : ThermalSource {
    private val _state = MutableStateFlow<SourceState>(SourceState.Idle)
    override val state: StateFlow<SourceState> = _state

    @Volatile private var scenario = scenarioById(scenarioId)
    private var job: Job? = null

    override fun start(scope: CoroutineScope, onFrame: (ThermalFrame) -> Unit, onClock: (Long) -> Unit) {
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            _state.value = SourceState.Live("Demo · ${scenario.title}")
            val wallStart = System.currentTimeMillis()
            var frameIndex = 0
            var disconnected = false
            while (isActive) {
                val speedNow = speed.coerceIn(0.25f, 30f)
                val simMs = ((System.currentTimeMillis() - wallStart) * speedNow).toLong()
                val clock = wallStart + simMs
                val current = scenario
                if (current.disconnectAfterMs != null && simMs >= current.disconnectAfterMs) {
                    if (!disconnected) {
                        disconnected = true
                        _state.value = SourceState.Down("Camera disconnected")
                    }
                    onClock(clock)
                } else {
                    val sample = if (current.loop && current.periodMs > 0) simMs % current.periodMs else simMs
                    val frame = current.frame(sample)
                    onFrame(
                        ThermalFrame(
                            clock,
                            frame.width,
                            frame.height,
                            frame.celsius,
                            current.title,
                            rangeMaxC = frame.rangeMaxC,
                        ),
                    )
                    onClock(clock)
                }
                frameIndex++
                val nextWall = wallStart + (frameIndex * 1000.0 / 8.0 / speedNow).toLong()
                val wait = nextWall - System.currentTimeMillis()
                if (wait > 0) delay(wait)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = SourceState.Idle
    }

    override fun cameraBattery(): CameraBattery = CameraBattery(placeholder = true)

    fun setScenario(id: String, speed: Float) {
        this.scenario = scenarioById(id)
        this.speed = speed
        _state.value = SourceState.Live("Demo · ${scenario.title}")
    }

    private class Script(
        val id: String,
        val title: String,
        val blurb: String,
        val loop: Boolean,
        val periodMs: Long,
        val disconnectAfterMs: Long?,
        val frame: (Long) -> ThermalFrame,
    )

    companion object {
        fun catalog(): List<DemoScenario> = scripts.map { DemoScenario(it.id, it.title, it.blurb) }

        private fun scenarioById(id: String): Script = scripts.firstOrNull { it.id == id } ?: scripts.first()

        private val scripts = listOf(
            Script("quiet", "Quiet Bench", "Ambient bench, nothing hot.", loop = true, periodMs = 20_000, disconnectAfterMs = null, frame = Frames::quiet),
            Script("flame", "Lighter Flame", "A few pixels flicker the way an open flame reads at bench distance.", loop = true, periodMs = 8_000, disconnectAfterMs = null, frame = Frames::lighter),
            Script("solder", "Soldering Pass", "A small very hot tip moves across the bench and leaves.", loop = true, periodMs = 14_000, disconnectAfterMs = null, frame = Frames::soldering),
            Script("spot", "Spot Weld", "One short, tiny, very hot flash.", loop = true, periodMs = 8_000, disconnectAfterMs = null, frame = Frames::spotWeld),
            Script("cool", "Soldered Cell Cooling", "Iron dwells, a cell warms, then the spot cools.", loop = false, periodMs = 0, disconnectAfterMs = null) {
                Frames.solderedCell(it, peakC = 74.0, tauSec = 18.0)
            },
            Script("self", "Self-Heating Cell", "A cell-sized spot warms with no iron nearby.", loop = false, periodMs = 0, disconnectAfterMs = null) {
                Frames.selfHeating(it)
            },
            Script("runaway", "Thermal Runaway", "A cell-sized spot climbs several degrees a second.", loop = false, periodMs = 0, disconnectAfterMs = null) {
                Frames.runaway(it)
            },
            Script("parked", "Iron Left Down", "A very hot tip sits still, outside the holder.", loop = false, periodMs = 0, disconnectAfterMs = null, frame = Frames::parkedIron),
            Script("frozen", "Frozen Frames", "The image stops changing. That is a fault.", loop = false, periodMs = 0, disconnectAfterMs = null, frame = Frames::frozen),
            Script("disconnect", "Camera Unplugged", "Frames stop, as if the USB link dropped.", loop = false, periodMs = 0, disconnectAfterMs = 3_000, frame = Frames::quiet),
        )
    }
}
