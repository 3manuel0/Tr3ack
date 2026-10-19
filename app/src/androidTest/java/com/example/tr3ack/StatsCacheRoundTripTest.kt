package com.example.tr3ack

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.StatsCalculator
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.repository.Tr3ackRepository
import java.util.TreeMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsCacheRoundTripTest {

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

    @Test
    fun refreshStatsCache_materializesTheDemoDataset() = runBlocking {
        val pullUps = ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true)
        val bicepCurls = ExerciseEntity(id = 4, name = "Bicep Curls", isBodyweightBased = false)
        db.exerciseDao().insertAll(listOf(pullUps, bicepCurls))
        db.bodyWeightDao().insertOrUpdate(BodyWeightEntity(date = "2026-08-21", bodyWeightKg = 70.0))
        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 32.0, reps = 8, timestamp = 1),
                WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 40.0, reps = 2, timestamp = 2),
                WorkoutSetEntity(exerciseId = 4, date = "2026-08-21", addedWeightKg = 30.0, reps = 10, timestamp = 3),
                WorkoutSetEntity(exerciseId = 4, date = "2026-08-21", addedWeightKg = 16.0, reps = 10, timestamp = 4),
            )
        )

        repository.refreshStatsCache()

        val stats = db.statsCacheDao().getExerciseStats().first()
        assertEquals(2, stats.size)

        val pullStats = stats.first { it.exerciseId == 1L }
        assertEquals(128.8120700, pullStats.bestE1RM, 1e-6)  // 102 kg TSW x 1.2628634
        assertEquals(1.57142857, pullStats.maxBodyweightRatio, 1e-6)

        val curlStats = stats.first { it.exerciseId == 4L }
        assertEquals(39.2802820, curlStats.bestE1RM, 1e-6)   // Mayhew at 10 reps
        assertEquals(30.0, curlStats.maxAddedWeightGte6, 1e-9)

        val daily = db.statsCacheDao().getDailyStatsForExercise(1L).first()
        assertEquals(1, daily.size)
        assertEquals(1036.0, daily[0].tonnage, 1e-9)         // 102*8 + 110*2
        assertEquals(128.8120700, daily[0].e1rm, 1e-6)

        val workoutDays = db.statsCacheDao().getWorkoutDays().first()
        assertEquals(1, workoutDays.size)
        assertEquals(4, workoutDays[0].setCount)
        assertEquals(1496.0, workoutDays[0].tonnage, 1e-9)   // 1036 + 460
    }

    private fun exerciseStatsRow(exerciseId: Long) = runBlocking {
        db.statsCacheDao().getExerciseStats().first().first { it.exerciseId == exerciseId }
    }

    @Test
    fun editingSet_refreshesAffectedCacheRows() = runBlocking {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true))
        )
        db.bodyWeightDao().insertOrUpdate(BodyWeightEntity(date = "2026-08-21", bodyWeightKg = 70.0))
        val id = repository.insertWorkoutSet(
            WorkoutSet(id = 0, exerciseId = 1, date = "2026-08-21", addedWeightKg = 20.0, reps = 5, timestamp = 1)
        )
        assertEquals(107.1096124, exerciseStatsRow(1L).bestE1RM, 1e-6) // TSW 90 @ 5 reps

        repository.updateWorkoutSet(
            WorkoutSet(id = id, exerciseId = 1, date = "2026-08-21", addedWeightKg = 20.0, reps = 8, timestamp = 1)
        )
        assertEquals(113.6577088, exerciseStatsRow(1L).bestE1RM, 1e-6) // TSW 90 @ 8 reps
    }

    @Test
    fun deletingSet_refreshesAffectedCacheRows() = runBlocking {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true))
        )
        db.bodyWeightDao().insertOrUpdate(BodyWeightEntity(date = "2026-08-21", bodyWeightKg = 70.0))
        val heavyId = repository.insertWorkoutSet(
            WorkoutSet(id = 0, exerciseId = 1, date = "2026-08-21", addedWeightKg = 40.0, reps = 2, timestamp = 1)
        )
        repository.insertWorkoutSet(
            WorkoutSet(id = 0, exerciseId = 1, date = "2026-08-21", addedWeightKg = 20.0, reps = 5, timestamp = 2)
        )
        assertEquals(122.5825464, exerciseStatsRow(1L).bestE1RM, 1e-6) // best is the 40kg set

        repository.deleteWorkoutSetById(heavyId)
        assertEquals(107.1096124, exerciseStatsRow(1L).bestE1RM, 1e-6) // falls back to 20kg set

        val day = db.statsCacheDao().getDailyStatsForExercise(1L).first().first()
        assertEquals(450.0, day.tonnage, 1e-9) // only the 20kg x 5 set remains
    }

    @Test
    fun bodyWeightChange_refreshesLaterDependentRows() = runBlocking {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true))
        )
        // Workout on 2026-08-20 has no body weight of its own; nearest entry wins.
        db.workoutSetDao().insertAll(
            listOf(WorkoutSetEntity(exerciseId = 1, date = "2026-08-20", addedWeightKg = 20.0, reps = 5, timestamp = 1))
        )
        repository.insertBodyWeight(BodyWeightEntry(id = 0, date = "2026-08-01", bodyWeightKg = 70.0))
        var day = db.statsCacheDao().getDailyStatsForExercise(1L).first().first()
        assertEquals(90.0, day.firstSetTSW, 1e-9) // 70 + 20

        // A nearer entry (1 day before the workout) must now be used.
        val added = repository.insertBodyWeight(BodyWeightEntry(id = 0, date = "2026-08-19", bodyWeightKg = 80.0))
        day = db.statsCacheDao().getDailyStatsForExercise(1L).first().first()
        assertEquals(100.0, day.firstSetTSW, 1e-9) // 80 + 20

        // Editing that closer entry refreshes the dependent day again.
        repository.updateBodyWeight(BodyWeightEntry(id = added, date = "2026-08-19", bodyWeightKg = 85.0))
        day = db.statsCacheDao().getDailyStatsForExercise(1L).first().first()
        assertEquals(105.0, day.firstSetTSW, 1e-9) // 85 + 20

        // Deleting it falls back to the older entry.
        repository.deleteBodyWeight(BodyWeightEntry(id = added, date = "2026-08-19", bodyWeightKg = 85.0))
        day = db.statsCacheDao().getDailyStatsForExercise(1L).first().first()
        assertEquals(90.0, day.firstSetTSW, 1e-9) // 70 + 20
    }

    @Test
    fun cacheMatchesStatsCalculatorOnSameSampleData() = runBlocking {
        val pullUps = ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true)
        val bicepCurls = ExerciseEntity(id = 4, name = "Bicep Curls", isBodyweightBased = false)
        db.exerciseDao().insertAll(listOf(pullUps, bicepCurls))
        db.bodyWeightDao().insertOrUpdate(BodyWeightEntity(date = "2026-08-21", bodyWeightKg = 70.0))
        val pullSets = listOf(
            WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 32.0, reps = 8, timestamp = 1),
            WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 40.0, reps = 2, timestamp = 2),
        )
        val curlSets = listOf(
            WorkoutSetEntity(exerciseId = 4, date = "2026-08-21", addedWeightKg = 19.0, reps = 9, timestamp = 3),
        )
        db.workoutSetDao().insertAll(pullSets + curlSets)

        repository.refreshStatsCache()

        val bwByDate = TreeMap<String, Double>().apply { this["2026-08-21"] = 70.0 }
        val effectiveBodyWeight: (String) -> Double? = { StatsCalculator.effectiveBodyWeight(bwByDate, it) }

        val expectedPull = StatsCalculator.computeExerciseStats(pullUps, pullSets, effectiveBodyWeight)
        val actualPull = exerciseStatsRow(1L)
        assertEquals(expectedPull.bestE1RM, actualPull.bestE1RM, 1e-9)
        assertEquals(expectedPull.bestE1RMReps, actualPull.bestE1RMReps)
        assertEquals(expectedPull.bestE1RMDate, actualPull.bestE1RMDate)

        val expectedCurl = StatsCalculator.computeExerciseStats(bicepCurls, curlSets, effectiveBodyWeight)
        val actualCurl = exerciseStatsRow(4L)
        assertEquals(expectedCurl.bestE1RM, actualCurl.bestE1RM, 1e-9)
        assertEquals(24.4401215, actualCurl.bestE1RM, 1e-6) // Mayhew at 9 reps
    }

    @Test
    fun refreshStatsCache_clearsCacheBeforeRebuild() = runBlocking {
        db.exerciseDao().insertAll(
            listOf(ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true))
        )
        db.bodyWeightDao().insertOrUpdate(BodyWeightEntity(date = "2026-08-21", bodyWeightKg = 70.0))
        db.workoutSetDao().insertAll(
            listOf(
                WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 20.0, reps = 6, timestamp = 1),
                WorkoutSetEntity(exerciseId = 1, date = "2026-08-21", addedWeightKg = 40.0, reps = 4, timestamp = 2),
            )
        )
        repository.refreshStatsCache()
        assertEquals(1, db.statsCacheDao().getWorkoutDays().first().size)

        db.workoutSetDao().deleteAll()
        repository.refreshStatsCache()
        assertEquals(0, db.statsCacheDao().getWorkoutDays().first().size)
        assertEquals(0, db.statsCacheDao().getDailyStatsForExercise(1L).first().size)
        // One stats row per exercise is retained with hasData=false so exercise
        // lists still render an (empty) entry.
        val stats = db.statsCacheDao().getExerciseStats().first()
        assertEquals(1, stats.size)
        assertEquals(false, stats[0].hasData)
    }
}