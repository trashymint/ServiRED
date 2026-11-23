package com.example.servired

import android.app.Application
import org.osmdroid.config.Configuration

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        // User-Agent de navegador móvil compatible para prevenir bloqueos de servidores HTTP de mapas
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
