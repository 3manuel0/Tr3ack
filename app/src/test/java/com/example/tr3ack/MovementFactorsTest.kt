package com.example.tr3ack

import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFactorsTest {

    @Test
    fun tswFactor_isTheConsensusOfSixClassicEquations() {
        assertEquals(1.0, MovementFactors.tswFactor(1), 1e-9)
        assertEquals(1.0638093, MovementFactors.tswFactor(2), 1e-6)
        assertEquals(1.1578696, MovementFactors.tswFactor(5), 1e-6)
        assertEquals(1.2464613, MovementFactors.tswFactor(8), 1e-6)
        assertEquals(1.3054003, MovementFactors.tswFactor(10), 1e-6)
    }

    @Test
    fun tswFactor_climbsToTwelveThenClamps() {
        // The consensus is reliable through ~12 reps; beyond that all formulas
        // extrapolate, so the 12-rep factor is reused.
        assertTrue(MovementFactors.tswFactor(12) > MovementFactors.tswFactor(10))
        assertEquals(1.3651720, MovementFactors.tswFactor(12), 1e-6)
        assertEquals(1.3651720, MovementFactors.tswFactor(13), 1e-6)
        assertEquals(1.3651720, MovementFactors.tswFactor(15), 1e-6)
        assertEquals(1.3651720, MovementFactors.tswFactor(200), 1e-6)
    }

    @Test
    fun freeWeightE1RM_keepsMayhew() {
        // Free weights intentionally stay on Mayhew (validated 1..15 reps).
        assertEquals(19.0, MovementFactors.freeWeightE1RM(19.0, 1), 1e-9)
        assertEquals(24.4401215, MovementFactors.freeWeightE1RM(19.0, 9), 1e-6)
        assertEquals(26.9266568, MovementFactors.freeWeightE1RM(19.0, 15), 1e-6)
        assertEquals(26.9266568, MovementFactors.freeWeightE1RM(19.0, 40), 1e-6) // capped at 15
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
