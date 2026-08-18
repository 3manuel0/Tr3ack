package com.example.tr3ack.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sets")
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: String,
    val addedWeightKg: Double,
    val reps: Int,
    val timestamp: Long
)
