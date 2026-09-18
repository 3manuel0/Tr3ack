package com.example.tr3ack.ui.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.Achievement
import com.example.tr3ack.viewmodel.AchievementsViewModel

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
                text = stringResource(R.string.goals_header, unlockedCount, achievements.size),
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