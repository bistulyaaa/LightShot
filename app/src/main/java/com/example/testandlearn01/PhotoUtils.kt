package com.example.testandlearn01

import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun loadRecentPhotos(context: android.content.Context): List<Uri> = withContext(Dispatchers.IO) {
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
        arrayOf("%Pictures/CameraApp%"),
        sortOrder
    )

    val photos = mutableListOf<Uri>()
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
    photos
}
