package com.example.testandlearn01

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
 import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CameraScreen(
    cameraExecutor: java.util.concurrent.ExecutorService,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    onPhotoTaken: () -> Unit,
    onGalleryClick: () -> Unit,
    recentPhotos: List<android.net.Uri>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPreferences = context.getSharedPreferences("CameraSettings", Context.MODE_PRIVATE)

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var exposureCompensation by remember { mutableFloatStateOf(0f) }
    var captureMode by remember { mutableStateOf(CaptureMode.JPEG) }
    var isRawSupported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedMode = sharedPreferences.getString("captureMode", CaptureMode.JPEG.name)
        captureMode = try {
            CaptureMode.valueOf(savedMode ?: CaptureMode.JPEG.name)
        } catch (e: Exception) {
            CaptureMode.JPEG
        }
    }

    LaunchedEffect(camera) {
        camera?.let { cam ->
            isRawSupported = checkRawSupport(cam)
        }
    }

    LaunchedEffect(captureMode) {
        sharedPreferences.edit().putString("captureMode", captureMode.name).apply()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        camera?.cameraControl?.setExposureCompensationIndex(exposureCompensation.roundToInt())
                    } catch (e: Exception) {
                        Log.e("Camera", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                view
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "EV: ${exposureCompensation.roundToInt()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(8.dp, 4.dp)
                    )
                    if (isRawSupported) {
                        IconButton(
                            onClick = {
                                captureMode = when (captureMode) {
                                    CaptureMode.JPEG -> CaptureMode.RAW
                                    CaptureMode.RAW -> CaptureMode.RAW_AND_JPEG
                                    CaptureMode.RAW_AND_JPEG -> CaptureMode.JPEG
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text(
                                text = when (captureMode) {
                                    CaptureMode.JPEG -> "JPEG"
                                    CaptureMode.RAW -> "RAW"
                                    CaptureMode.RAW_AND_JPEG -> "RAW+"
                                },
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                            imageCapture?.flashMode = flashMode
                        }
                    ) {
                        Text(
                            text = "⚡",
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                    Text(
                        text = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> "自动"
                            ImageCapture.FLASH_MODE_ON -> "开"
                            else -> "关"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(4.dp, 2.dp)
                    )
                }
            }

            Slider(
                value = exposureCompensation,
                onValueChange = { value ->
                    exposureCompensation = value
                    camera?.cameraControl?.setExposureCompensationIndex(value.roundToInt())
                },
                valueRange = -5f..5f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
                    .clickable { onGalleryClick() },
                contentAlignment = Alignment.Center
            ) {
                if (recentPhotos.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(recentPhotos.first())
                            .crossfade(true)
                            .size(200, 200)
                            .build(),
                        contentDescription = "最近照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Button(
                onClick = {
                    val imgCapture = imageCapture ?: return@Button
                    
                    when (captureMode) {
                        CaptureMode.JPEG -> {
                            captureJPEG(imgCapture, context, cameraExecutor, onPhotoTaken)
                        }
                        CaptureMode.RAW -> {
                            captureRAW(imgCapture, context, cameraExecutor, onPhotoTaken)
                        }
                        CaptureMode.RAW_AND_JPEG -> {
                            captureRAWAndJPEG(imgCapture, context, cameraExecutor, onPhotoTaken)
                        }
                    }
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
            }
            
            Spacer(modifier = Modifier.size(60.dp))
        }
    }
}

enum class CaptureMode {
    JPEG,
    RAW,
    RAW_AND_JPEG
}

fun checkRawSupport(camera: Camera): Boolean {
    return try {
        val cameraInfo = camera.cameraInfo
        val isBackCamera = cameraInfo.cameraSelector?.lensFacing == CameraSelector.LENS_FACING_BACK
        
        if (!isBackCamera) {
            return false
        }
        
        true
    } catch (e: Exception) {
        Log.e("Camera", "Error checking RAW support, assuming supported", e)
        true
    }
}

fun captureJPEG(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.ExecutorService,
    onPhotoTaken: () -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri
                Log.i("Camera", "JPEG saved to: $savedUri")
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "JPEG已保存", Toast.LENGTH_SHORT).show()
                    onPhotoTaken()
                }
            }
        }
    )
}

fun captureRAW(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.ExecutorService,
    onPhotoTaken: () -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_RAW.dng")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-adobe-dng")
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        Log.d("Camera", "Attempting RAW capture with ImageCapture")
        Log.d("Camera", "ImageCapture flash mode: ${imageCapture.flashMode}")
        
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "RAW capture failed: ${exc.message}", exc)
                    Log.e("Camera", "RAW capture error code: ${exc.imageCaptureError}")
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW拍摄失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    Log.i("Camera", "RAW saved to: $savedUri")
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW已保存", Toast.LENGTH_SHORT).show()
                        onPhotoTaken()
                    }
                }
            }
        )
    } catch (e: Exception) {
        Log.e("Camera", "RAW capture error: ${e.message}", e)
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, "RAW拍摄失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun captureRAWAndJPEG(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.ExecutorService,
    onPhotoTaken: () -> Unit
) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    var jpegSaved = false
    var rawSaved = false

    val jpegName = "${timestamp}_JPEG"
    val rawName = "${timestamp}_RAW"

    val jpegContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, jpegName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
        }
    }

    val rawContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${rawName}.dng")
        put(MediaStore.MediaColumns.MIME_TYPE, "application/x-adobe-dng")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
        }
    }

    val jpegOutputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        jpegContentValues
    ).build()

    val rawOutputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        rawContentValues
    ).build()

    imageCapture.takePicture(
        jpegOutputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "JPEG capture failed: ${exc.message}", exc)
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "JPEG拍摄失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri
                Log.i("Camera", "JPEG saved to: $savedUri")
                jpegSaved = true
                if (jpegSaved && rawSaved) {
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW+JPEG已保存", Toast.LENGTH_SHORT).show()
                        onPhotoTaken()
                    }
                }
            }
        }
    )

    imageCapture.takePicture(
        rawOutputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "RAW capture failed: ${exc.message}", exc)
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "RAW拍摄失败", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri
                Log.i("Camera", "RAW saved to: $savedUri")
                rawSaved = true
                if (jpegSaved && rawSaved) {
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW+JPEG已保存", Toast.LENGTH_SHORT).show()
                        onPhotoTaken()
                    }
                }
            }
        }
    )
}
