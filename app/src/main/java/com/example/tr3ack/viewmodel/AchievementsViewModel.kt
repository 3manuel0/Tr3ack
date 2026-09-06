package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LevelInfo(
    val level: Int = 1,
    val levelName: String = "Novice",
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
    val title: String,
    val description: String,
    val iconKey: String,
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

    init {
        viewModelScope.launch {
            repository.allWorkoutSets.collect { sets ->
                compute(sets)
            }
        }
    }

    private suspend fun compute(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) {
            _totalTonnage.value = 0.0
            _totalSessions.value = 0
            _level.value = LevelInfo(xpToNext = 100)
            _streak.value = StreakInfo()
            _achievements.value = buildAchievements(0.0, 0, 0, 0, 0.0, 0.0)
            return
        }

        val exercises = repository.allExercises.first()

        var totalTonnage = 0.0
        var maxBodyweightRatio = 0.0
        var maxFreeWeightRatio = 0.0
        for (set in sets) {
            if (set.reps <= 0) continue
            val exercise = exercises.find { it.id == set.exerciseId }
            val bodyWeight = repository.getEffectiveBodyWeight(set.date)
            if (exercise?.isBodyweightBased == true) {
                if (bodyWeight != null && bodyWeight > 0) {
                    val tsl = bodyWeight + set.addedWeightKg
                    totalTonnage += tsl * set.reps
                    val ratio = tsl / bodyWeight
                    if (ratio > maxBodyweightRatio) maxBodyweightRatio = ratio
                }
            } else {
                totalTonnage += set.addedWeightKg * set.reps
                if (bodyWeight != null && bodyWeight > 0 && set.addedWeightKg > 0) {
                    val ratio = set.addedWeightKg / bodyWeight
                    if (ratio > maxFreeWeightRatio) maxFreeWeightRatio = ratio
                }
            }
        }

        val sessionDates = sets.map { it.date }.distinct().sorted()
        val totalSessions = sessionDates.size
        val streak = computeStreak(sessionDates)

        _totalTonnage.value = totalTonnage
        _totalSessions.value = totalSessions
        _streak.value = streak
        _level.value = computeLevel(totalTonnage, totalSessions)
        _achievements.value = buildAchievements(
            totalTonnage,
            totalSessions,
            streak.current,
            streak.longest,
            maxBodyweightRatio,
            maxFreeWeightRatio
        )
    }

    private fun computeStreak(sessionDates: List<String>): StreakInfo {
        if (sessionDates.isEmpty()) return StreakInfo()
        val dateSet = sessionDates.map { LocalDate.parse(it) }.toSet()
        val today = LocalDate.now()

        var current = 0L
        var cursor = today
        if (dateSet.contains(today)) {
            while (dateSet.contains(cursor)) {
                current++
                cursor = cursor.minusDays(1)
            }
        } else if (dateSet.contains(today.minusDays(1))) {
            cursor = today.minusDays(1)
            while (dateSet.contains(cursor)) {
                current++
                cursor = cursor.minusDays(1)
            }
        }

        var longest = 0L
        for (date in dateSet) {
            var len = 1L
            var prev = date.minusDays(1)
            while (dateSet.contains(prev)) {
                len++
                prev = prev.minusDays(1)
            }
            if (len > longest) longest = len
        }

        return StreakInfo(current = current, longest = longest)
    }

    private fun computeLevel(totalTonnage: Double, totalSessions: Int): LevelInfo {
        val xp = (totalTonnage / 10).toLong() + totalSessions * 50L

        var level = 1
        var needed = 100L
        var remaining = xp
        while (remaining >= needed) {
            remaining -= needed
            level++
            needed = (needed * 1.4).toLong()
        }

        val names = listOf(
            "Novice", "Beginner", "Rookie", "Apprentice", "Trainee",
            "Grinder", "Strong", "Advanced", "Elite", "Beast"
        )
        val name = names.getOrElse(level - 1) { "Beast" }
        val progress = if (needed > 0) (remaining.toFloat() / needed.toFloat()).coerceIn(0f, 1f) else 0f

        return LevelInfo(
            level = level,
            levelName = name,
            currentXp = remaining,
            xpToNext = needed,
            progress = progress
        )
    }

    private fun buildAchievements(
        totalTonnage: Double,
        totalSessions: Int,
        currentStreak: Long,
        longestStreak: Long,
        maxBodyweightRatio: Double,
        maxFreeWeightRatio: Double
    ): List<Achievement> {
        return listOf(
            Achievement("tonnage_100k", "100k Moved", "Move 100,000 kg·reps total", "whatshot",
                totalTonnage >= 100_000),
            Achievement("tonnage_500k", "500k Moved", "Move 500,000 kg·reps total", "bolt",
                totalTonnage >= 500_000),
            Achievement("tonnage_1m", "Million Club", "Move 1,000,000 kg·reps total", "workspace_premium",
                totalTonnage >= 1_000_000),
            Achievement("sessions_10", "Getting Started", "Complete 10 workouts", "fitness_center",
                totalSessions >= 10),
            Achievement("sessions_50", "Consistent", "Complete 50 workouts", "directions_run",
                totalSessions >= 50),
            Achievement("sessions_100", "Century", "Complete 100 workouts", "emoji_events",
                totalSessions >= 100),
            Achievement("streak_7", "One Week", "Train 7 days in a row", "local_fire_department",
                longestStreak >= 7),
            Achievement("streak_30", "Full Month", "Train 30 days in a row", "local_fire_department",
                longestStreak >= 30),
            Achievement("streak_90", "Grind Mode", "Train 90 days in a row", "shield",
                longestStreak >= 90),
            Achievement("bw_125", "Relative Strength", "Lift 1.25x your bodyweight", "trending_up",
                maxBodyweightRatio >= 1.25),
            Achievement("bw_150", "Beast Mode", "Lift 1.5x your bodyweight", "barbell",
                maxBodyweightRatio >= 1.5),
            Achievement("bw_175", "Superhuman", "Lift 1.75x your bodyweight", "rocket_launch",
                maxBodyweightRatio >= 1.75),
            Achievement("fw_050", "Dumbbell Goal", "Curl half your bodyweight in a single hand", "fitness_center",
                maxFreeWeightRatio >= 0.5),
        )
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AchievementsViewModel::class.java)) {
                return AchievementsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
