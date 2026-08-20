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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Brush
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
    var showAddExerciseDialog by remember { mutableStateOf(false) }

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
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "+ Add Exercise",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                exerciseMenuExpanded = false
                                showAddExerciseDialog = true
                            }
                        )
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

                // Day count toggle + charts
                if (displayData.isNotEmpty()) {
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

                    // E1RM chart
                    item {
                        Text(
                            text = "Estimated 1RM (kg)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            E1RMChart(
                                data = displayData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Session Tonnage chart
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Session Tonnage (kg·reps)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            TonnageBarChart(
                                data = displayData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(12.dp)
                            )
                        }
                    }

                    // Belt Load vs Body Weight chart (bodyweight exercises only)
                    if (selectedExercise.isBodyweightBased) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belt Load vs Body Weight",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                BeltVsBodyChart(
                                    data = displayData,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .padding(12.dp)
                                )
                            }
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

                if (displayData.isEmpty() && freeWeightData.isEmpty()) {
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

    if (showAddExerciseDialog) {
        var newName by remember { mutableStateOf("") }
        var isBodyweight by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = { Text("Add Exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Exercise Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weighted Bodyweight")
                        Switch(
                            checked = isBodyweight,
                            onCheckedChange = { isBodyweight = it }
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addExercise(newName.trim(), isBodyweight)
                            showAddExerciseDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showAddExerciseDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun E1RMChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val e1rmValues = data.map { it.estimatedOneRM }
    val maxE1RM = e1rmValues.max()
    val minE1RM = e1rmValues.min()
    val padding = ((maxE1RM - minE1RM) * 0.15).coerceAtLeast(5.0)
    val yMin = (minE1RM - padding).coerceAtLeast(0.0)
    val yMax = maxE1RM + padding
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 16f
        val bottomPadding = 36f

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
            textSize = 24f
            isAntiAlias = true
        }
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = yMax - (yRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(value),
                4f,
                y + 8f,
                textPaint
            )
        }

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
                val shortDate = data[i].date.takeLast(5)
                drawContext.canvas.nativeCanvas.drawText(
                    shortDate,
                    x,
                    size.height - 4f,
                    labelPaint
                )
            }
        }

        fun pointX(index: Int) = leftPadding + if (stepCount > 0) chartWidth * index / stepCount else chartWidth / 2f

        if (data.size >= 2) {
            val strokePath = Path()
            data.forEachIndexed { index, point ->
                val x = pointX(index)
                val normalized = (point.estimatedOneRM - yMin) / yRange
                val y = topPadding + chartHeight * (1.0 - normalized).toFloat()
                if (index == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
            }
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(leftPadding + chartWidth, topPadding + chartHeight)
                lineTo(leftPadding, topPadding + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = topPadding,
                    endY = topPadding + chartHeight
                )
            )
        }

        data.forEachIndexed { index, point ->
            val x = pointX(index)
            val normalized = (point.estimatedOneRM - yMin) / yRange
            val y = topPadding + chartHeight * (1.0 - normalized).toFloat()
            drawCircle(color = lineColor, radius = 8f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
        }

        val unitLabelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            "kg",
            size.width - rightPadding,
            topPadding - 2f,
            unitLabelPaint
        )
    }
}

@Composable
private fun TonnageBarChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val barColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val tonnageValues = data.map { it.sessionTonnage }
    val maxTonnage = tonnageValues.max()
    val yMax = (maxTonnage * 1.15).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 16f
        val bottomPadding = 36f

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
            textSize = 24f
            isAntiAlias = true
        }
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = yMax - (yMax * i / 4.0)
            val label = if (value >= 1000) "%.1fk".format(value / 1000) else "%.0f".format(value)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                4f,
                y + 8f,
                textPaint
            )
        }

        val barCount = data.size
        val totalGap = chartWidth * 0.3f
        val gap = if (barCount > 1) totalGap / (barCount + 1) else 0f
        val barWidth = (chartWidth - totalGap) / barCount

        val labelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        data.forEachIndexed { index, point ->
            val barLeft = leftPadding + gap + index * (barWidth + gap)
            val barHeight = (point.sessionTonnage / yMax * chartHeight).toFloat()
            val barTop = topPadding + chartHeight - barHeight

            drawRoundRect(
                color = barColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            val centerX = barLeft + barWidth / 2f
            val shortDate = point.date.takeLast(5)
            drawContext.canvas.nativeCanvas.drawText(
                shortDate,
                centerX,
                size.height - 4f,
                labelPaint
            )
        }

        val unitLabelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            "kg·reps",
            size.width - rightPadding,
            topPadding - 2f,
            unitLabelPaint
        )
    }
}

@Composable
private fun BeltVsBodyChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val beltColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val allValues = data.flatMap { listOf(it.beltLoad, it.bodyWeightKg) }
    val maxVal = allValues.max()
    val minVal = allValues.min()
    val valuePadding = ((maxVal - minVal) * 0.15).coerceAtLeast(5.0)
    val yMin = (minVal - valuePadding).coerceAtLeast(0.0)
    val yMax = maxVal + valuePadding
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 28f
        val bottomPadding = 36f

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
            textSize = 24f
            isAntiAlias = true
        }
        for (i in 0..4) {
            val y = topPadding + chartHeight * (i / 4f)
            val value = yMax - (yRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(value),
                4f,
                y + 8f,
                textPaint
            )
        }

        val stepCount = data.size - 1
        val labelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        for (i in data.indices) {
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
            val beltPath = Path()
            data.forEachIndexed { index, point ->
                val x = pointX(index)
                val y = pointY(point.beltLoad)
                if (index == 0) beltPath.moveTo(x, y) else beltPath.lineTo(x, y)
            }
            drawPath(path = beltPath, color = beltColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            val bodyPath = Path()
            data.forEachIndexed { index, point ->
                val x = pointX(index)
                val y = pointY(point.bodyWeightKg)
                if (index == 0) bodyPath.moveTo(x, y) else bodyPath.lineTo(x, y)
            }
            drawPath(path = bodyPath, color = bodyColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        data.forEachIndexed { index, point ->
            val beltY = pointY(point.beltLoad)
            drawCircle(color = beltColor, radius = 7f, center = Offset(pointX(index), beltY))
            drawCircle(color = Color.White, radius = 3.5f, center = Offset(pointX(index), beltY))

            val bodyY = pointY(point.bodyWeightKg)
            drawCircle(color = bodyColor, radius = 7f, center = Offset(pointX(index), bodyY))
            drawCircle(color = Color.White, radius = 3.5f, center = Offset(pointX(index), bodyY))
        }

        val legendPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
        }
        val legendY = 10f
        var legendX = leftPadding + 8f

        drawCircle(color = beltColor, radius = 6f, center = Offset(legendX, legendY))
        drawContext.canvas.nativeCanvas.drawText("Belt Load", legendX + 14f, legendY + 6f, legendPaint)
        legendX += 110f

        drawCircle(color = bodyColor, radius = 6f, center = Offset(legendX, legendY))
        drawContext.canvas.nativeCanvas.drawText("Body Weight", legendX + 14f, legendY + 6f, legendPaint)
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
