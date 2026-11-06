package com.example.tr3ack.ui.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
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
import kotlin.math.cos
import kotlin.math.sin

internal fun achievementIcon(key: String): Int = when (key) {
    "whatshot", "local_fire_department" -> R.drawable.ic_ach_flame
    "bolt" -> R.drawable.ic_ach_zap
    "workspace_premium" -> R.drawable.ic_ach_award
    "check_circle" -> R.drawable.ic_ach_check
    "directions_run" -> R.drawable.ic_ach_person
    "emoji_events" -> R.drawable.ic_ach_trophy
    "shield" -> R.drawable.ic_ach_shield
    "bicep" -> R.drawable.ic_ach_bicep
    "pullup" -> R.drawable.ic_ach_pullup
    "shoulder" -> R.drawable.ic_ach_lateral
    "layers" -> R.drawable.ic_ach_layers
    "weight" -> R.drawable.ic_ach_weight
    "target" -> R.drawable.ic_ach_target
    "calendar_check" -> R.drawable.ic_ach_calendar_check
    else -> R.drawable.ic_ach_star
}

@Composable
internal fun AchievementIcon(key: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    @DrawableRes val res = achievementIcon(key)
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

/**
 * Metallic palettes modelled on the old League of Legends ranked emblems: a
 * bright top-light, a saturated body and a deep shadow tone. The hex banner and
 * its side wings are both painted from these three colors.
 */
private data class TierPalette(val light: Color, val base: Color, val dark: Color)

private fun tierPalette(tier: String): TierPalette = when (tier) {
    "iron" -> TierPalette(Color(0xFFB9C0C8), Color(0xFF6E757E), Color(0xFF33373C))
    "copper" -> TierPalette(Color(0xFFFFD2A1), Color(0xFFD07A2E), Color(0xFF63300F))
    "silver" -> TierPalette(Color(0xFFF3F7FA), Color(0xFFAFBAC4), Color(0xFF5F6A74))
    "gold" -> TierPalette(Color(0xFFFFEC9E), Color(0xFFD9A92C), Color(0xFF7A570B))
    "emerald" -> TierPalette(Color(0xFF8DF5C7), Color(0xFF22B573), Color(0xFF0A5735))
    "diamond" -> TierPalette(Color(0xFFD4F4FF), Color(0xFF4BB8E8), Color(0xFF125A80))
    else -> TierPalette(Color(0xFFB9C0C8), Color(0xFF6E757E), Color(0xFF33373C))
}

/**
 * Regalia per tier, following the old LoL ladder: wings gain feathers and reach
 * further out as the tier climbs, and diamond adds lightning bolts.
 */
private data class TierOrnament(
    val feathers: Int,
    val wingScale: Float,
    val bolts: Boolean = false
)

private fun tierOrnament(tier: String): TierOrnament = when (tier) {
    "iron" -> TierOrnament(feathers = 2, wingScale = 0.78f)
    "copper" -> TierOrnament(feathers = 2, wingScale = 0.86f)
    "silver" -> TierOrnament(feathers = 3, wingScale = 0.92f)
    "gold" -> TierOrnament(feathers = 3, wingScale = 0.96f)
    "emerald" -> TierOrnament(feathers = 4, wingScale = 1f)
    "diamond" -> TierOrnament(feathers = 5, wingScale = 1f, bolts = true)
    else -> TierOrnament(feathers = 2, wingScale = 0.8f)
}

/** Contrasting enamel used for the pointed base tail beneath the hex. */
private fun tierAccent(tier: String): Color = when (tier) {
    "iron" -> Color(0xFF5C6B7A)
    "copper" -> Color(0xFF34C27F)
    "silver" -> Color(0xFF7C6CF0)
    "gold" -> Color(0xFFE2603C)
    "emerald" -> Color(0xFFE8C14A)
    "diamond" -> Color(0xFFB57BFF)
    else -> Color(0xFF5C6B7A)
}

/** Dimmed steel palette used for achievements that are still locked. */
private val LockedPalette = TierPalette(
    light = Color(0xFF8A9099),
    base = Color(0xFF4C525A),
    dark = Color(0xFF23262A)
)

/** Base color assigned to each achievement tier, ranked by how hard/long they take to earn. */
internal fun tierColor(tier: String): Color = tierPalette(tier).base

private fun hexagonPath(cx: Float, cy: Float, radius: Float): Path {
    val path = Path()
    repeat(6) { i ->
        val angle = Math.toRadians((-90 + i * 60).toDouble())
        val x = cx + cos(angle).toFloat() * radius
        val y = cy + sin(angle).toFloat() * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Appends one tapered feather: start → (two control points) → tip → (two
 * control points) → end, all in the 52x52 design space.
 */
private fun Path.feather(
    scale: Float,
    sx: Float, sy: Float,
    c1x: Float, c1y: Float, c2x: Float, c2y: Float, tx: Float, ty: Float,
    c3x: Float, c3y: Float, c4x: Float, c4y: Float,
    ex: Float, ey: Float
) {
    moveTo(sx * scale, sy * scale)
    cubicTo(c1x * scale, c1y * scale, c2x * scale, c2y * scale, tx * scale, ty * scale)
    cubicTo(c3x * scale, c3y * scale, c4x * scale, c4y * scale, ex * scale, ey * scale)
    close()
}

/** Tapered feather wing in a 52x52 design space, mirrored for the other side. */
private fun wingPath(scale: Float, feathers: Int): Path = Path().apply {
    when (feathers) {
        2 -> {
            feather(scale, 12.8f, 21f, 9.5f, 18.2f, 6.8f, 16.8f, 4.3f, 16.2f, 5.6f, 19.5f, 8.4f, 21.3f, 12.2f, 23f)
            feather(scale, 12.3f, 26f, 8.8f, 25.4f, 6.2f, 25.8f, 4.4f, 27.2f, 6.4f, 29.8f, 9.6f, 29.4f, 12.2f, 28.2f)
        }
        3 -> {
            feather(scale, 13.2f, 20.2f, 8.4f, 15.8f, 4.4f, 13.8f, 1.4f, 12.9f, 3.1f, 17.6f, 6.9f, 20.6f, 12.6f, 23.1f)
            feather(scale, 12.6f, 24.4f, 6.8f, 22.1f, 2.6f, 21.1f, 0.6f, 21.2f, 2.8f, 24.5f, 6.9f, 26.2f, 12.4f, 27.8f)
            feather(scale, 12.5f, 28.4f, 7.2f, 28.4f, 3.4f, 29.6f, 1.4f, 31.8f, 4.2f, 34.1f, 8.4f, 33.6f, 12.4f, 31.8f)
        }
        4 -> {
            feather(scale, 13.4f, 18.6f, 9.6f, 14.2f, 5.6f, 11.8f, 2.2f, 10.6f, 3.9f, 15.4f, 7.8f, 18.8f, 12.9f, 21.4f)
            feather(scale, 13f, 22.4f, 8.4f, 19.2f, 4.2f, 17.2f, 1.2f, 16.4f, 2.8f, 20.8f, 6.6f, 23.4f, 12.4f, 25.4f)
            feather(scale, 12.4f, 26.2f, 7f, 24.2f, 2.8f, 23.4f, 0.4f, 23.8f, 2.4f, 27f, 6.6f, 28.4f, 12.2f, 29.2f)
            feather(scale, 12.2f, 29.8f, 7.6f, 30f, 3.6f, 31.4f, 1.4f, 33.8f, 4.4f, 35.8f, 8.6f, 34.8f, 12.2f, 32.8f)
        }
        else -> {
            feather(scale, 13.6f, 17f, 10.8f, 12f, 7.4f, 9.4f, 4.2f, 7.9f, 5.4f, 12.6f, 8.6f, 16.4f, 13f, 19.2f)
            feather(scale, 13.3f, 20.4f, 10f, 16.2f, 6.2f, 13.8f, 2.8f, 12.4f, 4.2f, 16.9f, 7.6f, 20.4f, 12.7f, 22.8f)
            feather(scale, 12.9f, 24f, 7.8f, 21.4f, 3.4f, 20.2f, 0.8f, 20.4f, 2.8f, 24.2f, 7f, 26.2f, 12.4f, 27.6f)
            feather(scale, 12.4f, 27.8f, 7.2f, 27.4f, 3.2f, 28.4f, 1f, 30.8f, 4f, 33.4f, 8.2f, 33f, 12.2f, 31.2f)
            feather(scale, 12.2f, 31.2f, 8f, 31.8f, 4.4f, 33.4f, 2.6f, 36f, 5.8f, 37.2f, 9.2f, 35.6f, 12.2f, 33.6f)
        }
    }
}

/** Lightning bolt accent used by the top tier, drawn behind the hex. */
private fun boltPath(scale: Float): Path = Path().apply {
    moveTo(13f * scale, 9.6f * scale)
    lineTo(7.8f * scale, 14.4f * scale)
    lineTo(10.6f * scale, 14.8f * scale)
    lineTo(8.6f * scale, 19.4f * scale)
    lineTo(14.2f * scale, 13.8f * scale)
    lineTo(11.2f * scale, 13.4f * scale)
    close()
}

private fun trianglePath(
    scale: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    x3: Float, y3: Float
): Path = Path().apply {
    moveTo(x1 * scale, y1 * scale)
    lineTo(x2 * scale, y2 * scale)
    lineTo(x3 * scale, y3 * scale)
    close()
}

/** Paints the LoL-style hex banner with tier-growing feather wings. */
private fun DrawScope.drawTierEmblem(
    palette: TierPalette,
    ornament: TierOrnament,
    accent: Color,
    alpha: Float
) {
    val unit = size.width / 64f          // badge frame geometry
    val wingUnit = size.width / 52f      // feather design space
    val radius = 20f * unit
    val fieldRadius = 17.1f * unit
    val metal = Brush.verticalGradient(
        0f to palette.light,
        0.42f to palette.base,
        1f to palette.dark,
        startY = center.y - radius,
        endY = center.y + radius
    )

    // Pointed enamel tail under the hex, like the rank emblems.
    val tail = trianglePath(unit, 23f, 45.5f, 41f, 45.5f, 32f, 59.5f)
    drawPath(tail, accent, alpha)
    drawPath(tail, Color.Black.copy(alpha = 0.35f * alpha), style = Stroke(width = 0.6f * unit))
    drawPath(
        trianglePath(unit, 26.5f, 47.5f, 37.5f, 47.5f, 32f, 55.5f),
        Color.White.copy(alpha = 0.25f * alpha)
    )

    val wingOutline = palette.dark.copy(alpha = 0.55f * alpha)
    val wingEdge = Color.White.copy(alpha = 0.20f * alpha)
    val wings = wingPath(wingUnit, ornament.feathers)

    scale(ornament.wingScale, ornament.wingScale, pivot = center) {
        val outlineWidth = 0.55f * wingUnit / ornament.wingScale
        val edgeWidth = 0.35f * wingUnit / ornament.wingScale
        drawPath(wings, metal, alpha)
        drawPath(wings, wingOutline, style = Stroke(width = outlineWidth))
        drawPath(wings, wingEdge, style = Stroke(width = edgeWidth))
        scale(scaleX = -1f, scaleY = 1f, pivot = center) {
            drawPath(wings, metal, alpha)
            drawPath(wings, wingOutline, style = Stroke(width = outlineWidth))
            drawPath(wings, wingEdge, style = Stroke(width = edgeWidth))
        }
    }

    if (ornament.bolts) {
        val bolt = boltPath(wingUnit)
        val boltOutline = palette.dark.copy(alpha = 0.55f * alpha)
        drawPath(bolt, metal, alpha)
        drawPath(bolt, boltOutline, style = Stroke(width = 0.45f * wingUnit))
        scale(scaleX = -1f, scaleY = 1f, pivot = center) {
            drawPath(bolt, metal, alpha)
            drawPath(bolt, boltOutline, style = Stroke(width = 0.45f * wingUnit))
        }
    }

    // Hex frame + inset field with gem-like facets.
    val hex = hexagonPath(center.x, center.y, radius)
    val field = hexagonPath(center.x, center.y, fieldRadius)
    drawPath(hex, metal, alpha)
    drawPath(hex, palette.dark.copy(alpha = 0.9f * alpha), style = Stroke(width = 0.9f * unit))
    drawPath(field, metal, alpha)
    clipPath(field) {
        drawPath(
            trianglePath(unit, 32f, 14.8f, 46.9f, 23.4f, 32f, 32f),
            Color.White.copy(alpha = 0.13f * alpha)
        )
        drawPath(
            trianglePath(unit, 32f, 14.8f, 17.1f, 23.4f, 32f, 32f),
            Color.White.copy(alpha = 0.05f * alpha)
        )
        drawPath(
            trianglePath(unit, 17.1f, 40.6f, 32f, 49.2f, 32f, 32f),
            Color.Black.copy(alpha = 0.13f * alpha)
        )
        drawPath(
            trianglePath(unit, 46.9f, 40.6f, 32f, 49.2f, 32f, 32f),
            Color.Black.copy(alpha = 0.08f * alpha)
        )
    }
    drawPath(field, Color.Black.copy(alpha = 0.35f * alpha), style = Stroke(width = 1f * unit))
    drawPath(field, Color.White.copy(alpha = 0.22f * alpha), style = Stroke(width = 0.5f * unit))

    drawLine(
        color = Color.White.copy(alpha = 0.55f * alpha),
        start = Offset(center.x, center.y - radius + 0.4f * unit),
        end = Offset(center.x + 16.6f * unit, center.y - 10f * unit),
        strokeWidth = 0.8f * unit,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = 0.30f * alpha),
        start = Offset(center.x, center.y - radius + 0.4f * unit),
        end = Offset(center.x - 16.6f * unit, center.y - 10f * unit),
        strokeWidth = 0.8f * unit,
        cap = StrokeCap.Round
    )
}

@Composable
internal fun TierBadge(
    tier: String,
    unlocked: Boolean,
    badgeSize: Dp,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    iconTint: Color = Color.White,
    lockContentDescription: String? = null
) {
    val palette = if (unlocked) tierPalette(tier) else LockedPalette
    val alpha = if (unlocked) 1f else 0.55f

    Box(
        modifier = modifier.size(badgeSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawTierEmblem(palette, tierOrnament(tier), tierAccent(tier), alpha)
        }
        if (iconKey != null) {
            AchievementIcon(
                key = iconKey,
                tint = iconTint,
                modifier = Modifier.size(badgeSize * 0.42f)
            )
        }
        if (!unlocked && lockContentDescription != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-8).dp, y = (-14).dp)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = lockContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private val TIER_ORDER = listOf("iron", "copper", "silver", "gold", "emerald", "diamond")

/** Same emerald used by the emerald achievement tier — marks a reached goal. */
private val GoalAccent = Color(0xFF2ECC71)

@StringRes
internal fun tierLabel(tier: String): Int = when (tier) {
    "iron" -> R.string.tier_iron
    "copper" -> R.string.tier_copper
    "silver" -> R.string.tier_silver
    "gold" -> R.string.tier_gold
    "emerald" -> R.string.tier_emerald
    "diamond" -> R.string.tier_diamond
    else -> R.string.tier_iron
}

@Composable
fun AchievementsScreen(
    repository: Tr3ackRepository,
    onOpenRankLadder: () -> Unit = {}
) {
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
        item {
            RankLadderEntry(onClick = onOpenRankLadder)
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
            onSave = { exerciseId, type, target, reps ->
                viewModel.saveGoal(exerciseId, type, target, reps)
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
    val repsGoal = goal.goal.type == GoalType.WEIGHT && goal.goal.targetReps > 0

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
                text = if (repsGoal) {
                    stringResource(R.string.goals_working_weight_reps, goal.goal.targetReps)
                } else {
                    stringResource(goalTypeLabel(goal.goal.type))
                },
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
                    text = if (repsGoal) {
                        stringResource(
                            R.string.goals_weight_progress_value,
                            formatGoalNumber(goal.current),
                            formatGoalNumber(goal.goal.targetValue)
                        )
                    } else {
                        stringResource(
                            R.string.goals_progress_value,
                            formatGoalValue(goal.goal.type, goal.current),
                            formatGoalValue(goal.goal.type, goal.goal.targetValue)
                        )
                    },
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
            if (repsGoal) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.goals_reps_progress_value,
                        goal.currentReps,
                        goal.goal.targetReps
                    ),
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
    onSave: (Long, GoalType, Double, Int) -> Unit,
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
    var repsText by remember {
        mutableStateOf(existing?.goal?.targetReps?.takeIf { it > 0 }?.toString() ?: "8")
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
    val parsedReps = repsText.toIntOrNull()
    val repsRequired = effectiveType == GoalType.WEIGHT
    val canSave = selectedExerciseId != null && parsedTarget != null && parsedTarget > 0.0 &&
        (!repsRequired || (parsedReps != null && parsedReps > 0))

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

                if (repsRequired) {
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { newValue -> repsText = newValue.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.goals_target_reps_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (canSave) {
                        onSave(
                            selectedExerciseId!!,
                            effectiveType,
                            parsedTarget!!,
                            if (effectiveType == GoalType.WEIGHT) parsedReps!! else 0
                        )
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
    else -> "${formatGoalNumber(value)} kg"
}

private fun formatGoalNumber(value: Double): String = "%.1f".format(value)

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
private fun RankLadderEntry(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierBadge(
                tier = "diamond",
                unlocked = true,
                badgeSize = 44.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rank_ladder_entry),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.rank_ladder_entry_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                TierBadge(
                    tier = tier,
                    unlocked = true,
                    badgeSize = 28.dp
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
    // Same hue as the badge, sunk into the dark surface so the metal still pops.
    val bgColor = if (achievement.unlocked) {
        lerp(MaterialTheme.colorScheme.surface, unlockedColor, 0.32f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (achievement.unlocked) {
        BorderStroke(1.dp, unlockedColor.copy(alpha = 0.35f))
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierBadge(
                tier = achievement.tier,
                unlocked = achievement.unlocked,
                badgeSize = 72.dp,
                iconKey = achievement.iconKey,
                iconTint = if (achievement.unlocked) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
                lockContentDescription = stringResource(R.string.ach_locked)
            )
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