package com.dti.kate.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fires when a background wake gesture (raise/shake) is detected.
 * HomeScreen collects this to automatically start listening, whether the
 * app was already open or just brought to the foreground.
 */
object KateWakeSignal {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun trigger() {
        _events.tryEmit(Unit)
    }
}
