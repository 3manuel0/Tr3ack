package com.example.tr3ack

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.repository.Tr3ackRepository
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
            db.statsCacheDao()
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
        assertEquals(126.48, pullStats.bestE1RM, 1e-6)       // 102 kg TSW x 1.240
        assertEquals(1.57142857, pullStats.maxBodyweightRatio, 1e-6)

        val curlStats = stats.first { it.exerciseId == 4L }
        assertEquals(40.0, curlStats.bestE1RM, 1e-6)
        assertEquals(30.0, curlStats.maxAddedWeightGte6, 1e-9)

        val daily = db.statsCacheDao().getDailyStatsForExercise(1L).first()
        assertEquals(1, daily.size)
        assertEquals(1036.0, daily[0].tonnage, 1e-9)         // 102*8 + 110*2
        assertEquals(126.48, daily[0].e1rm, 1e-6)

        val workoutDays = db.statsCacheDao().getWorkoutDays().first()
        assertEquals(1, workoutDays.size)
        assertEquals(4, workoutDays[0].setCount)
        assertEquals(1496.0, workoutDays[0].tonnage, 1e-9)   // 1036 + 460
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