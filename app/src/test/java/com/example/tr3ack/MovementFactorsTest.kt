package com.example.tr3ack

import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFactorsTest {

    @Test
    fun tswFactor_followsMayhewCurve() {
        assertEquals(1.0, MovementFactors.tswFactor(1), 1e-9)
        assertEquals(1.1143868, MovementFactors.tswFactor(2), 1e-6)
        assertEquals(1.1901068, MovementFactors.tswFactor(5), 1e-6)
        assertEquals(1.2628634, MovementFactors.tswFactor(8), 1e-6)
        assertEquals(1.3093427, MovementFactors.tswFactor(10), 1e-6)
    }

    @Test
    fun tswFactor_keepsClimbingPastTenThenClampsAtFifteen() {
        // Mayhew is validated to 15 reps, so weighted-bodyweight estimates keep
        // rising through 10..15 instead of flattening at 10 like a table cap.
        assertEquals(1.3539848, MovementFactors.tswFactor(12), 1e-6)
        assertTrue(MovementFactors.tswFactor(12) > MovementFactors.tswFactor(10))
        assertTrue(MovementFactors.tswFactor(15) > MovementFactors.tswFactor(12))

        // Flat at, and beyond, the cap.
        assertEquals(1.4171925, MovementFactors.tswFactor(15), 1e-6)
        assertEquals(1.4171925, MovementFactors.tswFactor(20), 1e-6)
        assertEquals(1.4171925, MovementFactors.tswFactor(200), 1e-6)
    }

    @Test
    fun freeWeightE1RM_usesTheSameMayhewFactor() {
        // One formula for both paths: free-weight E1RM is weight × the TSL factor.
        assertEquals(19.0, MovementFactors.freeWeightE1RM(19.0, 1), 1e-9)
        assertEquals(
            19.0 * MovementFactors.tswFactor(9),
            MovementFactors.freeWeightE1RM(19.0, 9),
            1e-9
        )
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
