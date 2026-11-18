package com.example.tr3ack.viewmodel

/**
 * "Did you beat last session?" — pure logic for the per-exercise overload signal.
 *
 * Input is a session e1RM series in ascending date order. The streak counts
 * consecutive strictly-increasing steps ending at the latest session.
 */
data class OverloadInfo(
    val beatLast: Boolean,
    val deltaKg: Double,
    val streak: Int
)

object OverloadRules {

    fun compute(e1rms: List<Double>): OverloadInfo? {
        val values = e1rms.filter { it > 0.0 }
        if (values.size < 2) return null
        val latest = values.last()
        val previous = values[values.size - 2]
        if (previous <= 0.0) return null

        var streak = 0
        for (i in values.size - 1 downTo 1) {
            if (values[i] > values[i - 1]) streak++ else break
        }

        return OverloadInfo(
            beatLast = latest > previous,
            deltaKg = latest - previous,
            streak = streak
        )
    }
}
