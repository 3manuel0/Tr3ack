package com.example.tr3ack.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    indices = [
        Index("date"),
        Index("exerciseId")
    ]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: String,
    val addedWeightKg: Double,
    val reps: Int,
    val timestamp: Long,
    /** Reps in reserve left at the end of the set (0 = taken to failure). */
    @ColumnInfo(defaultValue = "0")
    val rir: Int = 0
)
