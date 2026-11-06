package com.example.tr3ack.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tr3ack.R
import com.example.tr3ack.repository.Tr3ackRepository
import com.example.tr3ack.viewmodel.ExerciseRank
import com.example.tr3ack.viewmodel.RankLadderViewModel
import com.example.tr3ack.viewmodel.RankTier
import kotlin.math.roundToInt

@Composable
fun RankLadderScreen(repository: Tr3ackRepository) {
    val viewModel: RankLadderViewModel = viewModel { RankLadderViewModel(repository) }
    val ranks by viewModel.ranks.collectAsStateWithLifecycle()

    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    LaunchedEffect(ranks) {
        if (ranks.isNotEmpty() && ranks.none { it.exerciseId == selectedId }) {
            selectedId = ranks.first().exerciseId
        }
    }
    val selected = ranks.find { it.exerciseId == selectedId } ?: ranks.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ranks, key = { it.exerciseId }) { rank ->
                FilterChip(
                    selected = rank.exerciseId == selectedId,
                    onClick = { selectedId = rank.exerciseId },
                    label = { Text(shortName(rank.name)) }
                )
            }
        }

        if (selected != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { RankHero(selected) }
                items(selected.tiers.reversed(), key = { it.tier }) { tier ->
                    RankRow(selected, tier)
                }
            }
        }
    }
}

@Composable
private fun RankHero(rank: ExerciseRank) {
    val currentTier = rank.currentTier
    val accent = if (currentTier != null) tierColor(currentTier) else MaterialTheme.colorScheme.outline
    val iconTint = if (currentTier != null) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (currentTier != null) {
                lerp(MaterialTheme.colorScheme.surface, accent, 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.rank_your_rank).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TierBadge(
                tier = currentTier ?: "iron",
                unlocked = currentTier != null,
                badgeSize = 104.dp,
                iconKey = rank.iconKey,
                iconTint = iconTint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (currentTier != null) {
                    stringResource(tierLabel(currentTier)).uppercase()
                } else {
                    stringResource(R.string.rank_unranked).uppercase()
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (currentTier != null) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${rank.name} · ${bestLabel(rank)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { rank.progress },
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.10f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nextLabel(rank),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankRow(rank: ExerciseRank, tier: RankTier) {
    val accent = tierColor(tier.tier)
    val background = if (tier.reached) {
        lerp(MaterialTheme.colorScheme.surface, accent, 0.30f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val border = when {
        tier.isCurrent -> BorderStroke(1.dp, accent.copy(alpha = 0.85f))
        tier.reached -> BorderStroke(1.dp, accent.copy(alpha = 0.30f))
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background),
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierBadge(
                tier = tier.tier,
                unlocked = tier.reached,
                badgeSize = 54.dp,
                iconKey = rank.iconKey,
                iconTint = if (tier.reached) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(tierLabel(tier.tier)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (tier.isCurrent) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = requirementLabel(tier, rank.isBodyweight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                tier.isCurrent -> StateChip(
                    text = stringResource(R.string.rank_you_are_here),
                    color = accent
                )
                tier.reached -> Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = tierColor(tier.tier)
                )
                else -> Text(
                    text = stringResource(R.string.rank_locked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StateChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun bestLabel(rank: ExerciseRank): String =
    if (rank.isBodyweight) {
        stringResource(R.string.rank_best_bw, rank.bestValue)
    } else {
        stringResource(R.string.rank_best_weight, rank.bestValue.toInt())
    }

@Composable
private fun requirementLabel(tier: RankTier, isBodyweight: Boolean): String =
    if (isBodyweight) {
        stringResource(R.string.rank_requirement_bw, tier.threshold)
    } else {
        stringResource(R.string.rank_requirement_weight, tier.threshold.toInt())
    }

@Composable
private fun nextLabel(rank: ExerciseRank): String {
    val next = rank.tiers.firstOrNull { !it.reached }
    return when {
        next == null -> stringResource(R.string.rank_max_tier)
        rank.currentTier == null -> stringResource(
            R.string.rank_next_unranked,
            stringResource(tierLabel(next.tier)),
            requirementLabel(next, rank.isBodyweight)
        )
        rank.isBodyweight -> stringResource(
            R.string.rank_to_next_bw,
            (next.threshold - rank.bestValue).coerceAtLeast(0.0),
            stringResource(tierLabel(next.tier))
        )
        else -> stringResource(
            R.string.rank_to_next_weight,
            (next.threshold - rank.bestValue).roundToInt().coerceAtLeast(0),
            stringResource(tierLabel(next.tier))
        )
    }
}

/** The mock uses compact chip labels, e.g. "Weighted Pull-Ups" → "Pull-Ups". */
private fun shortName(name: String): String = name
    .replace("Weighted ", "")
    .removePrefix("Bicep ")
    .removeSuffix(" Raises")
