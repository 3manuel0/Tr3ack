package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One rung on an exercise's Iron → Diamond ladder. */
data class RankTier(
    val tier: String,
    val threshold: Double,
    val reached: Boolean,
    val isCurrent: Boolean,
)

/** The full ladder for a single exercise, derived from the cached stats. */
data class ExerciseRank(
    val exerciseId: Long,
    val name: String,
    val iconKey: String,
    val isBodyweight: Boolean,
    val bestValue: Double,
    val currentTier: String?,
    /** Ordered iron → diamond. */
    val tiers: List<RankTier>,
    val nextThreshold: Double?,
    val progress: Float,
)

/**
 * Builds per-exercise rank ladders from the materialized stats cache. No new
 * persistence: the rank is always derived from the same data as the charts and
 * achievements, so it survives restarts and imports automatically.
 */
class RankLadderViewModel(repository: Tr3ackRepository) : ViewModel() {

    val ranks: StateFlow<List<ExerciseRank>> =
        combine(repository.allExercises, repository.exerciseStats) { exercises, stats ->
            val statsById = stats.associateBy { it.exerciseId }
            exercises
                .sortedBy { it.id }
                .mapNotNull { exercise ->
                    val thresholds = RankLadderRules.thresholdsFor(exercise)
                    if (thresholds.isEmpty()) return@mapNotNull null

                    val best = statsById[exercise.id]?.let { stat ->
                        if (exercise.isBodyweightBased) {
                            stat.maxBodyweightRatio
                        } else {
                            stat.maxAddedWeightGte6
                        }
                    } ?: 0.0
                    val evaluation = RankLadderRules.evaluate(thresholds, best)

                    ExerciseRank(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        iconKey = iconKeyFor(exercise),
                        isBodyweight = exercise.isBodyweightBased,
                        bestValue = best,
                        currentTier = evaluation.currentTier,
                        tiers = evaluation.tiers,
                        nextThreshold = evaluation.nextThreshold,
                        progress = evaluation.progress,
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun iconKeyFor(exercise: Exercise): String = when (exercise.id) {
        ExerciseIds.BICEP_CURLS -> "bicep"
        ExerciseIds.LATERAL_RAISES -> "shoulder"
        else -> "pullup"
    }
}
