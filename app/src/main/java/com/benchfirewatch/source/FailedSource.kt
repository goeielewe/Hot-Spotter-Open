package com.benchfirewatch.source

import com.benchfirewatch.model.ThermalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps the watch honest when the camera was requested but cannot run. */
class FailedSource(val reason: String) : ThermalSource {
    private val _state = MutableStateFlow<SourceState>(SourceState.Down(reason))
    override val state: StateFlow<SourceState> = _state
    private var job: Job? = null

    override fun start(scope: CoroutineScope, onFrame: (ThermalFrame) -> Unit, onClock: (Long) -> Unit) {
        job?.cancel()
        _state.value = SourceState.Down(reason)
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                onClock(System.currentTimeMillis())
                delay(500)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        _state.value = SourceState.Idle
    }
}
