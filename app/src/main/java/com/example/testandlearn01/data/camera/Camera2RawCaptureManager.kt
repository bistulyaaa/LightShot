package com.example.testandlearn01.data.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface

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
            Log.e("Camera2Raw", "停止后台线程失败", e)
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
                    Log.e("Camera2Raw", "相机错误: $error")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("Camera2Raw", "打开相机失败", e)
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
                    Log.e("Camera2Raw", "捕获会话配置失败")
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("Camera2Raw", "创建捕获会话失败", e)
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