package com.example.testandlearn01.presentation.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testandlearn01.domain.model.CameraState
import com.example.testandlearn01.domain.model.CaptureMode
import com.example.testandlearn01.domain.model.FlashMode
import com.example.testandlearn01.domain.model.CaptureResult
import com.example.testandlearn01.domain.repository.CameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(
    private val cameraRepository: CameraRepository,
    private val context: Context
) : ViewModel() {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("CameraSettings", Context.MODE_PRIVATE)

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _captureResult = MutableStateFlow<CaptureResult?>(null)
    val captureResult: StateFlow<CaptureResult?> = _captureResult.asStateFlow()

    init {
        loadCaptureMode()
    }

    fun initializeCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        viewModelScope.launch {
            try {
                cameraRepository.initializeCamera(
                    provider,
                    lifecycleOwner,
                    previewView
                ) {
                    viewModelScope.launch {
                        checkRawSupport()
                    }
                }
            } catch (e: Exception) {
                _captureResult.value = CaptureResult.Error("相机初始化失败: ${e.message}")
            }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            _cameraState.value = _cameraState.value.copy(isCapturing = true)
            
            cameraRepository.capturePhoto(
                _cameraState.value.captureMode,
                _cameraState.value.flashMode,
                _cameraState.value.exposureCompensation
            ).collect { result ->
                _captureResult.value = result
                _cameraState.value = _cameraState.value.copy(isCapturing = false)
            }
        }
    }

    fun setExposureCompensation(value: Float) {
        viewModelScope.launch {
            cameraRepository.setExposureCompensation(value)
            _cameraState.value = _cameraState.value.copy(exposureCompensation = value)
        }
    }

    fun setFlashMode(mode: FlashMode) {
        viewModelScope.launch {
            cameraRepository.setFlashMode(mode)
            _cameraState.value = _cameraState.value.copy(flashMode = mode)
        }
    }

    fun setCaptureMode(mode: CaptureMode) {
        saveCaptureMode(mode)
        _cameraState.value = _cameraState.value.copy(captureMode = mode)
    }

    fun focusOnPoint(x: Float, y: Float, previewView: PreviewView) {
        android.util.Log.d("CameraViewModel", "Focus on point: x=$x, y=$y")
        _cameraState.value = _cameraState.value.copy(focusPoint = Pair(x, y))
        android.util.Log.d("CameraViewModel", "Focus point set: ${_cameraState.value.focusPoint}")
        cameraRepository.focusOnPoint(x, y, previewView)
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            android.util.Log.d("CameraViewModel", "Clearing focus point")
            _cameraState.value = _cameraState.value.copy(focusPoint = null)
        }
    }

    private suspend fun checkRawSupport() {
        val isSupported = cameraRepository.checkRawSupport()
        _cameraState.value = _cameraState.value.copy(isRawSupported = isSupported)
    }

    private fun loadCaptureMode() {
        val savedMode = sharedPreferences.getString("captureMode", CaptureMode.JPEG.name)
        val mode = try {
            CaptureMode.valueOf(savedMode ?: CaptureMode.JPEG.name)
        } catch (e: Exception) {
            CaptureMode.JPEG
        }
        _cameraState.value = _cameraState.value.copy(captureMode = mode)
    }

    private fun saveCaptureMode(mode: CaptureMode) {
        sharedPreferences.edit().putString("captureMode", mode.name).apply()
    }

    fun clearCaptureResult() {
        _captureResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cameraRepository.cleanup()
    }
}