package com.example.servired.ui.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun createTempMediaUri(context: Context, extension: String = "jpg"): Uri {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "JPEG_${timestamp}_"
    val storageDir = context.cacheDir
    val file = File.createTempFile(filename, ".$extension", storageDir)
    val authority = "${context.packageName}.fileprovider"
    
    return try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: Exception) {
        Uri.fromFile(file)
    }
}
