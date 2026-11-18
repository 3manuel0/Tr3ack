package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.WorkoutSet

/** Heaviest added weight lifted for a specific rep count. */
data class RepMax(
    val reps: Int,
    val addedWeightKg: Double
)

/**
 * Builds the rep-max table: for each rep count from 1–15 that has data, the
 * heaviest added weight logged at exactly that many reps.
 */
object RepMaxRules {

    fun build(sets: List<WorkoutSet>): List<RepMax> =
        sets
            .filter { it.reps in 1..15 }
            .groupBy { it.reps }
            .map { (reps, group) -> RepMax(reps, group.maxOf { it.addedWeightKg }) }
            .sortedBy { it.reps }
}
