package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.savedstate.SavedState
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme() {
                var splashTerminado by remember { mutableStateOf(false) }

                if (!splashTerminado) {
                    PantallaSplash(onTerminado = { splashTerminado = true })
                } else {
                    PantallaInicio()
                }
            }
        }
    }
}