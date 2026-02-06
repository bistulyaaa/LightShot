package com.example.testandlearn01.presentation.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.testandlearn01.domain.model.CaptureMode
import com.example.testandlearn01.domain.model.FlashMode
import kotlin.math.roundToInt

@Composable
fun CameraControls(
    exposureCompensation: Float,
    onExposureChange: (Float) -> Unit,
    flashMode: FlashMode,
    onFlashModeChange: (FlashMode) -> Unit,
    captureMode: CaptureMode,
    onCaptureModeChange: (CaptureMode) -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    isCapturing: Boolean = false,
    isRawSupported: Boolean = false,
    recentPhotos: List<Uri> = emptyList()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopControls(
                exposureCompensation = exposureCompensation,
                onExposureChange = onExposureChange,
                flashMode = flashMode,
                onFlashModeChange = onFlashModeChange
            )

            Spacer(modifier = Modifier.weight(1f))

            BottomControls(
                captureMode = captureMode,
                onCaptureModeChange = onCaptureModeChange,
                onCaptureClick = onCaptureClick,
                onGalleryClick = onGalleryClick,
                isCapturing = isCapturing,
                isRawSupported = isRawSupported,
                recentPhotos = recentPhotos
            )
        }
    }
}

@Composable
private fun TopControls(
    exposureCompensation: Float,
    onExposureChange: (Float) -> Unit,
    flashMode: FlashMode,
    onFlashModeChange: (FlashMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposureSlider(
            value = exposureCompensation,
            onValueChange = onExposureChange
        )

        FlashModeButton(
            mode = flashMode,
            onModeChange = onFlashModeChange
        )
    }
}

@Composable
private fun BottomControls(
    captureMode: CaptureMode,
    onCaptureModeChange: (CaptureMode) -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    isCapturing: Boolean,
    isRawSupported: Boolean,
    recentPhotos: List<Uri> = emptyList()
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // 相册缩略图按钮 - 左侧
        GalleryThumbnailButton(
            recentPhotos = recentPhotos,
            onClick = onGalleryClick,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 拍照按钮 - 中间
        CaptureButton(
            onClick = onCaptureClick,
            isCapturing = isCapturing,
            modifier = Modifier.align(Alignment.Center)
        )

        // 拍摄模式选择器 - 右侧
        CaptureModeSelector(
            mode = captureMode,
            onModeChange = onCaptureModeChange,
            isRawSupported = isRawSupported,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ExposureSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.width(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "曝光: ${value.roundToInt()}",
            fontSize = 14.sp,
            color = Color.White
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -6f..6f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FlashModeButton(
    mode: FlashMode,
    onModeChange: (FlashMode) -> Unit
) {
    val nextMode = when (mode) {
        FlashMode.AUTO -> FlashMode.ON
        FlashMode.ON -> FlashMode.OFF
        FlashMode.OFF -> FlashMode.AUTO
    }

    IconButton(
        onClick = { onModeChange(nextMode) },
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Text(
            text = when (mode) {
                FlashMode.AUTO -> "自动"
                FlashMode.ON -> "开启"
                FlashMode.OFF -> "关闭"
            },
            fontSize = 12.sp,
            color = Color.White
        )
    }
}

@Composable
private fun CaptureModeSelector(
    mode: CaptureMode,
    onModeChange: (CaptureMode) -> Unit,
    isRawSupported: Boolean,
    modifier: Modifier = Modifier
) {
    val modes = if (isRawSupported) {
        listOf(CaptureMode.JPEG, CaptureMode.RAW, CaptureMode.RAW_AND_JPEG)
    } else {
        listOf(CaptureMode.JPEG)
    }

    val currentIndex = modes.indexOf(mode)
    val nextMode = modes[(currentIndex + 1) % modes.size]

    Button(
        onClick = { onModeChange(nextMode) },
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White
        )
    ) {
        Text(
            text = when (mode) {
                CaptureMode.JPEG -> "JPEG"
                CaptureMode.RAW -> "RAW"
                CaptureMode.RAW_AND_JPEG -> "RAW+JPEG"
            },
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CaptureButton(
    onClick: () -> Unit,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape),
        enabled = !isCapturing,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    ) {}
}

@Composable
private fun GalleryThumbnailButton(
    recentPhotos: List<Uri>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (recentPhotos.isNotEmpty()) {
            // 显示最近照片的缩略图
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(recentPhotos.first())
                    .crossfade(true)
                    .size(100, 100)
                    .build(),
                contentDescription = "最近照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 没有照片时显示相册图标
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "相册",
                tint = Color.White
            )
        }
    }
}
