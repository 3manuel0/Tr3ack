package com.example.tr3ack.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.tr3ack.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int
) {
    data object Dashboard : Screen("dashboard", R.string.nav_home, R.drawable.ic_nav_home)
    data object LogWorkout : Screen("log_workout", R.string.nav_log, R.drawable.ic_nav_log)
    data object BodyWeight : Screen("body_weight", R.string.nav_weight, R.drawable.ic_nav_weight)
    data object History : Screen("history", R.string.nav_history, R.drawable.ic_nav_history)
    data object Progress : Screen("progress", R.string.nav_stats, R.drawable.ic_nav_stats)
    data object Achievements : Screen("achievements", R.string.nav_goals, R.drawable.ic_nav_goals)

    /** Detail screen reachable from Goals; not part of the bottom bar. */
    data object RankLadder : Screen("rank_ladder", R.string.nav_rank_ladder, R.drawable.ic_nav_stats)

    companion object {
        val all = listOf(Dashboard, LogWorkout, BodyWeight, History, Progress, Achievements)

        /** Every destination that should show a title in the top app bar. */
        val titled = all + RankLadder
    }
}
