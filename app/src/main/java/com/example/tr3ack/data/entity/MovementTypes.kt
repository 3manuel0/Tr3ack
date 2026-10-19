package com.example.tr3ack.data.entity

import kotlin.math.exp

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

/**
 * Shared 1RM estimation coefficients. Kept out of the ViewModels so the stats
 * cache (which is rebuilt in the repository) uses exactly the same maths as the
 * screens that render the results.
 *
 * Both free-weight and weighted-bodyweight estimates come from the same Mayhew
 * et al. (1992) equation — free weights apply it to the lifted weight, bodyweight
 * movements to the total system weight (TSL = body weight + added weight).
 */
object MovementFactors {
    /**
     * Reps above this are still logged and counted in tonnage, but their E1RM
     * uses the [MAX_FREE_WEIGHT_REPS] value. Mayhew was validated on sets of
     * 1..15 reps (and is the formula that holds up best past 10 reps, where
     * Epley/Brzycki-style linear models start drifting), so the estimate stops
     * at 15 rather than extrapolating into the unreliable range.
     */
    const val MAX_FREE_WEIGHT_REPS = 15

    /**
     * Bodyweight counterpart of [MAX_FREE_WEIGHT_REPS]: Mayhew's validated range
     * is 1..15 reps, so TSL-based estimates clamp there too (previously the
     * coefficient tables ran to 30 with a saturating tail). Reps above it reuse
     * the 15-rep factor; the set still counts toward tonnage.
     */
    const val MAX_BODYWEIGHT_REPS = 15

    /** Mayhew base term: 1RM = 100 * weight / (MAYHEW_BASE + MAYHEW_COEFF * e^(-MAYHEW_DECAY * reps)). */
    const val MAYHEW_BASE = 52.2

    /** Mayhew exponential coefficient, fitted on reps-to-failure data (Mayhew et al., 1992). */
    const val MAYHEW_COEFF = 41.9

    /** Mayhew decay rate per repetition. */
    const val MAYHEW_DECAY = 0.055

    fun getMovementType(exerciseName: String): MovementType {
        val lower = exerciseName.lowercase()
        return when {
            lower.contains("dip") -> MovementType.DIPS
            lower.contains("chin") -> MovementType.CHIN_UPS
            lower.contains("pull") -> MovementType.PULLUPS
            else -> MovementType.UNKNOWN
        }
    }

    /**
     * TSL-to-E1RM factor for a bodyweight movement, using reps clamped to
     * 1..[MAX_BODYWEIGHT_REPS]:
     *
     *     factor = 100 / (MAYHEW_BASE + MAYHEW_COEFF × e^(-MAYHEW_DECAY × reps))
     *
     * Identical for every movement (the old per-movement coefficient tables were
     * dropped in favour of the validated formula: on logged data they sat ~2–3%
     * below Mayhew and were no more internally consistent). A true single
     * (reps == 1) is a maximal effort, so the factor is 1.0 — E1RM equals the raw
     * TSL, no inflation.
     */
    fun tswFactor(reps: Int): Double {
        val effectiveReps = reps.coerceIn(1, MAX_BODYWEIGHT_REPS)
        if (effectiveReps <= 1) return 1.0
        return mayhewFactor(effectiveReps)
    }

    /**
     * Free-weight E1RM from the Mayhew et al. (1992) equation, using reps clamped
     * to 1..[MAX_FREE_WEIGHT_REPS]:
     *
     *     1RM = 100 × weight / (MAYHEW_BASE + MAYHEW_COEFF × e^(-MAYHEW_DECAY × reps))
     *
     * Mayhew is an exponential curve rather than a linear one, so it keeps
     * estimating sensibly above 10 reps where Epley/Brzycki start to drift, and in
     * head-to-head testing (LeSuer et al., 1997) it was one of only two formulas
     * not significantly different from the measured 1RM. It runs slightly below
     * plain Epley at moderate reps (e.g. 19 kg × 9 → 24.44 vs 24.70), which is the
     * conservative direction for light isolation work. A true single (reps == 1)
     * is a maximal effort, so it returns the weight unchanged rather than the
     * small inflation the formula would add.
     */
    fun freeWeightE1RM(weight: Double, reps: Int): Double {
        val effectiveReps = reps.coerceIn(1, MAX_FREE_WEIGHT_REPS)
        if (effectiveReps <= 1) return weight
        return weight * mayhewFactor(effectiveReps)
    }

    /** Mayhew factor for an already-clamped rep count (>= 2). */
    private fun mayhewFactor(reps: Int): Double =
        100.0 / (MAYHEW_BASE + MAYHEW_COEFF * exp(-MAYHEW_DECAY * reps))
}
