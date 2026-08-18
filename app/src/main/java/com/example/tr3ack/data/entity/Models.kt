package com.example.tr3ack.data.entity

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
) {
    val totalSystemWeight: Double? = null
    val percentOfBodyWeight: Double? = null
}

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
