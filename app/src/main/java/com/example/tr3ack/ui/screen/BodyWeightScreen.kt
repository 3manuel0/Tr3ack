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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.BodyWeightViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightScreen(repository: Tr3ackRepository) {
    val viewModel: BodyWeightViewModel = remember {
        BodyWeightViewModel(repository)
    }

    val entries by viewModel.allEntries.collectAsState()
    val editingDate by viewModel.editingDate.collectAsState()
    val editingWeight by viewModel.editingWeight.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var addDate by remember { mutableStateOf<LocalDate?>(null) }
    var addWeight by remember { mutableStateOf("") }
    var addDateError by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (entries.isEmpty()) {
                item {
                    Text(
                        text = "No body weight entries yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(entries) { entry ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        viewModel.startEditing(
                            LocalDate.parse(entry.date),
                            entry.bodyWeightKg
                        )
                    }
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
                                text = LocalDate.parse(entry.date).format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.1f kg".format(entry.bodyWeightKg),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.deleteEntry(entry) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Edit dialog
    if (editingDate != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelEditing() },
            title = { Text("Edit Body Weight") },
            text = {
                Column {
                    Text(
                        text = editingDate!!.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingWeight,
                        onValueChange = { viewModel.setEditingWeight(it) },
                        label = { Text("Weight (kg)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveWeight() }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelEditing() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add new entry dialog
    if (showAddDialog) {
        var showDatePicker by remember { mutableStateOf(true) }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = {
                    showAddDialog = false
                    addDate = null
                    addWeight = ""
                    addDateError = false
                },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            addDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        // Check if entry already exists for this date
                        val existingDate = addDate
                        if (existingDate != null) {
                            val existingEntry = entries.find { it.date == existingDate.toString() }
                            if (existingEntry != null) {
                                addDateError = true
                            } else {
                                showDatePicker = false
                                addDateError = false
                            }
                        }
                    }) {
                        Text("Next")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddDialog = false
                        addDate = null
                        addWeight = ""
                        addDateError = false
                    }) {
                        Text("Cancel")
                    }
                }
            ) {
                Column {
                    DatePicker(state = datePickerState)
                    if (addDateError) {
                        Text(
                            text = "An entry already exists for this date. Tap it to edit.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    addDate = null
                    addWeight = ""
                    addDateError = false
                },
                title = { Text("Add Body Weight") },
                text = {
                    Column {
                        Text(
                            text = addDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = addWeight,
                            onValueChange = { addWeight = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Weight (kg)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        addDate?.let { date ->
                            addWeight.toDoubleOrNull()?.let { weight ->
                                viewModel.startEditing(date, weight)
                                viewModel.saveWeight()
                                showAddDialog = false
                                addDate = null
                                addWeight = ""
                                addDateError = false
                            }
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddDialog = false
                        addDate = null
                        addWeight = ""
                        addDateError = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
