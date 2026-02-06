package com.example.testandlearn01.domain.repository

import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.testandlearn01.domain.model.CaptureMode
import com.example.testandlearn01.domain.model.FlashMode
import com.example.testandlearn01.domain.model.CaptureResult
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    suspend fun initializeCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit
    ): Result<Unit>

    suspend fun capturePhoto(
        captureMode: CaptureMode,
        flashMode: FlashMode,
        exposureCompensation: Float
    ): Flow<CaptureResult>

    suspend fun setExposureCompensation(value: Float): Result<Unit>

    suspend fun setFlashMode(mode: FlashMode): Result<Unit>

    suspend fun checkRawSupport(): Boolean

    fun focusOnPoint(x: Float, y: Float, previewView: PreviewView)

    fun cleanup()
}