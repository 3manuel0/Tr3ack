package com.example.tr3ack.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.tr3ack.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", R.string.nav_home, Icons.Default.Home)
    data object LogWorkout : Screen("log_workout", R.string.nav_log, Icons.Default.FitnessCenter)
    data object BodyWeight : Screen("body_weight", R.string.nav_weight, Icons.Default.MonitorWeight)
    data object History : Screen("history", R.string.nav_history, Icons.Default.History)
    data object Progress : Screen("progress", R.string.nav_stats, Icons.AutoMirrored.Filled.TrendingUp)
    data object Achievements : Screen("achievements", R.string.nav_goals, Icons.Default.EmojiEvents)

    companion object {
        val all = listOf(Dashboard, LogWorkout, BodyWeight, History, Progress, Achievements)
    }
}
