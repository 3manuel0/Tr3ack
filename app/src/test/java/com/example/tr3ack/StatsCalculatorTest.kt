package com.example.tr3ack

import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.MovementType
import com.example.tr3ack.data.entity.StatsCalculator
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.viewmodel.rollingAverage
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

    private fun set(
        exerciseId: Long,
        date: String,
        weight: Double,
        reps: Int,
        timestamp: Long,
        rir: Int = 0
    ) = WorkoutSetEntity(
        exerciseId = exerciseId,
        date = date,
        addedWeightKg = weight,
        reps = reps,
        timestamp = timestamp,
        rir = rir
    )

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
    fun bodyweightRepsAboveTen_extrapolateThenCapAtTwelve() {
        // Pull-Ups table ends at 10 reps (1.304). 11 and 12 continue the final
        // 0.032 slope; 13+ reuse the 12-rep coefficient (the cap).
        assertEquals(1.304, MovementFactors.getMovementFactor(MovementType.PULLUPS, 10), 1e-9)
        assertEquals(1.336, MovementFactors.getMovementFactor(MovementType.PULLUPS, 11), 1e-9)
        assertEquals(1.368, MovementFactors.getMovementFactor(MovementType.PULLUPS, 12), 1e-9)
        assertEquals(1.368, MovementFactors.getMovementFactor(MovementType.PULLUPS, 13), 1e-9)
        assertEquals(1.368, MovementFactors.getMovementFactor(MovementType.PULLUPS, 50), 1e-9)
    }

    @Test
    fun rir_liftsComparedBodyweightE1RM() {
        // Same set, but 2 reps in reserve means 10 effective reps (not 8).
        val atFailure = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 30.0, 8, 1, rir = 0)), effectiveBodyWeight
        )
        val withRir = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 30.0, 8, 1, rir = 2)), effectiveBodyWeight
        )

        assertEquals(124.0, atFailure.bestE1RM, 1e-6)   // TSW 100 * 1.240 (8 reps)
        assertEquals(130.4, withRir.bestE1RM, 1e-6)     // TSW 100 * 1.304 (10 reps)
    }

    @Test
    fun rir_clampedByRepCap() {
        // 11 reps + 3 RIR would be 14 effective reps; capped at 12.
        val stats = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 30.0, 11, 1, rir = 3)), effectiveBodyWeight
        )
        assertEquals(136.8, stats.bestE1RM, 1e-6)       // TSW 100 * 1.368 (12 reps)
    }

    @Test
    fun freeWeightSingleRep_isNotInflated() {
        // reps == 1 must be the raw weight (no Epley 3.3% bump).
        assertEquals(40.0, MovementFactors.freeWeightE1RM(40.0, 1), 1e-9)

        val stats = StatsCalculator.computeExerciseStats(
            bicepCurls, listOf(set(4, "2026-08-21", 40.0, 1, 1)), effectiveBodyWeight
        )
        assertEquals(40.0, stats.bestE1RM, 1e-6)
    }

    @Test
    fun freeWeightNineReps_matchesEpley() {
        // 19 * (1 + 9/30) = 24.7
        assertEquals(24.7, MovementFactors.freeWeightE1RM(19.0, 9), 1e-6)

        val stats = StatsCalculator.computeExerciseStats(
            bicepCurls, listOf(set(4, "2026-08-21", 19.0, 9, 1)), effectiveBodyWeight
        )
        assertEquals(24.7, stats.bestE1RM, 1e-6)
    }

    @Test
    fun exerciseStats_bodyweightCapsRepsAtTwelve() {
        val twelve = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 30.0, 12, 1)), effectiveBodyWeight
        )
        val thirteen = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 30.0, 13, 1)), effectiveBodyWeight
        )

        assertEquals(136.8, twelve.bestE1RM, 1e-6)          // TSW 100 * 1.368
        assertEquals(twelve.bestE1RM, thirteen.bestE1RM, 1e-9) // 13 capped to 12
        assertEquals(13, thirteen.bestE1RMReps)             // logged rep count preserved
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
    fun rollingAverage_trailingWindowHandlesShortSeries() {
        assertEquals(emptyList<Double>(), rollingAverage(emptyList()))
        assertEquals(listOf(10.0), rollingAverage(listOf(10.0)))

        // Default window of 3: points before a full window average what exists so far.
        assertEquals(
            listOf(10.0, 15.0, 20.0, 30.0),
            rollingAverage(listOf(10.0, 20.0, 30.0, 40.0))
        )

        assertEquals(
            listOf(1.0, 1.5, 2.5),
            rollingAverage(listOf(1.0, 2.0, 3.0), window = 2)
        )
    }

    @Test
    fun bodyweightSingleRep_usesBaselineCoefficient() {
        val stats = StatsCalculator.computeExerciseStats(
            pullUps, listOf(set(1, "2026-08-21", 20.0, 1, 1)), effectiveBodyWeight
        )
        assertEquals(90.0, stats.bestE1RM, 1e-9) // TSW 90 * 1.000 (1-rep coefficient)
        assertEquals(1, stats.bestE1RMReps)
    }

    @Test
    fun bestSet_sameE1RM_fewerRepsWins() {
        val sets = listOf(
            set(4, "2026-08-21", 6.0, 10, 1), // 6 * (1 + 10/30) = 8.0
            set(4, "2026-08-21", 8.0, 1, 2),  // 8.0
        )
        val stats = StatsCalculator.computeExerciseStats(bicepCurls, sets, effectiveBodyWeight)
        assertEquals(8.0, stats.bestE1RM, 1e-9)
        assertEquals(1, stats.bestE1RMReps)
        assertEquals(8.0, stats.bestE1RMAddedWeight, 1e-9)

        // Order-independent: the few-rep set wins whichever comes first.
        val reversed = StatsCalculator.computeExerciseStats(bicepCurls, sets.reversed(), effectiveBodyWeight)
        assertEquals(1, reversed.bestE1RMReps)
    }

    @Test
    fun effectiveBodyWeight_coversAllLookupCases() {
        val onTheDay = TreeMap<String, Double>().apply { this["2026-08-21"] = 70.0 }
        assertEquals(70.0, StatsCalculator.effectiveBodyWeight(onTheDay, "2026-08-21")!!, 1e-9)

        val earlierEntry = TreeMap<String, Double>().apply {
            this["2026-08-10"] = 69.0
            this["2026-08-30"] = 71.0
        }
        assertEquals(69.0, StatsCalculator.effectiveBodyWeight(earlierEntry, "2026-08-12")!!, 1e-9)

        val onlyLaterEntry = TreeMap<String, Double>().apply { this["2026-09-01"] = 72.0 }
        assertEquals(72.0, StatsCalculator.effectiveBodyWeight(onlyLaterEntry, "2026-08-21")!!, 1e-9)

        assertNull(StatsCalculator.effectiveBodyWeight(TreeMap(), "2026-08-21"))
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