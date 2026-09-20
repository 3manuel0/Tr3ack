package com.example.tr3ack

import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.StatsCalculator
import com.example.tr3ack.data.entity.WorkoutSetEntity
import java.util.TreeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private val bodyWeightByDate = TreeMap<String, Double>().apply {
        this["2026-08-21"] = 70.0
    }
    private val effectiveBodyWeight: (String) -> Double? = { date ->
        StatsCalculator.effectiveBodyWeight(bodyWeightByDate, date)
    }

    private val pullUps = ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true)
    private val bicepCurls = ExerciseEntity(id = 4, name = "Bicep Curls", isBodyweightBased = false)

    private fun set(exerciseId: Long, date: String, weight: Double, reps: Int, timestamp: Long) =
        WorkoutSetEntity(exerciseId = exerciseId, date = date, addedWeightKg = weight, reps = reps, timestamp = timestamp)

    // Demo dataset: pull-ups 32kg@8 (TSW 102) + 40kg@2 (TSW 110); curls 30kg@10 + 16kg@10 on 2026-08-21 (BW 70).
    private val demoPullSets = listOf(
        set(1, "2026-08-21", 32.0, 8, 1),
        set(1, "2026-08-21", 40.0, 2, 2)
    )
    private val demoCurlSets = listOf(
        set(4, "2026-08-21", 30.0, 10, 3),
        set(4, "2026-08-21", 16.0, 10, 4)
    )

    @Test
    fun exerciseStats_bodyweight_picksMaxE1RMAndTracksMaxRatio() {
        val stats = StatsCalculator.computeExerciseStats(pullUps, demoPullSets, effectiveBodyWeight)

        assertEquals(126.48, stats.bestE1RM, 1e-6)          // 102 * 1.240
        assertEquals(32.0, stats.bestE1RMAddedWeight, 1e-9)
        assertEquals(8, stats.bestE1RMReps)
        assertEquals(102.0, stats.bestE1RMTotalSystemWeight, 1e-9)
        assertEquals(145.7142857, stats.bestE1RMPercentBodyWeight, 1e-6)
        assertEquals(70.0, stats.bestE1RMBodyWeightKg, 1e-9)
        assertEquals("2026-08-21", stats.bestE1RMDate)
        assertEquals(110.0 / 70.0, stats.maxBodyweightRatio, 1e-9) // 40kg@2 set: highest ratio
        assertTrue(stats.hasData)
    }

    @Test
    fun exerciseStats_freeWeight_usesEpleyAndMaxAddedWeightGte6() {
        val stats = StatsCalculator.computeExerciseStats(bicepCurls, demoCurlSets, effectiveBodyWeight)

        assertEquals(40.0, stats.bestE1RM, 1e-6)            // 30 * (1 + 10/30)
        assertEquals(30.0, stats.bestE1RMAddedWeight, 1e-9)
        assertEquals(10, stats.bestE1RMReps)
        assertEquals(30.0, stats.bestE1RMTotalSystemWeight, 1e-9)
        assertEquals(30.0 / 70.0 * 100.0, stats.bestE1RMPercentBodyWeight, 1e-6)
        assertEquals(30.0, stats.maxAddedWeightGte6, 1e-9)
        assertEquals(0.0, stats.maxBodyweightRatio, 1e-9)
        assertEquals(70.0, stats.bestE1RMBodyWeightKg, 1e-9)
        assertTrue(stats.hasData)
    }

    @Test
    fun exerciseStats_emptySets_hasNoData() {
        val stats = StatsCalculator.computeExerciseStats(pullUps, emptyList(), effectiveBodyWeight)

        assertFalse(stats.hasData)
        assertEquals(0.0, stats.bestE1RM, 1e-9)
        assertEquals("", stats.lastLoggedDate)
    }

    @Test
    fun exerciseStats_bodyweightBeforeFirstWeightEntry_usesNearestFallback() {
        val stats = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2020-01-01", 20.0, 8, 1)), effectiveBodyWeight
        )

        assertEquals(111.6, stats.bestE1RM, 1e-6)           // (70 + 20) * 1.240
        assertEquals(90.0, stats.bestE1RMTotalSystemWeight, 1e-9)
        assertEquals(90.0 / 70.0 * 100.0, stats.bestE1RMPercentBodyWeight, 1e-6)
        assertEquals(70.0, stats.bestE1RMBodyWeightKg, 1e-9)
        assertEquals("2020-01-01", stats.lastLoggedDate)
        assertTrue(stats.hasData)
    }

    @Test
    fun dailyStats_bodyweight_matchesFirstSetE1rmAndTonnage() {
        val daily = StatsCalculator.computeDailyStats(pullUps, "2026-08-21", demoPullSets, effectiveBodyWeight)!!

        assertEquals(102.0, daily.firstSetTSW, 1e-9)
        assertEquals(8, daily.firstSetReps)
        assertEquals(102.0 / 70.0 * 100.0, daily.firstSetPercentBodyWeight, 1e-6)
        assertEquals(126.48, daily.e1rm, 1e-6)
        assertEquals(1036.0, daily.tonnage, 1e-9)           // 102*8 + 110*2
        assertEquals(70.0, daily.bodyWeightKg, 1e-9)
    }

    @Test
    fun dailyStats_freeWeight_ignoresBodyWeightTonnage() {
        val daily = StatsCalculator.computeDailyStats(bicepCurls, "2026-08-21", demoCurlSets, effectiveBodyWeight)!!

        assertEquals(30.0, daily.firstSetTSW, 1e-9)
        assertEquals(40.0, daily.e1rm, 1e-6)
        assertEquals(460.0, daily.tonnage, 1e-9)            // 30*10 + 16*10
        assertEquals(0.0, daily.bodyWeightKg, 1e-9)
    }

    @Test
    fun dailyStats_noFirstSet_returnsNull() {
        assertNull(StatsCalculator.computeDailyStats(pullUps, "2026-08-21", emptyList(), effectiveBodyWeight))
    }

    @Test
    fun dailyStats_bodyweightBeforeFirstWeightEntry_usesNearestFallback() {
        // No weight on/before 2020-01-01; the nearest known entry (2026-08-21, 70 kg) is used.
        val daily = StatsCalculator.computeDailyStats(pullUps, "2020-01-01", demoPullSets, effectiveBodyWeight)!!

        assertEquals(102.0, daily.firstSetTSW, 1e-9)
        assertEquals(126.48, daily.e1rm, 1e-6)
        assertEquals(1036.0, daily.tonnage, 1e-9)
        assertEquals(70.0, daily.bodyWeightKg, 1e-9)
    }

    @Test
    fun dailyStats_bodyweightWithNoWeightRecordAtAll_returnsNull() {
        val never: (String) -> Double? = { null }
        assertNull(StatsCalculator.computeDailyStats(pullUps, "2020-01-01", demoPullSets, never))
    }

    @Test
    fun effectiveBodyWeight_picksNearestKnownEntry() {
        val weights = TreeMap<String, Double>().apply {
            this["2026-08-10"] = 69.0
            this["2026-08-21"] = 70.0
            this["2026-08-30"] = 71.0
        }

        assertEquals(70.0, StatsCalculator.effectiveBodyWeight(weights, "2026-08-21")!!, 1e-9) // exact
        assertEquals(69.0, StatsCalculator.effectiveBodyWeight(weights, "2026-08-15")!!, 1e-9) // floor 5d, ceil 15d
        assertEquals(71.0, StatsCalculator.effectiveBodyWeight(weights, "2026-08-28")!!, 1e-9) // ceil 2d, floor 18d
        assertEquals(71.0, StatsCalculator.effectiveBodyWeight(weights, "2026-09-10")!!, 1e-9) // floor only
        assertEquals(69.0, StatsCalculator.effectiveBodyWeight(weights, "2026-07-01")!!, 1e-9) // ceil only
        assertNull(StatsCalculator.effectiveBodyWeight(TreeMap(), "2026-08-21"))
    }

    @Test
    fun workoutDay_countsSetsAndTonnageAcrossExercises() {
        val allSets = demoPullSets + demoCurlSets
        val exercisesById = mapOf(1L to pullUps, 4L to bicepCurls)
        val day = StatsCalculator.computeWorkoutDay("2026-08-21", allSets, exercisesById, effectiveBodyWeight)

        assertEquals(4, day.setCount)
        assertEquals(1496.0, day.tonnage, 1e-9)             // 1036 + 460
    }

    @Test
    fun freeWeightWithNoBodyWeightEntry_stillComputes() {
        val stats = StatsCalculator.computeExerciseStats(
            bicepCurls,
            listOf(set(4, "2020-01-01", 30.0, 10, 1)),
            effectiveBodyWeight
        )

        assertTrue(stats.hasData)
        assertEquals(40.0, stats.bestE1RM, 1e-6)
        assertEquals(30.0 / 70.0 * 100.0, stats.bestE1RMPercentBodyWeight, 1e-6) // nearest weight now used
        assertEquals(70.0, stats.bestE1RMBodyWeightKg, 1e-9)
    }
}