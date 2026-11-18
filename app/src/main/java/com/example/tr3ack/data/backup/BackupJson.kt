package com.example.tr3ack.data.backup

import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The result of parsing a backup file back into entities. */
data class BackupPayload(
    val exercises: List<ExerciseEntity>,
    val sets: List<WorkoutSetEntity>,
    val bodyWeightEntries: List<BodyWeightEntity>
)

/**
 * Single source of truth for the JSON backup format used by both the manual
 * export and the automatic backup/restore, so they can never drift apart.
 */
object BackupJson {

    val knownExerciseIds = setOf(
        ExerciseIds.WEIGHTED_PULL_UPS,
        ExerciseIds.WEIGHTED_DIPS,
        ExerciseIds.BICEP_CURLS,
        ExerciseIds.LATERAL_RAISES
    )

    fun build(
        allExercises: List<Exercise>,
        allSets: List<WorkoutSet>,
        allBodyWeight: List<BodyWeightEntry>
    ): String {
        val root = JSONObject()
        root.put("meta", JSONObject().apply {
            put("version", 1)
            put("exportDate", LocalDate.now().toString())
            put("exerciseCount", allExercises.size)
            put("workoutSetCount", allSets.size)
            put("bodyWeightEntryCount", allBodyWeight.size)
        })

        val exercisesArr = JSONArray()
        for (ex in allExercises) {
            exercisesArr.put(JSONObject().apply {
                put("id", ex.id)
                put("name", ex.name)
                put("isBodyweightBased", ex.isBodyweightBased)
            })
        }
        root.put("exercises", exercisesArr)

        val setsArr = JSONArray()
        for (set in allSets) {
            setsArr.put(JSONObject().apply {
                put("id", set.id)
                put("exerciseId", set.exerciseId)
                put("date", set.date)
                put("addedWeightKg", set.addedWeightKg)
                put("reps", set.reps)
                put("timestamp", set.timestamp)
            })
        }
        root.put("workoutSets", setsArr)

        val bwArr = JSONArray()
        for (bw in allBodyWeight) {
            bwArr.put(JSONObject().apply {
                put("id", bw.id)
                put("date", bw.date)
                put("bodyWeightKg", bw.bodyWeightKg)
            })
        }
        root.put("bodyWeightEntries", bwArr)

        return root.toString(2)
    }

    /**
     * Parses a backup and drops any exercise that is not one of the four known
     * lifts (same whitelist the manual import uses).
     */
    fun parse(jsonString: String): BackupPayload {
        val root = JSONObject(jsonString)
        val exercisesArr = root.getJSONArray("exercises")
        val setsArr = root.getJSONArray("workoutSets")
        val bwArr = root.getJSONArray("bodyWeightEntries")

        val exercises = mutableListOf<ExerciseEntity>()
        for (i in 0 until exercisesArr.length()) {
            val obj = exercisesArr.getJSONObject(i)
            val id = obj.getLong("id")
            if (id !in knownExerciseIds) continue
            exercises.add(
                ExerciseEntity(
                    id = id,
                    name = obj.getString("name"),
                    isBodyweightBased = obj.getBoolean("isBodyweightBased")
                )
            )
        }

        val sets = mutableListOf<WorkoutSetEntity>()
        for (i in 0 until setsArr.length()) {
            val obj = setsArr.getJSONObject(i)
            val exerciseId = obj.getLong("exerciseId")
            if (exerciseId !in knownExerciseIds) continue
            sets.add(
                WorkoutSetEntity(
                    id = obj.getLong("id"),
                    exerciseId = exerciseId,
                    date = obj.getString("date"),
                    addedWeightKg = obj.getDouble("addedWeightKg"),
                    reps = obj.getInt("reps"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }

        val bwEntries = mutableListOf<BodyWeightEntity>()
        for (i in 0 until bwArr.length()) {
            val obj = bwArr.getJSONObject(i)
            bwEntries.add(
                BodyWeightEntity(
                    id = obj.getLong("id"),
                    date = obj.getString("date"),
                    bodyWeightKg = obj.getDouble("bodyWeightKg")
                )
            )
        }

        return BackupPayload(exercises, sets, bwEntries)
    }
}
