package com.example.tr3ack.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.Achievement
import com.example.tr3ack.viewmodel.AchievementsViewModel

private fun achievementIcon(key: String): ImageVector = when (key) {
    "whatshot" -> Icons.Default.Whatshot
    "bolt" -> Icons.Default.Bolt
    "workspace_premium" -> Icons.Default.WorkspacePremium
    "fitness_center" -> Icons.Default.FitnessCenter
    "directions_run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "emoji_events" -> Icons.Default.EmojiEvents
    "local_fire_department" -> Icons.Default.LocalFireDepartment
    "shield" -> Icons.Default.Shield
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    "barbell" -> Icons.Default.MonitorWeight
    "rocket_launch" -> Icons.Default.RocketLaunch
    else -> Icons.Default.Star
}

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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            LevelCard(level)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            StreakCard(streak, totalTonnage, totalSessions)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(achievements) { achievement ->
            AchievementCell(achievement)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
            Spacer(modifier = Modifier.height(80.dp))
        }
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
            style = MaterialTheme.typography.headlineMedium,
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
private fun AchievementCell(achievement: Achievement) {
    val icon = achievementIcon(achievement.iconKey)
    val unlockedColor = MaterialTheme.colorScheme.primary
    val lockedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bgColor = if (achievement.unlocked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(if (achievement.unlocked) unlockedColor else lockedColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (achievement.unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (achievement.unlocked) { "" } else { achievement.description },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
