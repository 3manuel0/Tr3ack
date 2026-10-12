package com.example.tr3ack.data.entity

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

/**
 * Shared 1RM estimation coefficients. Kept out of the ViewModels so the stats
 * cache (which is rebuilt in the repository) uses exactly the same maths as the
 * screens that render the results.
 */
object MovementFactors {
    /**
     * Reps above this are still logged and counted in tonnage, but their 1RM
     * coefficient is computed from [MAX_E1RM_REPS] (the capped value). The set is
     * kept eligible for best-set selection at that capped value rather than being
     * excluded. Bump this constant to change the cap everywhere.
     */
    const val MAX_E1RM_REPS = 12

    /** Slope used for movements with no coefficient table. */
    private const val FALLBACK_SLOPE = 0.0333

    /** Epley divisor for free-weight E1RM: weight * (1 + reps / EPLEY_DIVISOR). */
    const val EPLEY_DIVISOR = 30.0

    private val DIP_COEFFICIENTS = doubleArrayOf(
        1.000, 1.035, 1.068, 1.100, 1.130,
        1.160, 1.190, 1.220, 1.250, 1.280
    )

    private val PULLUP_COEFFICIENTS = doubleArrayOf(
        1.000, 1.038, 1.073, 1.108, 1.142,
        1.175, 1.208, 1.240, 1.272, 1.304
    )

    private val CHINUP_COEFFICIENTS = doubleArrayOf(
        1.000, 1.042, 1.082, 1.120, 1.158,
        1.194, 1.229, 1.263, 1.296, 1.328
    )

    fun getMovementType(exerciseName: String): MovementType {
        val lower = exerciseName.lowercase()
        return when {
            lower.contains("dip") -> MovementType.DIPS
            lower.contains("chin") -> MovementType.CHIN_UPS
            lower.contains("pull") -> MovementType.PULLUPS
            else -> MovementType.UNKNOWN
        }
    }

    fun getMovementFactor(movementType: MovementType, reps: Int): Double {
        val effectiveReps = reps.coerceIn(1, MAX_E1RM_REPS)
        return when (movementType) {
            MovementType.DIPS -> extrapolate(DIP_COEFFICIENTS, effectiveReps)
            MovementType.PULLUPS -> extrapolate(PULLUP_COEFFICIENTS, effectiveReps)
            MovementType.CHIN_UPS -> extrapolate(CHINUP_COEFFICIENTS, effectiveReps)
            MovementType.UNKNOWN -> 1.0 + (effectiveReps - 1) * FALLBACK_SLOPE
        }
    }

    /**
     * Free-weight E1RM via the Epley formula. A true single (reps == 1) is already
     * a maximal effort, so it returns the weight unchanged instead of the 3.3%
     * inflation the formula would otherwise add. [reps] is assumed >= 1.
     */
    fun freeWeightE1RM(weight: Double, reps: Int): Double =
        if (reps <= 1) weight else weight * (1.0 + reps / EPLEY_DIVISOR)

    /**
     * Coefficient for [effectiveReps]. Rep counts past the end of [table] continue
     * with the table's final slope, so the curve stays continuous at its last entry
     * instead of flatlining or jumping to a different curve. [effectiveReps] is
     * assumed to be within 1..[MAX_E1RM_REPS].
     */
    private fun extrapolate(table: DoubleArray, effectiveReps: Int): Double {
        if (effectiveReps <= table.size) return table[effectiveReps - 1]
        val slope = table[table.size - 1] - table[table.size - 2]
        return table[table.size - 1] + (effectiveReps - table.size) * slope
    }
}