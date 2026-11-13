package com.example.tr3ack.data.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeMap

/**
 * Builds the user-facing CSV export.
 *
 * One row per exercise **per day**. The per-set data lives in three list cells
 * using `|` as the separator, so any number of sets is supported and a weight
 * change on, say, set 3 is visible as the third value:
 *
 *     added_weight_kg = 25.4|25.4|22.5|20
 *     reps            = 10|10|8|7
 *     system_weight_kg= 114.6|114.6|111.7|109.2
 *
 * This is deliberately not a fixed `set1/set2/...` layout: back-filled sets and
 * sessions with more/fewer sets stay on one line. The list values are paired by
 * index (set 3 = 3rd value in each list).
 *
 * Columns:
 *  - date, exercise_id, exercise, is_bodyweight, set_count, body_weight_kg
 *  - added_weight_kg: `|`-separated per-set logged weights (added weight for
 *    bodyweight movements)
 *  - reps: `|`-separated per-set reps
 *  - system_weight_kg: `|`-separated per-set system weights (BW + added for
 *    bodyweight, added for free weights)
 *  - volume_kg: total system weight × reps for the day
 *  - best_e1rm_kg: highest per-set e1RM (consensus for bodyweight, Mayhew for
 *    free weights)
 *  - best_e1rm_added_weight_kg: the same e1RM expressed as added weight
 *
 * Numbers use "." as the decimal separator regardless of device locale.
 */
object CsvExporter {

    const val LIST_SEPARATOR = "|"

    const val HEADER =
        "date,exercise_id,exercise,is_bodyweight,set_count,body_weight_kg," +
            "added_weight_kg,reps,system_weight_kg,volume_kg," +
            "best_e1rm_kg,best_e1rm_added_weight_kg"

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

        val csv = StringBuilder()
        csv.appendLine(HEADER)

        for (group in groups) {
            val exercise = exerciseById[group.exerciseId]
            val isBodyweight = exercise?.isBodyweightBased ?: false
            val bw = StatsCalculator.effectiveBodyWeight(bwByDate, group.date)

            val systemWeights = group.sets.map { set ->
                if (isBodyweight) bw?.let { it + set.addedWeightKg } else set.addedWeightKg
            }
            val hasAllSystemWeights = systemWeights.all { it != null }
            val volume = if (hasAllSystemWeights) {
                group.sets.mapIndexed { index, set -> systemWeights[index]!! * set.reps }.sum()
            } else {
                null
            }

            var bestE1rm: Double? = null
            var bestE1rmAdded: Double? = null
            group.sets.forEach { set ->
                if (set.reps <= 0) return@forEach
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

            csv.append(group.date).append(',')
            csv.append(group.exerciseId).append(',')
            csv.append(quote(exercise?.name ?: "Unknown")).append(',')
            csv.append(isBodyweight).append(',')
            csv.append(group.sets.size).append(',')
            csv.append(bw?.let(::number).orEmpty()).append(',')
            csv.append(group.sets.joinToString(LIST_SEPARATOR) { number(it.addedWeightKg) }).append(',')
            csv.append(group.sets.joinToString(LIST_SEPARATOR) { it.reps.toString() }).append(',')
            csv.append(
                group.sets.mapIndexed { index, _ ->
                    systemWeights[index]?.let(::number).orEmpty()
                }.joinToString(LIST_SEPARATOR)
            ).append(',')
            csv.append(volume?.let(::number).orEmpty()).append(',')
            csv.append(bestE1rm?.let(::number).orEmpty()).append(',')
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
