package com.example.tr3ack.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.LogWorkoutViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(repository: Tr3ackRepository) {
    val viewModel: LogWorkoutViewModel = viewModel { LogWorkoutViewModel(repository) }

    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val reps by viewModel.reps.collectAsStateWithLifecycle()
    val addedWeight by viewModel.addedWeight.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val bodyWeight by viewModel.bodyWeight.collectAsStateWithLifecycle()
    val savedSets by viewModel.savedSets.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val newlyUnlocked by viewModel.newlyUnlocked.collectAsStateWithLifecycle()
    val lastUsedWeight by viewModel.lastUsedWeight.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    val selectedExercise = exercises.find { it.id == selectedExerciseId }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val setSavedMessage = stringResource(R.string.snackbar_set_saved)
    val unlockedTitles = newlyUnlocked.map { achievementTitle(it) }
    val unlockMessage = if (unlockedTitles.isEmpty()) {
        null
    } else {
        pluralStringResource(
            R.plurals.snackbar_achievements_unlocked,
            unlockedTitles.size,
            unlockedTitles.size,
            unlockedTitles.first()
        )
    }

    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)
    val dateLabel = remember(selectedDate, todayLabel, yesterdayLabel) {
        val today = LocalDate.now()
        when {
            selectedDate == today -> todayLabel
            selectedDate == today.minusDays(1) -> yesterdayLabel
            else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        }
    }

    val canLog = selectedExerciseId != null && reps.isNotEmpty()
    val logButtonLabel = if (selectedExerciseId != null && reps.isNotEmpty() && addedWeight.isNotEmpty()) {
        stringResource(R.string.action_log_set_weight, addedWeight, reps)
    } else {
        stringResource(R.string.action_log_set)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            val message = unlockMessage ?: setSavedMessage
            viewModel.clearNewlyUnlocked()
            viewModel.resetSaveSuccess()
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Exercise selector — one tap per lift.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        exercises.forEach { exercise ->
                            val selected = exercise.id == selectedExerciseId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectExercise(exercise.id) },
                                label = { Text(shortExerciseName(exercise.name)) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            painter = painterResource(exerciseIcon(exercise.id)),
                                            contentDescription = null,
                                            modifier = Modifier.width(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }

                // Date — compact, tappable.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_date).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.width(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dateLabel)
                        }
                    }
                }

                // Body weight for bodyweight lifts.
                if (selectedExercise?.isBodyweightBased == true) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                                    text = stringResource(R.string.body_weight),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = bodyWeight?.let { "%.1f kg".format(it) }
                                        ?: stringResource(R.string.no_body_weight_logged),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Weight + reps side by side.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = addedWeight,
                            onValueChange = { viewModel.setAddedWeight(it) },
                            label = {
                                Text(
                                    stringResource(
                                        if (selectedExercise?.isBodyweightBased == true)
                                            R.string.added_weight_kg_label
                                        else
                                            R.string.weight_kg_label
                                    )
                                )
                            },
                            suffix = {
                                lastUsedWeight?.let {
                                    Text(
                                        text = "last ${it}kg",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { viewModel.setReps(it) },
                            label = { Text(stringResource(R.string.label_reps)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Live metrics while typing (bodyweight lifts).
                if (selectedExercise?.isBodyweightBased == true && bodyWeight != null && addedWeight.isNotEmpty()) {
                    item {
                        val tsw = viewModel.getTotalSystemWeight()
                        val pct = viewModel.getPercentOfBodyWeight()
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.live_metrics),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (tsw != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.total_system_weight))
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
                                        Text(stringResource(R.string.percent_body_weight))
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

                // Daily volume for bodyweight lifts.
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
                                        text = stringResource(R.string.daily_volume_sets, savedSets.size),
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

                // Sets logged for this session.
                if (savedSets.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.sets_logged_today),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(savedSets, key = { it.id }) { set ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reps_count, set.reps),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedExercise?.isBodyweightBased == true && bodyWeight != null) {
                                    val tsw = bodyWeight!! + set.addedWeightKg
                                    val pct = (tsw / bodyWeight!!) * 100
                                    Text(
                                        text = "+${set.addedWeightKg}kg · %.1fkg · %.1f%% BW".format(tsw, pct),
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
                                IconButton(onClick = { viewModel.deleteSet(set) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.content_desc_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Always-visible log button.
            Button(
                onClick = { if (canLog) viewModel.saveSet() },
                enabled = canLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(logButtonLabel, style = MaterialTheme.typography.titleMedium)
            }
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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@DrawableRes
private fun exerciseIcon(exerciseId: Long): Int = when (exerciseId) {
    ExerciseIds.WEIGHTED_PULL_UPS -> R.drawable.ic_ach_pullup
    ExerciseIds.WEIGHTED_DIPS -> R.drawable.ic_ex_dips
    ExerciseIds.BICEP_CURLS -> R.drawable.ic_ach_bicep
    ExerciseIds.LATERAL_RAISES -> R.drawable.ic_ach_lateral
    else -> R.drawable.ic_ach_weight
}

private fun shortExerciseName(name: String): String = name
    .replace("Weighted ", "")
    .removePrefix("Bicep ")
    .removeSuffix(" Raises")
