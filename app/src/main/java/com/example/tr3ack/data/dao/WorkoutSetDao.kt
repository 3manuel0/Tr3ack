package com.example.tr3ack.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.tr3ack.data.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets ORDER BY date DESC, timestamp ASC")
    fun getAllSets(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY timestamp ASC")
    fun getSetsForDate(date: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastSetForExercise(exerciseId: Long): WorkoutSetEntity?

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY timestamp DESC")
    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND date = :date ORDER BY timestamp ASC")
    suspend fun getSetsForExerciseOnDate(exerciseId: Long, date: String): List<WorkoutSetEntity>

    @Query("SELECT DISTINCT date FROM workout_sets ORDER BY date DESC")
    fun getAllWorkoutDates(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM workout_sets WHERE date <= :date ORDER BY date DESC")
    suspend fun getWorkoutDatesOnOrBefore(date: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(set: WorkoutSetEntity): Long

    @Update
    suspend fun update(set: WorkoutSetEntity)

    @Delete
    suspend fun delete(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND date = :date ORDER BY timestamp ASC")
    fun getSetsForExerciseOnDateFlow(exerciseId: Long, date: String): Flow<List<WorkoutSetEntity>>
}
