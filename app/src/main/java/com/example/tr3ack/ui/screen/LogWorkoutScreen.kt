package com.example.tr3ack.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.LogWorkoutViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(repository: Tr3ackRepository) {
    val viewModel: LogWorkoutViewModel = remember {
        LogWorkoutViewModel(repository)
    }

    val exercises by viewModel.exercises.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsState()
    val reps by viewModel.reps.collectAsState()
    val addedWeight by viewModel.addedWeight.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val bodyWeight by viewModel.bodyWeight.collectAsState()
    val savedSets by viewModel.savedSets.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val lastUsedWeight by viewModel.lastUsedWeight.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var exerciseMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Set saved!")
            }
            viewModel.resetSaveSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedExerciseId != null && reps.isNotEmpty()) {
                        viewModel.saveSet()
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save Set")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Exercise selector
            item {
                ExposedDropdownMenuBox(
                    expanded = exerciseMenuExpanded,
                    onExpandedChange = { exerciseMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = exercises.find { it.id == selectedExerciseId }?.name ?: "Select Exercise",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exercise") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exerciseMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = exerciseMenuExpanded,
                        onDismissRequest = { exerciseMenuExpanded = false }
                    ) {
                        exercises.forEach { exercise ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(exercise.name)
                                        if (exercise.isBodyweightBased) {
                                            Text(
                                                "Weighted Bodyweight",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectExercise(exercise.id)
                                    exerciseMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Date picker
            item {
                OutlinedTextField(
                    value = selectedDate.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false
                )
            }

            // Body weight display for bodyweight exercises
            val selectedExercise = exercises.find { it.id == selectedExerciseId }
            if (selectedExercise?.isBodyweightBased == true) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Body Weight",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = bodyWeight?.let { "%.1f kg".format(it) } ?: "No body weight logged",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Added weight input
            item {
                OutlinedTextField(
                    value = addedWeight,
                    onValueChange = { viewModel.setAddedWeight(it) },
                    label = {
                        Text(
                            if (selectedExercise?.isBodyweightBased == true)
                                "Added Weight (kg)"
                            else
                                "Weight (kg)"
                        )
                    },
                    suffix = { lastUsedWeight?.let { Text("Last: ${it}kg") } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Reps input
            item {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { viewModel.setReps(it) },
                    label = { Text("Reps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Live metrics for bodyweight exercises
            if (selectedExercise?.isBodyweightBased == true && bodyWeight != null && addedWeight.isNotEmpty()) {
                item {
                    val tsw = viewModel.getTotalSystemWeight()
                    val pct = viewModel.getPercentOfBodyWeight()
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Live Metrics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (tsw != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total System Weight")
                                    Text(
                                        "%.1f kg".format(tsw),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (pct != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("% of Body Weight")
                                    Text(
                                        "%.1f%%".format(pct),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daily volume for bodyweight exercises
            if (selectedExercise?.isBodyweightBased == true && savedSets.isNotEmpty()) {
                item {
                    val volume = viewModel.getDailyVolume()
                    if (volume != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Volume (${savedSets.size} sets)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "%.1f kg".format(volume),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Saved sets for this session
            if (savedSets.isNotEmpty()) {
                item {
                    Text(
                        text = "Sets Logged Today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(savedSets) { set ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${set.reps} reps",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (selectedExercise?.isBodyweightBased == true && bodyWeight != null) {
                                val tsw = bodyWeight!! + set.addedWeightKg
                                val pct = (tsw / bodyWeight!!) * 100
                                Text(
                                    text = "+${set.addedWeightKg}kg | %.1fkg total | %.1f%% BW".format(tsw, pct),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "${set.addedWeightKg}kg",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.of("UTC"))
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        viewModel.setDate(date)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
