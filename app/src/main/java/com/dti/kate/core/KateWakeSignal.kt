package com.dti.kate.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Fires when a background wake gesture (raise/shake) is detected.
 * HomeScreen collects this to automatically start listening, whether the
 * app was already open or just brought to the foreground.
 *
 * Emits a monotonically increasing token rather than Unit, with replay = 1.
 *
 * Why: KateForegroundService calls startActivity(...) then trigger() back
 * to back. startActivity() only *queues* the launch - KateActivity/
 * HomeScreen aren't created and collecting yet by the time trigger() runs,
 * especially on a cold start (process launch + Compose + Vosk init all
 * take real time). With no replay, that emission fired into an empty
 * SharedFlow and was gone by the time HomeScreen subscribed a moment
 * later - the app would open, but the mic never started. replay = 1 means
 * a subscriber that shows up after the emission still receives it.
 *
 * The token (rather than replaying Unit) lets a collector recognize "this
 * is the same wake event I already handled" on an unrelated recomposition
 * (e.g. navigating back to Home) and avoid re-triggering listening for a
 * shake that happened five minutes ago.
 */
object KateWakeSignal {
    private val _events = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<Long> = _events.asSharedFlow()

    private val counter = AtomicLong(0)

    fun trigger() {
        _events.tryEmit(counter.incrementAndGet())
    }
}
