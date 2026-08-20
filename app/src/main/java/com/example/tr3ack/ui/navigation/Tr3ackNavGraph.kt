package com.example.tr3ack.ui.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.tr3ack.viewmodel.HistoryViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tr3ackNavGraph(repository: Tr3ackRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = Screen.all.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    val isHistory = currentDestination?.hierarchy?.any { it.route == Screen.History.route } == true
    val context = LocalContext.current

    val historyViewModel: HistoryViewModel = remember { HistoryViewModel(repository) }
    val exportCsv by historyViewModel.exportCsv.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                os.write(exportCsv?.toByteArray() ?: return@let)
            }
            Toast.makeText(context, "CSV exported!", Toast.LENGTH_SHORT).show()
            historyViewModel.consumeCsv()
        }
    }

    LaunchedEffect(exportCsv) {
        exportCsv?.let {
            csvLauncher.launch("Tr3ack_Export_${LocalDate.now()}.csv")
        }
    }

    val exportJson by historyViewModel.exportJson.collectAsState()
    val importResult by historyViewModel.importResult.collectAsState()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                os.write(exportJson?.toByteArray() ?: return@let)
            }
            Toast.makeText(context, "Backup exported!", Toast.LENGTH_SHORT).show()
            historyViewModel.consumeJson()
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText()
            if (content != null) {
                pendingImportJson = content
                showImportConfirmDialog = true
            }
        }
    }

    LaunchedEffect(exportJson) {
        exportJson?.let {
            jsonExportLauncher.launch("Tr3ack_Backup_${LocalDate.now()}.json")
        }
    }

    LaunchedEffect(importResult) {
        importResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            historyViewModel.consumeImportResult()
        }
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
                },
                actions = {
                    if (isHistory) {
                        IconButton(onClick = { historyViewModel.generateCsv() }) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export CSV"
                            )
                        }
                    }
                    if (currentDestination?.route == Screen.Dashboard.route) {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Backup") },
                                onClick = {
                                    showOverflowMenu = false
                                    historyViewModel.generateBackupJson()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FileDownload, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Backup") },
                                onClick = {
                                    showOverflowMenu = false
                                    jsonImportLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                }
                            )
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
                HistoryScreen(repository = repository, viewModel = historyViewModel)
            }
            composable(Screen.Progress.route) {
                ProgressScreen(repository = repository)
            }
        }
    }

    if (showImportConfirmDialog && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportJson = null
            },
            title = { Text("Import Backup") },
            text = { Text("This will replace ALL current data with the backup. This cannot be undone. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    historyViewModel.importBackupJson(pendingImportJson!!)
                    showImportConfirmDialog = false
                    pendingImportJson = null
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportJson = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
