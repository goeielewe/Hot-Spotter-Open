package com.benchfirewatch.source

import com.benchfirewatch.model.PaletteChoice
import com.benchfirewatch.model.ThermalFrame
import com.benchfirewatch.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

sealed interface SourceState {
    data object Idle : SourceState
    data class Searching(val detail: String) : SourceState
    data class Live(val detail: String) : SourceState
    data class Down(val detail: String) : SourceState
}

/**
 * Radiometric frames. The USB camera and the demo both implement this.
 * Detection stays on the Celsius values.
 */
interface ThermalSource {
    val state: StateFlow<SourceState>
    fun start(scope: CoroutineScope, onFrame: (ThermalFrame) -> Unit, onClock: (Long) -> Unit)
    fun stop()
    fun setEmissivity(value: Float) {}

    /** Display only. The next frame should use this view. Detection is unchanged. */
    fun setDisplay(viewMode: ViewMode, palette: PaletteChoice) {}

    /** Latest camera battery. Default is "not available", never a guessed percent. */
    fun cameraBattery(): CameraBattery = CameraBattery()
}

data class DemoScenario(
    val id: String,
    val title: String,
    val blurb: String,
)
