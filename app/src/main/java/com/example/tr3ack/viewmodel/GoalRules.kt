package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.GoalType

/**
 * A goal paired with the exercise's current best value, ready for display.
 * [progress] is clamped to 0f..1f; [achieved] is the unclamped truth.
 */
data class GoalProgress(
    val goal: Goal,
    val current: Double,
    val progress: Float,
    val achieved: Boolean
)

/**
 * Derives goal progress from the materialized stats cache. All reads come from
 * [ExerciseStatsEntity] so the Goals tab never has to scan raw sets.
 */
object GoalRules {

    /** [GoalType.BW_RATIO] only makes sense for bodyweight-based movements. */
    fun availableTypes(isBodyweightBased: Boolean): List<GoalType> =
        if (isBodyweightBased) {
            listOf(GoalType.E1RM, GoalType.WEIGHT, GoalType.BW_RATIO)
        } else {
            listOf(GoalType.E1RM, GoalType.WEIGHT)
        }

    fun of(goal: Goal, stats: ExerciseStatsEntity?): GoalProgress {
        val current = when (goal.type) {
            GoalType.E1RM -> stats?.bestE1RM ?: 0.0
            GoalType.WEIGHT -> stats?.maxAddedWeightGte6 ?: 0.0
            GoalType.BW_RATIO -> stats?.maxBodyweightRatio ?: 0.0
        }
        val target = goal.targetValue
        val progress = if (target <= 0.0) {
            0f
        } else {
            (current / target).coerceIn(0.0, 1.0).toFloat()
        }
        return GoalProgress(
            goal = goal,
            current = current,
            progress = progress,
            achieved = target > 0.0 && current >= target
        )
    }
}
