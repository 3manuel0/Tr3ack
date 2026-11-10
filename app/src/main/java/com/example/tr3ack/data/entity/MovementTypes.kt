package com.example.tr3ack.data.entity

import kotlin.math.exp
import kotlin.math.pow

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

/**
 * Shared 1RM estimation coefficients. Kept out of the ViewModels so the stats
 * cache (which is rebuilt in the repository) uses exactly the same maths as the
 * screens that render the results.
 *
 * Free weights keep the Mayhew et al. (1992) equation applied to the lifted
 * weight. Weighted-bodyweight movements use a consensus of the six classic
 * reps-to-failure equations applied to the total system weight (TSL = body
 * weight + added weight); see [tswFactor] for the evidence behind that choice.
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
     * Weighted-bodyweight estimates use the consensus factor only up to 12
     * reps: beyond that the individual equations diverge quickly and the
     * literature agrees that rep-max predictions become unreliable. Sets above
     * the cap still count toward tonnage.
     */
    const val MAX_BODYWEIGHT_REPS = 12

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
     * TSL-to-E1RM factor for a weighted-bodyweight movement, using reps clamped
     * to 1..[MAX_BODYWEIGHT_REPS].
     *
     * There is no validated pull-up/dip-specific reps-to-failure equation, so
     * instead of trusting one formula this returns the **mean of six classic
     * equations** — Epley, Brzycki, Lombardi, Mayhew, O'Conner and Wathen —
     * all applied to total system weight. Averaging cancels the individual
     * formula biases (they disagree most at low reps) and lands on the
     * consensus that the literature supports:
     *
     *  - the updated reps-%1RM meta-analysis (Nuzzo et al., Sports Med 2023,
     *    ~7,000 participants) shows the relationship is broadly similar across
     *    exercises, so there is no reason to use a special pull-up curve;
     *  - the only direct weighted-pull-up validation (Baker, 2004) found
     *    11.5 ± 4.3 reps at 74 ± 7% 1RM — this consensus returns 74% for
     *    ~11.5 reps, whereas Mayhew alone reads low at low reps and high at
     *    mid reps.
     *
     * A true single (reps == 1) is a maximal effort, so the factor is 1.0 —
     * E1RM equals the raw TSL, no inflation.
     */
    fun tswFactor(reps: Int): Double {
        val effectiveReps = reps.coerceIn(1, MAX_BODYWEIGHT_REPS)
        if (effectiveReps <= 1) return 1.0
        return consensusFactor(effectiveReps)
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

    /** Mean of the six validated rep-max equations for an already-clamped rep count. */
    private fun consensusFactor(reps: Int): Double {
        val factors = doubleArrayOf(
            epleyFactor(reps),
            brzyckiFactor(reps),
            lombardiFactor(reps),
            mayhewFactor(reps),
            oConnerFactor(reps),
            wathenFactor(reps),
        )
        return factors.average()
    }

    private fun epleyFactor(reps: Int): Double = 1.0 + reps / 30.0

    private fun brzyckiFactor(reps: Int): Double = 36.0 / (37.0 - reps)

    private fun lombardiFactor(reps: Int): Double = reps.toDouble().pow(0.10)

    private fun oConnerFactor(reps: Int): Double = 1.0 + reps / 40.0

    private fun wathenFactor(reps: Int): Double =
        100.0 / (48.8 + 53.8 * exp(-0.075 * reps))

    /** Mayhew factor for an already-clamped rep count (>= 2). */
    private fun mayhewFactor(reps: Int): Double =
        100.0 / (MAYHEW_BASE + MAYHEW_COEFF * exp(-MAYHEW_DECAY * reps))
}
