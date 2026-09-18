package com.example.tr3ack

import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementFactorsTest {

    @Test
    fun pullUpCoefficients_matchReferenceTable() {
        val expected = doubleArrayOf(
            1.000, 1.038, 1.073, 1.108, 1.142,
            1.175, 1.208, 1.240, 1.272, 1.304
        )
        repeat(10) { i ->
            val reps = i + 1
            assertEquals("PULLUPS reps=$reps", expected[i], MovementFactors.getMovementFactor(MovementType.PULLUPS, reps), 1e-9)
        }
    }

    @Test
    fun dipCoefficients_matchReferenceTable() {
        val expected = doubleArrayOf(
            1.000, 1.035, 1.068, 1.100, 1.130,
            1.160, 1.190, 1.220, 1.250, 1.280
        )
        repeat(10) { i ->
            val reps = i + 1
            assertEquals("DIPS reps=$reps", expected[i], MovementFactors.getMovementFactor(MovementType.DIPS, reps), 1e-9)
        }
    }

    @Test
    fun chinUpCoefficients_matchReferenceTable() {
        val expected = doubleArrayOf(
            1.000, 1.042, 1.082, 1.120, 1.158,
            1.194, 1.229, 1.263, 1.296, 1.328
        )
        repeat(10) { i ->
            val reps = i + 1
            assertEquals("CHIN_UPS reps=$reps", expected[i], MovementFactors.getMovementFactor(MovementType.CHIN_UPS, reps), 1e-9)
        }
    }

    @Test
    fun unknownMovement_usesLinearFallback() {
        assertEquals(1.0, MovementFactors.getMovementFactor(MovementType.UNKNOWN, 1), 1e-9)
        assertEquals(1.0999, MovementFactors.getMovementFactor(MovementType.UNKNOWN, 4), 1e-9)
        assertEquals(1.2997, MovementFactors.getMovementFactor(MovementType.UNKNOWN, 10), 1e-9)
        assertEquals(1.3663, MovementFactors.getMovementFactor(MovementType.UNKNOWN, 12), 1e-9)
    }

    @Test
    fun namedMovement_capsAtTableBoundary_beyond10Reps() {
        assertEquals(1.304, MovementFactors.getMovementFactor(MovementType.PULLUPS, 11), 1e-9)
        assertEquals(1.328, MovementFactors.getMovementFactor(MovementType.CHIN_UPS, 30), 1e-9)
    }

    @Test
    fun singleRep_returnsBaselineOne() {
        for (type in MovementType.entries) {
            assertEquals("$type reps=1", 1.0, MovementFactors.getMovementFactor(type, 1), 1e-9)
        }
    }

    @Test
    fun movementType_detectedFromExerciseName() {
        assertEquals(MovementType.PULLUPS, MovementFactors.getMovementType("Weighted Pull-Ups"))
        assertEquals(MovementType.PULLUPS, MovementFactors.getMovementType("weighted pullups"))
        assertEquals(MovementType.DIPS, MovementFactors.getMovementType("Weighted Dips"))
        assertEquals(MovementType.CHIN_UPS, MovementFactors.getMovementType("Weighted Chin-Ups"))
        assertEquals(MovementType.UNKNOWN, MovementFactors.getMovementType("Bicep Curls"))
        assertEquals(MovementType.UNKNOWN, MovementFactors.getMovementType("Lateral Raises"))
    }
}