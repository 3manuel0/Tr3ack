package com.example.tr3ack

import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.CsvExporter
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private val pullUps = Exercise(1, "Weighted Pull-Ups", true)
    private val curls = Exercise(4, "Bicep Curls", false)

    private fun set(
        id: Long,
        exerciseId: Long,
        date: String,
        weight: Double,
        reps: Int,
        timestamp: Long
    ) = WorkoutSet(id, exerciseId, date, weight, reps, timestamp)

    private val bodyWeight = listOf(
        BodyWeightEntry(1, "2026-08-21", 70.0)
    )

    /** Maps a CSV row to header → value (safe because test names contain no commas). */
    private fun rowMap(header: String, row: String): Map<String, String> =
        header.split(",").zip(row.split(",")).toMap()

    @Test
    fun oneRowPerExercisePerDay_withSetColumnsAndTotals() {
        val sets = listOf(
            set(3, 4, "2026-08-21", 19.0, 9, 3),
            set(1, 1, "2026-08-21", 32.0, 8, 2),
            set(2, 1, "2026-08-21", 40.0, 2, 1)
        )

        val csv = CsvExporter.buildSetsCsv(sets, listOf(pullUps, curls), bodyWeight)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size) // header + pull-ups + curls
        val header = lines[0]

        val pull = rowMap(header, lines[1])
        // Sets are ordered by timestamp: 40x2 then 32x8.
        assertEquals("2", pull["set_count"])
        assertEquals("70", pull["body_weight_kg"])
        assertEquals("40", pull["set1_added_weight_kg"])
        assertEquals("2", pull["set1_reps"])
        assertEquals("32", pull["set2_added_weight_kg"])
        assertEquals("8", pull["set2_reps"])
        assertEquals("1036", pull["volume_kg"]) // 110*2 + 102*8
        val bestE1rm = 102 * MovementFactors.tswFactor(8)
        assertEquals(bestE1rm, pull["best_e1rm_kg"]!!.toDouble(), 0.01)
        assertEquals(bestE1rm - 70, pull["best_e1rm_added_weight_kg"]!!.toDouble(), 0.01)

        val curl = rowMap(header, lines[2])
        assertEquals("1", curl["set_count"])
        assertEquals("19", curl["set1_added_weight_kg"])
        assertEquals("9", curl["set1_reps"])
        assertEquals("", curl["set2_added_weight_kg"])
        assertEquals("", curl["set2_reps"])
        assertEquals("171", curl["volume_kg"])
        assertEquals(
            MovementFactors.freeWeightE1RM(19.0, 9),
            curl["best_e1rm_kg"]!!.toDouble(),
            0.01
        )
    }

    @Test
    fun exerciseNameIsQuotedAndEscaped() {
        val weird = Exercise(9, "Curl, \"heavy\"", false)
        val csv = CsvExporter.buildSetsCsv(
            listOf(set(1, 9, "2026-08-21", 10.0, 5, 1)),
            listOf(weird),
            emptyList()
        )
        assertTrue(csv.contains("\"Curl, \"\"heavy\"\"\""))
    }

    @Test
    fun unknownBodyWeightLeavesBodyweightAggregatesBlank() {
        val csv = CsvExporter.buildSetsCsv(
            listOf(set(1, 1, "2026-08-21", 20.0, 5, 1)),
            listOf(pullUps),
            emptyList()
        )
        val lines = csv.trim().lines()
        val row = rowMap(lines[0], lines[1])
        assertEquals("20", row["set1_added_weight_kg"])
        assertEquals("5", row["set1_reps"])
        assertEquals("", row["body_weight_kg"])
        assertEquals("", row["volume_kg"])
        assertEquals("", row["best_e1rm_kg"])
        assertEquals("", row["best_e1rm_added_weight_kg"])
    }
}
