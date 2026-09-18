package com.example.tr3ack.data.entity

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

/**
 * Shared 1RM estimation coefficients. Kept out of the ViewModels so the stats
 * cache (which is rebuilt in the repository) uses exactly the same maths as the
 * screens that render the results.
 */
object MovementFactors {
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
        val cappedReps = (reps - 1).coerceIn(0, 9)
        return when (movementType) {
            MovementType.DIPS -> DIP_COEFFICIENTS[cappedReps]
            MovementType.PULLUPS -> PULLUP_COEFFICIENTS[cappedReps]
            MovementType.CHIN_UPS -> CHINUP_COEFFICIENTS[cappedReps]
            MovementType.UNKNOWN -> 1.0 + (reps - 1) * 0.0333
        }
    }
}