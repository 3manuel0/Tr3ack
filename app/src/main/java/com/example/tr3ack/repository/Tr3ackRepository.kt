package com.example.tr3ack.repository

import androidx.room.withTransaction
import com.example.tr3ack.data.dao.BodyWeightDao
import com.example.tr3ack.data.dao.ExerciseDao
import com.example.tr3ack.data.dao.GoalDao
import com.example.tr3ack.data.dao.StatsCacheDao
import com.example.tr3ack.data.dao.WorkoutSetDao
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.StatsCalculator
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
import com.example.tr3ack.data.entity.toDomain
import com.example.tr3ack.data.entity.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.TreeMap

class Tr3ackRepository(
    private val database: Tr3ackDatabase,
    private val exerciseDao: ExerciseDao,
    private val bodyWeightDao: BodyWeightDao,
    private val workoutSetDao: WorkoutSetDao,
    private val statsCacheDao: StatsCacheDao,
    private val goalDao: GoalDao
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

    /** Active goal per exercise (one row per exercise). */
    val goals: Flow<List<Goal>> = goalDao.getAllGoals().map { list -> list.map { it.toDomain() } }

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

    /** Newest logged body weight, regardless of date. */
    suspend fun getMostRecentBodyWeight(): Double? =
        bodyWeightDao.getMostRecentEntry()?.bodyWeightKg

    suspend fun getEffectiveBodyWeight(date: String): Double? {
        val direct = getBodyWeightForDate(date)
        if (direct != null) return direct
        val before = bodyWeightDao.getMostRecentEntryOnOrBefore(date)
        val after = bodyWeightDao.getOldestEntryOnOrAfter(date)
        if (before != null && after != null) {
            val targetEpoch = LocalDate.parse(date).toEpochDay()
            val beforeDistance = targetEpoch - LocalDate.parse(before.date).toEpochDay()
            val afterDistance = LocalDate.parse(after.date).toEpochDay() - targetEpoch
            return (if (beforeDistance <= afterDistance) before else after).bodyWeightKg
        }
        return (before ?: after)?.bodyWeightKg
    }

    suspend fun getWorkoutDatesOnOrBefore(date: String): List<String> =
        workoutSetDao.getWorkoutDatesOnOrBefore(date)

    suspend fun insertExercise(exercise: Exercise): Long = exerciseDao.insert(exercise.toEntity())

    suspend fun saveGoal(goal: Goal) = goalDao.upsert(goal.toEntity())

    suspend fun deleteGoal(exerciseId: Long) = goalDao.deleteByExerciseId(exerciseId)

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
        refreshStatsCacheFor(setOf(set.exerciseId), setOf(set.date))
        id
    }

    suspend fun updateWorkoutSet(set: WorkoutSet) = database.withTransaction {
        val previous = workoutSetDao.getSetById(set.id)
        workoutSetDao.update(set.toEntity())
        refreshStatsCacheFor(
            exerciseIds = setOfNotNull(previous?.exerciseId, set.exerciseId),
            dates = setOfNotNull(previous?.date, set.date)
        )
    }

    suspend fun deleteWorkoutSet(set: WorkoutSet) = database.withTransaction {
        workoutSetDao.delete(set.toEntity())
        refreshStatsCacheFor(setOf(set.exerciseId), setOf(set.date))
    }

    suspend fun deleteWorkoutSetById(id: Long) = database.withTransaction {
        val previous = workoutSetDao.getSetById(id)
        workoutSetDao.deleteById(id)
        if (previous != null) refreshStatsCacheFor(setOf(previous.exerciseId), setOf(previous.date))
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

        val context = buildCacheContext(exercises, bodyWeightEntries)
        val setsByExercise = sets.groupBy { it.exerciseId }

        val exerciseStats = exercises.map { exercise ->
            StatsCalculator.computeExerciseStats(
                exercise,
                setsByExercise[exercise.id].orEmpty(),
                context.effectiveBodyWeight
            )
        }
        if (exerciseStats.isNotEmpty()) statsCacheDao.insertExerciseStats(exerciseStats)

        val dailyStats = mutableListOf<ExerciseDailyStatsEntity>()
        for ((exerciseId, exerciseSets) in setsByExercise) {
            val exercise = context.exercisesById[exerciseId] ?: continue
            for ((date, daySets) in exerciseSets.groupBy { it.date }) {
                StatsCalculator.computeDailyStats(exercise, date, daySets, context.effectiveBodyWeight)
                    ?.let { dailyStats.add(it) }
            }
        }
        if (dailyStats.isNotEmpty()) statsCacheDao.insertExerciseDailyStats(dailyStats)

        val workoutDays = sets.groupBy { it.date }.map { (date, daySets) ->
            StatsCalculator.computeWorkoutDay(date, daySets, context.exercisesById, context.effectiveBodyWeight)
        }
        if (workoutDays.isNotEmpty()) statsCacheDao.insertWorkoutDays(workoutDays)
    }

    /**
     * Rewrites only the cache rows a set change can affect: the aggregate row of
     * each [exerciseIds] entry, the ([exerciseIds], [dates]) daily rows and the
     * [dates] workout-day rows. Rows that no longer correspond to any set are
     * deleted; rows that do are recomputed from source so the result is identical
     * to [refreshStatsCacheInternal]. Body-weight edits still need the full
     * rebuild because every stored percentage depends on it.
     */
    private suspend fun refreshStatsCacheFor(exerciseIds: Set<Long>, dates: Set<String>) {
        if (exerciseIds.isEmpty() && dates.isEmpty()) return

        val exercises = exerciseDao.getAllExercisesSync()
        val context = buildCacheContext(exercises, bodyWeightDao.getAllEntriesSync())

        val exerciseStats = exerciseIds.mapNotNull { id ->
            context.exercisesById[id]?.let { exercise ->
                StatsCalculator.computeExerciseStats(
                    exercise,
                    workoutSetDao.getSetsForExerciseSync(id),
                    context.effectiveBodyWeight
                )
            }
        }
        if (exerciseStats.isNotEmpty()) statsCacheDao.insertExerciseStats(exerciseStats)

        for (id in exerciseIds) {
            val exercise = context.exercisesById[id] ?: continue
            for (date in dates) {
                val daySets = workoutSetDao.getSetsForExerciseOnDate(id, date)
                val row = StatsCalculator.computeDailyStats(exercise, date, daySets, context.effectiveBodyWeight)
                if (row == null) {
                    statsCacheDao.deleteDailyStats(id, date)
                } else {
                    statsCacheDao.insertExerciseDailyStats(listOf(row))
                }
            }
        }

        for (date in dates) {
            val daySets = workoutSetDao.getSetsForDateSync(date)
            if (daySets.isEmpty()) {
                statsCacheDao.deleteWorkoutDay(date)
            } else {
                statsCacheDao.insertWorkoutDays(
                    listOf(StatsCalculator.computeWorkoutDay(date, daySets, context.exercisesById, context.effectiveBodyWeight))
                )
            }
        }
    }

    private fun buildCacheContext(
        exercises: List<ExerciseEntity>,
        bodyWeightEntries: List<BodyWeightEntity>
    ): CacheContext {
        val bwByDate = TreeMap<String, Double>()
        for (entry in bodyWeightEntries) bwByDate[entry.date] = entry.bodyWeightKg
        return CacheContext(
            exercisesById = exercises.associateBy { it.id },
            effectiveBodyWeight = { date -> StatsCalculator.effectiveBodyWeight(bwByDate, date) }
        )
    }

    private data class CacheContext(
        val exercisesById: Map<Long, ExerciseEntity>,
        val effectiveBodyWeight: (String) -> Double?
    )
}
