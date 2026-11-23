package com.example.tr3ack

import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.viewmodel.RepMaxRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepMaxRulesTest {

    @Test
    fun noE1rm_isEmpty() {
        assertTrue(RepMaxRules.predict(0.0, isBodyweightBased = false, bodyWeightKg = 0.0).isEmpty())
    }

    @Test
    fun freeWeight_prediction_roundTripsThroughE1rm() {
        val e1rm = 100.0
        val result = RepMaxRules.predict(e1rm, isBodyweightBased = false, bodyWeightKg = 0.0)
        // Free weights show every rep range up to 15.
        assertEquals(listOf(3, 5, 6, 8, 10, 12, 15), result.map { it.reps })

        for (rm in result) {
            // Feeding the predicted weight back into the E1RM formula returns the original 1RM.
            val backToE1rm = MovementFactors.freeWeightE1RM(rm.addedWeightKg, rm.reps)
            assertEquals(e1rm, backToE1rm, 0.5)
            // Heavier reps = lighter predicted weight.
        }
    }

    @Test
    fun predictedWeight_decreasesAsRepsIncrease() {
        val result = RepMaxRules.predict(100.0, isBodyweightBased = false, bodyWeightKg = 0.0)
        val weights = result.map { it.addedWeightKg }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun bodyweight_subtractsBodyWeight_forAddedLoad() {
        val e1rm = 130.0
        val bw = 89.2
        val result = RepMaxRules.predict(e1rm, isBodyweightBased = true, bodyWeightKg = bw)
        // Bodyweight movements cap at 12 reps (validated range).
        assertEquals(listOf(3, 5, 6, 8, 10, 12), result.map { it.reps })

        val six = result.first { it.reps == 6 }
        val expectedSystem = MovementFactors.predictedWeightAt(e1rm, 6, isBodyweightBased = true)
        assertEquals(expectedSystem - bw, six.addedWeightKg, 0.01)

        // A single (rep 1) prediction equals the 1RM itself.
        assertEquals(e1rm, MovementFactors.predictedWeightAt(e1rm, 1, isBodyweightBased = true), 1e-9)
    }

    @Test
    fun bodyweight_withUnknownWeight_treatsAddedAsZero() {
        val result = RepMaxRules.predict(130.0, isBodyweightBased = true, bodyWeightKg = 0.0)
        // No body weight -> fall back to total system load (added treated as 0 offset).
        val six = result.first { it.reps == 6 }
        assertEquals(MovementFactors.predictedWeightAt(130.0, 6, true), six.addedWeightKg, 0.01)
    }
}
