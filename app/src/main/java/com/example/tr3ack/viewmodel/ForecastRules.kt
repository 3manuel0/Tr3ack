package com.example.tr3ack.viewmodel

import java.time.LocalDate

/** A linear projection of a series into the future. */
data class Forecast(
    /** Slope in kg per week (positive = improving). */
    val kgPerWeek: Double,
    /** Projected value 4 weeks out, or null if it could not be computed. */
    val at4Weeks: Double?,
    /** Projected value 8 weeks out, or null if it could not be computed. */
    val at8Weeks: Double?
)

/**
 * Least-squares linear fit over (date, value) pairs, projected 4 and 8 weeks
 * beyond the most recent point.
 */
object ForecastRules {

    fun forecast(dates: List<String>, values: List<Double>): Forecast? {
        if (dates.size != values.size || values.size < 2) return null

        val points = dates.mapIndexedNotNull { i, date ->
            val value = values[i]
            if (value <= 0.0) {
                null
            } else {
                runCatching { LocalDate.parse(date).toEpochDay() to value }.getOrNull()
            }
        }
        if (points.size < 2) return null

        val firstDay = points.first().first
        val n = points.size.toDouble()
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        for ((day, value) in points) {
            val x = (day - firstDay).toDouble()
            sumX += x
            sumY += value
            sumXY += x * value
            sumXX += x * x
        }

        val denominator = n * sumXX - sumX * sumX
        if (denominator <= 0.0) return null
        val slopePerDay = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slopePerDay * sumX) / n

        val lastX = (points.last().first - firstDay).toDouble()
        fun project(daysAhead: Long): Double =
            (intercept + slopePerDay * (lastX + daysAhead)).coerceAtLeast(0.0)

        return Forecast(
            kgPerWeek = slopePerDay * 7.0,
            at4Weeks = project(28),
            at8Weeks = project(56)
        )
    }
}
