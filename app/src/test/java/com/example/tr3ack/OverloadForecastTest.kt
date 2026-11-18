package com.example.tr3ack

import com.example.tr3ack.viewmodel.ForecastRules
import com.example.tr3ack.viewmodel.OverloadRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverloadRulesTest {

    @Test
    fun notEnoughSessions_isNull() {
        assertNull(OverloadRules.compute(emptyList()))
        assertNull(OverloadRules.compute(listOf(100.0)))
        assertNull(OverloadRules.compute(listOf(0.0, 100.0)))
    }

    @Test
    fun beatLast_positiveDelta() {
        val info = OverloadRules.compute(listOf(100.0, 105.0, 110.0))!!
        assertTrue(info.beatLast)
        assertEquals(5.0, info.deltaKg, 1e-9)
        assertEquals(2, info.streak)
    }

    @Test
    fun belowLast_negativeDelta_resetsStreak() {
        val info = OverloadRules.compute(listOf(100.0, 105.0, 103.0))!!
        assertFalse(info.beatLast)
        assertEquals(-2.0, info.deltaKg, 1e-9)
        assertEquals(0, info.streak)
    }

    @Test
    fun matched_flats() {
        val info = OverloadRules.compute(listOf(100.0, 100.0))!!
        assertFalse(info.beatLast)
        assertEquals(0.0, info.deltaKg, 1e-9)
        assertEquals(0, info.streak)
    }

    @Test
    fun streak_countsOnlyConsecutiveIncreases() {
        // Increases at the end: 95 -> 100 -> 105, but the earlier 110 -> 95 was a drop.
        val info = OverloadRules.compute(listOf(110.0, 95.0, 100.0, 105.0))!!
        assertEquals(2, info.streak)
        assertTrue(info.beatLast)
    }
}

class ForecastRulesTest {

    private fun date(daysFromEpoch: Long) =
        LocalDate.ofEpochDay(daysFromEpoch).toString()

    @Test
    fun notEnoughPoints_isNull() {
        assertNull(ForecastRules.forecast(emptyList(), emptyList()))
        assertNull(ForecastRules.forecast(listOf(date(0)), listOf(100.0)))
    }

    @Test
    fun steadyGain_projectsUpward() {
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val dates = listOf(date(start), date(start + 7), date(start + 14))
        val values = listOf(100.0, 101.0, 102.0) // +1 kg/week
        val forecast = ForecastRules.forecast(dates, values)!!

        assertEquals(1.0, forecast.kgPerWeek, 0.01)
        assertNotNull(forecast.at4Weeks)
        assertNotNull(forecast.at8Weeks)
        // 4 weeks after the last point (102) at +1/wk = 106.
        assertEquals(106.0, forecast.at4Weeks!!, 0.05)
        assertEquals(110.0, forecast.at8Weeks!!, 0.05)
    }

    @Test
    fun decline_projectsDownwardButClampedToZero() {
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val dates = listOf(date(start), date(start + 7), date(start + 14))
        val values = listOf(102.0, 101.0, 100.0)
        val forecast = ForecastRules.forecast(dates, values)!!
        assertTrue(forecast.kgPerWeek < 0.0)
        assertTrue(forecast.at4Weeks!! >= 0.0)
    }

    @Test
    fun flatSeries_hasZeroSlope() {
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val dates = listOf(date(start), date(start + 7), date(start + 14))
        val values = listOf(100.0, 100.0, 100.0)
        val forecast = ForecastRules.forecast(dates, values)!!
        assertEquals(0.0, forecast.kgPerWeek, 1e-9)
        assertEquals(100.0, forecast.at4Weeks!!, 1e-9)
    }
}
