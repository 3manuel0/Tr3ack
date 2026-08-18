package com.example.tr3ack.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.ui.screen.BodyWeightScreen
import com.example.tr3ack.ui.screen.DashboardScreen
import com.example.tr3ack.ui.screen.HistoryScreen
import com.example.tr3ack.ui.screen.LogWorkoutScreen
import com.example.tr3ack.ui.screen.ProgressScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tr3ackNavGraph(repository: Tr3ackRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = Screen.all.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        topBar = {
            val title = Screen.all.find { screen ->
                currentDestination?.hierarchy?.any { it.route == screen.route } == true
            }?.title ?: "Tr3ack"

            val canNavigateBack = currentDestination?.route != Screen.Dashboard.route

            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.all.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    repository = repository,
                    onNavigateToLog = { navController.navigate(Screen.LogWorkout.route) },
                    onNavigateToBodyWeight = { navController.navigate(Screen.BodyWeight.route) }
                )
            }
            composable(Screen.LogWorkout.route) {
                LogWorkoutScreen(repository = repository)
            }
            composable(Screen.BodyWeight.route) {
                BodyWeightScreen(repository = repository)
            }
            composable(Screen.History.route) {
                HistoryScreen(repository = repository)
            }
            composable(Screen.Progress.route) {
                ProgressScreen(repository = repository)
            }
        }
    }
}
