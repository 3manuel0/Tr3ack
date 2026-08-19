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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.ChartPoint
import com.example.tr3ack.viewmodel.ProgressViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(repository: Tr3ackRepository) {
    val viewModel: ProgressViewModel = remember {
        ProgressViewModel(repository)
    }

    val exercises by viewModel.exercises.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val freeWeightData by viewModel.freeWeightData.collectAsState()
    val personalRecords by viewModel.personalRecords.collectAsState()
    val oneRepMax by viewModel.oneRepMax.collectAsState()

    var exerciseMenuExpanded by remember { mutableStateOf(false) }
    var dayCount by remember { mutableIntStateOf(5) }

    val selectedExercise = exercises.find { it.id == selectedExerciseId }
    val displayData = chartData.takeLast(dayCount)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Exercise selector
            item {
                ExposedDropdownMenuBox(
                    expanded = exerciseMenuExpanded,
                    onExpandedChange = { exerciseMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedExercise?.name ?: "Select Exercise",
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

            if (selectedExercise == null) {
                item {
                    Text(
                        text = "Select an exercise to view progress",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedExercise != null) {
                // Personal Records
                item {
                    Text(
                        text = "Personal Records",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedExercise.isBodyweightBased) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                PRRow("Max System Weight", "%.1f kg".format(personalRecords.maxTotalSystemWeight))
                                PRRow("Max % Body Weight", "%.1f%%".format(personalRecords.maxPercentBodyWeight))
                                PRRow("Max Added Weight", "%.1f kg".format(personalRecords.maxAddedWeight))
                                PRRow("Max Reps (1 set)", "${personalRecords.maxReps}")
                            }
                        }
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                PRRow("Max Weight", "%.1f kg".format(personalRecords.maxAddedWeight))
                                PRRow("Max Reps", "${personalRecords.maxReps}")
                            }
                        }
                    }
                }

                // 1RM Card
                if (oneRepMax != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Estimated 1RM",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PRRow(
                                    "1RM Total System Load",
                                    "%.1f kg".format(oneRepMax!!.oneRepMaxTSL)
                                )
                                PRRow(
                                    "1RM Added Weight",
                                    "%.1f kg".format(oneRepMax!!.oneRepMaxAddedWeight)
                                )
                                PRRow(
                                    "Based on",
                                    "%.1f kg TSL x %d reps".format(oneRepMax!!.basedOnTSL, oneRepMax!!.basedOnReps)
                                )
                                PRRow(
                                    "Set Date",
                                    oneRepMax!!.basedOnDate
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Relative Strength",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                PRRow(
                                    "BW Multiplier",
                                    "%.2fx BW".format(oneRepMax!!.strengthMultiplier)
                                )
                                PRRow(
                                    "Added as % BW",
                                    "%.1f%%".format(oneRepMax!!.bodyweightPercentage)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Working Loads",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                PRRow(
                                    "85% 1RM",
                                    "%.1f kg TSL".format(oneRepMax!!.workingLoad85)
                                )
                                PRRow(
                                    "80% 1RM",
                                    "%.1f kg TSL".format(oneRepMax!!.workingLoad80)
                                )
                                PRRow(
                                    "75% 1RM",
                                    "%.1f kg TSL".format(oneRepMax!!.workingLoad75)
                                )
                                if (oneRepMax!!.currentBodyWeight > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "At Current BW (%.1f kg)".format(oneRepMax!!.currentBodyWeight),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    PRRow(
                                        "Added Weight Needed",
                                        "%.1f kg".format(oneRepMax!!.oneRepMaxAddedWeight)
                                    )
                                }
                            }
                        }
                    }
                }

                // Day count toggle + chart for weighted exercises
                if (selectedExercise.isBodyweightBased && displayData.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progress",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = dayCount == 5,
                                    onClick = { dayCount = 5 },
                                    label = { Text("5 days") }
                                )
                                FilterChip(
                                    selected = dayCount == 10,
                                    onClick = { dayCount = 10 },
                                    label = { Text("10 days") }
                                )
                            }
                        }
                    }

                    // System Weight chart
                    item {
                        Text(
                            text = "Total System Weight (kg)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            DualAxisChart(
                                data = displayData,
                                leftLabel = "kg",
                                rightLabel = "reps",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                // Free weight data list
                if (!selectedExercise.isBodyweightBased && freeWeightData.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sets Over Time",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                freeWeightData.takeLast(20).reversed().forEach { set ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = set.date,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${set.reps} reps @ ${set.addedWeightKg}kg",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedExercise.isBodyweightBased && displayData.isEmpty()) {
                    item {
                        Text(
                            text = "Log some sets to see your progress chart",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DualAxisChart(
    data: List<ChartPoint>,
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val weightColor = MaterialTheme.colorScheme.primary
    val repsColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val maxWeight = data.maxOf { it.totalSystemWeight }
    val minWeight = data.minOf { it.totalSystemWeight }
    val weightRange = (maxWeight - minWeight).coerceAtLeast(1.0)

    val maxReps = data.maxOf { it.reps }.coerceAtLeast(1)
    val minReps = data.minOf { it.reps }
    val repsRange = (maxReps - minReps).coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val leftPadding = 48f
        val rightPadding = 48f
        val topPadding = 16f
        val bottomPadding = 36f

        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        // Grid lines
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1f
            )
        }

        // Left Y-axis labels (weight)
        val textPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 24f
            isAntiAlias = true
        }
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = maxWeight - (weightRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(value),
                4f,
                y + 8f,
                textPaint
            )
        }

        // Right Y-axis labels (reps)
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = maxReps - (repsRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(value),
                size.width - rightPadding + 8f,
                y + 8f,
                textPaint
            )
        }

        // X-axis date labels
        val stepCount = data.size - 1
        if (stepCount >= 0) {
            val labelPaint = android.graphics.Paint().apply {
                color = textColor.hashCode()
                textSize = 20f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (i in data.indices) {
                val x = leftPadding + (if (stepCount > 0) chartWidth * i / stepCount else chartWidth / 2f)
                val shortDate = data[i].date.takeLast(5) // MM-DD
                drawContext.canvas.nativeCanvas.drawText(
                    shortDate,
                    x,
                    size.height - 4f,
                    labelPaint
                )
            }
        }

        // Weight line
        if (data.size >= 2) {
            val weightPath = Path()
            data.forEachIndexed { index, point ->
                val x = leftPadding + chartWidth * index / stepCount
                val normalizedWeight = (point.totalSystemWeight - minWeight) / weightRange
                val y = topPadding + chartHeight * (1.0 - normalizedWeight).toFloat()
                if (index == 0) weightPath.moveTo(x, y) else weightPath.lineTo(x, y)
            }
            drawPath(
                path = weightPath,
                color = weightColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }

        // Weight dots
        data.forEachIndexed { index, point ->
            val x = leftPadding + chartWidth * index / stepCount
            val normalizedWeight = (point.totalSystemWeight - minWeight) / weightRange
            val y = topPadding + chartHeight * (1.0 - normalizedWeight).toFloat()
            drawCircle(color = weightColor, radius = 8f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
        }

        // Reps dots (smaller, different color)
        data.forEachIndexed { index, point ->
            val x = leftPadding + chartWidth * index / stepCount
            val normalizedReps = (point.reps - minReps).toDouble() / repsRange
            val y = topPadding + chartHeight * (1.0 - normalizedReps).toFloat()
            drawCircle(color = repsColor, radius = 6f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
        }

        // Legend
        val legendY = 10f
        val legendX = leftPadding + 10f
        drawCircle(color = weightColor, radius = 6f, center = Offset(legendX, legendY))
        drawContext.canvas.nativeCanvas.drawText(
            leftLabel,
            legendX + 14f,
            legendY + 6f,
            android.graphics.Paint().apply {
                color = textColor.hashCode()
                textSize = 22f
                isAntiAlias = true
            }
        )
        drawCircle(color = repsColor, radius = 6f, center = Offset(legendX + 80f, legendY))
        drawContext.canvas.nativeCanvas.drawText(
            rightLabel,
            legendX + 94f,
            legendY + 6f,
            android.graphics.Paint().apply {
                color = textColor.hashCode()
                textSize = 22f
                isAntiAlias = true
            }
        )
    }
}

@Composable
private fun PRRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
