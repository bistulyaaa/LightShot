package com.example.testandlearn01.presentation.ui

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.testandlearn01.domain.model.CaptureResult
import com.example.testandlearn01.domain.repository.CameraRepository
import com.example.testandlearn01.presentation.ui.components.CameraControls
import com.example.testandlearn01.presentation.viewmodel.CameraViewModel
import com.example.testandlearn01.util.VolumeKeyShutter
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(
    cameraExecutor: java.util.concurrent.ExecutorService,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onPhotoTaken: () -> Unit,
    onGalleryClick: () -> Unit,
    recentPhotos: List<Uri>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // 使用remember确保只创建一次
    val cameraRepository = remember(context) {
        com.example.testandlearn01.data.camera.CameraRepositoryImpl(context)
    }
    val viewModel = remember(cameraRepository, context) {
        CameraViewModel(cameraRepository, context)
    }

    val cameraState by viewModel.cameraState.collectAsState()
    val captureResult by viewModel.captureResult.collectAsState()
    val volumeTrigger by VolumeKeyShutter.trigger.collectAsState()

    LaunchedEffect(volumeTrigger) {
        if (volumeTrigger > 0 && !cameraState.isCapturing) {
            viewModel.capturePhoto()
        }
    }

    // 使用remember确保PreviewView只创建一次
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    
    var isCameraInitialized by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 初始化相机 - 只在previewView准备好后执行一次
    LaunchedEffect(Unit) {
        if (!isCameraInitialized) {
            try {
                viewModel.initializeCamera(
                    cameraProviderFuture.get(),
                    lifecycleOwner,
                    previewView
                )
                isCameraInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("CameraScreen", "相机初始化失败", e)
            }
        }
    }

    LaunchedEffect(captureResult) {
        captureResult?.let { result ->
            when (result) {
                is CaptureResult.Success -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("照片已保存")
                        onPhotoTaken()
                        viewModel.clearCaptureResult()
                    }
                }
                is CaptureResult.Error -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("拍照失败: ${result.message}")
                        viewModel.clearCaptureResult()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 点击对焦覆盖层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val x = offset.x / previewView.width.toFloat()
                        val y = offset.y / previewView.height.toFloat()
                        android.util.Log.d("CameraScreen", "Tap detected: x=$x, y=$y, previewSize=${previewView.width}x${previewView.height}")
                        viewModel.focusOnPoint(x, y, previewView)
                    }
                }
        )

        CameraControls(
            exposureCompensation = cameraState.exposureCompensation,
            onExposureChange = viewModel::setExposureCompensation,
            flashMode = cameraState.flashMode,
            onFlashModeChange = viewModel::setFlashMode,
            captureMode = cameraState.captureMode,
            onCaptureModeChange = viewModel::setCaptureMode,
            onCaptureClick = viewModel::capturePhoto,
            onGalleryClick = onGalleryClick,
            isCapturing = cameraState.isCapturing,
            isRawSupported = cameraState.isRawSupported,
            recentPhotos = recentPhotos
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White
            )
        }

        // 对焦框 - 放在最上层确保不被遮挡
        android.util.Log.d("CameraScreen", "Focus point in state: ${cameraState.focusPoint}")
        cameraState.focusPoint?.let { (x, y) ->
            android.util.Log.d("CameraScreen", "Rendering focus box at: x=$x, y=$y")
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (x * previewView.width - 50).dp,
                            y = (y * previewView.height - 50).dp
                        )
                        .size(100.dp)
                        .border(
                            width = 3.dp,
                            color = Color.Yellow
                        )
                        .background(Color.Yellow.copy(alpha = 0.1f))
                )
            }
        }
    }
}
