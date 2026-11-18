package com.example.tr3ack.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.GoalType
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LevelInfo(
    val level: Int = 1,
    @StringRes val levelNameRes: Int = R.string.level_name_novice,
    val currentXp: Long = 0,
    val xpToNext: Long = 0,
    val progress: Float = 0f,
)

data class StreakInfo(
    val current: Long = 0,
    val longest: Long = 0,
)

enum class AchievementCategory {
    STRENGTH, VOLUME, CONSISTENCY, PROGRESS, BODYWEIGHT
}

/** How [Achievement.progressCurrent] / [Achievement.progressTarget] are rendered. */
enum class AchievementProgressKind {
    KG, RATIO, COUNT, TONNAGE
}

data class Achievement(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val titleArgRes: Int? = null,
    val descriptionArg: Any? = null,
    val iconKey: String,
    val tier: String,
    val unlocked: Boolean,
    val category: AchievementCategory,
    /** 0..1, derived from current/target so the card and bar always agree. */
    val progress: Float = if (unlocked) 1f else 0f,
    val progressCurrent: Double = 0.0,
    val progressTarget: Double = 0.0,
    val progressKind: AchievementProgressKind = AchievementProgressKind.COUNT,
)

class AchievementsViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    private val _level = MutableStateFlow(LevelInfo())
    val level: StateFlow<LevelInfo> = _level.asStateFlow()

    private val _streak = MutableStateFlow(StreakInfo())
    val streak: StateFlow<StreakInfo> = _streak.asStateFlow()

    private val _totalTonnage = MutableStateFlow(0.0)
    val totalTonnage: StateFlow<Double> = _totalTonnage.asStateFlow()

    private val _totalSessions = MutableStateFlow(0)
    val totalSessions: StateFlow<Int> = _totalSessions.asStateFlow()

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _goalProgress = MutableStateFlow<List<GoalProgress>>(emptyList())
    val goalProgress: StateFlow<List<GoalProgress>> = _goalProgress.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.workoutDays,
                repository.exerciseStats,
                repository.allBodyWeightEntries,
                repository.allWorkoutSets
            ) { days, stats, bodyWeightEntries, sets ->
                compute(days, stats, bodyWeightEntries, sets)
            }.collect { }
        }

        viewModelScope.launch {
            combine(repository.goals, repository.exerciseStats, repository.allWorkoutSets) { goals, stats, sets ->
                val statsById = stats.associateBy { it.exerciseId }
                val setsByExercise = sets.groupBy { it.exerciseId }
                goals
                    .map { goal ->
                        GoalRules.of(
                            goal,
                            statsById[goal.exerciseId],
                            setsByExercise[goal.exerciseId].orEmpty()
                        )
                    }
                    .sortedByDescending { it.achieved }
            }.collect { _goalProgress.value = it }
        }
    }

    fun saveGoal(exerciseId: Long, type: GoalType, targetValue: Double, targetReps: Int = 0) {
        val createdAt = _goalProgress.value
            .find { it.goal.exerciseId == exerciseId }?.goal?.createdAt
            ?: System.currentTimeMillis()
        viewModelScope.launch {
            repository.saveGoal(
                Goal(
                    exerciseId = exerciseId,
                    type = type,
                    targetValue = targetValue,
                    targetReps = targetReps,
                    createdAt = createdAt
                )
            )
        }
    }

    fun deleteGoal(exerciseId: Long) {
        viewModelScope.launch { repository.deleteGoal(exerciseId) }
    }

    private fun compute(
        workoutDays: List<WorkoutDayEntity>,
        exerciseStats: List<ExerciseStatsEntity>,
        bodyWeightEntries: List<BodyWeightEntry>,
        workoutSets: List<WorkoutSet>
    ) {
        val snapshot = AchievementSnapshotBuilder.build(
            workoutDays = workoutDays,
            exerciseStats = exerciseStats,
            bodyWeightEntries = bodyWeightEntries,
            workoutSets = workoutSets
        )
        _totalTonnage.value = snapshot.totalTonnage
        _totalSessions.value = snapshot.totalSessions
        _streak.value = snapshot.streak
        _level.value = snapshot.level
        _achievements.value = snapshot.achievements
    }
}