package com.example.cityfixmaas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.cityfixmaas.ui.navigation.AppShell
import com.example.cityfixmaas.ui.theme.CityFixMAASTheme

class MainActivity : ComponentActivity() {

    /** Set when the "possible pothole" notification opened the app: AppShell then opens the report form. */
    private var roadDefectRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        roadDefectRequested = intent.getBooleanExtra(EXTRA_ROAD_DEFECT, false)
        setContent {
            CityFixMAASTheme {
                AppShell(roadDefectRequested = roadDefectRequested, onRoadDefectOpened = { roadDefectRequested = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        roadDefectRequested = intent.getBooleanExtra(EXTRA_ROAD_DEFECT, false)
    }

    companion object {
        const val EXTRA_ROAD_DEFECT = "roadDefect"
    }
}
