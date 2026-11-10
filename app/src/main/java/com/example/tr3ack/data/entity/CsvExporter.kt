package com.example.tr3ack.data.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.TreeMap

/**
 * Builds the user-facing CSV export. One row per logged set (tidy/long format)
 * so spreadsheets and AI tools can parse it without splitting "-" separated
 * cells. Values use "." as the decimal separator regardless of device locale.
 *
 * Columns:
 *  - date: ISO date of the set (the session day)
 *  - timestamp_ms: ordering key within the session
 *  - exercise_id / exercise / is_bodyweight
 *  - set_number: 1-based within the date+exercise
 *  - added_weight_kg: the weight logged (added weight for bodyweight moves)
 *  - body_weight_kg: effective body weight on that date (blank if none known)
 *  - system_weight_kg: BW + added for bodyweight, added for free weights
 *  - percent_body_weight: system / BW × 100 (blank if BW unknown)
 *  - reps
 *  - volume_kg: system × reps
 *  - e1rm_kg: estimated 1RM of the set (consensus for bodyweight, Mayhew for free)
 *  - e1rm_added_weight_kg: e1RM expressed as added weight (e1RM − BW for bodyweight)
 */
object CsvExporter {

    const val HEADER =
        "date,timestamp_ms,exercise_id,exercise,is_bodyweight,set_number," +
            "added_weight_kg,body_weight_kg,system_weight_kg,percent_body_weight," +
            "reps,volume_kg,e1rm_kg,e1rm_added_weight_kg"

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

        // Chronological order is the friendliest for time-series analysis.
        val ordered = sets.sortedWith(
            compareBy<WorkoutSet> { it.date }
                .thenBy { it.timestamp }
                .thenBy { it.id }
        )

        val csv = StringBuilder()
        csv.appendLine(HEADER)

        val setNumberByDayExercise = HashMap<String, Int>()
        for (set in ordered) {
            val exercise = exerciseById[set.exerciseId]
            val isBodyweight = exercise?.isBodyweightBased ?: false
            val key = "${set.date}_${set.exerciseId}"
            val setNumber = (setNumberByDayExercise[key] ?: 0) + 1
            setNumberByDayExercise[key] = setNumber

            val bw = StatsCalculator.effectiveBodyWeight(bwByDate, set.date)
            val added = set.addedWeightKg
            val systemWeight = if (isBodyweight) {
                bw?.let { it + added }
            } else {
                added
            }
            val percentBw = if (bw != null && bw > 0 && systemWeight != null) {
                systemWeight / bw * 100.0
            } else {
                null
            }
            val volume = systemWeight?.let { it * set.reps }
            val e1rm = when {
                set.reps <= 0 -> null
                isBodyweight && bw != null ->
                    (bw + added) * MovementFactors.tswFactor(set.reps)
                isBodyweight -> null
                else -> MovementFactors.freeWeightE1RM(added, set.reps)
            }
            val e1rmAdded = when {
                e1rm == null -> null
                isBodyweight && bw != null -> (e1rm - bw).coerceAtLeast(0.0)
                else -> e1rm
            }

            csv.append(set.date).append(',')
            csv.append(set.timestamp).append(',')
            csv.append(set.exerciseId).append(',')
            csv.append(quote(exercise?.name ?: "Unknown")).append(',')
            csv.append(isBodyweight).append(',')
            csv.append(setNumber).append(',')
            csv.append(number(added)).append(',')
            csv.append(bw?.let(::number).orEmpty()).append(',')
            csv.append(systemWeight?.let(::number).orEmpty()).append(',')
            csv.append(percentBw?.let(::number).orEmpty()).append(',')
            csv.append(set.reps).append(',')
            csv.append(volume?.let(::number).orEmpty()).append(',')
            csv.append(e1rm?.let(::number).orEmpty()).append(',')
            csv.append(e1rmAdded?.let(::number).orEmpty())
            csv.appendLine()
        }

        return csv.toString()
    }

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
