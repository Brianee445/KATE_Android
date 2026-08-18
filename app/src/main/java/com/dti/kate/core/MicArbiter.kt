package com.dti.kate.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates ownership of the microphone between:
 *  - KateForegroundService's always-on wake-word AudioRecord (low duty,
 *    runs continuously in the background)
 *  - HomeScreen's AudioCapture, used for an active command-listening
 *    session (starts on tap/gesture/wake-word-trigger, runs for a few
 *    seconds)
 *
 * Two simultaneous AudioRecord instances against the same input on one
 * device is unreliable across OEMs (silent failures, garbled audio, or
 * outright STATE_UNINITIALIZED on the second one) - so KateForegroundService
 * must fully release its AudioRecord before HomeScreen opens its own, and
 * only reacquire it after HomeScreen is done. HomeScreen sets
 * [appIsCapturing] around its listening session; KateForegroundService
 * collects it to pause/resume.
 */
object MicArbiter {
    private val _appIsCapturing = MutableStateFlow(false)
    val appIsCapturing: StateFlow<Boolean> = _appIsCapturing

    fun setCapturing(capturing: Boolean) {
        _appIsCapturing.value = capturing
    }
}
