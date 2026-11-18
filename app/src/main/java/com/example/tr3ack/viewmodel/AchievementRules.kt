package com.example.tr3ack.viewmodel

import androidx.annotation.StringRes
import com.example.tr3ack.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Pure achievement / streak / level logic, extracted so it can be unit-tested on
 * the JVM without a repository or database. Mirrors the rules used by
 * [AchievementsViewModel].
 */
object AchievementRules {

    val rankRes = listOf(
        R.string.ach_rank_good_plus,
        R.string.ach_rank_intermediate,
        R.string.ach_rank_intermediate_plus,
        R.string.ach_rank_advanced,
        R.string.ach_rank_advanced_plus,
        R.string.ach_rank_elite
    )

    val colorTiers = listOf("iron", "copper", "silver", "gold", "emerald", "diamond")

    /** Added-weight milestones (kg, over a 6+ rep set) for the two weighted bodyweight lifts. */
    val pullUpMilestones = listOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0)
    val dipMilestones = listOf(25.0, 35.0, 45.0, 55.0, 65.0, 75.0)

    fun computeStreak(sessionDates: List<String>): StreakInfo {
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

    fun computeLevel(totalTonnage: Double, totalSessions: Int): LevelInfo {
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

    fun buildAchievements(
        totalTonnage: Double,
        totalSessions: Int,
        currentStreak: Long,
        longestStreak: Long,
        maxBodyweightRatio: Double,
        maxBicepCurlWeight: Double,
        maxLateralRaiseWeight: Double,
        prExerciseCount: Int,
        bodyWeightCheckins: Int,
        maxPullUpWeight: Double = 0.0,
        maxDipWeight: Double = 0.0
    ): List<Achievement> = buildList {
        addAll(
            listOf(
                badge(
                    id = "tonnage_100k",
                    titleRes = R.string.ach_tonnage_100k_title,
                    descriptionRes = R.string.ach_tonnage_100k_desc,
                    iconKey = "bolt",
                    tier = "iron",
                    category = AchievementCategory.VOLUME,
                    progressKind = AchievementProgressKind.TONNAGE,
                    current = totalTonnage,
                    target = 100_000.0
                ),
                badge(
                    id = "tonnage_500k",
                    titleRes = R.string.ach_tonnage_500k_title,
                    descriptionRes = R.string.ach_tonnage_500k_desc,
                    iconKey = "layers",
                    tier = "copper",
                    category = AchievementCategory.VOLUME,
                    progressKind = AchievementProgressKind.TONNAGE,
                    current = totalTonnage,
                    target = 500_000.0
                ),
                badge(
                    id = "tonnage_1m",
                    titleRes = R.string.ach_tonnage_1m_title,
                    descriptionRes = R.string.ach_tonnage_1m_desc,
                    iconKey = "workspace_premium",
                    tier = "diamond",
                    category = AchievementCategory.VOLUME,
                    progressKind = AchievementProgressKind.TONNAGE,
                    current = totalTonnage,
                    target = 1_000_000.0
                ),
                badge(
                    id = "sessions_10",
                    titleRes = R.string.ach_sessions_10_title,
                    descriptionRes = R.string.ach_sessions_10_desc,
                    iconKey = "check_circle",
                    tier = "iron",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = totalSessions.toDouble(),
                    target = 10.0
                ),
                badge(
                    id = "sessions_50",
                    titleRes = R.string.ach_sessions_50_title,
                    descriptionRes = R.string.ach_sessions_50_desc,
                    iconKey = "directions_run",
                    tier = "copper",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = totalSessions.toDouble(),
                    target = 50.0
                ),
                badge(
                    id = "sessions_100",
                    titleRes = R.string.ach_sessions_100_title,
                    descriptionRes = R.string.ach_sessions_100_desc,
                    iconKey = "emoji_events",
                    tier = "diamond",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = totalSessions.toDouble(),
                    target = 100.0
                ),
                badge(
                    id = "streak_4",
                    titleRes = R.string.ach_streak_4_title,
                    descriptionRes = R.string.ach_streak_4_desc,
                    iconKey = "local_fire_department",
                    tier = "copper",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = longestStreak.toDouble(),
                    target = 4.0
                ),
                badge(
                    id = "streak_12",
                    titleRes = R.string.ach_streak_12_title,
                    descriptionRes = R.string.ach_streak_12_desc,
                    iconKey = "calendar_check",
                    tier = "silver",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = longestStreak.toDouble(),
                    target = 12.0
                ),
                badge(
                    id = "streak_26",
                    titleRes = R.string.ach_streak_26_title,
                    descriptionRes = R.string.ach_streak_26_desc,
                    iconKey = "shield",
                    tier = "gold",
                    category = AchievementCategory.CONSISTENCY,
                    progressKind = AchievementProgressKind.COUNT,
                    current = longestStreak.toDouble(),
                    target = 26.0
                ),
                badge(
                    id = "bw_125",
                    titleRes = R.string.ach_bw_125_title,
                    descriptionRes = R.string.ach_bw_125_desc,
                    iconKey = "pullup",
                    tier = "silver",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.RATIO,
                    current = maxBodyweightRatio,
                    target = 1.25
                ),
                badge(
                    id = "bw_150",
                    titleRes = R.string.ach_bw_150_title,
                    descriptionRes = R.string.ach_bw_150_desc,
                    iconKey = "pullup",
                    tier = "gold",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.RATIO,
                    current = maxBodyweightRatio,
                    target = 1.5
                ),
                badge(
                    id = "bw_175",
                    titleRes = R.string.ach_bw_175_title,
                    descriptionRes = R.string.ach_bw_175_desc,
                    iconKey = "pullup",
                    tier = "emerald",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.RATIO,
                    current = maxBodyweightRatio,
                    target = 1.75
                ),
                badge(
                    id = "pr_exercises_1",
                    titleRes = R.string.ach_pr_1_title,
                    descriptionRes = R.string.ach_pr_1_desc,
                    iconKey = "target",
                    tier = "iron",
                    category = AchievementCategory.PROGRESS,
                    progressKind = AchievementProgressKind.COUNT,
                    current = prExerciseCount.toDouble(),
                    target = 1.0
                ),
                badge(
                    id = "pr_exercises_3",
                    titleRes = R.string.ach_pr_3_title,
                    descriptionRes = R.string.ach_pr_3_desc,
                    iconKey = "target",
                    tier = "copper",
                    category = AchievementCategory.PROGRESS,
                    progressKind = AchievementProgressKind.COUNT,
                    current = prExerciseCount.toDouble(),
                    target = 3.0
                ),
                badge(
                    id = "pr_exercises_5",
                    titleRes = R.string.ach_pr_5_title,
                    descriptionRes = R.string.ach_pr_5_desc,
                    iconKey = "target",
                    tier = "silver",
                    category = AchievementCategory.PROGRESS,
                    progressKind = AchievementProgressKind.COUNT,
                    current = prExerciseCount.toDouble(),
                    target = 5.0
                ),
                badge(
                    id = "bodyweight_checkins_30",
                    titleRes = R.string.ach_bodyweight_30_title,
                    descriptionRes = R.string.ach_bodyweight_30_desc,
                    iconKey = "weight",
                    tier = "iron",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.COUNT,
                    current = bodyWeightCheckins.toDouble(),
                    target = 30.0
                ),
                badge(
                    id = "bodyweight_checkins_90",
                    titleRes = R.string.ach_bodyweight_90_title,
                    descriptionRes = R.string.ach_bodyweight_90_desc,
                    iconKey = "weight",
                    tier = "copper",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.COUNT,
                    current = bodyWeightCheckins.toDouble(),
                    target = 90.0
                ),
                badge(
                    id = "bodyweight_checkins_365",
                    titleRes = R.string.ach_bodyweight_365_title,
                    descriptionRes = R.string.ach_bodyweight_365_desc,
                    iconKey = "weight",
                    tier = "gold",
                    category = AchievementCategory.BODYWEIGHT,
                    progressKind = AchievementProgressKind.COUNT,
                    current = bodyWeightCheckins.toDouble(),
                    target = 365.0
                )
            )
        )

        // Bicep Curls: 16 -> 26 kg across the 6 tiers
        listOf(16.0, 18.0, 20.0, 22.0, 24.0, 26.0).forEachIndexed { index, weight ->
            add(
                badge(
                    id = "curl_${weight.toInt()}",
                    titleRes = R.string.ach_curl_title,
                    descriptionRes = R.string.ach_target_desc,
                    titleArgRes = rankRes[index],
                    descriptionArg = weight.toInt(),
                    iconKey = "bicep",
                    tier = colorTiers[index],
                    category = AchievementCategory.STRENGTH,
                    progressKind = AchievementProgressKind.KG,
                    current = maxBicepCurlWeight,
                    target = weight
                )
            )
        }

        // Lateral Raises: 10 -> 20 kg across the 6 tiers
        listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0).forEachIndexed { index, weight ->
            add(
                badge(
                    id = "lat_${weight.toInt()}",
                    titleRes = R.string.ach_lat_title,
                    descriptionRes = R.string.ach_target_desc,
                    titleArgRes = rankRes[index],
                    descriptionArg = weight.toInt(),
                    iconKey = "shoulder",
                    tier = colorTiers[index],
                    category = AchievementCategory.STRENGTH,
                    progressKind = AchievementProgressKind.KG,
                    current = maxLateralRaiseWeight,
                    target = weight
                )
            )
        }

        // Weighted Pull-Ups: added weight, 20 -> 70 kg
        pullUpMilestones.forEachIndexed { index, weight ->
            add(
                badge(
                    id = "pullup_${weight.toInt()}",
                    titleRes = R.string.ach_pullup_title,
                    descriptionRes = R.string.ach_added_target_desc,
                    titleArgRes = rankRes[index],
                    descriptionArg = weight.toInt(),
                    iconKey = "pullup",
                    tier = colorTiers[index],
                    category = AchievementCategory.STRENGTH,
                    progressKind = AchievementProgressKind.KG,
                    current = maxPullUpWeight,
                    target = weight
                )
            )
        }

        // Weighted Dips: added weight, 25 -> 75 kg
        dipMilestones.forEachIndexed { index, weight ->
            add(
                badge(
                    id = "dip_${weight.toInt()}",
                    titleRes = R.string.ach_dip_title,
                    descriptionRes = R.string.ach_added_target_desc,
                    titleArgRes = rankRes[index],
                    descriptionArg = weight.toInt(),
                    iconKey = "dips",
                    tier = colorTiers[index],
                    category = AchievementCategory.STRENGTH,
                    progressKind = AchievementProgressKind.KG,
                    current = maxDipWeight,
                    target = weight
                )
            )
        }
    }

    /**
     * Builds one badge and derives `unlocked` + [Achievement.progress] from the
     * same current/target pair, so the card and its progress bar can never
     * disagree.
     */
    private fun badge(
        id: String,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        iconKey: String,
        tier: String,
        category: AchievementCategory,
        progressKind: AchievementProgressKind,
        current: Double,
        target: Double,
        @StringRes titleArgRes: Int? = null,
        descriptionArg: Any? = null
    ): Achievement {
        val ratio = if (target > 0.0) (current / target).coerceIn(0.0, 1.0) else 0.0
        return Achievement(
            id = id,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
            titleArgRes = titleArgRes,
            descriptionArg = descriptionArg,
            iconKey = iconKey,
            tier = tier,
            unlocked = current >= target,
            category = category,
            progress = ratio.toFloat(),
            progressCurrent = current.coerceAtMost(target),
            progressTarget = target,
            progressKind = progressKind
        )
    }
}
