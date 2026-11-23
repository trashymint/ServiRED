package com.example.servired.data

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream

object OfflineMapManager {

    fun initOfflineMaps(context: Context) {
        val basePath = File(context.getExternalFilesDir(null), "osmdroid")
        if (!basePath.exists()) {
            basePath.mkdirs()
        }

        val tileCache = File(basePath, "tiles")
        if (!tileCache.exists()) {
            tileCache.mkdirs()
        }

        Configuration.getInstance().osmdroidBasePath = basePath
        Configuration.getInstance().osmdroidTileCache = tileCache

        val archiveName = "colombia-261024-free.gpkg.zip"
        val targetFile = File(basePath, archiveName)

        if (!targetFile.exists()) {
            try {
                context.assets.open(archiveName).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                // Si no se encuentra en assets, osmdroid buscará en la carpeta osmdroidBasePath
            }
        }
    }
}
