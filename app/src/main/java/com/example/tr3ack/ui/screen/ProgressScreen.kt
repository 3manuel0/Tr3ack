package com.example.tr3ack.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.BestSetRecord
import com.example.tr3ack.viewmodel.ChartPoint
import com.example.tr3ack.viewmodel.OneRepMax
import com.example.tr3ack.viewmodel.ProgressViewModel
import com.example.tr3ack.viewmodel.rollingAverage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Interval between x-axis date labels so crowded charts stay readable (anchored to newest point). */
private fun xLabelInterval(pointCount: Int, chartWidthPx: Float, labelSlotPx: Float): Int {
    if (pointCount <= 1) return 1
    val maxLabels = maxOf(1, floor(chartWidthPx / labelSlotPx.coerceAtLeast(1f)).toInt())
    return maxOf(1, ceil(pointCount.toFloat() / maxLabels).toInt())
}

/**
 * Draws "MM-DD" date labels rotated 90° so far more of them fit under a chart
 * without overlapping. [xAt] returns each point's horizontal anchor; labels sit
 * just below the plot (start at [topY]) and grow downward, centered on the anchor.
 */
private fun drawVerticalDateLabels(
    canvas: android.graphics.Canvas,
    data: List<ChartPoint>,
    labelEvery: Int,
    topY: Float,
    xAt: (Int) -> Float,
    paint: android.graphics.Paint
) {
    val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
    val previousAlign = paint.textAlign
    paint.textAlign = android.graphics.Paint.Align.RIGHT
    for (i in data.indices) {
        if ((data.lastIndex - i) % labelEvery != 0) continue
        val x = xAt(i) + textHeight / 2f
        canvas.save()
        canvas.rotate(-90f, x, topY)
        canvas.drawText(data[i].date.takeLast(5), x, topY, paint)
        canvas.restore()
    }
    paint.textAlign = previousAlign
}

@Composable
fun ProgressScreen(repository: Tr3ackRepository, onOpenRankLadder: () -> Unit = {}) {
    val viewModel: ProgressViewModel = viewModel { ProgressViewModel(repository) }

    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val chartData by viewModel.chartData.collectAsStateWithLifecycle()
    val freeWeightData by viewModel.freeWeightData.collectAsStateWithLifecycle()
    val personalRecords by viewModel.bestSet.collectAsStateWithLifecycle()
    val oneRepMax by viewModel.oneRepMax.collectAsStateWithLifecycle()
    val currentTier by viewModel.currentTier.collectAsStateWithLifecycle()

    var dayCount by remember { mutableIntStateOf(30) }

    val selectedExercise = exercises.find { it.id == selectedExerciseId }
    val displayData = remember(chartData, dayCount) { chartData.takeLast(dayCount) }
    val recentFreeWeightSets = remember(freeWeightData) {
        val cutoff = LocalDate.now().minusDays(4).toString()
        freeWeightData.filter { set -> set.date >= cutoff }.sortedByDescending { it.date }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
            // Exercise selector
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exercises.forEach { exercise ->
                        FilterChip(
                            selected = exercise.id == selectedExerciseId,
                            onClick = { viewModel.selectExercise(exercise.id) },
                            label = { Text(shortExerciseName(exercise.name)) }
                        )
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
                item {
                    ProgressHeroCard(
                        record = personalRecords,
                        oneRepMax = oneRepMax,
                        isBodyweight = selectedExercise.isBodyweightBased,
                        rankTier = currentTier,
                        onOpenRank = onOpenRankLadder
                    )
                }

                // Day count toggle + charts
                if (displayData.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            FilterChip(
                                selected = dayCount == Int.MAX_VALUE,
                                onClick = { dayCount = Int.MAX_VALUE },
                                label = { Text(stringResource(R.string.progress_chip_all)) }
                            )
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
                                recentFreeWeightSets.forEach { set ->
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
        }
    }
}

/** Compact chip labels, matching the rank ladder ("Weighted Pull-Ups" → "Pull-Ups"). */
private fun shortExerciseName(name: String): String = name
    .replace("Weighted ", "")
    .removePrefix("Bicep ")
    .removeSuffix(" Raises")

@Composable
private fun ProgressHeroCard(
    record: BestSetRecord,
    oneRepMax: OneRepMax?,
    isBodyweight: Boolean,
    rankTier: String?,
    onOpenRank: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2C3854), Color(0xFF1D2233))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.progress_personal_record).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFFAEBBD8)
                )
                if (rankTier != null) {
                    val accent = tierColor(rankTier)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable { onOpenRank() }
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(tierLabel(rankTier)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(record.estimatedOneRM),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 44.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.progress_estimated_1rm_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            if (record.estimatedOneRM > 0) {
                Text(
                    text = "%+.1f kg × %d · %s".format(record.addedWeight, record.reps, shortDate(record.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB9C3DD)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isBodyweight) {
                    StatTile(stringResource(R.string.stat_pct_bw), "%.1f%%".format(record.percentBodyWeight), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_added), "%.1f kg".format(record.addedWeight), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_system), "%.1f kg".format(record.totalSystemWeight), Modifier.weight(1f))
                } else {
                    StatTile(stringResource(R.string.stat_added), "%.1f kg".format(record.addedWeight), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_reps), "${record.reps}", Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_e1rm), "%.1f kg".format(record.estimatedOneRM), Modifier.weight(1f))
                }
            }
            if (oneRepMax != null && oneRepMax.workingLoad85 > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WorkingLoad("85%", oneRepMax.workingLoad85)
                    WorkingLoad("80%", oneRepMax.workingLoad80)
                    WorkingLoad("75%", oneRepMax.workingLoad75)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFAEBBD8)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WorkingLoad(label: String, kg: Double) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFAEBBD8)
        )
        Text(
            text = "%.1f kg".format(kg),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun shortDate(date: String): String {
    if (date.isBlank()) return "—"
    return runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }.getOrDefault("—")
}

