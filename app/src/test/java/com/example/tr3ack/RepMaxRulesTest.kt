package com.example.tr3ack

import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.viewmodel.RepMaxRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepMaxRulesTest {

    private fun set(id: Long, reps: Int, weight: Double) =
        WorkoutSet(id, exerciseId = 1, date = "2026-08-21", addedWeightKg = weight, reps = reps, timestamp = id)

    @Test
    fun bestWeightPerRepCount_isMaxAtThatReps() {
        val sets = listOf(
            set(1, 6, 20.0),
            set(2, 6, 25.0),
            set(3, 6, 22.5),
            set(4, 8, 18.0),
            set(5, 10, 15.0)
        )
        val result = RepMaxRules.build(sets)
        assertEquals(
            listOf(6 to 25.0, 8 to 18.0, 10 to 15.0),
            result.map { it.reps to it.addedWeightKg }
        )
    }

    @Test
    fun ignoresRepsOutsideOneToFifteen() {
        val sets = listOf(
            set(1, 0, 50.0),
            set(2, 20, 60.0),
            set(3, 5, 10.0)
        )
        val result = RepMaxRules.build(sets)
        assertEquals(1, result.size)
        assertEquals(5, result.first().reps)
        assertEquals(10.0, result.first().addedWeightKg, 1e-9)
    }

    @Test
    fun emptyInput_isEmpty() {
        assertTrue(RepMaxRules.build(emptyList()).isEmpty())
    }
}
