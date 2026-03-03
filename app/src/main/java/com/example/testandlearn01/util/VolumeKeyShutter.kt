package com.example.testandlearn01.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VolumeKeyShutter {
    private val _trigger = MutableStateFlow(0L)
    val trigger: StateFlow<Long> = _trigger.asStateFlow()

    fun triggerCapture() {
        _trigger.value = System.currentTimeMillis()
    }
}
