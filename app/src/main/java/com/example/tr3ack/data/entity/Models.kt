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

/**
 * What a goal measures. [E1RM] and [WEIGHT] apply to every exercise; [BW_RATIO]
 * is only meaningful for bodyweight-based movements (weighted pull-ups, etc.).
 */
enum class GoalType { E1RM, WEIGHT, BW_RATIO }

data class Goal(
    val exerciseId: Long,
    val type: GoalType,
    val targetValue: Double,
    val createdAt: Long
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

fun GoalEntity.toDomain() = Goal(
    exerciseId = exerciseId,
    type = goalTypeFromName(type),
    targetValue = targetValue,
    createdAt = createdAt
)

fun Goal.toEntity() = GoalEntity(
    exerciseId = exerciseId,
    type = type.name,
    targetValue = targetValue,
    createdAt = createdAt
)

/** Parses a stored goal type, defaulting to [GoalType.E1RM] for unknown values. */
fun goalTypeFromName(name: String): GoalType =
    GoalType.entries.firstOrNull { it.name == name } ?: GoalType.E1RM
