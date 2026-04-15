package com.lightxin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lightxin.core.auth.SessionManager
import com.lightxin.core.designsystem.theme.LightXinTheme
import com.lightxin.navigation.LightXinNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LightXinTheme {
                LightXinNavHost(sessionManager = sessionManager)
            }
        }
    }
}
