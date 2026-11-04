package com.together.lmsalert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.together.lmsalert.dev.LmsDevScreen
import com.together.lmsalert.ui.theme.LMSalertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LmsAlertApp).container
        setContent {
            LMSalertTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // TODO(작업 B): 실제 앱 화면(홈/캘린더/설정 내비게이션)으로 교체
                    LmsDevScreen(container, Modifier.padding(innerPadding))
                }
            }
        }
    }
}
