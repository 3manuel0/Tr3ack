package com.example.tr3ack

import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.viewmodel.RankLadderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankLadderRulesTest {

    private fun curls() = Exercise(ExerciseIds.BICEP_CURLS, "Bicep Curls", false)
    private fun pullUps() = Exercise(ExerciseIds.WEIGHTED_PULL_UPS, "Weighted Pull-Ups", true)

    @Test
    fun thresholds_matchExistingAchievementBenchmarks() {
        assertEquals(listOf(16.0, 18.0, 20.0, 22.0, 24.0, 26.0), RankLadderRules.thresholdsFor(curls()))
        assertEquals(
            listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0),
            RankLadderRules.thresholdsFor(Exercise(ExerciseIds.LATERAL_RAISES, "Lateral Raises", false))
        )
        assertEquals(
            listOf(1.0, 1.10, 1.25, 1.50, 1.75, 2.0),
            RankLadderRules.thresholdsFor(pullUps())
        )
    }

    @Test
    fun belowFirstTier_isUnrankedAndProgressesTowardIron() {
        val result = RankLadderRules.evaluate(RankLadderRules.curlThresholds, best = 12.0)

        assertNull(result.currentTier)
        assertEquals(16.0, result.nextThreshold!!, 1e-9)
        assertEquals(0.75f, result.progress, 1e-6f)
        assertTrue(result.tiers.none { it.reached })
        assertTrue(result.tiers.none { it.isCurrent })
    }

    @Test
    fun atThreshold_currentTierAndZeroProgressToNext() {
        val result = RankLadderRules.evaluate(RankLadderRules.curlThresholds, best = 20.0)

        assertEquals("silver", result.currentTier)
        assertEquals(22.0, result.nextThreshold!!, 1e-9)
        assertEquals(0.0f, result.progress, 1e-6f)
        assertTrue(result.tiers.first { it.tier == "silver" }.isCurrent)
        assertTrue(result.tiers.first { it.tier == "silver" }.reached)
        assertFalse(result.tiers.first { it.tier == "gold" }.reached)
    }

    @Test
    fun betweenTiers_reportsProgressWithinBand() {
        val result = RankLadderRules.evaluate(RankLadderRules.curlThresholds, best = 24.5)

        assertEquals("emerald", result.currentTier)
        assertEquals(26.0, result.nextThreshold!!, 1e-9)
        assertEquals(0.25f, result.progress, 1e-6f)
    }

    @Test
    fun maxTier_hasNoNextAndFullProgress() {
        val result = RankLadderRules.evaluate(RankLadderRules.curlThresholds, best = 30.0)

        assertEquals("diamond", result.currentTier)
        assertNull(result.nextThreshold)
        assertEquals(1.0f, result.progress, 1e-6f)
        assertTrue(result.tiers.all { it.reached })
    }

    @Test
    fun bodyweightLadder_usesSystemWeightRatio() {
        val result = RankLadderRules.evaluate(RankLadderRules.bodyweightThresholds, best = 1.86)

        assertEquals("emerald", result.currentTier)
        assertEquals(2.0, result.nextThreshold!!, 1e-9)
        assertEquals(0.44f, result.progress, 1e-6f)
    }
}
