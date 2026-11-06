package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds

/** Result of placing a best performance on an exercise's tier ladder. */
data class RankEvaluation(
    val currentTier: String?,
    /** Ordered iron → diamond. */
    val tiers: List<RankTier>,
    val nextThreshold: Double?,
    val progress: Float,
)

/**
 * Pure rank-ladder logic, kept out of the ViewModel so it can be unit-tested.
 * Thresholds for curls and lateral raises match the existing achievements;
 * bodyweight movements use total-system-weight ÷ bodyweight.
 */
object RankLadderRules {

    val bodyweightThresholds = listOf(1.0, 1.10, 1.25, 1.50, 1.75, 2.0)
    val curlThresholds = listOf(16.0, 18.0, 20.0, 22.0, 24.0, 26.0)
    val lateralRaiseThresholds = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)

    fun thresholdsFor(exercise: Exercise): List<Double> = when (exercise.id) {
        ExerciseIds.BICEP_CURLS -> curlThresholds
        ExerciseIds.LATERAL_RAISES -> lateralRaiseThresholds
        else -> if (exercise.isBodyweightBased) bodyweightThresholds else emptyList()
    }

    fun evaluate(thresholds: List<Double>, best: Double): RankEvaluation {
        require(thresholds.isNotEmpty()) { "A rank ladder needs at least one tier" }
        val tierNames = AchievementRules.colorTiers
        val currentIndex = thresholds.indexOfLast { best >= it }
        val currentThreshold = thresholds.getOrNull(currentIndex)
        val nextThreshold = thresholds.getOrNull(currentIndex + 1)
        val progress = when {
            currentIndex < 0 -> (best / thresholds.first()).coerceIn(0.0, 1.0)
            nextThreshold == null || currentThreshold == null -> 1.0
            else -> ((best - currentThreshold) / (nextThreshold - currentThreshold)).coerceIn(0.0, 1.0)
        }.toFloat()

        return RankEvaluation(
            currentTier = tierNames.getOrNull(currentIndex),
            tiers = thresholds.mapIndexed { index, threshold ->
                RankTier(
                    tier = tierNames[index],
                    threshold = threshold,
                    reached = best >= threshold,
                    isCurrent = index == currentIndex,
                )
            },
            nextThreshold = nextThreshold,
            progress = progress,
        )
    }
}
