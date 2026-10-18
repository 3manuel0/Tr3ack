package com.example.tr3ack.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.Goal
import com.example.tr3ack.data.entity.GoalType
import com.example.tr3ack.data.entity.WorkoutDayEntity
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

data class Achievement(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val titleArgRes: Int? = null,
    val descriptionArg: Any? = null,
    val iconKey: String,
    val tier: String,
    val unlocked: Boolean,
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
            combine(repository.workoutDays, repository.exerciseStats, repository.allBodyWeightEntries) { days, stats, bodyWeightEntries ->
                compute(days, stats, bodyWeightEntries)
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
        bodyWeightEntries: List<BodyWeightEntry>
    ) {
        val sessionDates = workoutDays.map { it.date }.sorted()
        val totalTonnage = workoutDays.sumOf { it.tonnage }
        val totalSessions = workoutDays.size
        val streak = AchievementRules.computeStreak(sessionDates)

        val maxBodyweightRatio = exerciseStats
            .filter { it.isBodyweightBased }
            .maxOfOrNull { it.maxBodyweightRatio } ?: 0.0
        val maxBicepCurlWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.BICEP_CURLS }?.maxAddedWeightGte6 ?: 0.0
        val maxLateralRaiseWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.LATERAL_RAISES }?.maxAddedWeightGte6 ?: 0.0
        val prExerciseCount = exerciseStats.count { it.hasData && it.bestE1RM > 0.0 }
        val bodyWeightCheckins = bodyWeightEntries
            .filter { it.bodyWeightKg > 0.0 }
            .map { it.date }
            .distinct()
            .size

        _totalTonnage.value = totalTonnage
        _totalSessions.value = totalSessions
        _streak.value = streak
        _level.value = AchievementRules.computeLevel(totalTonnage, totalSessions)
        _achievements.value = AchievementRules.buildAchievements(
            totalTonnage,
            totalSessions,
            streak.current,
            streak.longest,
            maxBodyweightRatio,
            maxBicepCurlWeight,
            maxLateralRaiseWeight,
            prExerciseCount,
            bodyWeightCheckins
        )
    }
}