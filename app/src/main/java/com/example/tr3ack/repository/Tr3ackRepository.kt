package com.example.tr3ack.repository

import androidx.room.withTransaction
import com.example.tr3ack.data.dao.BodyWeightDao
import com.example.tr3ack.data.dao.ExerciseDao
import com.example.tr3ack.data.dao.StatsCacheDao
import com.example.tr3ack.data.dao.WorkoutSetDao
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.data.entity.toDomain
import com.example.tr3ack.data.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.TreeMap

class Tr3ackRepository(
    private val database: Tr3ackDatabase,
    private val exerciseDao: ExerciseDao,
    private val bodyWeightDao: BodyWeightDao,
    private val workoutSetDao: WorkoutSetDao,
    private val statsCacheDao: StatsCacheDao
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

    /**
     * Materialized stats caches. These are recomputed inside the transaction that
     * mutates source data, so reads never scan workout sets or body weights again.
     */
    val exerciseStats: Flow<List<ExerciseStatsEntity>> = statsCacheDao.getExerciseStats()

    val workoutDays: Flow<List<WorkoutDayEntity>> = statsCacheDao.getWorkoutDays()

    val workoutDatesCached: Flow<List<String>> = statsCacheDao.getWorkoutDatesCached()

    fun getDailyStatsForExercise(exerciseId: Long): Flow<List<ExerciseDailyStatsEntity>> =
        statsCacheDao.getDailyStatsForExercise(exerciseId)

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

    suspend fun insertBodyWeight(entry: BodyWeightEntry): Long = database.withTransaction {
        val id = bodyWeightDao.insertOrUpdate(entry.toEntity())
        refreshStatsCacheInternal()
        id
    }

    suspend fun updateBodyWeight(entry: BodyWeightEntry) = database.withTransaction {
        bodyWeightDao.update(entry.toEntity())
        refreshStatsCacheInternal()
    }

    suspend fun deleteBodyWeight(entry: BodyWeightEntry) = database.withTransaction {
        bodyWeightDao.delete(entry.toEntity())
        refreshStatsCacheInternal()
    }

    suspend fun insertWorkoutSet(set: WorkoutSet): Long = database.withTransaction {
        val id = workoutSetDao.insert(set.toEntity())
        refreshStatsCacheInternal()
        id
    }

    suspend fun updateWorkoutSet(set: WorkoutSet) = database.withTransaction {
        workoutSetDao.update(set.toEntity())
        refreshStatsCacheInternal()
    }

    suspend fun deleteWorkoutSet(set: WorkoutSet) = database.withTransaction {
        workoutSetDao.delete(set.toEntity())
        refreshStatsCacheInternal()
    }

    suspend fun deleteWorkoutSetById(id: Long) = database.withTransaction {
        workoutSetDao.deleteById(id)
        refreshStatsCacheInternal()
    }

    /**
     * Atomically replaces all data with the given backup payload. Either the whole
     * restore succeeds or nothing is changed.
     */
    suspend fun replaceAllData(
        exercises: List<ExerciseEntity>,
        workoutSets: List<WorkoutSetEntity>,
        bodyWeightEntries: List<BodyWeightEntity>
    ) = database.withTransaction {
        workoutSetDao.deleteAll()
        bodyWeightDao.deleteAll()
        exerciseDao.deleteAll()
        exerciseDao.insertAll(exercises)
        workoutSetDao.insertAll(workoutSets)
        bodyWeightDao.insertAll(bodyWeightEntries)
        refreshStatsCacheInternal()
    }

    /**
     * Rebuilds the stats caches from the raw workout/body-weight/exercise rows.
     * Runs at startup (after a migration this backfills the new tables) and is a
     * no-op-safe for empty databases.
     */
    suspend fun refreshStatsCache() = database.withTransaction {
        refreshStatsCacheInternal()
    }

    private suspend fun refreshStatsCacheInternal() {
        val exercises = exerciseDao.getAllExercisesSync()
        val sets = workoutSetDao.getAllSetsSync()
        val bodyWeightEntries = bodyWeightDao.getAllEntriesSync()

        statsCacheDao.clearExerciseStats()
        statsCacheDao.clearExerciseDailyStats()
        statsCacheDao.clearWorkoutDays()

        if (exercises.isEmpty()) return

        val bwByDate = TreeMap<String, Double>()
        for (entry in bodyWeightEntries) bwByDate[entry.date] = entry.bodyWeightKg
        fun effectiveBodyWeight(date: String): Double? =
            bwByDate.floorEntry(date)?.value?.takeIf { it > 0 }

        val exercisesById = exercises.associateBy { it.id }

        val exerciseStats = exercises.map { exercise ->
            computeExerciseStats(exercise, sets.filter { it.exerciseId == exercise.id }, ::effectiveBodyWeight)
        }
        if (exerciseStats.isNotEmpty()) statsCacheDao.insertExerciseStats(exerciseStats)

        val dailyStats = mutableListOf<ExerciseDailyStatsEntity>()
        for (exercise in exercises) {
            val grouped = sets.filter { it.exerciseId == exercise.id }.groupBy { it.date }
            for ((date, daySets) in grouped) {
                computeDailyStats(exercise, date, daySets, ::effectiveBodyWeight)?.let { dailyStats.add(it) }
            }
        }
        if (dailyStats.isNotEmpty()) statsCacheDao.insertExerciseDailyStats(dailyStats)

        val workoutDays = sets.groupBy { it.date }.map { (date, daySets) ->
            computeWorkoutDay(date, daySets, exercisesById, ::effectiveBodyWeight)
        }
        if (workoutDays.isNotEmpty()) statsCacheDao.insertWorkoutDays(workoutDays)
    }

    private fun computeExerciseStats(
        exercise: ExerciseEntity,
        sets: List<WorkoutSetEntity>,
        effectiveBodyWeight: (String) -> Double?
    ): ExerciseStatsEntity {
        val bodyweight = exercise.isBodyweightBased

        var bestE1RM = 0.0
        var bestAdded = 0.0
        var bestReps = 0
        var bestTSW = 0.0
        var bestPct = 0.0
        var bestDate = ""
        var bestBw = 0.0
        var maxGte6 = 0.0
        var maxRatio = 0.0
        var lastDate = ""

        val movementType = MovementFactors.getMovementType(exercise.name)

        for (set in sets) {
            if (set.date > lastDate) lastDate = set.date
            if (set.reps <= 0) continue

            val e1rm: Double
            val tsw: Double
            val pct: Double
            val bwAtSet: Double

            if (bodyweight) {
                val bw = effectiveBodyWeight(set.date) ?: continue
                tsw = bw + set.addedWeightKg
                e1rm = tsw * MovementFactors.getMovementFactor(movementType, set.reps)
                val ratio = tsw / bw
                if (ratio > maxRatio) maxRatio = ratio
                pct = (tsw / bw) * 100.0
                bwAtSet = bw
            } else {
                tsw = set.addedWeightKg
                e1rm = tsw * (1.0 + set.reps / 30.0)
                if (set.reps >= 6 && tsw > maxGte6) maxGte6 = tsw
                pct = effectiveBodyWeight(set.date)?.let { if (it > 0) (tsw / it) * 100.0 else 0.0 } ?: 0.0
                bwAtSet = effectiveBodyWeight(set.date) ?: 0.0
            }

            val isBetter = e1rm > bestE1RM ||
                (e1rm == bestE1RM && bestE1RM != 0.0 && set.reps < bestReps)
            if (isBetter) {
                bestE1RM = e1rm
                bestAdded = set.addedWeightKg
                bestReps = set.reps
                bestTSW = tsw
                bestPct = pct
                bestDate = set.date
                bestBw = bwAtSet
            }
        }

        return ExerciseStatsEntity(
            exerciseId = exercise.id,
            isBodyweightBased = bodyweight,
            hasData = bestE1RM > 0.0 || lastDate.isNotEmpty(),
            bestE1RM = bestE1RM,
            bestE1RMAddedWeight = bestAdded,
            bestE1RMReps = bestReps,
            bestE1RMTotalSystemWeight = bestTSW,
            bestE1RMPercentBodyWeight = bestPct,
            bestE1RMDate = bestDate,
            bestE1RMBodyWeightKg = bestBw,
            maxAddedWeightGte6 = maxGte6,
            maxBodyweightRatio = maxRatio,
            lastLoggedDate = lastDate
        )
    }

    private fun computeDailyStats(
        exercise: ExerciseEntity,
        date: String,
        sets: List<WorkoutSetEntity>,
        effectiveBodyWeight: (String) -> Double?
    ): ExerciseDailyStatsEntity? {
        val bodyweight = exercise.isBodyweightBased
        val bodyWeight = if (bodyweight) effectiveBodyWeight(date) else null
        if (bodyweight && bodyWeight == null) return null

        val firstSet = sets.minByOrNull { it.timestamp } ?: return null
        val movementType = MovementFactors.getMovementType(exercise.name)
        val bw = bodyWeight ?: 0.0

        val bestE1RM = sets
            .filter { it.reps > 0 }
            .maxOfOrNull { set ->
                if (bodyweight) {
                    (bw + set.addedWeightKg) * MovementFactors.getMovementFactor(movementType, set.reps)
                } else {
                    set.addedWeightKg * (1.0 + set.reps / 30.0)
                }
            } ?: 0.0

        val tonnage = sets
            .filter { it.reps > 0 }
            .sumOf { set ->
                if (bodyweight) (bw + set.addedWeightKg) * set.reps else set.addedWeightKg * set.reps
            }

        val firstTSW = if (bodyweight) bw + firstSet.addedWeightKg else firstSet.addedWeightKg
        val firstPct = if (bodyweight && bw > 0) (firstTSW / bw) * 100.0 else 0.0

        return ExerciseDailyStatsEntity(
            exerciseId = exercise.id,
            date = date,
            isBodyweightBased = bodyweight,
            firstSetTSW = firstTSW,
            firstSetReps = firstSet.reps,
            firstSetAddedWeight = firstSet.addedWeightKg,
            firstSetPercentBodyWeight = firstPct,
            e1rm = bestE1RM,
            tonnage = tonnage,
            bodyWeightKg = if (bodyweight) bw else 0.0
        )
    }

    private fun computeWorkoutDay(
        date: String,
        sets: List<WorkoutSetEntity>,
        exercisesById: Map<Long, ExerciseEntity>,
        effectiveBodyWeight: (String) -> Double?
    ): WorkoutDayEntity {
        var tonnage = 0.0
        for (set in sets) {
            if (set.reps <= 0) continue
            val exercise = exercisesById[set.exerciseId] ?: continue
            if (exercise.isBodyweightBased) {
                val bw = effectiveBodyWeight(set.date)
                if (bw != null) tonnage += (bw + set.addedWeightKg) * set.reps
            } else {
                tonnage += set.addedWeightKg * set.reps
            }
        }
        return WorkoutDayEntity(
            date = date,
            setCount = sets.size,
            tonnage = tonnage
        )
    }
}