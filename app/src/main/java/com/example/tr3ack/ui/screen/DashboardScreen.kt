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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.DashboardViewModel
import com.example.tr3ack.viewmodel.TrendDirection
import com.example.tr3ack.viewmodel.TrendNote
import com.example.tr3ack.viewmodel.WeekVolume
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun DashboardScreen(
    repository: Tr3ackRepository,
    onNavigateToLog: () -> Unit,
    onNavigateToBodyWeight: () -> Unit
) {
    val viewModel: DashboardViewModel = viewModel { DashboardViewModel(repository) }

    val todaySets by viewModel.todaySets.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val todayBodyWeight by viewModel.todayBodyWeightLive.collectAsStateWithLifecycle()
    val bodyWeightHistory by viewModel.bodyWeightHistory.collectAsStateWithLifecycle()
    val pullUpsPB by viewModel.pullUpsPB.collectAsStateWithLifecycle()
    val dipsPB by viewModel.dipsPB.collectAsStateWithLifecycle()
    val bicepCurlsPB by viewModel.bicepCurlsPB.collectAsStateWithLifecycle()
    val lateralRaisesPB by viewModel.lateralRaisesPB.collectAsStateWithLifecycle()
    val exerciseTrends by viewModel.exerciseTrends.collectAsStateWithLifecycle()
    val exerciseTrendNotes by viewModel.exerciseTrendNotes.collectAsStateWithLifecycle()
    val workoutDays by viewModel.workoutDays.collectAsStateWithLifecycle()
    val weekVolume by viewModel.weekVolume.collectAsStateWithLifecycle()
    val lastLoggedByExercise by viewModel.lastLoggedDateByExercise.collectAsStateWithLifecycle()
    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }

    val groupedTodaySets = remember(todaySets) { todaySets.groupBy { it.exerciseId }.entries.toList() }
    val exerciseById = remember(exercises) { exercises.associateBy { it.id } }
    val trainingLogRows = remember(exercises, lastLoggedByExercise) {
        val today = LocalDate.now()
        exercises.map { exercise ->
            val last = lastLoggedByExercise[exercise.id]
            val daysAgo = if (last.isNullOrBlank()) {
                null
            } else {
                ChronoUnit.DAYS.between(LocalDate.parse(last), today)
            }
            exercise to daysAgo
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToLog) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_desc_log_set))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
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
                                text = stringResource(R.string.dashboard_today_body_weight),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = todayBodyWeight?.let { "%.1f kg".format(it) } ?: stringResource(R.string.dashboard_weight_not_logged),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.content_desc_edit),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Training Consistency
            item {
                Text(
                    text = stringResource(R.string.dashboard_training_consistency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_training_consistency_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        if (workoutDays.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dashboard_training_consistency_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TrainingConsistencyHeatmap(
                                workoutDays = workoutDays,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Week-over-week Volume
            if (weekVolume.hasData) {
                item {
                    WeekVolumeCard(volume = weekVolume)
                }
            }

            // Body Weight Trend
            if (bodyWeightHistory.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_body_weight_trend),
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
                    text = stringResource(R.string.dashboard_personal_bests),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                PersonalBestCard(
                    pb = pullUpsPB,
                    trend = exerciseTrends[ExerciseIds.WEIGHTED_PULL_UPS].orEmpty(),
                    trendNote = exerciseTrendNotes[ExerciseIds.WEIGHTED_PULL_UPS]
                )
            }
            item {
                PersonalBestCard(
                    pb = dipsPB,
                    trend = exerciseTrends[ExerciseIds.WEIGHTED_DIPS].orEmpty(),
                    trendNote = exerciseTrendNotes[ExerciseIds.WEIGHTED_DIPS]
                )
            }
            item {
                PersonalBestCard(
                    pb = bicepCurlsPB,
                    trend = exerciseTrends[ExerciseIds.BICEP_CURLS].orEmpty(),
                    trendNote = exerciseTrendNotes[ExerciseIds.BICEP_CURLS]
                )
            }
            item {
                PersonalBestCard(
                    pb = lateralRaisesPB,
                    trend = exerciseTrends[ExerciseIds.LATERAL_RAISES].orEmpty(),
                    trendNote = exerciseTrendNotes[ExerciseIds.LATERAL_RAISES]
                )
            }

            // Today's Sets Header
            item {
                Text(
                    text = stringResource(R.string.dashboard_todays_sets),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (todaySets.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_no_sets_today),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Group sets by exercise
            items(groupedTodaySets, key = { "day_${it.key}" }) { (exerciseId, sets) ->
                ExerciseDayCard(
                    exercise = exerciseById[exerciseId],
                    sets = sets,
                    todayBodyWeight = todayBodyWeight,
                    modifier = Modifier.animateItem()
                )
            }

            // Per-exercise last logged
            if (trainingLogRows.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_training_log),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(trainingLogRows, key = { "log_${it.first.id}" }) { (exercise, daysAgo) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = daysAgo?.let {
                                if (it == 0L) stringResource(R.string.dashboard_today) else stringResource(R.string.dashboard_days_ago, it)
                            } ?: stringResource(R.string.dashboard_never),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showWeightDialog) {
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = { Text(stringResource(R.string.dashboard_today_body_weight)) },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.weight_kg_label)) },
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
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun WeekVolumeCard(volume: WeekVolume) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dashboard_week_volume),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_week_volume_this_week),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatVolume(volume.thisWeekTonnage),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_week_volume_unit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.dashboard_week_volume_sessions_sets,
                            volume.thisWeekSessions,
                            volume.thisWeekSets
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val change = volume.tonnageChangePct
                    if (change != null) {
                        val color = when {
                            change > 0.0 -> Color(0xFF66BB6A)
                            change < 0.0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val arrow = when {
                            change > 0.0 -> "▲"
                            change < 0.0 -> "▼"
                            else -> "•"
                        }
                        Text(
                            text = "$arrow %.0f%%".format(abs(change)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        Text(
                            text = stringResource(R.string.dashboard_week_volume_vs_last),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dashboard_week_volume_no_last),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (volume.perExercise.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_week_volume_this_last),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                volume.perExercise.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.exerciseName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${formatVolume(entry.thisWeekTonnage)} / ${formatVolume(entry.lastWeekTonnage)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatVolume(tonnage: Double): String = when {
    tonnage >= 1_000_000 -> "%.1fM".format(tonnage / 1_000_000)
    tonnage >= 1_000 -> "%.1fk".format(tonnage / 1_000)
    else -> "%.0f".format(tonnage)
}

@Composable
private fun ExerciseDayCard(
    exercise: Exercise?,
    sets: List<WorkoutSet>,
    todayBodyWeight: Double?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exercise?.name ?: stringResource(R.string.unknown_exercise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dashboard_sets_count, sets.size),
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
                        text = stringResource(R.string.dashboard_set_reps, index + 1, set.reps),
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
                        text = stringResource(R.string.dashboard_daily_volume),
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

    val fontScale = LocalDensity.current.fontScale
    val lineColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val chartSummary = pluralStringResource(
        R.plurals.chart_bw_trend_desc,
        data.size,
        data.size,
        "%.1f".format(data.last().bodyWeightKg)
    )

    val metrics = remember(data) {
        val values = data.map { it.bodyWeightKg }
        val maxVal = values.max()
        val minVal = values.min()
        val valuePadding = ((maxVal - minVal) * 0.15).coerceAtLeast(2.0)
        val yMin = (minVal - valuePadding).coerceAtLeast(0.0)
        val yMax = maxVal + valuePadding
        BodyWeightChartMetrics(yMin = yMin, yMax = yMax, yRange = (yMax - yMin).coerceAtLeast(1.0))
    }
    val yMin = metrics.yMin
    val yMax = metrics.yMax
    val yRange = metrics.yRange

    val textPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 22f * fontScale
            isAntiAlias = true
        }
    }
    val labelPaint = remember(textColor, fontScale) {
        android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 18f * fontScale
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier.semantics {
            contentDescription = chartSummary
        }
    ) {
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
private fun PersonalBestCard(
    pb: com.example.tr3ack.viewmodel.PersonalBest,
    trend: List<Double>,
    trendNote: TrendNote? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                        text = stringResource(R.string.pb_system_weight),
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
                                        text = stringResource(R.string.reps_literal),
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
                                        text = stringResource(R.string.pb_body_weight_pct),
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
                                        text = stringResource(R.string.pb_weight),
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
                                        text = stringResource(R.string.reps_literal),
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
                                        text = stringResource(R.string.pb_of_body_weight),
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
                                        text = stringResource(R.string.pb_e1rm),
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
                                text = if (daysAgo == 0L) {
                                    stringResource(R.string.pb_set_today)
                                } else {
                                    stringResource(R.string.pb_set_days_ago, daysAgo)
                                },
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
                            text = stringResource(R.string.pb_no_sets_logged),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            if (pb.estimatedOneRM > 0 && trend.size >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniTrendChart(
                        values = trend,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    if (trendNote != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val noteColor = when (trendNote.direction) {
                            TrendDirection.UP -> Color(0xFF66BB6A)
                            TrendDirection.DOWN -> MaterialTheme.colorScheme.error
                            TrendDirection.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = when (trendNote.direction) {
                                TrendDirection.UP -> "▲ %.1f%%".format(trendNote.pctChange)
                                TrendDirection.DOWN -> "▼ %.1f%%".format(-trendNote.pctChange)
                                TrendDirection.FLAT -> "• %.1f%%".format(trendNote.pctChange)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = noteColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTrendChart(
    values: List<Double>,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) return

    val metrics = remember(values) {
        val minVal = values.min()
        val maxVal = values.max()
        MiniTrendMetrics(
            minVal = minVal,
            range = (maxVal - minVal).coerceAtLeast(1.0),
            stepCount = values.size - 1
        )
    }
    val minVal = metrics.minVal
    val range = metrics.range
    val stepCount = metrics.stepCount

    val rising = values.last() >= values.first()
    val lineColor = if (rising) Color(0xFF66BB6A) else MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        fun pointY(value: Double) = size.height * (1f - ((value - minVal) / range).toFloat())
        fun pointX(index: Int) = if (stepCount > 0) size.width * index / stepCount else size.width / 2f

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = pointX(index)
            val y = pointY(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                endY = size.height
            )
        )

        values.forEachIndexed { index, value ->
            drawCircle(color = lineColor, radius = 2.5f, center = Offset(pointX(index), pointY(value)))
        }
    }
}


@Composable
private fun TrainingConsistencyHeatmap(
    workoutDays: List<com.example.tr3ack.data.entity.WorkoutDayEntity>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val start = today.minusWeeks(18)
    val daySet = remember(workoutDays) { workoutDays.map { LocalDate.parse(it.date) }.toHashSet() }
    val weeks = remember(start, today) {
        mutableListOf<List<LocalDate>>().apply {
            var cursor = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            while (!cursor.isAfter(today)) {
                val week = (0..6).map { cursor.plusDays(it.toLong()) }
                add(week)
                cursor = cursor.plusWeeks(1)
            }
        }
    }
    val shownWeeks = weeks.takeLast(18)

    // Month label spans: label + the week-column range it covers
    val monthSpans = remember(shownWeeks) {
        val spans = mutableListOf<Pair<String, IntRange>>()
        var i = 0
        while (i < shownWeeks.size) {
            val week = shownWeeks[i]
            val firstDay = week.firstOrNull() ?: break
            val monthKey = firstDay.year * 100 + firstDay.monthValue
            var j = i
            while (j < shownWeeks.size) {
                val w = shownWeeks[j]
                val d = w.firstOrNull() ?: break
                if (d.year * 100 + d.monthValue == monthKey) j++ else break
            }
            spans.add(firstDay.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to (i until j))
            i = j
        }
        spans
    }

    val dow = DOW_LABELS
    val cellSize = 12.dp
    val cellSpacing = 4.dp
    val labelColumnWidth = 20.dp
    val monthRowHeight = 16.dp
    val monthToGridGap = 4.dp
    // month row + gap + 7 cells + 6 gaps
    val gridHeight = monthRowHeight + monthToGridGap + cellSize * 7 + cellSpacing * 6

    val primary = MaterialTheme.colorScheme.primary
    val emptyCellColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val monthPaint = remember(labelColor, density) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
        }
    }
    val dowPaint = remember(labelColor, density) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            isAntiAlias = true
        }
    }
    val trainedDayCount = remember(shownWeeks, daySet) {
        shownWeeks.count { week -> week.any { daySet.contains(it) } }
    }
    val chartSummary = pluralStringResource(R.plurals.chart_heatmap_desc, trainedDayCount, trainedDayCount)

    Canvas(
        modifier = modifier
            .height(gridHeight)
            .semantics { contentDescription = chartSummary }
    ) {
        val gutterPx = labelColumnWidth.toPx()
        val gapPx = cellSpacing.toPx()
        val cellPx = cellSize.toPx()
        val gridLeft = gutterPx + gapPx
        val gridTop = monthRowHeight.toPx() + monthToGridGap.toPx()

        fun centeredBaseline(paint: android.graphics.Paint, centerY: Float): Float {
            val fm = paint.fontMetrics
            return centerY - (fm.ascent + fm.descent) / 2f
        }

        // Month labels, each anchored to the first week column of its span
        val monthCenterY = monthRowHeight.toPx() / 2f
        monthSpans.forEach { (label, span) ->
            if (span.isEmpty()) return@forEach
            val x = gridLeft + span.first * (cellPx + gapPx)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                centeredBaseline(monthPaint, monthCenterY),
                monthPaint
            )
        }

        // Day-of-week gutter (every other row, matching the previous layout)
        (0..6).forEach { row ->
            if (row % 2 == 0) return@forEach
            val centerY = gridTop + row * (cellPx + gapPx) + cellPx / 2f
            drawContext.canvas.nativeCanvas.drawText(
                dow[row],
                0f,
                centeredBaseline(dowPaint, centerY),
                dowPaint
            )
        }

        // Workout cells
        shownWeeks.forEachIndexed { column, week ->
            week.forEachIndexed { row, day ->
                val x = gridLeft + column * (cellPx + gapPx)
                val y = gridTop + row * (cellPx + gapPx)
                val color = if (daySet.contains(day)) primary.copy(alpha = 0.85f) else emptyCellColor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}


private val DOW_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private data class BodyWeightChartMetrics(
    val yMin: Double,
    val yMax: Double,
    val yRange: Double
)

private data class MiniTrendMetrics(
    val minVal: Double,
    val range: Double,
    val stepCount: Int
)
