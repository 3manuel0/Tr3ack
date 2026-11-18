package com.example.tr3ack

import com.example.tr3ack.data.backup.BackupJson
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private val pullUps = Exercise(ExerciseIds.WEIGHTED_PULL_UPS, "Weighted Pull-Ups", true)
    private val curls = Exercise(ExerciseIds.BICEP_CURLS, "Bicep Curls", false)

    @Test
    fun buildThenParse_roundTrips() {
        val sets = listOf(
            WorkoutSet(1, ExerciseIds.WEIGHTED_PULL_UPS, "2026-08-21", 20.0, 8, 100),
            WorkoutSet(2, ExerciseIds.BICEP_CURLS, "2026-08-21", 18.5, 9, 200)
        )
        val bw = listOf(BodyWeightEntry(1, "2026-08-21", 88.0))

        val json = BackupJson.build(listOf(pullUps, curls), sets, bw)
        val payload = BackupJson.parse(json)

        assertEquals(2, payload.exercises.size)
        assertEquals(2, payload.sets.size)
        assertEquals(1, payload.bodyWeightEntries.size)

        val pull = payload.sets.first { it.exerciseId == ExerciseIds.WEIGHTED_PULL_UPS }
        assertEquals(20.0, pull.addedWeightKg, 1e-9)
        assertEquals(8, pull.reps)
        assertEquals("2026-08-21", pull.date)

        val parsedBw = payload.bodyWeightEntries.first()
        assertEquals(88.0, parsedBw.bodyWeightKg, 1e-9)
    }

    @Test
    fun parse_filtersUnknownExerciseIds() {
        val json = BackupJson.build(
            listOf(pullUps, Exercise(999L, "Unknown", false)),
            listOf(
                WorkoutSet(1, ExerciseIds.WEIGHTED_PULL_UPS, "2026-08-21", 20.0, 8, 100),
                WorkoutSet(2, 999L, "2026-08-21", 10.0, 5, 101)
            ),
            emptyList()
        )
        val payload = BackupJson.parse(json)
        assertEquals(1, payload.exercises.size)
        assertEquals(ExerciseIds.WEIGHTED_PULL_UPS, payload.exercises.first().id)
        assertEquals(1, payload.sets.size)
    }

    @Test
    fun build_isHumanReadableAndSparse() {
        val json = BackupJson.build(listOf(pullUps), emptyList(), emptyList())
        assertTrue(json.contains("\"exercises\""))
        assertTrue(json.contains("\"workoutSets\""))
        assertTrue(json.contains("\"bodyWeightEntries\""))
    }
}
