package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.GoalType
import com.example.tr3ack.data.entity.WorkoutSet

/**
 * A goal paired with the exercise's current best value, ready for display.
 * [progress] is clamped to 0f..1f; [achieved] is the unclamped truth.
 * [currentReps] is the reps half of a "working weight × reps" goal (0 otherwise),
 * i.e. the most reps done at or above the target weight.
 */
data class GoalProgress(
    val goal: Goal,
    val current: Double,
    val progress: Float,
    val achieved: Boolean,
    val currentReps: Int = 0
)

/**
 * Derives goal progress from the materialized stats cache. Simple goals read a
 * single value from [ExerciseStatsEntity]; a working-weight goal with a rep target
 * additionally scores the heaviest set done for that many reps (the [sets] passed
 * in for its exercise) so both the weight and the reps drive progress.
 */
object GoalRules {

    /** [GoalType.BW_RATIO] only makes sense for bodyweight-based movements. */
    fun availableTypes(isBodyweightBased: Boolean): List<GoalType> =
        if (isBodyweightBased) {
            listOf(GoalType.E1RM, GoalType.WEIGHT, GoalType.BW_RATIO)
        } else {
            listOf(GoalType.E1RM, GoalType.WEIGHT)
        }

    fun of(
        goal: Goal,
        stats: ExerciseStatsEntity?,
        sets: List<WorkoutSet> = emptyList()
    ): GoalProgress = when (goal.type) {
        GoalType.E1RM -> singleValue(goal, stats?.bestE1RM ?: 0.0)
        GoalType.BW_RATIO -> singleValue(goal, stats?.maxBodyweightRatio ?: 0.0)
        GoalType.WEIGHT ->
            if (goal.targetReps > 0) weightWithReps(goal, sets)
            else singleValue(goal, stats?.maxAddedWeightGte6 ?: 0.0)
    }

    private fun singleValue(goal: Goal, current: Double): GoalProgress {
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

    /**
     * "Working weight × reps": progress is the average of how heavy you've gone for
     * the target reps and how many reps you've done at the target weight. Achieved
     * once a single set hits both the weight and the rep target.
     */
    private fun weightWithReps(goal: Goal, sets: List<WorkoutSet>): GoalProgress {
        val targetWeight = goal.targetValue
        val targetReps = goal.targetReps
        val bestWeight = sets.filter { it.reps >= targetReps }.maxOfOrNull { it.addedWeightKg } ?: 0.0
        val bestReps = sets.filter { it.addedWeightKg >= targetWeight }.maxOfOrNull { it.reps } ?: 0

        val weightRatio = if (targetWeight > 0.0) bestWeight / targetWeight else 0.0
        val repsRatio = if (targetReps > 0) bestReps.toDouble() / targetReps else 0.0
        val progress = ((weightRatio + repsRatio) / 2.0).coerceIn(0.0, 1.0).toFloat()

        return GoalProgress(
            goal = goal,
            current = bestWeight,
            progress = progress,
            achieved = weightRatio >= 1.0 && repsRatio >= 1.0,
            currentReps = bestReps
        )
    }
}
