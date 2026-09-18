package com.example.tr3ack.data.entity

import java.util.TreeMap

/**
 * Pure computation of the materialized stats caches. All 1RM / tonnage / body-
 * weight maths live here (instead of in the repository or ViewModels) so they can
 * be unit-tested on the JVM without a database. These functions exactly mirror
 * the SQL that the cache tables are built from.
 */
object StatsCalculator {

    fun effectiveBodyWeight(bwByDate: TreeMap<String, Double>, date: String): Double? =
        bwByDate.floorEntry(date)?.value?.takeIf { it > 0 }

    fun computeExerciseStats(
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

    fun computeDailyStats(
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

    fun computeWorkoutDay(
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