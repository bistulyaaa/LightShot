package com.example.testandlearn01

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadRecentPhotos(context: android.content.Context): List<Uri> = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATA
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val photos = mutableListOf<Uri>()

    try {
        // 尝试使用RELATIVE_PATH过滤（Android 10+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projectionWithPath = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.RELATIVE_PATH
            )

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projectionWithPath,
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("%Pictures/CameraApp%"),
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < 20) {
                    val id = it.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    photos.add(uri)
                    count++
                }
            }
        }

        // 如果没有找到照片或者Android版本低于10，使用DATA列过滤
        if (photos.isEmpty()) {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATA} LIKE ?",
                arrayOf("%Pictures/CameraApp%"),
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < 20) {
                    val id = it.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    photos.add(uri)
                    count++
                }
            }
        }

        // 如果还是没有找到，获取最近的20张照片
        if (photos.isEmpty()) {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < 20) {
                    val id = it.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    photos.add(uri)
                    count++
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    photos
}
