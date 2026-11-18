package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.data.entity.WorkoutSet

/**
 * A consistent snapshot of everything the Goals screen shows: the achievements
 * plus the level/streak/tonnage totals they are derived from.
 */
data class AchievementSnapshot(
    val achievements: List<Achievement>,
    val level: LevelInfo,
    val streak: StreakInfo,
    val totalTonnage: Double,
    val totalSessions: Int,
)

/**
 * Single place that turns cached stats into achievements. Used by the Goals
 * screen and by the log-set flow (which diffs two snapshots to detect unlocks).
 */
object AchievementSnapshotBuilder {

    fun build(
        workoutDays: List<WorkoutDayEntity>,
        exerciseStats: List<ExerciseStatsEntity>,
        bodyWeightEntries: List<BodyWeightEntry>,
        workoutSets: List<WorkoutSet> = emptyList()
    ): AchievementSnapshot {
        val sessionDates = workoutDays.map { it.date }.sorted()
        val totalTonnage = workoutDays.sumOf { it.tonnage }
        val totalSessions = workoutDays.size
        val streak = AchievementRules.computeStreak(sessionDates)

        val maxBodyweightRatio = exerciseStats
            .filter { it.isBodyweightBased }
            .maxOfOrNull { it.maxBodyweightRatio } ?: 0.0
        val maxBicepCurlWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.BICEP_CURLS }?.maxAddedWeightGte6 ?: 0.0
        val maxLateralRaiseWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.LATERAL_RAISES }?.maxAddedWeightGte6 ?: 0.0
        // The stats cache stores system weight for bodyweight lifts, so the added
        // weight on the belt has to come from the sets themselves.
        val maxAddedGte6 = workoutSets
            .filter { it.reps >= 6 }
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) -> sets.maxOf { it.addedWeightKg } }
        val maxPullUpWeight = maxAddedGte6[ExerciseIds.WEIGHTED_PULL_UPS] ?: 0.0
        val maxDipWeight = maxAddedGte6[ExerciseIds.WEIGHTED_DIPS] ?: 0.0
        val prExerciseCount = exerciseStats.count { it.hasData && it.bestE1RM > 0.0 }
        val bodyWeightCheckins = bodyWeightEntries
            .filter { it.bodyWeightKg > 0.0 }
            .map { it.date }
            .distinct()
            .size

        return AchievementSnapshot(
            achievements = AchievementRules.buildAchievements(
                totalTonnage = totalTonnage,
                totalSessions = totalSessions,
                currentStreak = streak.current,
                longestStreak = streak.longest,
                maxBodyweightRatio = maxBodyweightRatio,
                maxBicepCurlWeight = maxBicepCurlWeight,
                maxLateralRaiseWeight = maxLateralRaiseWeight,
                prExerciseCount = prExerciseCount,
                bodyWeightCheckins = bodyWeightCheckins,
                maxPullUpWeight = maxPullUpWeight,
                maxDipWeight = maxDipWeight
            ),
            level = AchievementRules.computeLevel(totalTonnage, totalSessions),
            streak = streak,
            totalTonnage = totalTonnage,
            totalSessions = totalSessions
        )
    }
}
