package com.example.tr3ack.viewmodel

import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One exercise's tonnage for the current vs the previous ISO week. */
data class ExerciseWeekVolume(
    val exerciseId: Long,
    val exerciseName: String,
    val thisWeekTonnage: Double,
    val lastWeekTonnage: Double,
)

/**
 * Tonnage / session totals for the current ISO week (Mon-Sun) compared with the
 * previous one, plus the per-exercise breakdown. Derived from the `workout_days`
 * and `exercise_daily_stats` caches so no sets are scanned on read.
 */
data class WeekVolume(
    val thisWeekTonnage: Double = 0.0,
    val lastWeekTonnage: Double = 0.0,
    val thisWeekSessions: Int = 0,
    val lastWeekSessions: Int = 0,
    val thisWeekSets: Int = 0,
    val lastWeekSets: Int = 0,
    val perExercise: List<ExerciseWeekVolume> = emptyList(),
) {
    val hasData: Boolean
        get() = thisWeekTonnage > 0.0 || lastWeekTonnage > 0.0 ||
            thisWeekSessions > 0 || lastWeekSessions > 0

    /** Percent change vs last week, or null when there is no previous week to compare. */
    val tonnageChangePct: Double?
        get() = if (lastWeekTonnage > 0.0) {
            (thisWeekTonnage - lastWeekTonnage) / lastWeekTonnage * 100.0
        } else {
            null
        }
}

/** Monday of the ISO week containing [date] (matches the streak/heatmap week convention). */
fun weekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun buildWeekVolume(
    thisWeekStart: LocalDate,
    days: List<WorkoutDayEntity>,
    daily: List<ExerciseDailyStatsEntity>,
    exercises: List<Exercise>,
): WeekVolume {
    val thisWeekEnd = thisWeekStart.plusWeeks(1).toString()
    val thisWeek = thisWeekStart.toString()
    val lastWeek = thisWeekStart.minusWeeks(1).toString()

    fun inThisWeek(date: String) = date >= thisWeek && date < thisWeekEnd
    fun inLastWeek(date: String) = date >= lastWeek && date < thisWeek

    val thisDays = days.filter { inThisWeek(it.date) }
    val lastDays = days.filter { inLastWeek(it.date) }

    val exercisesById = exercises.associateBy { it.id }
    val perExercise = daily
        .groupBy { it.exerciseId }
        .mapNotNull { (exerciseId, rows) ->
            val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
            val thisTonnage = rows.filter { inThisWeek(it.date) }.sumOf { it.tonnage }
            val lastTonnage = rows.filter { inLastWeek(it.date) }.sumOf { it.tonnage }
            if (thisTonnage <= 0.0 && lastTonnage <= 0.0) {
                null
            } else {
                ExerciseWeekVolume(exercise.id, exercise.name, thisTonnage, lastTonnage)
            }
        }
        .sortedByDescending { it.thisWeekTonnage }

    return WeekVolume(
        thisWeekTonnage = thisDays.sumOf { it.tonnage },
        lastWeekTonnage = lastDays.sumOf { it.tonnage },
        thisWeekSessions = thisDays.size,
        lastWeekSessions = lastDays.size,
        thisWeekSets = thisDays.sumOf { it.setCount },
        lastWeekSets = lastDays.sumOf { it.setCount },
        perExercise = perExercise,
    )
}
