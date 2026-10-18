package com.example.tr3ack.ui.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.GoalType
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.Achievement
import com.example.tr3ack.viewmodel.AchievementsViewModel
import com.example.tr3ack.viewmodel.GoalProgress
import com.example.tr3ack.viewmodel.GoalRules

private fun achievementIcon(key: String): Int = when (key) {
    "whatshot", "local_fire_department" -> R.drawable.ic_ach_flame
    "bolt" -> R.drawable.ic_ach_zap
    "workspace_premium" -> R.drawable.ic_ach_award
    "check_circle" -> R.drawable.ic_ach_check
    "directions_run" -> R.drawable.ic_ach_person
    "emoji_events" -> R.drawable.ic_ach_trophy
    "shield" -> R.drawable.ic_ach_shield
    "bicep" -> R.drawable.ic_ach_dumbbell
    "pullup" -> R.drawable.ic_ach_pullup
    "shoulder" -> R.drawable.ic_ach_lateral
    else -> R.drawable.ic_ach_star
}

@Composable
private fun AchievementIcon(key: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    @DrawableRes val res = achievementIcon(key)
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

/** Color assigned to each achievement tier, ranked by how hard/long they take to earn. */
private fun tierColor(tier: String): Color = when (tier) {
    "iron" -> Color(0xFF9E9E9E)
    "copper" -> Color(0xFFB87333)
    "silver" -> Color(0xFFC0C0C0)
    "gold" -> Color(0xFFD4AF37)
    "emerald" -> Color(0xFF2ECC71)
    "diamond" -> Color(0xFF4FC3F7)
    else -> Color(0xFF9E9E9E)
}

private val TIER_ORDER = listOf("iron", "copper", "silver", "gold", "emerald", "diamond")

/** Same emerald used by the emerald achievement tier — marks a reached goal. */
private val GoalAccent = Color(0xFF2ECC71)

@StringRes
private fun tierLabel(tier: String): Int = when (tier) {
    "iron" -> R.string.tier_iron
    "copper" -> R.string.tier_copper
    "silver" -> R.string.tier_silver
    "gold" -> R.string.tier_gold
    "emerald" -> R.string.tier_emerald
    "diamond" -> R.string.tier_diamond
    else -> R.string.tier_iron
}

@Composable
fun AchievementsScreen(repository: Tr3ackRepository) {
    val viewModel: AchievementsViewModel = viewModel { AchievementsViewModel(repository) }

    val level by viewModel.level.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val totalTonnage by viewModel.totalTonnage.collectAsStateWithLifecycle()
    val totalSessions by viewModel.totalSessions.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val goalProgress by viewModel.goalProgress.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val unlockedCount = achievements.count { it.unlocked }

    var showGoalDialog by remember { mutableStateOf(false) }
    var editingGoalExerciseId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
    ) {
        item { LevelCard(level) }
        item {
            StreakCard(streak, totalTonnage, totalSessions)
        }
        item {
            GoalsSection(
                goals = goalProgress,
                exercises = exercises,
                onAdd = {
                    editingGoalExerciseId = null
                    showGoalDialog = true
                },
                onEdit = { exerciseId ->
                    editingGoalExerciseId = exerciseId
                    showGoalDialog = true
                }
            )
        }
        item {
            Text(
                text = stringResource(R.string.goals_header, unlockedCount, achievements.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            TierLegend()
        }
        items(achievements, key = { it.id }) { achievement ->
            AchievementRow(achievement, Modifier.animateItem())
        }
    }

    if (showGoalDialog) {
        GoalEditDialog(
            exercises = exercises,
            existing = goalProgress.find { it.goal.exerciseId == editingGoalExerciseId },
            onDismiss = { showGoalDialog = false },
            onSave = { exerciseId, type, target ->
                viewModel.saveGoal(exerciseId, type, target)
                showGoalDialog = false
            },
            onDelete = { exerciseId ->
                viewModel.deleteGoal(exerciseId)
                showGoalDialog = false
            }
        )
    }
}

@Composable
private fun GoalsSection(
    goals: List<GoalProgress>,
    exercises: List<Exercise>,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val nameById = remember(exercises) { exercises.associate { it.id to it.name } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.goals_targets_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_desc_add_goal),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.goals_add_target))
            }
        }

        if (goals.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.goals_no_goals),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goals.forEach { goal ->
                    GoalCard(
                        goal = goal,
                        exerciseName = nameById[goal.goal.exerciseId]
                            ?: stringResource(R.string.unknown_exercise),
                        onClick = { onEdit(goal.goal.exerciseId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: GoalProgress,
    exerciseName: String,
    onClick: () -> Unit
) {
    val accent = if (goal.achieved) GoalAccent else MaterialTheme.colorScheme.primary

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (goal.achieved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.goals_achieved),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(goalTypeLabel(goal.goal.type)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { goal.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.goals_progress_value,
                        formatGoalValue(goal.goal.type, goal.current),
                        formatGoalValue(goal.goal.type, goal.goal.targetValue)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = stringResource(R.string.goals_percent, (goal.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditDialog(
    exercises: List<Exercise>,
    existing: GoalProgress?,
    onDismiss: () -> Unit,
    onSave: (Long, GoalType, Double) -> Unit,
    onDelete: (Long) -> Unit
) {
    val isEditing = existing != null
    var selectedExerciseId by remember {
        mutableStateOf(existing?.goal?.exerciseId ?: exercises.firstOrNull()?.id)
    }
    var selectedType by remember { mutableStateOf(existing?.goal?.type ?: GoalType.E1RM) }
    var targetText by remember {
        mutableStateOf(existing?.goal?.targetValue?.let { formatGoalInput(it) } ?: "")
    }
    var exerciseMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val selectedExercise = exercises.find { it.id == selectedExerciseId }
    val availableTypes = GoalRules.availableTypes(selectedExercise?.isBodyweightBased ?: false)

    // Keep the effective type valid when the exercise (and therefore the allowed
    // goal types) changes — e.g. switching from a free weight to a pull-up. This
    // is derived rather than written back to state during composition.
    val effectiveType = if (selectedType in availableTypes) selectedType else availableTypes.first()

    val parsedTarget = targetText.toDoubleOrNull()
    val canSave = selectedExerciseId != null && parsedTarget != null && parsedTarget > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isEditing) R.string.goals_edit_goal else R.string.goals_set_goal))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = exerciseMenuExpanded && !isEditing,
                    onExpandedChange = { if (!isEditing) exerciseMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedExercise?.name ?: stringResource(R.string.select_exercise),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isEditing,
                        label = { Text(stringResource(R.string.label_exercise)) },
                        trailingIcon = {
                            if (!isEditing) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = exerciseMenuExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = exerciseMenuExpanded && !isEditing,
                        onDismissRequest = { exerciseMenuExpanded = false }
                    ) {
                        exercises.forEach { exercise ->
                            DropdownMenuItem(
                                text = { Text(exercise.name) },
                                onClick = {
                                    selectedExerciseId = exercise.id
                                    exerciseMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(goalTypeLabel(effectiveType)),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.goals_target_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        availableTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(stringResource(goalTypeLabel(type))) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { newValue ->
                        targetText = newValue.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text(stringResource(R.string.goals_target_value_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        onSave(selectedExerciseId!!, effectiveType, parsedTarget!!)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    TextButton(onClick = { selectedExerciseId?.let(onDelete) }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@StringRes
private fun goalTypeLabel(type: GoalType): Int = when (type) {
    GoalType.E1RM -> R.string.goal_type_e1rm
    GoalType.WEIGHT -> R.string.goal_type_weight
    GoalType.BW_RATIO -> R.string.goal_type_bw_ratio
}

private fun formatGoalValue(type: GoalType, value: Double): String = when (type) {
    GoalType.BW_RATIO -> "%.2fx BW".format(value)
    else -> "%.1f kg".format(value)
}

private fun formatGoalInput(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun LevelCard(level: com.example.tr3ack.viewmodel.LevelInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.goals_level),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { level.progress },
                    modifier = Modifier.size(110.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.primary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${level.level}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
Text(
                text = stringResource(level.levelNameRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.goals_xp_to_level, level.currentXp, level.level + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun StreakCard(
    streak: com.example.tr3ack.viewmodel.StreakInfo,
    totalTonnage: Double,
    totalSessions: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StreakStat(stringResource(R.string.goals_current), "${streak.current}", stringResource(R.string.goals_weeks))
            StreakStat(stringResource(R.string.goals_longest), "${streak.longest}", stringResource(R.string.goals_weeks))
            StreakStat(stringResource(R.string.goals_workouts), "$totalSessions", stringResource(R.string.goals_sessions))
            StreakStat(stringResource(R.string.goals_volume), formatTonnage(totalTonnage), stringResource(R.string.goals_volume_unit))
        }
    }
}

@Composable
private fun StreakStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTonnage(tonnage: Double): String {
    return when {
        tonnage >= 1_000_000 -> "%.1fM".format(tonnage / 1_000_000)
        tonnage >= 1_000 -> "%.1fk".format(tonnage / 1_000)
        else -> "%.0f".format(tonnage)
    }
}

@Composable
private fun TierLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TIER_ORDER.forEach { tier ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(tierColor(tier), CircleShape)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(tierLabel(tier)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, modifier: Modifier = Modifier) {
    val unlockedColor = tierColor(achievement.tier)
    val lockedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bgColor = if (achievement.unlocked) {
        unlockedColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (achievement.unlocked) unlockedColor else lockedColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AchievementIcon(
                    key = achievement.iconKey,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                val titleArg = achievement.titleArgRes?.let { stringResource(it) }
                val title = if (titleArg != null) {
                    stringResource(achievement.titleRes, titleArg)
                } else {
                    stringResource(achievement.titleRes)
                }
                val description = if (achievement.descriptionArg != null) {
                    stringResource(achievement.descriptionRes, achievement.descriptionArg)
                } else {
                    stringResource(achievement.descriptionRes)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (achievement.unlocked) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}