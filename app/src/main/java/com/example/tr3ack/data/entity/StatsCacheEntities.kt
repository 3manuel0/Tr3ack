package com.example.tr3ack.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Materialized stats cache. Expensive aggregates (best set / e1RM, daily chart
 * series, per-day workout totals) are recomputed once per write instead of being
 * recalculated on every screen read. Columns are ordered to match the CREATE
 * TABLE statements used in MIGRATION_3_4.
 */
@Entity(tableName = "exercise_stats")
data class ExerciseStatsEntity(
    @PrimaryKey val exerciseId: Long,
    val isBodyweightBased: Boolean,
    val hasData: Boolean,
    val bestE1RM: Double,
    val bestE1RMAddedWeight: Double,
    val bestE1RMReps: Int,
    val bestE1RMTotalSystemWeight: Double,
    val bestE1RMPercentBodyWeight: Double,
    val bestE1RMDate: String,
    val bestE1RMBodyWeightKg: Double,
    val maxAddedWeightGte6: Double,
    val maxBodyweightRatio: Double,
    val lastLoggedDate: String,
)

@Entity(tableName = "exercise_daily_stats", primaryKeys = ["exerciseId", "date"])
data class ExerciseDailyStatsEntity(
    val exerciseId: Long,
    val date: String,
    val isBodyweightBased: Boolean,
    val firstSetTSW: Double,
    val firstSetReps: Int,
    val firstSetAddedWeight: Double,
    val firstSetPercentBodyWeight: Double,
    val e1rm: Double,
    val tonnage: Double,
    val bodyWeightKg: Double,
)

@Entity(tableName = "workout_days")
data class WorkoutDayEntity(
    @PrimaryKey val date: String,
    val setCount: Int,
    val tonnage: Double,
)