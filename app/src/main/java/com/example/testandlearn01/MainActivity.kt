package com.example.testandlearn01

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.testandlearn01.presentation.ui.CameraScreen
import com.example.testandlearn01.GalleryScreen
import com.example.testandlearn01.ui.theme.TestAndLearn01Theme
import com.example.testandlearn01.util.VolumeKeyShutter
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("Camera", "Permission granted")
        } else {
            Log.e("Camera", "Permission denied")
            Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            TestAndLearn01Theme {
                CameraApp(cameraExecutor, cameraProviderFuture)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    VolumeKeyShutter.triggerCapture()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun CameraApp(
    cameraExecutor: ExecutorService,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showGallery by remember { mutableStateOf(false) }
    var recentPhotos by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        recentPhotos = loadRecentPhotos(context)
        isLoading = false
    }

    if (showGallery) {
        GalleryScreen(
            photos = recentPhotos,
            isLoading = isLoading,
            onBack = { showGallery = false },
            onPhotoDeleted = {
                refreshTrigger++
            }
        )
    } else {
        CameraScreen(
            cameraExecutor = cameraExecutor,
            cameraProviderFuture = cameraProviderFuture,
            onPhotoTaken = {
                refreshTrigger++
                coroutineScope.launch {
                    recentPhotos = loadRecentPhotos(context)
                }
            },
            onGalleryClick = { showGallery = true },
            recentPhotos = recentPhotos
        )
    }
}
