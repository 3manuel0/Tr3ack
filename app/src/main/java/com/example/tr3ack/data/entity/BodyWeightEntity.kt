package com.example.tr3ack.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_weight_entries")
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val bodyWeightKg: Double
)
