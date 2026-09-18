package com.example.tr3ack.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.R
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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

    init {
        viewModelScope.launch {
            combine(repository.workoutDays, repository.exerciseStats) { days, stats ->
                compute(days, stats)
            }.collect { }
        }
    }

    private fun compute(workoutDays: List<WorkoutDayEntity>, exerciseStats: List<ExerciseStatsEntity>) {
        if (workoutDays.isEmpty()) {
            _totalTonnage.value = 0.0
            _totalSessions.value = 0
            _level.value = LevelInfo(xpToNext = 100)
            _streak.value = StreakInfo()
            _achievements.value = buildAchievements(0.0, 0, 0, 0, 0.0, 0.0, 0.0)
            return
        }

        val sessionDates = workoutDays.map { it.date }.sorted()
        val totalTonnage = workoutDays.sumOf { it.tonnage }
        val totalSessions = workoutDays.size
        val streak = computeStreak(sessionDates)

        val maxBodyweightRatio = exerciseStats
            .filter { it.isBodyweightBased }
            .maxOfOrNull { it.maxBodyweightRatio } ?: 0.0
        val maxBicepCurlWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.BICEP_CURLS }?.maxAddedWeightGte6 ?: 0.0
        val maxLateralRaiseWeight = exerciseStats
            .find { it.exerciseId == ExerciseIds.LATERAL_RAISES }?.maxAddedWeightGte6 ?: 0.0

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

        val nameRes = when {
            level <= 1 -> R.string.level_name_novice
            level == 2 -> R.string.level_name_beginner
            level == 3 -> R.string.level_name_rookie
            level == 4 -> R.string.level_name_apprentice
            level == 5 -> R.string.level_name_trainee
            level == 6 -> R.string.level_name_grinder
            level == 7 -> R.string.level_name_strong
            level == 8 -> R.string.level_name_advanced
            level == 9 -> R.string.level_name_elite
            else -> R.string.level_name_beast
        }
        val progress = if (needed > 0) (remaining.toFloat() / needed.toFloat()).coerceIn(0f, 1f) else 0f

        return LevelInfo(
            level = level,
            levelNameRes = nameRes,
            currentXp = remaining,
            xpToNext = needed,
            progress = progress
        )
    }

    private val rankRes = listOf(
        R.string.ach_rank_good_plus,
        R.string.ach_rank_intermediate,
        R.string.ach_rank_intermediate_plus,
        R.string.ach_rank_advanced,
        R.string.ach_rank_advanced_plus,
        R.string.ach_rank_elite
    )

    private val colorTiers = listOf("iron", "copper", "silver", "gold", "emerald", "diamond")

    private fun buildAchievements(
        totalTonnage: Double,
        totalSessions: Int,
        currentStreak: Long,
        longestStreak: Long,
        maxBodyweightRatio: Double,
        maxBicepCurlWeight: Double,
        maxLateralRaiseWeight: Double
    ): List<Achievement> {
        // Bicep Curls: 16 -> 26 kg across the 6 tiers
        val curlWeights = listOf(16.0, 18.0, 20.0, 22.0, 24.0, 26.0)
        val curlAchievements = curlWeights.mapIndexed { index, weight ->
            Achievement(
                id = "curl_${weight.toInt()}",
                titleRes = R.string.ach_curl_title,
                descriptionRes = R.string.ach_target_desc,
                titleArgRes = rankRes[index],
                descriptionArg = weight.toInt(),
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
                titleRes = R.string.ach_lat_title,
                descriptionRes = R.string.ach_target_desc,
                titleArgRes = rankRes[index],
                descriptionArg = weight.toInt(),
                iconKey = "shoulder",
                tier = colorTiers[index],
                unlocked = maxLateralRaiseWeight >= weight
            )
        }

        return listOf(
            Achievement(id = "tonnage_100k", titleRes = R.string.ach_tonnage_100k_title, descriptionRes = R.string.ach_tonnage_100k_desc, iconKey = "bolt", tier = "iron",
                unlocked = totalTonnage >= 100_000),
            Achievement(id = "tonnage_500k", titleRes = R.string.ach_tonnage_500k_title, descriptionRes = R.string.ach_tonnage_500k_desc, iconKey = "whatshot", tier = "copper",
                unlocked = totalTonnage >= 500_000),
            Achievement(id = "tonnage_1m", titleRes = R.string.ach_tonnage_1m_title, descriptionRes = R.string.ach_tonnage_1m_desc, iconKey = "workspace_premium", tier = "diamond",
                unlocked = totalTonnage >= 1_000_000),
            Achievement(id = "sessions_10", titleRes = R.string.ach_sessions_10_title, descriptionRes = R.string.ach_sessions_10_desc, iconKey = "check_circle", tier = "iron",
                unlocked = totalSessions >= 10),
            Achievement(id = "sessions_50", titleRes = R.string.ach_sessions_50_title, descriptionRes = R.string.ach_sessions_50_desc, iconKey = "directions_run", tier = "copper",
                unlocked = totalSessions >= 50),
            Achievement(id = "sessions_100", titleRes = R.string.ach_sessions_100_title, descriptionRes = R.string.ach_sessions_100_desc, iconKey = "emoji_events", tier = "diamond",
                unlocked = totalSessions >= 100),
            Achievement(id = "streak_4", titleRes = R.string.ach_streak_4_title, descriptionRes = R.string.ach_streak_4_desc, iconKey = "local_fire_department", tier = "copper",
                unlocked = longestStreak >= 4),
            Achievement(id = "streak_12", titleRes = R.string.ach_streak_12_title, descriptionRes = R.string.ach_streak_12_desc, iconKey = "local_fire_department", tier = "silver",
                unlocked = longestStreak >= 12),
            Achievement(id = "streak_26", titleRes = R.string.ach_streak_26_title, descriptionRes = R.string.ach_streak_26_desc, iconKey = "shield", tier = "gold",
                unlocked = longestStreak >= 26),
            Achievement(id = "bw_125", titleRes = R.string.ach_bw_125_title, descriptionRes = R.string.ach_bw_125_desc, iconKey = "pullup", tier = "silver",
                unlocked = maxBodyweightRatio >= 1.25),
            Achievement(id = "bw_150", titleRes = R.string.ach_bw_150_title, descriptionRes = R.string.ach_bw_150_desc, iconKey = "pullup", tier = "gold",
                unlocked = maxBodyweightRatio >= 1.5),
            Achievement(id = "bw_175", titleRes = R.string.ach_bw_175_title, descriptionRes = R.string.ach_bw_175_desc, iconKey = "pullup", tier = "emerald",
                unlocked = maxBodyweightRatio >= 1.75),
        ) + curlAchievements + latAchievements
    }
}