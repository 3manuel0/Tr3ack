package com.example.tr3ack.repository

import com.example.tr3ack.data.dao.BodyWeightDao
import com.example.tr3ack.data.dao.ExerciseDao
import com.example.tr3ack.data.dao.WorkoutSetDao
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.data.entity.toDomain
import com.example.tr3ack.data.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Tr3ackRepository(
    private val exerciseDao: ExerciseDao,
    private val bodyWeightDao: BodyWeightDao,
    private val workoutSetDao: WorkoutSetDao
) {
    val allExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises().map { list ->
        list.map { it.toDomain() }
    }

    val bodyweightExercises: Flow<List<Exercise>> = exerciseDao.getBodyweightExercises().map { list ->
        list.map { it.toDomain() }
    }

    val allWorkoutSets: Flow<List<WorkoutSet>> = workoutSetDao.getAllSets().map { list ->
        list.map { it.toDomain() }
    }

    val allBodyWeightEntries: Flow<List<BodyWeightEntry>> = bodyWeightDao.getAllEntries().map { list ->
        list.map { it.toDomain() }
    }

    val allWorkoutDates: Flow<List<String>> = workoutSetDao.getAllWorkoutDates()

    fun getSetsForDate(date: String): Flow<List<WorkoutSet>> =
        workoutSetDao.getSetsForDate(date).map { list -> list.map { it.toDomain() } }

    fun getSetsForExercise(exerciseId: Long): Flow<List<WorkoutSet>> =
        workoutSetDao.getSetsForExercise(exerciseId).map { list -> list.map { it.toDomain() } }

    fun getSetsForExerciseOnDate(exerciseId: Long, date: String): Flow<List<WorkoutSet>> =
        workoutSetDao.getSetsForExerciseOnDateFlow(exerciseId, date).map { list -> list.map { it.toDomain() } }

    suspend fun getExerciseById(id: Long): Exercise? = exerciseDao.getExerciseById(id)?.toDomain()

    suspend fun getLastSetForExercise(exerciseId: Long): WorkoutSet? =
        workoutSetDao.getLastSetForExercise(exerciseId)?.toDomain()

    suspend fun getBodyWeightForDate(date: String): Double? =
        bodyWeightDao.getEntryForDate(date)?.bodyWeightKg

    suspend fun getTodayBodyWeightEntry(date: String): BodyWeightEntry? =
        bodyWeightDao.getEntryForDate(date)?.toDomain()

    suspend fun getMostRecentBodyWeightOnOrBefore(date: String): Double? =
        bodyWeightDao.getMostRecentEntryOnOrBefore(date)?.bodyWeightKg

    suspend fun getEffectiveBodyWeight(date: String): Double? {
        val direct = getBodyWeightForDate(date)
        if (direct != null) return direct
        return getMostRecentBodyWeightOnOrBefore(date)
    }

    suspend fun getWorkoutDatesOnOrBefore(date: String): List<String> =
        workoutSetDao.getWorkoutDatesOnOrBefore(date)

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise.toEntity())

    suspend fun insertBodyWeight(entry: BodyWeightEntry): Long =
        bodyWeightDao.insertOrUpdate(entry.toEntity())

    suspend fun updateBodyWeight(entry: BodyWeightEntry) = bodyWeightDao.update(entry.toEntity())

    suspend fun deleteBodyWeight(entry: BodyWeightEntry) = bodyWeightDao.delete(entry.toEntity())

    suspend fun insertWorkoutSet(set: WorkoutSet): Long = workoutSetDao.insert(set.toEntity())

    suspend fun updateWorkoutSet(set: WorkoutSet) = workoutSetDao.update(set.toEntity())

    suspend fun deleteWorkoutSet(set: WorkoutSet) = workoutSetDao.delete(set.toEntity())

    suspend fun deleteWorkoutSetById(id: Long) = workoutSetDao.deleteById(id)
}
