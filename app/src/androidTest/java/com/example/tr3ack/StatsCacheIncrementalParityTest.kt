package com.example.tr3ack

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Differential test for the incremental stats-cache updates: after an arbitrary
 * sequence of set writes, the cache must be byte-for-byte identical to what a
 * full rebuild produces. This is the safety net that lets the app update only the
 * affected rows on every log/delete instead of recomputing everything.
 */
@RunWith(AndroidJUnit4::class)
class StatsCacheIncrementalParityTest {

    private lateinit var db: Tr3ackDatabase
    private lateinit var repository: Tr3ackRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, Tr3ackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = Tr3ackRepository(
            db,
            db.exerciseDao(),
            db.bodyWeightDao(),
            db.workoutSetDao(),
            db.statsCacheDao(),
            db.goalDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val exerciseStatsColumns = listOf(
        "exerciseId", "isBodyweightBased", "hasData", "bestE1RM", "bestE1RMAddedWeight",
        "bestE1RMReps", "bestE1RMTotalSystemWeight", "bestE1RMPercentBodyWeight", "bestE1RMDate",
        "bestE1RMBodyWeightKg", "maxAddedWeightGte6", "maxBodyweightRatio", "lastLoggedDate"
    )
    private val dailyColumns = listOf(
        "exerciseId", "date", "isBodyweightBased", "firstSetTSW", "firstSetReps",
        "firstSetAddedWeight", "firstSetPercentBodyWeight", "e1rm", "tonnage", "bodyWeightKg"
    )
    private val workoutDayColumns = listOf("date", "setCount", "tonnage")

    private fun dumpTable(table: String, columns: List<String>, orderBy: String, into: MutableList<String>) {
        val db2 = db.openHelper.writableDatabase
        db2.query("SELECT ${columns.joinToString(", ")} FROM $table ORDER BY $orderBy").use { cursor ->
            while (cursor.moveToNext()) {
                into.add(table + "|" + columns.joinToString("|") { cursor.getString(cursor.getColumnIndexOrThrow(it)) })
            }
        }
    }

    private fun snapshot(): List<String> {
        val rows = mutableListOf<String>()
        dumpTable("exercise_stats", exerciseStatsColumns, "exerciseId", rows)
        dumpTable("exercise_daily_stats", dailyColumns, "exerciseId, date", rows)
        dumpTable("workout_days", workoutDayColumns, "date", rows)
        return rows
    }

    private fun seedExercises() = runBlocking {
        db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true),
                ExerciseEntity(id = 2, name = "Weighted Dips", isBodyweightBased = true),
                ExerciseEntity(id = 4, name = "Bicep Curls", isBodyweightBased = false),
                ExerciseEntity(id = 6, name = "Lateral Raises", isBodyweightBased = false),
            )
        )
    }

    private fun set(exerciseId: Long, date: String, weight: Double, reps: Int, timestamp: Long) =
        WorkoutSet(id = 0, exerciseId = exerciseId, date = date, addedWeightKg = weight, reps = reps, timestamp = timestamp)

    @Test
    fun incrementalWrites_matchFullRebuild() = runBlocking {
        seedExercises()
        repository.insertBodyWeight(BodyWeightEntry(id = 0, date = "2026-08-01", bodyWeightKg = 70.0))
        repository.insertBodyWeight(BodyWeightEntry(id = 0, date = "2026-08-20", bodyWeightKg = 72.0))
        repository.refreshStatsCache()

        val pullSet = repository.insertWorkoutSet(set(1, "2026-08-21", 32.0, 8, 1))
        repository.insertWorkoutSet(set(1, "2026-08-21", 40.0, 2, 2))
        repository.insertWorkoutSet(set(4, "2026-08-21", 30.0, 10, 3))
        repository.insertWorkoutSet(set(2, "2026-08-22", 10.0, 6, 4))
        val raisesSet = repository.insertWorkoutSet(set(6, "2026-08-23", 5.0, 12, 5))

        repository.updateWorkoutSet(
            WorkoutSet(id = raisesSet, exerciseId = 6, date = "2026-08-23", addedWeightKg = 7.5, reps = 12, timestamp = 5)
        )
        repository.updateWorkoutSet(
            WorkoutSet(id = pullSet, exerciseId = 1, date = "2026-08-25", addedWeightKg = 32.0, reps = 8, timestamp = 1)
        )
        repository.deleteWorkoutSetById(pullSet)

        val secondRaises = repository.insertWorkoutSet(set(6, "2026-08-24", 5.0, 15, 6))
        repository.deleteWorkoutSetById(secondRaises)

        val incremental = snapshot()

        repository.refreshStatsCache()
        assertEquals(incremental, snapshot())
    }

    @Test
    fun incrementalWrites_withoutAnyBodyWeight_matchFullRebuild() = runBlocking {
        seedExercises()
        repository.refreshStatsCache()

        repository.insertWorkoutSet(set(1, "2026-08-21", 20.0, 5, 1))
        repository.insertWorkoutSet(set(4, "2026-08-21", 30.0, 10, 2))
        val id = repository.insertWorkoutSet(set(4, "2026-08-22", 35.0, 8, 3))
        repository.deleteWorkoutSetById(id)

        val incremental = snapshot()

        repository.refreshStatsCache()
        assertEquals(incremental, snapshot())
    }

    @Test
    fun deletingEverySet_removesDayAndDailyRowsButKeepsExerciseRow() = runBlocking {
        seedExercises()
        repository.insertBodyWeight(BodyWeightEntry(id = 0, date = "2026-08-01", bodyWeightKg = 70.0))
        repository.refreshStatsCache()

        val id = repository.insertWorkoutSet(set(1, "2026-08-21", 20.0, 5, 1))
        repository.deleteWorkoutSetById(id)

        assertEquals(emptyList<String>(), snapshotWorkoutDays())
        assertEquals(emptyList<String>(), snapshotDaily(1L))

        val stats = db.statsCacheDao().getExerciseStats().first()
        assertEquals(4, stats.size)
        assertEquals(false, stats.first { it.exerciseId == 1L }.hasData)

        val incremental = snapshot()
        repository.refreshStatsCache()
        assertEquals(incremental, snapshot())
    }

    private fun snapshotWorkoutDays(): List<String> {
        val rows = mutableListOf<String>()
        dumpTable("workout_days", workoutDayColumns, "date", rows)
        return rows
    }

    private fun snapshotDaily(exerciseId: Long): List<String> {
        val rows = mutableListOf<String>()
        val db2 = db.openHelper.writableDatabase
        db2.query(
            "SELECT ${dailyColumns.joinToString(", ")} FROM exercise_daily_stats WHERE exerciseId = $exerciseId"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(dailyColumns.joinToString("|") { cursor.getString(cursor.getColumnIndexOrThrow(it)) })
            }
        }
        return rows
    }
}
