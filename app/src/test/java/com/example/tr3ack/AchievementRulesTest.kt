package com.example.tr3ack

import com.example.tr3ack.R
import com.example.tr3ack.viewmodel.AchievementProgressKind
import com.example.tr3ack.viewmodel.AchievementRules
import com.example.tr3ack.viewmodel.LevelInfo
import com.example.tr3ack.viewmodel.StreakInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementRulesTest {

    @Test
    fun level_progression_followsXpCurve() {
        val l1 = AchievementRules.computeLevel(totalTonnage = 0.0, totalSessions = 0)
        assertEquals(1, l1.level)
        assertEquals(R.string.level_name_novice, l1.levelNameRes)
        assertEquals(0L, l1.currentXp)
        assertEquals(100L, l1.xpToNext)
        assertEquals(0f, l1.progress, 1e-6f)

        val l2 = AchievementRules.computeLevel(totalTonnage = 1000.0, totalSessions = 0) // xp = 100
        assertEquals(2, l2.level)
        assertEquals(R.string.level_name_beginner, l2.levelNameRes)
        assertEquals(0L, l2.currentXp)
        assertEquals(140L, l2.xpToNext)

        val l2partial = AchievementRules.computeLevel(totalTonnage = 1500.0, totalSessions = 0) // xp = 150
        assertEquals(2, l2partial.level)
        assertEquals(50L, l2partial.currentXp)
        assertEquals(140L, l2partial.xpToNext)
        assertEquals(50f / 140f, l2partial.progress, 1e-6f)

        val l3 = AchievementRules.computeLevel(totalTonnage = 2400.0, totalSessions = 0) // xp = 240 = 100 + 140
        assertEquals(3, l3.level)
        assertEquals(R.string.level_name_rookie, l3.levelNameRes)
        assertEquals(196L, l3.xpToNext)
    }

    @Test
    fun level_sessionsContribute50XpEach() {
        // 10 sessions, no tonnage -> 500 xp = 100 + 140 + 196 = 436, remainder 64 into level 4
        val l4 = AchievementRules.computeLevel(totalTonnage = 0.0, totalSessions = 10)
        assertEquals(4, l4.level)
        assertEquals(R.string.level_name_apprentice, l4.levelNameRes)
        assertEquals(64L, l4.currentXp)
    }

    @Test
    fun level_highXp_landsInBeastTier() {
        val level = AchievementRules.computeLevel(totalTonnage = 10_000_000.0, totalSessions = 100)
        assertTrue(level.level >= 10)
        assertEquals(R.string.level_name_beast, level.levelNameRes)
    }

    @Test
    fun streak_emptyDates_isZero() {
        assertEquals(StreakInfo(0, 0), AchievementRules.computeStreak(emptyList()))
    }

    @Test
    fun streak_consecutiveWeeks_currentAndLongestMatch() {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dates = listOf(
            monday.toString(),
            monday.minusWeeks(1).toString(),
            monday.minusWeeks(2).toString(),
        )
        val streak = AchievementRules.computeStreak(dates)
        assertEquals(3L, streak.current)
        assertEquals(3L, streak.longest)
    }

    @Test
    fun streak_gapBreaksChain_longestStillCounts() {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dates = listOf(
            monday.toString(),
            monday.minusWeeks(1).toString(),
            monday.minusWeeks(2).toString(),
            monday.minusWeeks(4).toString(), // gap: no training in the 3rd week back
        )
        val streak = AchievementRules.computeStreak(dates)
        assertEquals(3L, streak.current)
        assertEquals(3L, streak.longest)
    }

    @Test
    fun streak_noRecentTraining_currentIsZero() {
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val streak = AchievementRules.computeStreak(listOf(monday.minusWeeks(3).toString()))
        assertEquals(0L, streak.current)
        assertEquals(1L, streak.longest)
    }

    @Test
    fun achievements_defaultTo42BadgesAllLocked() {
        val ach = AchievementRules.buildAchievements(
            totalTonnage = 0.0,
            totalSessions = 0,
            currentStreak = 0,
            longestStreak = 0,
            maxBodyweightRatio = 0.0,
            maxBicepCurlWeight = 0.0,
            maxLateralRaiseWeight = 0.0,
            prExerciseCount = 0,
            bodyWeightCheckins = 0
        )
        assertEquals(42, ach.size)
        assertTrue(ach.all { !it.unlocked })
        assertTrue(ach.all { it.progress == 0f })
    }

    @Test
    fun achievements_unlockAcrossAllCategories() {
        val ach = AchievementRules.buildAchievements(
            totalTonnage = 1_000_000.0,
            totalSessions = 100,
            currentStreak = 26,
            longestStreak = 26,
            maxBodyweightRatio = 1.6,
            maxBicepCurlWeight = 30.0,
            maxLateralRaiseWeight = 18.0,
            prExerciseCount = 5,
            bodyWeightCheckins = 365,
            maxPullUpWeight = 25.4,
            maxDipWeight = 25.4
        )
        val byId = ach.associateBy { it.id }
        val unlockedIds = ach.filter { it.unlocked }.map { it.id }.toSet()

        assertEquals(3, ach.count { it.id.startsWith("tonnage") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("sessions") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("streak") && it.unlocked })
        assertEquals(2, ach.count { it.id.startsWith("bw") && it.unlocked })
        assertEquals(6, ach.count { it.id.startsWith("curl") && it.unlocked })
        assertEquals(5, ach.count { it.id.startsWith("lat") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("pullup") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("dip") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("pr_exercises") && it.unlocked })
        assertEquals(3, ach.count { it.id.startsWith("bodyweight_checkins") && it.unlocked })
        assertEquals(34, unlockedIds.size)

        // Tier mapping: lower tiers unlock before higher ones.
        assertTrue(byId.getValue("curl_16").tier == "iron" && byId.getValue("curl_26").tier == "diamond")
        assertTrue(byId.getValue("lat_10").tier == "iron" && byId.getValue("lat_20").tier == "diamond")
        assertTrue(byId.getValue("pullup_10").tier == "iron" && byId.getValue("pullup_60").tier == "diamond")
        assertTrue(byId.getValue("dip_10").tier == "iron" && byId.getValue("dip_60").tier == "diamond")

        // Per-exercise unlocks stop at the max achievable weight.
        assertTrue(byId.getValue("lat_18").unlocked)
        assertFalse(byId.getValue("lat_20").unlocked)
        assertTrue(byId.getValue("curl_26").unlocked)
        assertTrue(byId.getValue("pullup_20").unlocked)
        assertFalse(byId.getValue("pullup_30").unlocked)
        assertTrue(byId.getValue("dip_20").unlocked)
        assertFalse(byId.getValue("dip_30").unlocked)
    }

    @Test
    fun achievements_exposeProgressTowardEachTarget() {
        val ach = AchievementRules.buildAchievements(
            totalTonnage = 141_000.0,
            totalSessions = 31,
            currentStreak = 4,
            longestStreak = 14,
            maxBodyweightRatio = 1.29,
            maxBicepCurlWeight = 20.5,
            maxLateralRaiseWeight = 15.0,
            prExerciseCount = 4,
            bodyWeightCheckins = 12,
            maxPullUpWeight = 25.4,
            maxDipWeight = 25.4
        ).associateBy { it.id }

        val tonnage = ach.getValue("tonnage_500k")
        assertFalse(tonnage.unlocked)
        assertEquals(141_000.0, tonnage.progressCurrent, 1e-9)
        assertEquals(500_000.0, tonnage.progressTarget, 1e-9)
        assertEquals((141_000.0 / 500_000.0).toFloat(), tonnage.progress, 1e-6f)
        assertEquals(AchievementProgressKind.TONNAGE, tonnage.progressKind)

        val dip = ach.getValue("dip_30")
        assertEquals(25.4, dip.progressCurrent, 1e-9)
        assertEquals(30.0, dip.progressTarget, 1e-9)
        assertEquals((25.4 / 30.0).toFloat(), dip.progress, 1e-6f)

        val ratio = ach.getValue("bw_150")
        assertEquals((1.29 / 1.5).toFloat(), ratio.progress, 1e-6f)

        val sessions = ach.getValue("sessions_50")
        assertEquals(31.0, sessions.progressCurrent, 1e-9)
        assertEquals(AchievementProgressKind.COUNT, sessions.progressKind)

        // An unlocked badge is always at 100%, never above.
        assertEquals(1f, ach.getValue("sessions_10").progress, 1e-6f)
        assertEquals(1f, ach.getValue("tonnage_100k").progress, 1e-6f)
    }

    @Test
    fun achievements_unlockPrAndBodyweightThresholds() {
        val ach = AchievementRules.buildAchievements(
            totalTonnage = 0.0,
            totalSessions = 0,
            currentStreak = 0,
            longestStreak = 0,
            maxBodyweightRatio = 0.0,
            maxBicepCurlWeight = 0.0,
            maxLateralRaiseWeight = 0.0,
            prExerciseCount = 1,
            bodyWeightCheckins = 30
        )
        val byId = ach.associateBy { it.id }

        assertTrue(byId.getValue("pr_exercises_1").unlocked)
        assertFalse(byId.getValue("pr_exercises_3").unlocked)
        assertFalse(byId.getValue("pr_exercises_5").unlocked)
        assertTrue(byId.getValue("bodyweight_checkins_30").unlocked)
        assertFalse(byId.getValue("bodyweight_checkins_90").unlocked)
        assertFalse(byId.getValue("bodyweight_checkins_365").unlocked)
    }

    @Test
    fun achievements_orderPrBodyweightThenCurlsThenLatRaises() {
        val ids = AchievementRules.buildAchievements(0.0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0).map { it.id }
        assertEquals(
            listOf("pr_exercises_1", "pr_exercises_3", "pr_exercises_5", "bodyweight_checkins_30", "bodyweight_checkins_90", "bodyweight_checkins_365"),
            ids.drop(12).take(6)
        )
        assertEquals(listOf("curl_16", "curl_18", "curl_20", "curl_22", "curl_24", "curl_26"), ids.drop(18).take(6))
        assertEquals(listOf("lat_10", "lat_12", "lat_14", "lat_16", "lat_18", "lat_20"), ids.drop(24).take(6))
        assertEquals(listOf("pullup_10", "pullup_15", "pullup_20", "pullup_30", "pullup_45", "pullup_60"), ids.drop(30).take(6))
        assertEquals(listOf("dip_10", "dip_15", "dip_20", "dip_30", "dip_45", "dip_60"), ids.drop(36).take(6))
    }
}