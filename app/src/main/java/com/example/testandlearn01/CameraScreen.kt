package com.example.testandlearn01

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
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
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    
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
            isRawSupported = checkRawSupport(context, cam)
        }
    }

    LaunchedEffect(captureMode) {
        sharedPreferences.edit().putString("captureMode", captureMode.name).apply()
        
        previewView?.let { view ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build().also {
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
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx)
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView = view

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build().also {
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
                    when (captureMode) {
                        CaptureMode.JPEG -> {
                            val imgCapture = imageCapture ?: return@Button
                            captureJPEG(imgCapture, context, cameraExecutor, exposureCompensation.roundToInt(), flashMode, onPhotoTaken)
                        }
                        CaptureMode.RAW -> {
                            captureRAW(context, cameraExecutor, exposureCompensation.roundToInt(), flashMode, onPhotoTaken)
                        }
                        CaptureMode.RAW_AND_JPEG -> {
                            val imgCapture = imageCapture ?: return@Button
                            captureRAWAndJPEG(imgCapture, context, cameraExecutor, exposureCompensation.roundToInt(), flashMode, onPhotoTaken)
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

fun checkRawSupport(context: Context, camera: Camera): Boolean {
    return checkRawSupportWithCamera2(context, camera)
}

fun captureJPEG(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.ExecutorService,
    exposureCompensation: Int = 0,
    flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
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

    imageCapture.flashMode = flashMode

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
    context: android.content.Context,
    executor: java.util.concurrent.ExecutorService,
    exposureCompensation: Int = 0,
    flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    onPhotoTaken: () -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())

    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${name}_RAW.dng")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/dng")
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        if (uri == null) {
            Log.e("Camera", "Failed to create MediaStore entry for RAW")
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "RAW拍摄失败: 无法创建文件", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val tempFile = java.io.File.createTempFile("raw_", ".dng", context.cacheDir)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        
        var backCameraId: String? = null
        for (cameraId in cameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                backCameraId = cameraId
                break
            }
        }

        if (backCameraId == null) {
            Log.e("Camera", "No back camera found")
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "RAW拍摄失败: 未找到后置摄像头", Toast.LENGTH_SHORT).show()
            }
            return
        }

        captureRAWWithCamera2(context, cameraManager, backCameraId, tempFile, exposureCompensation, flashMode) { success ->
            if (success) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                    Log.d("Camera", "RAW data copied to MediaStore")
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW已保存", Toast.LENGTH_SHORT).show()
                        onPhotoTaken()
                    }
                } catch (e: Exception) {
                    Log.e("Camera", "Error copying RAW to MediaStore", e)
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                tempFile.delete()
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "RAW拍摄失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
    exposureCompensation: Int = 0,
    flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
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
        put(MediaStore.MediaColumns.MIME_TYPE, "image/dng")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
        }
    }

    val jpegOutputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        jpegContentValues
    ).build()

    val rawUri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        rawContentValues
    )

    if (rawUri == null) {
        Log.e("Camera", "Failed to create MediaStore entry for RAW")
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, "RAW拍摄失败: 无法创建文件", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val rawTempFile = java.io.File.createTempFile("raw_", ".dng", context.cacheDir)

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraIds = cameraManager.cameraIdList
    
    var backCameraId: String? = null
    for (cameraId in cameraIds) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
        if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
            backCameraId = cameraId
            break
        }
    }

    if (backCameraId == null) {
        Log.e("Camera", "No back camera found")
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, "RAW拍摄失败: 未找到后置摄像头", Toast.LENGTH_SHORT).show()
        }
        return
    }

    imageCapture.flashMode = flashMode

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

    captureRAWWithCamera2(context, cameraManager, backCameraId, rawTempFile, exposureCompensation, flashMode) { success ->
        if (success) {
            try {
                context.contentResolver.openOutputStream(rawUri)?.use { output ->
                    rawTempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                rawTempFile.delete()
                rawSaved = true
                if (jpegSaved && rawSaved) {
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, "RAW+JPEG已保存", Toast.LENGTH_SHORT).show()
                        onPhotoTaken()
                    }
                }
            } catch (e: Exception) {
                Log.e("Camera", "Error copying RAW to MediaStore", e)
                rawTempFile.delete()
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "RAW保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            rawTempFile.delete()
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "RAW拍摄失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class Camera2RawCaptureManager(
    private val context: Context,
    private val cameraManager: CameraManager
) {
    private var cameraDevice: CameraDevice? = null
    var captureSession: CameraCaptureSession? = null
    var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var rawSurface: Surface? = null
    private var jpegSurface: Surface? = null

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
        } catch (e: InterruptedException) {
            Log.e("Camera2Raw", "Error stopping background thread", e)
        }
    }

    fun checkRawSupport(cameraId: String): Boolean {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val capabilities = characteristics.get(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            capabilities?.contains(android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
        } catch (e: CameraAccessException) {
            Log.e("Camera2Raw", "Error checking RAW support", e)
            false
        }
    }

    fun openCamera(cameraId: String, surface: Surface, jpegSurface: Surface?, callback: (CameraDevice) -> Unit) {
        try {
            this.rawSurface = surface
            this.jpegSurface = jpegSurface
            
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    callback(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e("Camera2Raw", "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("Camera2Raw", "Error opening camera", e)
        }
    }

    fun createCaptureSession(surfaces: List<Surface>, callback: () -> Unit) {
        try {
            cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    callback()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2Raw", "Capture session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("Camera2Raw", "Error creating capture session", e)
        }
    }

    fun captureRAW(
        rawSurface: Surface,
        onComplete: (TotalCaptureResult) -> Unit
    ) {
        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            requestBuilder?.addTarget(rawSurface)
            requestBuilder?.set(CaptureRequest.JPEG_QUALITY, 100)
            
            captureSession?.capture(
                requestBuilder?.build()!!,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        Log.d("Camera2Raw", "RAW capture completed")
                        onComplete(result)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: android.hardware.camera2.CaptureFailure
                    ) {
                        Log.e("Camera2Raw", "RAW capture failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e("Camera2Raw", "Error capturing RAW", e)
        }
    }

    fun close() {
        captureSession?.close()
        cameraDevice?.close()
        rawSurface?.release()
        jpegSurface?.release()
        stopBackgroundThread()
    }
}

fun captureRAWWithCamera2(
    context: Context,
    cameraManager: CameraManager,
    cameraId: String,
    outputFile: java.io.File,
    exposureCompensation: Int = 0,
    flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
    onComplete: (Boolean) -> Unit
) {
    val manager = Camera2RawCaptureManager(context, cameraManager)
    val handlerThread = HandlerThread("RAWHandler")
    handlerThread.start()
    val handler = Handler(handlerThread.looper)
    
    try {
        manager.startBackgroundThread()
        
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configs = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = configs?.getOutputSizes(ImageFormat.RAW_SENSOR)
        
        if (rawSizes == null || rawSizes.isEmpty()) {
            Log.e("Camera2Raw", "No RAW sizes available")
            onComplete(false)
            handlerThread.quitSafely()
            return
        }
        
        val rawSize = rawSizes[0]
        val rawReader = ImageReader.newInstance(
            rawSize.width,
            rawSize.height,
            ImageFormat.RAW_SENSOR,
            1
        )
        
        var captureResult: TotalCaptureResult? = null
        var rawImage: android.media.Image? = null
        
        rawReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader) {
                rawImage = reader.acquireLatestImage()
                val result = captureResult
                if (rawImage != null && result != null) {
                    try {
                        val dngCreator = DngCreator(characteristics, result)
                        
                        outputFile.outputStream().use { outputStream ->
                            dngCreator.writeImage(outputStream, rawImage!!)
                        }
                        
                        Log.d("Camera2Raw", "DNG file saved to ${outputFile.absolutePath}")
                        onComplete(true)
                    } catch (e: Exception) {
                        Log.e("Camera2Raw", "Error saving DNG file", e)
                        onComplete(false)
                    } finally {
                        rawImage?.close()
                        rawReader.close()
                        manager.close()
                        handlerThread.quitSafely()
                    }
                }
            }
        }, handler)
        
        manager.openCamera(cameraId, rawReader.surface, null) { camera ->
            manager.createCaptureSession(listOf(rawReader.surface)) {
                try {
                    val aeCompRange = characteristics.get(
                        android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
                    )
                    val aeCompStep = characteristics.get(
                        android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP
                    )
                    
                    val isAeCompSupported = aeCompRange != null
                    
                    Log.d("Camera2Exposure", "AE补偿范围: $aeCompRange")
                    Log.d("Camera2Exposure", "AE补偿步长: $aeCompStep")
                    Log.d("Camera2Exposure", "支持AE补偿: $isAeCompSupported")
                    
                    val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(rawReader.surface)
                        
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> {
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                            }
                            ImageCapture.FLASH_MODE_ON -> {
                                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                            }
                            ImageCapture.FLASH_MODE_OFF -> {
                                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                            }
                        }
                        
                        if (isAeCompSupported) {
                            val evValue = exposureCompensation.coerceIn(aeCompRange!!.lower, aeCompRange!!.upper)
                            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evValue)
                            Log.d("Camera2Exposure", "设置曝光补偿: $evValue")
                        }
                        
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                        set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
                        
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    
                    manager.captureSession?.capture(
                        requestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                captureResult = result
                                Log.d("Camera2RAW", "RAW捕获完成，曝光状态: ${result.get(CaptureResult.CONTROL_AE_STATE)}")
                            }

                            override fun onCaptureFailed(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                failure: android.hardware.camera2.CaptureFailure
                            ) {
                                Log.e("Camera2RAW", "RAW捕获失败")
                            }
                        },
                        manager.backgroundHandler
                    )
                } catch (e: Exception) {
                    Log.e("Camera2Exposure", "设置曝光失败", e)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Camera2RAW", "RAW捕获错误", e)
        onComplete(false)
        handlerThread.quitSafely()
    }
}

fun checkRawSupportWithCamera2(
    context: Context,
    camera: Camera
): Boolean {
    return try {
        val cameraInfo = camera.cameraInfo
        val isBackCamera = cameraInfo.cameraSelector?.lensFacing == CameraSelector.LENS_FACING_BACK
        
        if (!isBackCamera) {
            return false
        }
        
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        
        for (cameraId in cameraIds) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            
            if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                val capabilities = characteristics.get(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                val isRawSupported = capabilities?.contains(android.hardware.camera2.CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
                Log.d("Camera2Raw", "Camera $cameraId RAW support: $isRawSupported")
                return isRawSupported
            }
        }
        
        false
    } catch (e: Exception) {
        Log.e("Camera2Raw", "Error checking RAW support", e)
        false
    }
}
