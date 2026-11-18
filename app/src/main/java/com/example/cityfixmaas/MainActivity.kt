package com.example.cityfixmaas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cityfixmaas.ui.navigation.AppShell
import com.example.cityfixmaas.ui.theme.CityFixMAASTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CityFixMAASTheme {
                AppShell()
            }
        }
    }
}
