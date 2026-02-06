package com.example.testandlearn01.domain.model

enum class CaptureMode {
    JPEG,
    RAW,
    RAW_AND_JPEG
}

enum class FlashMode {
    AUTO,
    ON,
    OFF
}

data class CameraState(
    val captureMode: CaptureMode = CaptureMode.JPEG,
    val flashMode: FlashMode = FlashMode.AUTO,
    val exposureCompensation: Float = 0f,
    val isRawSupported: Boolean = false,
    val isCapturing: Boolean = false,
    val focusPoint: Pair<Float, Float>? = null
)

sealed class CaptureResult {
    data class Success(val uri: android.net.Uri) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}