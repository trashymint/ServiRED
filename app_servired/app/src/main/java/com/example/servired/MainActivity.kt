package com.example.servired

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.servired.data.OfflineMapManager
import com.example.servired.ui.navigation.NavGraph
import com.example.servired.ui.theme.ServiREDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OfflineMapManager.initOfflineMaps(applicationContext)

        enableEdgeToEdge()
        setContent {
            ServiREDTheme {
                NavGraph()
            }
        }
    }
}
