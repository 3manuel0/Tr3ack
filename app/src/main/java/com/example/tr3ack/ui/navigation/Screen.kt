package com.example.tr3ack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    data object LogWorkout : Screen("log_workout", "Log", Icons.Default.FitnessCenter)
    data object BodyWeight : Screen("body_weight", "Weight", Icons.Default.MonitorWeight)
    data object History : Screen("history", "History", Icons.Default.History)
    data object Progress : Screen("progress", "Stats", Icons.AutoMirrored.Filled.TrendingUp)

    companion object {
        val all = listOf(Dashboard, LogWorkout, BodyWeight, History, Progress)
    }
}
