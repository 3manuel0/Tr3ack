package com.example.tr3ack.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One active target per exercise. [type] stores the [GoalType] name so the enum can
 * evolve without renumbering; [createdAt] preserves the original creation time when
 * a goal is edited (upsert replaces the whole row). [targetReps] qualifies
 * [GoalType.WEIGHT] goals ("working weight × reps"); it is 0 for the other types.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val exerciseId: Long,
    val type: String,
    val targetValue: Double,
    @ColumnInfo(defaultValue = "0") val targetReps: Int = 0,
    val createdAt: Long
)
