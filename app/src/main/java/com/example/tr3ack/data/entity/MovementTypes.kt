package com.example.tr3ack.data.entity

import kotlin.math.exp

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

/**
 * Shared 1RM estimation coefficients. Kept out of the ViewModels so the stats
 * cache (which is rebuilt in the repository) uses exactly the same maths as the
 * screens that render the results.
 */
object MovementFactors {
    /**
     * Free-weight reps above this are still logged and counted in tonnage, but
     * their E1RM uses the [MAX_FREE_WEIGHT_REPS] value. Epley/Brzycki both get
     * unreliable much past this, so the estimate stops here rather than drifting.
     */
    const val MAX_FREE_WEIGHT_REPS = 12

    /**
     * Upper bound for the bodyweight coefficient curve. The saturating tail below
     * plateaus well before this, so the exact value only guards against absurd
     * rep counts. Reps above it reuse the [MAX_BODYWEIGHT_REPS] coefficient.
     */
    const val MAX_BODYWEIGHT_REPS = 30

    /** Slope used for movements with no coefficient table. */
    private const val FALLBACK_SLOPE = 0.0333

    /** Epley divisor for free-weight E1RM: weight * (1 + reps / EPLEY_DIVISOR). */
    const val EPLEY_DIVISOR = 30.0

    /** Brzycki denominator: weight * 36 / (BRZYCKI_OVER - reps). */
    const val BRZYCKI_OVER = 37.0

    /**
     * Most a bodyweight coefficient can gain beyond the last table entry. Past the
     * table the curve saturates toward `last + TAIL_EXTRA`, so very high-rep sets
     * keep growing but flatten out instead of climbing linearly without bound.
     */
    private const val TAIL_EXTRA = 0.10

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
        val table = when (movementType) {
            MovementType.DIPS -> DIP_COEFFICIENTS
            MovementType.PULLUPS -> PULLUP_COEFFICIENTS
            MovementType.CHIN_UPS -> CHINUP_COEFFICIENTS
            MovementType.UNKNOWN -> null
        }
        // Unknown movements have no table; fall back to the conservative linear
        // estimate and cap it like a free weight rather than letting it run high.
        if (table == null) {
            val effectiveReps = reps.coerceIn(1, MAX_FREE_WEIGHT_REPS)
            return 1.0 + (effectiveReps - 1) * FALLBACK_SLOPE
        }
        val effectiveReps = reps.coerceIn(1, MAX_BODYWEIGHT_REPS)
        return if (effectiveReps <= table.size) table[effectiveReps - 1]
        else saturatingTail(table, effectiveReps)
    }

    /**
     * Free-weight E1RM as the average of the Epley and Brzycki formulas, using reps
     * clamped to 1..[MAX_FREE_WEIGHT_REPS]. Averaging the two cancels part of each
     * formula's bias (Epley runs high at low reps, Brzycki runs high at high reps),
     * and they already agree exactly at 10 reps. A true single (reps == 1) is a
     * maximal effort, so it returns the weight unchanged rather than the small
     * inflation the formula would add.
     */
    fun freeWeightE1RM(weight: Double, reps: Int): Double {
        val effectiveReps = reps.coerceIn(1, MAX_FREE_WEIGHT_REPS)
        if (effectiveReps <= 1) return weight
        val epley = weight * (1.0 + effectiveReps / EPLEY_DIVISOR)
        val brzycki = weight * 36.0 / (BRZYCKI_OVER - effectiveReps)
        return (epley + brzycki) / 2.0
    }

    /**
     * Coefficient for rep counts beyond the end of [table]. The curve starts from
     * the table's last value with the table's final slope (so it is continuous at
     * the boundary) and saturates toward `last + [TAIL_EXTRA]`, giving progressively
     * diminishing returns instead of unbounded linear growth.
     */
    private fun saturatingTail(table: DoubleArray, effectiveReps: Int): Double {
        val last = table[table.size - 1]
        val slope = last - table[table.size - 2]
        val extra = effectiveReps - table.size
        val gained = TAIL_EXTRA * (1.0 - exp(-extra * slope / TAIL_EXTRA))
        return last + gained
    }
}