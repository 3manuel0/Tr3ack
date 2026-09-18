package com.example.tr3ack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsCacheDao {
    @Query("SELECT * FROM exercise_stats ORDER BY exerciseId ASC")
    fun getExerciseStats(): Flow<List<ExerciseStatsEntity>>

    @Query("SELECT * FROM exercise_daily_stats WHERE exerciseId = :exerciseId ORDER BY date ASC")
    fun getDailyStatsForExercise(exerciseId: Long): Flow<List<ExerciseDailyStatsEntity>>

    @Query("SELECT * FROM workout_days ORDER BY date ASC")
    fun getWorkoutDays(): Flow<List<WorkoutDayEntity>>

    @Query("SELECT date FROM workout_days ORDER BY date DESC")
    fun getWorkoutDatesCached(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseStats(rows: List<ExerciseStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseDailyStats(rows: List<ExerciseDailyStatsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutDays(rows: List<WorkoutDayEntity>)

    @Query("DELETE FROM exercise_stats")
    suspend fun clearExerciseStats()

    @Query("DELETE FROM exercise_daily_stats")
    suspend fun clearExerciseDailyStats()

    @Query("DELETE FROM workout_days")
    suspend fun clearWorkoutDays()
}