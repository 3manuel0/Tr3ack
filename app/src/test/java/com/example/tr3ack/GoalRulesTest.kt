package com.example.tr3ack

import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.GoalType
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.goalTypeFromName
import com.example.tr3ack.viewmodel.GoalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalRulesTest {

    private fun stats(
        exerciseId: Long = 1L,
        bestE1RM: Double = 136.4,
        maxAddedWeightGte6: Double = 40.0,
        maxBodyweightRatio: Double = 1.55
    ) = ExerciseStatsEntity(
        exerciseId = exerciseId,
        isBodyweightBased = true,
        hasData = true,
        bestE1RM = bestE1RM,
        bestE1RMAddedWeight = 40.0,
        bestE1RMReps = 8,
        bestE1RMTotalSystemWeight = 110.0,
        bestE1RMPercentBodyWeight = 157.1,
        bestE1RMDate = "2026-09-20",
        bestE1RMBodyWeightKg = 70.0,
        maxAddedWeightGte6 = maxAddedWeightGte6,
        maxBodyweightRatio = maxBodyweightRatio,
        lastLoggedDate = "2026-09-20"
    )

    private fun goal(type: GoalType, target: Double) =
        Goal(exerciseId = 1L, type = type, targetValue = target, createdAt = 1L)

    @Test
    fun availableTypes_includesBwRatioOnlyForBodyweightMovements() {
        assertEquals(
            listOf(GoalType.E1RM, GoalType.WEIGHT, GoalType.BW_RATIO),
            GoalRules.availableTypes(isBodyweightBased = true)
        )
        assertEquals(
            listOf(GoalType.E1RM, GoalType.WEIGHT),
            GoalRules.availableTypes(isBodyweightBased = false)
        )
    }

    @Test
    fun progress_readsTheMatchingStatPerType() {
        val s = stats()
        assertEquals(136.4, GoalRules.of(goal(GoalType.E1RM, 200.0), s).current, 1e-9)
        assertEquals(40.0, GoalRules.of(goal(GoalType.WEIGHT, 60.0), s).current, 1e-9)
        assertEquals(1.55, GoalRules.of(goal(GoalType.BW_RATIO, 2.0), s).current, 1e-9)
    }

    @Test
    fun progress_isClampedWhileAchievedIsNot() {
        val over = GoalRules.of(goal(GoalType.E1RM, 100.0), stats(bestE1RM = 136.4))
        assertEquals(1f, over.progress, 1e-6f)
        assertTrue(over.achieved)

        val under = GoalRules.of(goal(GoalType.E1RM, 200.0), stats(bestE1RM = 100.0))
        assertEquals(0.5f, under.progress, 1e-6f)
        assertFalse(under.achieved)

        val exact = GoalRules.of(goal(GoalType.E1RM, 136.4), stats())
        assertEquals(1f, exact.progress, 1e-6f)
        assertTrue(exact.achieved)
    }

    @Test
    fun progress_withoutStats_fallsBackToZero() {
        val none = GoalRules.of(goal(GoalType.E1RM, 150.0), stats = null)
        assertEquals(0.0, none.current, 1e-9)
        assertEquals(0f, none.progress, 1e-6f)
        assertFalse(none.achieved)
    }

    @Test
    fun goalTypeFromName_defaultsToE1rmForUnknownValues() {
        assertEquals(GoalType.WEIGHT, goalTypeFromName("WEIGHT"))
        assertEquals(GoalType.BW_RATIO, goalTypeFromName("BW_RATIO"))
        assertEquals(GoalType.E1RM, goalTypeFromName("SOMETHING_NEW"))
    }

    private fun repsGoal(weight: Double, reps: Int) =
        Goal(exerciseId = 1L, type = GoalType.WEIGHT, targetValue = weight, targetReps = reps, createdAt = 1L)

    private fun workSet(weight: Double, reps: Int, ts: Long = 0) =
        WorkoutSet(id = ts, exerciseId = 1L, date = "2026-08-21", addedWeightKg = weight, reps = reps, timestamp = ts)

    @Test
    fun weightGoal_withoutRepsTarget_usesLegacyWeightOnlyValue() {
        val s = stats(maxAddedWeightGte6 = 40.0)
        val p = GoalRules.of(goal(GoalType.WEIGHT, 60.0), s)
        assertEquals(40.0, p.current, 1e-9)
        assertEquals(0, p.currentReps)
        assertEquals((40.0 / 60.0).toFloat(), p.progress, 1e-6f)
    }

    @Test
    fun weightGoal_withRepsTarget_averagesWeightAndRepsFractions() {
        // Goal 40 kg × 8. Best 8-rep set is 35 kg (87.5%); best set at >=40 kg is 5 reps (62.5%).
        val sets = listOf(workSet(35.0, 8, 1), workSet(40.0, 5, 2))
        val p = GoalRules.of(repsGoal(40.0, 8), stats(), sets)
        assertEquals(35.0, p.current, 1e-9)
        assertEquals(5, p.currentReps)
        assertEquals(((35.0 / 40.0) + (5.0 / 8.0)).toFloat() / 2f, p.progress, 1e-6f)
        assertFalse(p.achieved)
    }

    @Test
    fun weightGoal_withRepsTarget_isAchievedWhenOneSetHitsBothTargets() {
        val sets = listOf(workSet(35.0, 8, 1), workSet(40.0, 5, 2), workSet(40.0, 8, 3))
        val p = GoalRules.of(repsGoal(40.0, 8), stats(), sets)
        assertEquals(40.0, p.current, 1e-9)
        assertEquals(8, p.currentReps)
        assertEquals(1f, p.progress, 1e-6f)
        assertTrue(p.achieved)
    }

    @Test
    fun weightGoal_withRepsTarget_noSets_isZero() {
        val p = GoalRules.of(repsGoal(40.0, 8), stats(), emptyList())
        assertEquals(0.0, p.current, 1e-9)
        assertEquals(0, p.currentReps)
        assertEquals(0f, p.progress, 1e-6f)
        assertFalse(p.achieved)
    }
}
