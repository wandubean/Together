package com.together.lmsalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.together.lmsalert.ui.AppScreen
import com.together.lmsalert.ui.theme.LMSalertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LmsAlertApp).container
        setContent {
            LMSalertTheme {
                AppScreen(container)
            }
        }
    }
}
