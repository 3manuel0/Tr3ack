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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.ChartPoint
import com.example.tr3ack.viewmodel.ProgressViewModel
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

/** Interval between x-axis date labels so crowded charts stay readable (anchored to newest point). */
private fun xLabelInterval(pointCount: Int, chartWidthPx: Float): Int {
    if (pointCount <= 1) return 1
    return maxOf(1, ceil(pointCount * 60f / chartWidthPx.coerceAtLeast(1f)).toInt())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(repository: Tr3ackRepository) {
    val viewModel: ProgressViewModel = viewModel { ProgressViewModel(repository) }

    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val chartData by viewModel.chartData.collectAsStateWithLifecycle()
    val freeWeightData by viewModel.freeWeightData.collectAsStateWithLifecycle()
    val personalRecords by viewModel.bestSet.collectAsStateWithLifecycle()
    val oneRepMax by viewModel.oneRepMax.collectAsStateWithLifecycle()

    var exerciseMenuExpanded by remember { mutableStateOf(false) }
    var dayCount by remember { mutableIntStateOf(10) }

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
                        value = selectedExercise?.name ?: stringResource(R.string.select_exercise),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_exercise)) },
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
                                                stringResource(R.string.weighted_bodyweight),
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
                        text = stringResource(R.string.progress_select_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedExercise != null) {
                // Personal Record — Best Set
                if (personalRecords.estimatedOneRM > 0) {
                    item {
                        Text(
                            text = stringResource(R.string.progress_personal_record),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.progress_best_set),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "%.1f kg".format(personalRecords.estimatedOneRM),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.progress_estimated_1rm_caption),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PRRow(
                                    stringResource(R.string.progress_set_label),
                                    stringResource(R.string.progress_set_value, personalRecords.addedWeight, personalRecords.reps)
                                )
                                if (selectedExercise.isBodyweightBased) {
                                    PRRow(
                                        stringResource(R.string.progress_total_system_load),
                                        "%.1f kg".format(personalRecords.totalSystemWeight)
                                    )
                                    PRRow(
                                        stringResource(R.string.progress_pct_body_weight),
                                        "%.1f%%".format(personalRecords.percentBodyWeight)
                                    )
                                }
                                PRRow(stringResource(R.string.progress_date), personalRecords.date)
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
                                    text = stringResource(R.string.progress_estimated_1rm),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PRRow(
                                    stringResource(R.string.progress_1rm_tsl),
                                    "%.1f kg".format(oneRepMax!!.oneRepMaxTSL)
                                )
                                PRRow(
                                    stringResource(R.string.progress_1rm_added_weight),
                                    "%.1f kg".format(oneRepMax!!.oneRepMaxAddedWeight)
                                )
                                PRRow(
                                    stringResource(R.string.progress_based_on),
                                    stringResource(R.string.progress_based_on_value, oneRepMax!!.basedOnTSL, oneRepMax!!.basedOnReps)
                                )
                                PRRow(
                                    stringResource(R.string.progress_set_date),
                                    oneRepMax!!.basedOnDate
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.progress_relative_strength),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                PRRow(
                                    stringResource(R.string.progress_bw_multiplier),
                                    stringResource(R.string.progress_bw_multiplier_value, oneRepMax!!.strengthMultiplier)
                                )
                                PRRow(
                                    stringResource(R.string.progress_added_as_pct_bw),
                                    "%.1f%%".format(oneRepMax!!.bodyweightPercentage)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.progress_working_loads),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                PRRow(
                                    stringResource(R.string.progress_working_85),
                                    stringResource(R.string.progress_working_value, oneRepMax!!.workingLoad85)
                                )
                                PRRow(
                                    stringResource(R.string.progress_working_80),
                                    stringResource(R.string.progress_working_value, oneRepMax!!.workingLoad80)
                                )
                                PRRow(
                                    stringResource(R.string.progress_working_75),
                                    stringResource(R.string.progress_working_value, oneRepMax!!.workingLoad75)
                                )
                                if (oneRepMax!!.currentBodyWeight > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.progress_at_current_bw, oneRepMax!!.currentBodyWeight),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    PRRow(
                                        stringResource(R.string.progress_added_weight_needed),
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
                        Column {
                            Text(
                                text = stringResource(R.string.progress_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = dayCount == 10,
                                    onClick = { dayCount = 10 },
                                    label = { Text(stringResource(R.string.progress_chip_10d)) }
                                )
                                FilterChip(
                                    selected = dayCount == 30,
                                    onClick = { dayCount = 30 },
                                    label = { Text(stringResource(R.string.progress_chip_30d)) }
                                )
                                FilterChip(
                                    selected = dayCount == 90,
                                    onClick = { dayCount = 90 },
                                    label = { Text(stringResource(R.string.progress_chip_90d)) }
                                )
                            }
                        }
                    }

                    // E1RM chart
                    item {
                        Text(
                            text = stringResource(R.string.progress_e1rm_chart),
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
                            text = stringResource(R.string.progress_tonnage_chart),
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
                                text = stringResource(R.string.progress_belt_vs_bw),
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
                            text = stringResource(R.string.progress_sets_over_time),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val cutoff = LocalDate.now().minusDays(4).toString()
                                freeWeightData.filter { set -> set.date >= cutoff }.sortedByDescending { it.date }.forEach { set ->
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
                                            text = stringResource(R.string.progress_reps_at_weight, set.reps, "${set.addedWeightKg}kg"),
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
                            text = stringResource(R.string.progress_empty),
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
private fun E1RMChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val kgLabel = stringResource(R.string.unit_kg)

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
            val labelEvery = xLabelInterval(data.size, chartWidth)
            for (i in data.indices) {
                if ((data.lastIndex - i) % labelEvery != 0) continue
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

        val dotRadius = if (data.size > 30) 4f else 8f
        val dotCoreRadius = dotRadius / 2f
        data.forEachIndexed { index, point ->
            val x = pointX(index)
            val normalized = (point.estimatedOneRM - yMin) / yRange
            val y = topPadding + chartHeight * (1.0 - normalized).toFloat()
            drawCircle(color = lineColor, radius = dotRadius, center = Offset(x, y))
            drawCircle(color = Color.White, radius = dotCoreRadius, center = Offset(x, y))
        }

        val unitLabelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            kgLabel,
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
    val kgRepsLabel = stringResource(R.string.unit_kg_reps)

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
        val labelEvery = xLabelInterval(data.size, chartWidth)

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

            if ((data.lastIndex - index) % labelEvery == 0) {
                val centerX = barLeft + barWidth / 2f
                val shortDate = point.date.takeLast(5)
                drawContext.canvas.nativeCanvas.drawText(
                    shortDate,
                    centerX,
                    size.height - 4f,
                    labelPaint
                )
            }
        }

        val unitLabelPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText(
            kgRepsLabel,
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
    val beltLabel = stringResource(R.string.chart_legend_belt_load)
    val bodyWeightLabel = stringResource(R.string.chart_legend_body_weight)

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
        val labelEvery = xLabelInterval(data.size, chartWidth)
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

        val dotRadius = if (data.size > 30) 3.5f else 7f
        data.forEachIndexed { index, point ->
            val beltY = pointY(point.beltLoad)
            drawCircle(color = beltColor, radius = dotRadius, center = Offset(pointX(index), beltY))
            drawCircle(color = Color.White, radius = dotRadius / 2f, center = Offset(pointX(index), beltY))

            val bodyY = pointY(point.bodyWeightKg)
            drawCircle(color = bodyColor, radius = dotRadius, center = Offset(pointX(index), bodyY))
            drawCircle(color = Color.White, radius = dotRadius / 2f, center = Offset(pointX(index), bodyY))
        }

        val legendPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 20f
            isAntiAlias = true
        }
        val legendY = 10f
        var legendX = leftPadding + 8f

        drawCircle(color = beltColor, radius = 6f, center = Offset(legendX, legendY))
        drawContext.canvas.nativeCanvas.drawText(beltLabel, legendX + 14f, legendY + 6f, legendPaint)
        legendX += 110f

        drawCircle(color = bodyColor, radius = 6f, center = Offset(legendX, legendY))
        drawContext.canvas.nativeCanvas.drawText(bodyWeightLabel, legendX + 14f, legendY + 6f, legendPaint)
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
