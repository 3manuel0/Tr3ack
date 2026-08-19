package com.example.tr3ack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.tr3ack.ui.navigation.Tr3ackNavGraph
import com.example.tr3ack.ui.theme.Tr3ackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as Tr3ackApplication

        setContent {
            Tr3ackTheme {
                Tr3ackNavGraph(repository = app.repository)
            }
        }
    }
}
