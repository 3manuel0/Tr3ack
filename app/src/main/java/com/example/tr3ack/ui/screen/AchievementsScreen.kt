package com.example.tr3ack.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.Achievement
import com.example.tr3ack.viewmodel.AchievementsViewModel

private fun achievementIcon(key: String): ImageVector = when (key) {
    "whatshot" -> Icons.Default.Whatshot
    "bolt" -> Icons.Default.Bolt
    "workspace_premium" -> Icons.Default.WorkspacePremium
    "directions_run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "emoji_events" -> Icons.Default.EmojiEvents
    "local_fire_department" -> Icons.Default.LocalFireDepartment
    "shield" -> Icons.Default.Shield
    "check_circle" -> Icons.Default.CheckCircleOutline
    else -> Icons.Default.Star
}

private val CANVAS_KEYS = setOf("pullup", "bicep", "shoulder")

@Composable
private fun AchievementIcon(key: String, modifier: Modifier = Modifier, tint: Color = Color.White) {
    if (key in CANVAS_KEYS) {
        Canvas(modifier = modifier) {
            val stroke = size.minDimension * 0.06f
            when (key) {
                "pullup" -> drawPullUp(tint, stroke)
                "bicep" -> drawBicepCurl(tint, stroke)
                else -> drawLateralRaise(tint, stroke)
            }
        }
    } else {
        Icon(
            imageVector = achievementIcon(key),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

private fun DrawScope.drawLineNorm(
    color: Color,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    stroke: Float
) {
    drawLine(
        color = color,
        start = Offset(x1 * size.width, y1 * size.height),
        end = Offset(x2 * size.width, y2 * size.height),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawCircleNorm(color: Color, cx: Float, cy: Float, r: Float, stroke: Float) {
    drawCircle(
        color = color,
        radius = r * size.minDimension,
        center = Offset(cx * size.width, cy * size.height),
        style = Stroke(stroke)
    )
}

private fun DrawScope.drawPullUp(color: Color, stroke: Float) {
    drawLineNorm(color, 0.12f, 0.14f, 0.88f, 0.14f, stroke)  // bar
    drawCircleNorm(color, 0.5f, 0.34f, 0.10f, stroke)        // head
    drawLineNorm(color, 0.39f, 0.49f, 0.36f, 0.14f, stroke)   // left arm
    drawLineNorm(color, 0.61f, 0.49f, 0.64f, 0.14f, stroke)   // right arm
    drawLineNorm(color, 0.5f, 0.49f, 0.5f, 0.74f, stroke)     // torso
    drawLineNorm(color, 0.5f, 0.74f, 0.36f, 0.91f, stroke)    // left leg
    drawLineNorm(color, 0.5f, 0.74f, 0.64f, 0.91f, stroke)    // right leg
}

private fun DrawScope.drawBicepCurl(color: Color, stroke: Float) {
    drawCircleNorm(color, 0.5f, 0.17f, 0.08f, stroke)          // head
    drawLineNorm(color, 0.5f, 0.28f, 0.5f, 0.72f, stroke)      // torso
    drawLineNorm(color, 0.5f, 0.72f, 0.38f, 0.89f, stroke)     // left leg
    drawLineNorm(color, 0.5f, 0.72f, 0.62f, 0.89f, stroke)     // right leg
    drawLineNorm(color, 0.48f, 0.34f, 0.64f, 0.30f, stroke)    // upper arm
    drawLineNorm(color, 0.64f, 0.30f, 0.78f, 0.44f, stroke)    // forearm (curling up)
    drawLineNorm(color, 0.78f, 0.36f, 0.78f, 0.52f, stroke)    // dumbbell handle
    drawLineNorm(color, 0.73f, 0.38f, 0.73f, 0.50f, stroke)    // inner plate
    drawLineNorm(color, 0.83f, 0.38f, 0.83f, 0.50f, stroke)    // outer plate
}

private fun DrawScope.drawLateralRaise(color: Color, stroke: Float) {
    drawCircleNorm(color, 0.5f, 0.16f, 0.09f, stroke)          // head
    drawLineNorm(color, 0.5f, 0.28f, 0.5f, 0.70f, stroke)      // torso
    drawLineNorm(color, 0.5f, 0.70f, 0.38f, 0.90f, stroke)     // left leg
    drawLineNorm(color, 0.5f, 0.70f, 0.62f, 0.90f, stroke)     // right leg
    drawLineNorm(color, 0.5f, 0.34f, 0.14f, 0.34f, stroke)     // left arm out
    drawLineNorm(color, 0.5f, 0.34f, 0.86f, 0.34f, stroke)     // right arm out
    drawLineNorm(color, 0.08f, 0.28f, 0.08f, 0.40f, stroke)    // left dumbbell
    drawLineNorm(color, 0.92f, 0.28f, 0.92f, 0.40f, stroke)    // right dumbbell
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

@Composable
fun AchievementsScreen(repository: Tr3ackRepository) {
    val viewModel: AchievementsViewModel = remember {
        AchievementsViewModel(repository)
    }

    val level by viewModel.level.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val totalTonnage by viewModel.totalTonnage.collectAsState()
    val totalSessions by viewModel.totalSessions.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val unlockedCount = achievements.count { it.unlocked }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            LevelCard(level)
        }
        item {
            StreakCard(streak, totalTonnage, totalSessions)
        }
        item {
            Text(
                text = "Achievements ($unlockedCount/${achievements.size} unlocked)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            TierLegend()
        }
        items(achievements) { achievement ->
            AchievementRow(achievement)
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

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
                text = "Level",
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
                        text = level.levelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${level.currentXp} XP to Level ${level.level + 1}",
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
            StreakStat("Current", "${streak.current}", "days")
            StreakStat("Longest", "${streak.longest}", "days")
            StreakStat("Workouts", "$totalSessions", "sessions")
            StreakStat("Volume", formatTonnage(totalTonnage), "kg·r")
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
                    text = tier.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val unlockedColor = tierColor(achievement.tier)
    val lockedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bgColor = if (achievement.unlocked) {
        unlockedColor.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = achievement.title,
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
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}