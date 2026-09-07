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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
            _achievements.value = buildAchievements(0.0, 0, 0, 0, 0.0, 0.0, 0.0)
            return
        }

        val exercises = repository.allExercises.first()

        var totalTonnage = 0.0
        var maxBodyweightRatio = 0.0
        var maxBicepCurlWeight = 0.0
        var maxLateralRaiseWeight = 0.0
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
                if (set.reps >= 6) {
                    when (exercise?.name) {
                        "Bicep Curls" ->
                            if (set.addedWeightKg > maxBicepCurlWeight) maxBicepCurlWeight = set.addedWeightKg
                        "Lateral Raises" ->
                            if (set.addedWeightKg > maxLateralRaiseWeight) maxLateralRaiseWeight = set.addedWeightKg
                    }
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
            maxBicepCurlWeight,
            maxLateralRaiseWeight
        )
    }

    private fun computeStreak(sessionDates: List<String>): StreakInfo {
        if (sessionDates.isEmpty()) return StreakInfo()

        // A training week = ISO week (Mon-Sun) containing at least one workout.
        // The streak breaks only when a full week goes by with no training.
        val weekStarts = sessionDates
            .map { LocalDate.parse(it).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSet()

        var current = 0L
        val thisWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var cursor: LocalDate? = when {
            weekStarts.contains(thisWeek) -> thisWeek
            weekStarts.contains(thisWeek.minusWeeks(1)) -> thisWeek.minusWeeks(1)
            else -> null
        }
        while (cursor != null && weekStarts.contains(cursor)) {
            current++
            cursor = cursor.minusWeeks(1)
        }

        var longest = 0L
        var run = 0L
        var prev: LocalDate? = null
        for (start in weekStarts.sorted()) {
            run = if (prev != null && start == prev.plusWeeks(1)) run + 1 else 1L
            prev = start
            if (run > longest) longest = run
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
        maxBicepCurlWeight: Double,
        maxLateralRaiseWeight: Double
    ): List<Achievement> {
        val colorTiers = listOf("iron", "copper", "silver", "gold", "emerald", "diamond")

        // Bicep Curls: 16 -> 26 kg across the 6 tiers
        val curlWeights = listOf(16.0, 18.0, 20.0, 22.0, 24.0, 26.0)
        val curlTierNames = listOf("Good+", "Intermediate", "Intermediate+", "Advanced", "Advanced+", "Elite")
        val curlAchievements = curlWeights.mapIndexed { index, weight ->
            Achievement(
                id = "curl_${weight.toInt()}",
                title = "Bicep Curls · ${curlTierNames[index]}",
                description = "Log a ${weight.toInt()}kg set for 6+ reps",
                iconKey = "bicep",
                tier = colorTiers[index],
                unlocked = maxBicepCurlWeight >= weight
            )
        }

        // Lateral Raises: 10 -> 20 kg across the 6 tiers
        val latWeights = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0)
        val latAchievements = latWeights.mapIndexed { index, weight ->
            Achievement(
                id = "lat_${weight.toInt()}",
                title = "Lateral Raises · ${curlTierNames[index]}",
                description = "Log a ${weight.toInt()}kg set for 6+ reps",
                iconKey = "shoulder",
                tier = colorTiers[index],
                unlocked = maxLateralRaiseWeight >= weight
            )
        }

        return listOf(
            Achievement("tonnage_100k", "Tonnage 100k", "Move 100,000 total kg·reps", "bolt", "iron",
                totalTonnage >= 100_000),
            Achievement("tonnage_500k", "Tonnage 500k", "Move 500,000 total kg·reps", "whatshot", "copper",
                totalTonnage >= 500_000),
            Achievement("tonnage_1m", "Million Club", "Move 1,000,000 total kg·reps", "workspace_premium", "diamond",
                totalTonnage >= 1_000_000),
            Achievement("sessions_10", "Getting Started", "Complete 10 workouts", "check_circle", "iron",
                totalSessions >= 10),
            Achievement("sessions_50", "Consistent", "Complete 50 workouts", "directions_run", "copper",
                totalSessions >= 50),
            Achievement("sessions_100", "Century", "Complete 100 workouts", "emoji_events", "diamond",
                totalSessions >= 100),
            Achievement("streak_4", "One Month", "Train at least once a week for 4 weeks in a row", "local_fire_department", "copper",
                longestStreak >= 4),
            Achievement("streak_12", "Quarter", "Train at least once a week for 12 weeks in a row", "local_fire_department", "silver",
                longestStreak >= 12),
            Achievement("streak_26", "Half Year", "Train at least once a week for 26 weeks in a row", "shield", "gold",
                longestStreak >= 26),
            Achievement("bw_125", "Relative Strength", "Lift 1.25x your bodyweight", "pullup", "silver",
                maxBodyweightRatio >= 1.25),
            Achievement("bw_150", "Beast Mode", "Lift 1.5x your bodyweight", "pullup", "gold",
                maxBodyweightRatio >= 1.5),
            Achievement("bw_175", "Superhuman", "Lift 1.75x your bodyweight", "pullup", "emerald",
                maxBodyweightRatio >= 1.75),
        ) + curlAchievements + latAchievements
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
