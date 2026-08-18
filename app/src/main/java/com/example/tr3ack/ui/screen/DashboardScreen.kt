package com.example.tr3ack.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    repository: Tr3ackRepository,
    onNavigateToLog: () -> Unit,
    onNavigateToBodyWeight: () -> Unit
) {
    val viewModel: DashboardViewModel = remember {
        DashboardViewModel(repository)
    }

    val todaySets by viewModel.todaySets.collectAsState()
    val exercises by viewModel.exercises.collectAsState()
    val todayBodyWeight by viewModel.todayBodyWeightLive.collectAsState()
    val pullUpsPB by viewModel.pullUpsPB.collectAsState()
    val dipsPB by viewModel.dipsPB.collectAsState()
    val allSets by viewModel.allSets.collectAsState()
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    LaunchedEffect(allSets, exercises) {
        viewModel.recalculatePersonalBest()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToLog) {
                Icon(Icons.Default.Add, contentDescription = "Log a Set")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Today's Body Weight
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showWeightDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Body Weight",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = todayBodyWeight?.let { "%.1f kg".format(it) } ?: "Not logged",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Personal Bests
            item {
                PersonalBestCard(pullUpsPB)
            }
            item {
                PersonalBestCard(dipsPB)
            }

            // Today's Sets Header
            item {
                Text(
                    text = "Today's Sets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (todaySets.isEmpty()) {
                item {
                    Text(
                        text = "No sets logged today. Tap + to get started!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Group sets by exercise
            val groupedSets = todaySets.groupBy { it.exerciseId }
            items(groupedSets.entries.toList()) { (exerciseId, sets) ->
                val exercise = exercises.find { it.id == exerciseId }
                ExerciseDayCard(
                    exercise = exercise,
                    sets = sets,
                    todayBodyWeight = todayBodyWeight
                )
            }

            // Per-exercise last logged
            if (exercises.isNotEmpty()) {
                item {
                    Text(
                        text = "Training Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(exercises) { exercise ->
                    val daysAgo = viewModel.lastLoggedForExercise(exercise.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = daysAgo?.let {
                                if (it == 0L) "Today" else "$it days ago"
                            } ?: "Never",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text("Today's Body Weight") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Weight (kg)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    weightInput.toDoubleOrNull()?.let {
                        viewModel.saveBodyWeight(it)
                        showWeightDialog = false
                        weightInput = ""
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExerciseDayCard(
    exercise: Exercise?,
    sets: List<WorkoutSet>,
    todayBodyWeight: Double?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise?.name ?: "Unknown Exercise",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sets.size} sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            sets.forEachIndexed { index, set ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Set ${index + 1}: ${set.reps} reps",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (exercise?.isBodyweightBased == true && todayBodyWeight != null) {
                        val totalSystemWeight = todayBodyWeight + set.addedWeightKg
                        val percentBodyWeight = (totalSystemWeight / todayBodyWeight) * 100
                        Text(
                            text = "+${set.addedWeightKg}kg | %.1f%% BW".format(percentBodyWeight),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (set.addedWeightKg > 0) {
                        Text(
                            text = "${set.addedWeightKg}kg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Daily volume for bodyweight exercises
            if (exercise?.isBodyweightBased == true && todayBodyWeight != null) {
                val dailyVolume = sets.sumOf { (todayBodyWeight + it.addedWeightKg) * it.reps }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily Volume",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%.1f kg".format(dailyVolume),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalBestCard(pb: com.example.tr3ack.viewmodel.PersonalBest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (pb.maxTotalSystemWeight > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pb.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                text = "%.1f kg".format(pb.maxTotalSystemWeight),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "System Weight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Column {
                            Text(
                                text = "${pb.reps}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "reps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Column {
                            Text(
                                text = "%.1f%%".format(pb.maxPercentBodyWeight),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "BW",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (pb.dateAchieved.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val pbDate = LocalDate.parse(pb.dateAchieved)
                        val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(pbDate, LocalDate.now())
                        Text(
                            text = "Set ${if (daysAgo == 0L) "today" else "$daysAgo days ago"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column {
                    Text(
                        text = pb.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "No sets logged yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
