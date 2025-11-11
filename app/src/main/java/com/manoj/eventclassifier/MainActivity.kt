package com.manoj.eventclassifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manoj.eventclassifier.ui.EventClassificationApp
import com.manoj.eventclassifier.ui.theme.EventClassifierTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EventClassifierTheme {
                EventClassificationApp()
            }
        }
    }
}
