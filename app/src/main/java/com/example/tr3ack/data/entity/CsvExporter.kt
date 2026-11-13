package com.example.tr3ack.data.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeMap

/**
 * Builds the user-facing CSV export.
 *
 * One row per exercise **per day** (the way sets are actually grouped in the
 * app), with each set on the same line as `setN_added_weight_kg` /
 * `setN_reps` columns. This keeps a back-filled set from creating a separate
 * row, and unlike the old `10-10-10` cells each set stays individually
 * parseable by spreadsheets and AI tools.
 *
 * Columns:
 *  - date, exercise_id, exercise, is_bodyweight, set_count
 *  - body_weight_kg: effective body weight on that date (blank if none known)
 *  - set1_added_weight_kg, set1_reps, ... setN_... (N = the largest set count
 *    in the export; unused slots are blank)
 *  - volume_kg: sum of system weight × reps (system = BW + added for bodyweight,
 *    added for free weights)
 *  - best_e1rm_kg: highest estimated 1RM among the day's sets
 *  - best_e1rm_added_weight_kg: the same e1RM expressed as added weight
 *
 * Numbers use "." as the decimal separator regardless of device locale.
 */
object CsvExporter {

    fun buildSetsCsv(
        sets: List<WorkoutSet>,
        exercises: List<Exercise>,
        bodyWeightEntries: List<BodyWeightEntry>
    ): String {
        val exerciseById = exercises.associateBy { it.id }
        val bwByDate = TreeMap<String, Double>().apply {
            bodyWeightEntries.forEach { entry ->
                if (entry.bodyWeightKg > 0) put(entry.date, entry.bodyWeightKg)
            }
        }

        val groups = sets
            .groupBy { it.date to it.exerciseId }
            .map { (key, groupSets) ->
                Group(
                    date = key.first,
                    exerciseId = key.second,
                    sets = groupSets.sortedWith(compareBy({ it.timestamp }, { it.id }))
                )
            }
            .sortedWith(compareBy({ it.date }, { it.exerciseId }))

        val maxSets = groups.maxOfOrNull { it.sets.size }?.coerceAtLeast(1) ?: 1

        val csv = StringBuilder()
        csv.append("date,exercise_id,exercise,is_bodyweight,set_count,body_weight_kg")
        for (i in 1..maxSets) {
            csv.append(",set").append(i).append("_added_weight_kg")
            csv.append(",set").append(i).append("_reps")
        }
        csv.append(",volume_kg,best_e1rm_kg,best_e1rm_added_weight_kg")
        csv.appendLine()

        for (group in groups) {
            val exercise = exerciseById[group.exerciseId]
            val isBodyweight = exercise?.isBodyweightBased ?: false
            val bw = StatsCalculator.effectiveBodyWeight(bwByDate, group.date)

            var totalVolume = 0.0
            var hasVolume = false
            var bestE1rm: Double? = null
            var bestE1rmAdded: Double? = null

            for (set in group.sets) {
                val systemWeight = if (isBodyweight) {
                    bw?.let { it + set.addedWeightKg }
                } else {
                    set.addedWeightKg
                }
                if (systemWeight != null) {
                    totalVolume += systemWeight * set.reps
                    hasVolume = true
                }
                if (set.reps > 0) {
                    val e1rm = when {
                        isBodyweight && bw != null ->
                            (bw + set.addedWeightKg) * MovementFactors.tswFactor(set.reps)
                        isBodyweight -> null
                        else -> MovementFactors.freeWeightE1RM(set.addedWeightKg, set.reps)
                    }
                    if (e1rm != null && (bestE1rm == null || e1rm > bestE1rm)) {
                        bestE1rm = e1rm
                        bestE1rmAdded = if (isBodyweight && bw != null) {
                            (e1rm - bw).coerceAtLeast(0.0)
                        } else {
                            e1rm
                        }
                    }
                }
            }

            csv.append(group.date).append(',')
            csv.append(group.exerciseId).append(',')
            csv.append(quote(exercise?.name ?: "Unknown")).append(',')
            csv.append(isBodyweight).append(',')
            csv.append(group.sets.size).append(',')
            csv.append(bw?.let(::number).orEmpty())

            group.sets.forEach { set ->
                csv.append(',').append(number(set.addedWeightKg))
                csv.append(',').append(set.reps)
            }
            repeat(maxSets - group.sets.size) {
                csv.append(",,")
            }

            csv.append(',')
            if (hasVolume) csv.append(number(totalVolume))
            csv.append(',')
            csv.append(bestE1rm?.let(::number).orEmpty())
            csv.append(',')
            csv.append(bestE1rmAdded?.let(::number).orEmpty())
            csv.appendLine()
        }

        return csv.toString()
    }

    private data class Group(
        val date: String,
        val exerciseId: Long,
        val sets: List<WorkoutSet>
    )

    /** Up to 2 decimals, no trailing zeros, always "." as the decimal separator. */
    private fun number(value: Double): String =
        BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /** RFC 4180 quoting for fields that may contain commas or quotes. */
    private fun quote(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
