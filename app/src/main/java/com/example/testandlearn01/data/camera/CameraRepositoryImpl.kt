package com.example.testandlearn01.data.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult as Camera2CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.testandlearn01.domain.model.CaptureMode
import com.example.testandlearn01.domain.model.FlashMode
import com.example.testandlearn01.domain.model.CaptureResult
import com.example.testandlearn01.domain.repository.CameraRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

class CameraRepositoryImpl(
    private val context: Context
) : CameraRepository {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraManager: CameraManager? = null

    override suspend fun initializeCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit
    ): Result<Unit> {
        return try {
            cameraProvider = provider
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                onCameraReady()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("CameraRepository", "Use case binding failed", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Camera initialization failed", e)
            Result.failure(e)
        }
    }

    override suspend fun checkRawSupport(): Boolean {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = manager.cameraIdList
            var isRawSupported = false

            for (cameraId in cameraIds) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                    val configs = characteristics.get(
                        android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                    )
                    val rawSizes = configs?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    isRawSupported = rawSizes != null && rawSizes.isNotEmpty()
                    break
                }
            }
            isRawSupported
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error checking RAW support", e)
            false
        }
    }

    override suspend fun capturePhoto(
        captureMode: CaptureMode,
        flashMode: FlashMode,
        exposureCompensation: Float
    ): Flow<CaptureResult> = callbackFlow {
        val imgCapture = imageCapture ?: run {
            trySend(CaptureResult.Error("相机未初始化"))
            close()
            return@callbackFlow
        }

        when (captureMode) {
            CaptureMode.JPEG -> {
                captureJPEG(imgCapture, flashMode, exposureCompensation) { result ->
                    trySend(result)
                    close()
                }
            }
            CaptureMode.RAW -> {
                captureRAW(flashMode, exposureCompensation) { result ->
                    trySend(result)
                    close()
                }
            }
            CaptureMode.RAW_AND_JPEG -> {
                captureRAWAndJPEG(flashMode, exposureCompensation) { result ->
                    trySend(result)
                    close()
                }
            }
        }

        awaitClose {}
    }

    override suspend fun setFlashMode(mode: FlashMode): Result<Unit> {
        return try {
            imageCapture?.flashMode = when (mode) {
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setExposureCompensation(value: Float): Result<Unit> {
        // 曝光补偿在拍照时通过Camera2 API设置
        return Result.success(Unit)
    }

    override fun focusOnPoint(x: Float, y: Float, previewView: PreviewView) {
        val cameraControl = camera?.cameraControl ?: return
        val cameraInfo = camera?.cameraInfo ?: return

        // 创建对焦点
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = androidx.camera.core.FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        cameraControl.startFocusAndMetering(action)
    }

    override fun cleanup() {
        cameraProvider?.unbindAll()
    }

    private fun captureJPEG(
        imageCapture: ImageCapture,
        flashMode: FlashMode,
        exposureCompensation: Float,
        onComplete: (CaptureResult) -> Unit
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val displayName = "JPEG_${timestamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.flashMode = when (flashMode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }

        imageCapture.takePicture(
            outputOptions,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
                    onComplete(CaptureResult.Error(exc.message ?: "拍摄失败"))
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    Log.i("Camera", "Photo capture succeeded: $savedUri")
                    onComplete(
                        CaptureResult.Success(
                            savedUri ?: Uri.EMPTY
                        )
                    )
                }
            }
        )
    }

    private fun captureRAW(
        flashMode: FlashMode,
        exposureCompensation: Float,
        onComplete: (CaptureResult) -> Unit
    ) {
        val manager = Camera2RawCaptureManager(context, cameraManager ?: run {
            onComplete(CaptureResult.Error("CameraManager未初始化"))
            return
        })
        val handlerThread = HandlerThread("RAWHandler")
        var rawReader: ImageReader? = null

        try {
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            manager.startBackgroundThread()

            val cameraIds = cameraManager!!.cameraIdList
            var backCameraId: String? = null

            for (cameraId in cameraIds) {
                val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    break
                }
            }

            if (backCameraId == null) {
                onComplete(CaptureResult.Error("未找到后置摄像头"))
                return
            }

            val characteristics = cameraManager!!.getCameraCharacteristics(backCameraId)
            val configs = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            val rawSizes = configs?.getOutputSizes(ImageFormat.RAW_SENSOR)

            if (rawSizes == null || rawSizes.isEmpty()) {
                onComplete(CaptureResult.Error("不支持RAW格式"))
                return
            }

            val rawSize = rawSizes[0]
            rawReader = ImageReader.newInstance(
                rawSize.width,
                rawSize.height,
                ImageFormat.RAW_SENSOR,
                1
            )

            val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
            val rawFileName = "RAW_${timestamp}.dng"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, rawFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
                }
            }

            var captureResult: TotalCaptureResult? = null
            var rawImage: android.media.Image? = null

            rawReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(reader: ImageReader) {
                    rawImage = reader.acquireLatestImage()
                    val result = captureResult
                    if (rawImage != null && result != null) {
                        try {
                            val dngCreator = DngCreator(characteristics, result)
                            val uri = context.contentResolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )
                            if (uri != null) {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    dngCreator.writeImage(outputStream, rawImage!!)
                                }
                                Log.i("Camera", "RAW photo saved: $uri")
                                onComplete(CaptureResult.Success(uri))
                            } else {
                                onComplete(CaptureResult.Error("无法创建文件"))
                            }
                        } catch (e: Exception) {
                            Log.e("Camera", "Error saving RAW: ${e.message}", e)
                            onComplete(CaptureResult.Error(e.message ?: "RAW保存失败"))
                        } finally {
                            rawImage?.close()
                            rawReader?.close()
                            manager.close()
                            handlerThread.quitSafely()
                        }
                    }
                }
            }, handler)

            manager.openCamera(backCameraId, rawReader.surface, null) { camera ->
                manager.createCaptureSession(listOf(rawReader.surface)) {
                    try {
                        val aeCompRange = characteristics.get(
                            android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
                        )
                        val isAeCompSupported = aeCompRange != null

                        val requestBuilder = camera.createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE
                        ).apply {
                            addTarget(rawReader.surface)

                            // 设置测光模式为矩阵测光，确保曝光准确
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AE_LOCK, false)
                            set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

                            // 设置测光模式
                            set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)

                            val flash = when (flashMode) {
                                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                            }
                            when (flash) {
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

                            // 曝光补偿
                            if (isAeCompSupported) {
                                val evValue = exposureCompensation.roundToInt()
                                    .coerceIn(aeCompRange!!.lower, aeCompRange!!.upper)
                                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evValue)
                            }

                            // 降噪和边缘处理
                            set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)

                            // 对焦模式
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

                            // 设置RAW传感器灵敏度（ISO）为自动
                            set(CaptureRequest.SENSOR_SENSITIVITY, null)
                            set(CaptureRequest.SENSOR_EXPOSURE_TIME, null)
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
                                    Log.d("CameraRepository", "RAW capture completed")
                                }

                                override fun onCaptureFailed(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    failure: android.hardware.camera2.CaptureFailure
                                ) {
                                    Log.e("CameraRepository", "RAW capture failed")
                                    onComplete(CaptureResult.Error("RAW capture failed"))
                                }
                            },
                            manager.backgroundHandler
                        )
                    } catch (e: Exception) {
                        Log.e("CameraRepository", "Error setting exposure: ${e.message}", e)
                        onComplete(CaptureResult.Error(e.message ?: "设置曝光失败"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "RAW capture error: ${e.message}", e)
            onComplete(CaptureResult.Error(e.message ?: "RAW capture error"))
        } finally {
            if (rawReader == null) {
                manager.close()
                handlerThread.quitSafely()
            }
        }
    }

    private suspend fun captureRAWAndJPEG(
        flashMode: FlashMode,
        exposureCompensation: Float,
        onComplete: (CaptureResult) -> Unit
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val jpegName = "${timestamp}_JPEG"
        val rawName = "${timestamp}_RAW"

        // 先拍摄JPEG到临时文件
        val imgCapture = imageCapture ?: run {
            onComplete(CaptureResult.Error("相机未初始化"))
            return
        }

        val jpegTempFile = File(context.cacheDir, "${jpegName}.jpg")
        val jpegOutputOptions = ImageCapture.OutputFileOptions.Builder(jpegTempFile).build()

        imgCapture.flashMode = when (flashMode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }

        // 使用suspendCancellableCoroutine等待JPEG拍摄完成
        val jpegResult = suspendCancellableCoroutine<Result<File>> { continuation ->
            imgCapture.takePicture(
                jpegOutputOptions,
                androidx.core.content.ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e("Camera", "JPEG拍摄失败: ${exc.message}", exc)
                        jpegTempFile.delete()
                        continuation.resume(Result.failure(exc))
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Log.i("Camera", "JPEG临时文件已保存")
                        continuation.resume(Result.success(jpegTempFile))
                    }
                }
            )
        }

        if (jpegResult.isFailure) {
            onComplete(CaptureResult.Error("JPEG拍摄失败: ${jpegResult.exceptionOrNull()?.message}"))
            return
        }

        // 然后拍摄RAW
        val manager = Camera2RawCaptureManager(context, cameraManager ?: run {
            jpegTempFile.delete()
            onComplete(CaptureResult.Error("CameraManager未初始化"))
            return
        })
        val handlerThread = HandlerThread("RAWHandler")
        var rawReader: ImageReader? = null

        try {
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            manager.startBackgroundThread()

            val cameraIds = cameraManager!!.cameraIdList
            var backCameraId: String? = null

            for (cameraId in cameraIds) {
                val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK) {
                    backCameraId = cameraId
                    break
                }
            }

            if (backCameraId == null) {
                jpegTempFile.delete()
                onComplete(CaptureResult.Error("未找到后置摄像头"))
                return
            }

            val characteristics = cameraManager!!.getCameraCharacteristics(backCameraId)
            val configs = characteristics.get(
                android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            val rawSizes = configs?.getOutputSizes(ImageFormat.RAW_SENSOR)

            if (rawSizes == null || rawSizes.isEmpty()) {
                jpegTempFile.delete()
                onComplete(CaptureResult.Error("不支持RAW格式"))
                return
            }

            val rawSize = rawSizes[0]
            rawReader = ImageReader.newInstance(
                rawSize.width,
                rawSize.height,
                ImageFormat.RAW_SENSOR,
                1
            )

            val rawTempFile = File.createTempFile("raw_", ".dng", context.cacheDir)

            var captureResult: TotalCaptureResult? = null
            var rawImage: android.media.Image? = null

            // 使用suspendCancellableCoroutine等待RAW拍摄完成
            val rawResult = suspendCancellableCoroutine<Result<File>> { continuation ->
                rawReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
                    override fun onImageAvailable(reader: ImageReader) {
                        rawImage = reader.acquireLatestImage()
                        val result = captureResult
                        if (rawImage != null && result != null) {
                            try {
                                val dngCreator = DngCreator(characteristics, result)
                                rawTempFile.outputStream().use { outputStream ->
                                    dngCreator.writeImage(outputStream, rawImage!!)
                                }
                                Log.d("CameraRepository", "DNG临时文件已保存: ${rawTempFile.absolutePath}")
                                continuation.resume(Result.success(rawTempFile))
                            } catch (e: Exception) {
                                Log.e("CameraRepository", "保存DNG文件失败", e)
                                continuation.resume(Result.failure(e))
                            } finally {
                                rawImage?.close()
                                rawReader?.close()
                                manager.close()
                                handlerThread.quitSafely()
                            }
                        }
                    }
                }, handler)

                manager.openCamera(backCameraId, rawReader.surface, null) { camera ->
                    manager.createCaptureSession(listOf(rawReader.surface)) {
                        try {
                            val aeCompRange = characteristics.get(
                                android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
                            )
                            val isAeCompSupported = aeCompRange != null

                            val requestBuilder = camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(rawReader.surface)

                                // 设置测光模式为矩阵测光，确保曝光准确
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.CONTROL_AE_LOCK, false)
                                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

                                // 设置测光模式
                                set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)

                                val flash = when (flashMode) {
                                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                                }
                                when (flash) {
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

                                // 曝光补偿
                                if (isAeCompSupported) {
                                    val evValue = exposureCompensation.roundToInt()
                                        .coerceIn(aeCompRange!!.lower, aeCompRange!!.upper)
                                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evValue)
                                }

                                // 降噪和边缘处理
                                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)

                                // 对焦模式
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

                                // 设置RAW传感器灵敏度（ISO）为自动
                                set(CaptureRequest.SENSOR_SENSITIVITY, null)
                                set(CaptureRequest.SENSOR_EXPOSURE_TIME, null)
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
                                        Log.d("CameraRepository", "RAW捕获完成")
                                    }

                                    override fun onCaptureFailed(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        failure: android.hardware.camera2.CaptureFailure
                                    ) {
                                        Log.e("CameraRepository", "RAW捕获失败")
                                        continuation.resume(Result.failure(Exception("RAW捕获失败")))
                                    }
                                },
                                manager.backgroundHandler
                            )
                        } catch (e: Exception) {
                            Log.e("CameraRepository", "设置曝光失败", e)
                            continuation.resume(Result.failure(e))
                        }
                    }
                }
            }

            if (rawResult.isFailure) {
                jpegTempFile.delete()
                rawTempFile.delete()
                onComplete(CaptureResult.Error("RAW拍摄失败: ${rawResult.exceptionOrNull()?.message}"))
                return
            }

            // 两个都拍摄成功，保存到MediaStore
            // 保存JPEG
            val jpegContentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, jpegName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
                }
            }

            val jpegUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                jpegContentValues
            )

            if (jpegUri != null) {
                context.contentResolver.openOutputStream(jpegUri)?.use { outputStream ->
                    jpegTempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i("Camera", "JPEG已保存到MediaStore: $jpegUri")
            }
            jpegTempFile.delete()

            // 保存RAW
            val rawContentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${rawName}.dng")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
                }
            }

            val rawUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                rawContentValues
            )

            if (rawUri != null) {
                context.contentResolver.openOutputStream(rawUri)?.use { outputStream ->
                    rawTempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i("Camera", "RAW已保存到MediaStore: $rawUri")
            }
            rawTempFile.delete()

            onComplete(CaptureResult.Success(jpegUri ?: Uri.EMPTY))
        } catch (e: Exception) {
            Log.e("CameraRepository", "RAW+JPEG捕获错误", e)
            jpegTempFile.delete()
            onComplete(CaptureResult.Error(e.message ?: "RAW+JPEG捕获错误"))
        } finally {
            rawReader?.close()
            manager.close()
            handlerThread.quitSafely()
        }
    }
}
