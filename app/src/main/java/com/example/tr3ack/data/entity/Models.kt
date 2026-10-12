package com.example.tr3ack.data.entity

/** Stable IDs of the built-in seeded exercises. Keep in sync with Tr3ackDatabase seeding. */
object ExerciseIds {
    const val WEIGHTED_PULL_UPS = 1L
    const val WEIGHTED_DIPS = 2L
    const val WEIGHTED_CHIN_UPS = 3L
    const val BICEP_CURLS = 4L
    const val LATERAL_RAISES = 6L
}

data class Exercise(
    val id: Long,
    val name: String,
    val isBodyweightBased: Boolean
)

data class BodyWeightEntry(
    val id: Long,
    val date: String,
    val bodyWeightKg: Double
)

data class WorkoutSet(
    val id: Long,
    val exerciseId: Long,
    val date: String,
    val addedWeightKg: Double,
    val reps: Int,
    val timestamp: Long
)

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    isBodyweightBased = isBodyweightBased
)

fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    isBodyweightBased = isBodyweightBased
)

fun BodyWeightEntity.toDomain() = BodyWeightEntry(
    id = id,
    date = date,
    bodyWeightKg = bodyWeightKg
)

fun BodyWeightEntry.toEntity() = BodyWeightEntity(
    id = id,
    date = date,
    bodyWeightKg = bodyWeightKg
)

fun WorkoutSetEntity.toDomain() = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    date = date,
    addedWeightKg = addedWeightKg,
    reps = reps,
    timestamp = timestamp
)

fun WorkoutSet.toEntity() = WorkoutSetEntity(
    id = id,
    exerciseId = exerciseId,
    date = date,
    addedWeightKg = addedWeightKg,
    reps = reps,
    timestamp = timestamp
)
