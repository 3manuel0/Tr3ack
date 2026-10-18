package com.example.tr3ack.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One active target per exercise. [type] stores the [GoalType] name so the enum can
 * evolve without renumbering; [createdAt] preserves the original creation time when
 * a goal is edited (upsert replaces the whole row).
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val exerciseId: Long,
    val type: String,
    val targetValue: Double,
    val createdAt: Long
)
