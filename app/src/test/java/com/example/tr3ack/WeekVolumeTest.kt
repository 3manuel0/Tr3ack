package com.example.tr3ack

import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.viewmodel.buildWeekVolume
import com.example.tr3ack.viewmodel.weekStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekVolumeTest {

    private val thisWeekStart = LocalDate.parse("2026-10-12") // Monday; matches weekStart(2026-10-18)

    private val exercises = listOf(
        Exercise(id = 1L, name = "Weighted Pull-Ups", isBodyweightBased = true),
        Exercise(id = 2L, name = "Weighted Dips", isBodyweightBased = true),
        Exercise(id = 4L, name = "Bicep Curls", isBodyweightBased = false),
    )

    private fun day(date: String, setCount: Int, tonnage: Double) =
        WorkoutDayEntity(date = date, setCount = setCount, tonnage = tonnage)

    private fun daily(exerciseId: Long, date: String, tonnage: Double) = ExerciseDailyStatsEntity(
        exerciseId = exerciseId,
        date = date,
        isBodyweightBased = true,
        firstSetTSW = 0.0,
        firstSetReps = 8,
        firstSetAddedWeight = 0.0,
        firstSetPercentBodyWeight = 0.0,
        e1rm = 0.0,
        tonnage = tonnage,
        bodyWeightKg = 70.0,
    )

    @Test
    fun weekStart_returnsMondayOfContainingWeek() {
        assertEquals(LocalDate.parse("2026-10-12"), weekStart(LocalDate.parse("2026-10-12")))
        assertEquals(LocalDate.parse("2026-10-12"), weekStart(LocalDate.parse("2026-10-18")))
        assertEquals(LocalDate.parse("2026-10-12"), weekStart(LocalDate.parse("2026-10-13")))
    }

    @Test
    fun buildWeekVolume_totalsCurrentAndPreviousWeek() {
        val days = listOf(
            day("2026-10-12", setCount = 10, tonnage = 1000.0),
            day("2026-10-14", setCount = 5, tonnage = 500.0),
            day("2026-10-06", setCount = 12, tonnage = 1200.0),
            day("2026-09-01", setCount = 99, tonnage = 9999.0), // outside both weeks
        )

        val volume = buildWeekVolume(thisWeekStart, days, emptyList(), exercises)

        assertEquals(1500.0, volume.thisWeekTonnage, 1e-9)
        assertEquals(1200.0, volume.lastWeekTonnage, 1e-9)
        assertEquals(2, volume.thisWeekSessions)
        assertEquals(1, volume.lastWeekSessions)
        assertEquals(15, volume.thisWeekSets)
        assertEquals(12, volume.lastWeekSets)
        assertEquals(25.0, volume.tonnageChangePct!!, 1e-9)
    }

    @Test
    fun buildWeekVolume_breaksDownPerExerciseSortedByThisWeek() {
        val dailyStats = listOf(
            daily(1L, "2026-10-12", 800.0),
            daily(1L, "2026-10-14", 200.0),
            daily(1L, "2026-10-06", 700.0),
            daily(2L, "2026-10-13", 300.0),
            daily(4L, "2026-10-06", 0.0), // nothing this week, zero last week -> excluded
            daily(99L, "2026-10-13", 50.0), // unknown exercise -> excluded
        )

        val volume = buildWeekVolume(thisWeekStart, emptyList(), dailyStats, exercises)

        assertEquals(listOf(1L, 2L), volume.perExercise.map { it.exerciseId })
        assertEquals("Weighted Pull-Ups", volume.perExercise[0].exerciseName)
        assertEquals(1000.0, volume.perExercise[0].thisWeekTonnage, 1e-9)
        assertEquals(700.0, volume.perExercise[0].lastWeekTonnage, 1e-9)
        assertEquals(300.0, volume.perExercise[1].thisWeekTonnage, 1e-9)
        assertEquals(0.0, volume.perExercise[1].lastWeekTonnage, 1e-9)
    }

    @Test
    fun buildWeekVolume_noPreviousWeek_hasNullChange() {
        val volume = buildWeekVolume(
            thisWeekStart,
            listOf(day("2026-10-12", setCount = 4, tonnage = 400.0)),
            emptyList(),
            exercises
        )

        assertNull(volume.tonnageChangePct)
        assertEquals(400.0, volume.thisWeekTonnage, 1e-9)
        assertEquals(0.0, volume.lastWeekTonnage, 1e-9)
    }

    @Test
    fun buildWeekVolume_emptyInput_isEmpty() {
        val volume = buildWeekVolume(thisWeekStart, emptyList(), emptyList(), exercises)
        assertEquals(0.0, volume.thisWeekTonnage, 1e-9)
        assertEquals(0, volume.thisWeekSessions)
        assertNull(volume.tonnageChangePct)
    }
}
