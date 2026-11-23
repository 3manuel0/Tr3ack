package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.MovementFactors

/** Predicted added weight at a given rep count. */
data class RepMax(
    val reps: Int,
    val addedWeightKg: Double
)

/**
 * Builds the "predicted maxes" table: from the exercise's estimated 1RM, what
 * weight could the lifter move at each common rep range. This inverts the same
 * equation used to derive the 1RM, so it is a prediction, not a best-so-far.
 *
 * [addedWeightKg] is the number to actually load: the lifted weight for free
 * weights, or the belt/added weight (predicted total system load minus body
 * weight) for weighted-bodyweight movements.
 */
object RepMaxRules {

    private val BODYWEIGHT_REPS = listOf(3, 5, 6, 8, 10, 12)
    private val FREE_WEIGHT_REPS = listOf(3, 5, 6, 8, 10, 12, 15)

    fun predict(e1rm: Double, isBodyweightBased: Boolean, bodyWeightKg: Double): List<RepMax> {
        if (e1rm <= 0.0) return emptyList()
        val reps = if (isBodyweightBased) BODYWEIGHT_REPS else FREE_WEIGHT_REPS
        return reps.map { rep ->
            val totalSystem = MovementFactors.predictedWeightAt(e1rm, rep, isBodyweightBased)
            val added = if (isBodyweightBased && bodyWeightKg > 0.0) {
                (totalSystem - bodyWeightKg).coerceAtLeast(0.0)
            } else {
                totalSystem
            }
            RepMax(rep, added)
        }
    }
}