@Composable
private fun E1RMChart(
    data: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val fontScale = LocalDensity.current.fontScale
    val lineColor = MaterialTheme.colorScheme.primary
    val trendColor = MaterialTheme.colorScheme.tertiary
    val prColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val kgLabel = stringResource(R.string.unit_kg)
    val prLabel = stringResource(R.string.chart_legend_pr)
    val trendLabel = stringResource(R.string.chart_legend_trend)

    val metrics = remember(data) {
        val values = data.map { it.estimatedOneRM }
        val maxE1RM = values.max()
        val minE1RM = values.min()
        val padding = ((maxE1RM - minE1RM) * 0.15).coerceAtLeast(5.0)
        val yMin = (minE1RM - padding).coerceAtLeast(0.0)
        val yMax = maxE1RM + padding
        E1RMChartMetrics(
            yMin = yMin,
            yMax = yMax,
            yRange = (yMax - yMin).coerceAtLeast(1.0),
            trend = rollingAverage(values)
        )
    }
    val yMin = metrics.yMin
    val yMax = metrics.yMax
    val yRange = metrics.yRange
    val trendValues = metrics.trend

    val textPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 24f * fontScale
            isAntiAlias = true
        }
    }
    val labelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
        }
    }
    val legendPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 18f * fontScale
            isAntiAlias = true
        }
    }
    val unitLabelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val chartSummary = pluralStringResource(
        R.plurals.chart_e1rm_desc,
        data.size,
        data.size,
        "%.1f".format(data.last().estimatedOneRM)
    )

    Canvas(
        modifier = modifier.semantics {
            contentDescription = chartSummary
        }
    ) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 16f
        val bottomPadding = 64f

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
            val labelSlot = labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent + 8f
            val labelEvery = xLabelInterval(data.size, chartWidth, labelSlot)
            drawVerticalDateLabels(
                canvas = drawContext.canvas.nativeCanvas,
                data = data,
                labelEvery = labelEvery,
                topY = topPadding + chartHeight + 6f,
                xAt = { i -> leftPadding + if (stepCount > 0) chartWidth * i / stepCount else chartWidth / 2f },
                paint = labelPaint
            )
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

            // Smoothed rolling-average trend over the raw line (thinner + dashed).
            val trendPath = Path()
            data.forEachIndexed { index, _ ->
                val x = pointX(index)
                val normalized = (trendValues[index] - yMin) / yRange
                val y = topPadding + chartHeight * (1.0 - normalized).toFloat()
                if (index == 0) trendPath.moveTo(x, y) else trendPath.lineTo(x, y)
            }
            drawPath(
                path = trendPath,
                color = trendColor,
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            )
        }

        val dotRadius = if (data.size > 30) 4f else 8f
        val dotCoreRadius = dotRadius / 2f
        data.forEachIndexed { index, point ->
            val x = pointX(index)
            val normalized = (point.estimatedOneRM - yMin) / yRange
            val y = topPadding + chartHeight * (1.0 - normalized).toFloat()
            // PR sessions get a distinct ring so new all-time bests stand out.
            if (point.isAllTimePr) {
                drawCircle(
                    color = prColor,
                    radius = dotRadius + 4f,
                    center = Offset(x, y),
                    style = Stroke(width = 2.5f)
                )
            }
            drawCircle(color = lineColor, radius = dotRadius, center = Offset(x, y))
            drawCircle(color = Color.White, radius = dotCoreRadius, center = Offset(x, y))
        }

        // Trend + PR legend (kept inside the top padding so labels are not clipped).
        run {
            val legendY = 12f
            drawLine(
                color = trendColor,
                start = Offset(leftPadding, legendY),
                end = Offset(leftPadding + 22f, legendY),
                strokeWidth = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
            )
            drawContext.canvas.nativeCanvas.drawText(
                trendLabel,
                leftPadding + 28f,
                legendY + 6f,
                legendPaint
            )
        }
        run {
            val legendY = 26f
            drawCircle(color = prColor, radius = 5f, center = Offset(leftPadding + 11f, legendY), style = Stroke(width = 2f))
            drawContext.canvas.nativeCanvas.drawText(
                prLabel,
                leftPadding + 28f,
                legendY + 6f,
                legendPaint
            )
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

    val fontScale = LocalDensity.current.fontScale
    val barColor = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val kgRepsLabel = stringResource(R.string.unit_kg_reps)

    val yMax = remember(data) { (data.maxOf { it.sessionTonnage } * 1.15).coerceAtLeast(1.0) }
    val chartSummary = pluralStringResource(
        R.plurals.chart_tonnage_desc,
        data.size,
        data.size,
        "%.0f".format(data.last().sessionTonnage)
    )

    val textPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 24f * fontScale
            isAntiAlias = true
        }
    }
    val labelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
        }
    }
    val unitLabelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = chartSummary
        }
    ) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 16f
        val bottomPadding = 64f

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

        val labelSlot = labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent + 8f
        val labelEvery = xLabelInterval(data.size, chartWidth, labelSlot)

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
        }

        drawVerticalDateLabels(
            canvas = drawContext.canvas.nativeCanvas,
            data = data,
            labelEvery = labelEvery,
            topY = topPadding + chartHeight + 6f,
            xAt = { index -> leftPadding + gap + index * (barWidth + gap) + barWidth / 2f },
            paint = labelPaint
        )

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

    val fontScale = LocalDensity.current.fontScale
    val beltColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val beltLabel = stringResource(R.string.chart_legend_belt_load)
    val bodyWeightLabel = stringResource(R.string.chart_legend_body_weight)

    val metrics = remember(data) {
        val allValues = data.flatMap { listOf(it.beltLoad, it.bodyWeightKg) }
        val maxVal = allValues.max()
        val minVal = allValues.min()
        val valuePadding = ((maxVal - minVal) * 0.15).coerceAtLeast(5.0)
        val yMin = (minVal - valuePadding).coerceAtLeast(0.0)
        val yMax = maxVal + valuePadding
        BeltChartMetrics(yMin = yMin, yMax = yMax, yRange = (yMax - yMin).coerceAtLeast(1.0))
    }
    val yMin = metrics.yMin
    val yMax = metrics.yMax
    val yRange = metrics.yRange
    val chartSummary = pluralStringResource(R.plurals.chart_belt_desc, data.size, data.size)

    val textPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 24f * fontScale
            isAntiAlias = true
        }
    }
    val labelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
        }
    }
    val legendPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 20f * fontScale
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = chartSummary
        }
    ) {
        val leftPadding = 56f
        val rightPadding = 24f
        val topPadding = 28f
        val bottomPadding = 64f

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
        val labelSlot = labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent + 8f
        val labelEvery = xLabelInterval(data.size, chartWidth, labelSlot)
        drawVerticalDateLabels(
            canvas = drawContext.canvas.nativeCanvas,
            data = data,
            labelEvery = labelEvery,
            topY = topPadding + chartHeight + 6f,
            xAt = { i -> leftPadding + if (stepCount > 0) chartWidth * i / stepCount else chartWidth / 2f },
            paint = labelPaint
        )

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

private data class E1RMChartMetrics(
    val yMin: Double,
    val yMax: Double,
    val yRange: Double,
    val trend: List<Double>
)

private data class BeltChartMetrics(
    val yMin: Double,
    val yMax: Double,
    val yRange: Double
)
