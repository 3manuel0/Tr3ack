package com.example.tr3ack.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

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
    val bodyWeightHistory by viewModel.bodyWeightHistory.collectAsState()
    val pullUpsPB by viewModel.pullUpsPB.collectAsState()
    val dipsPB by viewModel.dipsPB.collectAsState()
    val bicepCurlsPB by viewModel.bicepCurlsPB.collectAsState()
    val lateralRaisesPB by viewModel.lateralRaisesPB.collectAsState()
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

            // Body Weight Trend
            if (bodyWeightHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Body Weight Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BodyWeightTrendChart(
                            data = bodyWeightHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .padding(12.dp)
                        )
                    }
                }
            }

            // Personal Bests
            item {
                Text(
                    text = "Personal Bests",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                PersonalBestCard(pullUpsPB)
            }
            item {
                PersonalBestCard(dipsPB)
            }
            item {
                PersonalBestCard(bicepCurlsPB)
            }
            item {
                PersonalBestCard(lateralRaisesPB)
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
private fun BodyWeightTrendChart(
    data: List<BodyWeightEntry>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val values = data.map { it.bodyWeightKg }
    val maxVal = values.max()
    val minVal = values.min()
    val valuePadding = ((maxVal - minVal) * 0.15).coerceAtLeast(2.0)
    val yMin = (minVal - valuePadding).coerceAtLeast(0.0)
    val yMax = maxVal + valuePadding
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val leftPadding = 48f
        val rightPadding = 24f
        val topPadding = 16f
        val bottomPadding = 28f

        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1f
            )
        }

        val textPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 22f
            isAntiAlias = true
        }
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = yMax - (yRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(value),
                4f,
                y + 8f,
                textPaint
            )
        }

        val stepCount = data.size - 1
        val labelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 18f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val labelEvery = ceil(data.size * 50f / chartWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        for (i in data.indices) {
            if ((data.lastIndex - i) % labelEvery != 0) continue
            val x = leftPadding + if (stepCount > 0) chartWidth * i / stepCount else chartWidth / 2f
            val shortDate = data[i].date.takeLast(5)
            drawContext.canvas.nativeCanvas.drawText(
                shortDate,
                x,
                size.height - 4f,
                labelPaint
            )
        }

        fun pointX(index: Int) = leftPadding + if (stepCount > 0) chartWidth * index / stepCount else chartWidth / 2f
        fun pointY(value: Double) = topPadding + chartHeight * (1.0 - (value - yMin) / yRange).toFloat()

        if (data.size >= 2) {
            val path = Path()
            data.forEachIndexed { index, point ->
                val x = pointX(index)
                val y = pointY(point.bodyWeightKg)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }

        val dotRadius = if (data.size > 60) 2.5f else 4f
        data.forEachIndexed { index, point ->
            drawCircle(
                color = lineColor,
                radius = dotRadius,
                center = Offset(pointX(index), pointY(point.bodyWeightKg))
            )
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
            if (pb.estimatedOneRM > 0) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pb.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (pb.isBodyweightBased) {
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
                        } else {
                            Column {
                                Text(
                                    text = "%.1f kg".format(pb.addedWeight),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Weight",
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
                                    text = if (pb.addedWeightPercentBodyWeight > 0)
                                        "%.1f%%".format(pb.addedWeightPercentBodyWeight)
                                    else "—",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "of BW",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column {
                                Text(
                                    text = "%.1f kg".format(pb.estimatedOneRM),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "e1RM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
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
