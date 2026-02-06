package com.example.testandlearn01.presentation.ui.components

import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture

@Composable
fun CameraPreview(
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onPreviewViewReady: (PreviewView) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = { context ->
            val view = PreviewView(context)
            view.scaleType = PreviewView.ScaleType.FILL_CENTER
            previewView = view

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()

                preview.setSurfaceProvider(view.surfaceProvider)

                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    onPreviewViewReady(view)
                } catch (e: Exception) {
                    android.util.Log.e("CameraPreview", "绑定相机失败", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))

            view
        },
        modifier = Modifier.fillMaxSize()
    )
}