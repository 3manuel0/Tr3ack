package com.example.tr3ack

import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.CsvExporter

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

    @Test
    fun oneRowPerSet_chronologicalWithSetNumbersAndMath() {
        val sets = listOf(
            // deliberately out of order
            set(3, 4, "2026-08-21", 19.0, 9, 3),
            set(1, 1, "2026-08-21", 32.0, 8, 2),
            set(2, 1, "2026-08-21", 40.0, 2, 1)
        )

        val csv = CsvExporter.buildSetsCsv(sets, listOf(pullUps, curls), bodyWeight)
        val lines = csv.trim().lines()
        assertEquals(CsvExporter.HEADER, lines[0])
        assertEquals(4, lines.size)

        // Earliest timestamp first (40kg x2), then 32kg x8, then curl.
        assertTrue(lines[1].startsWith("2026-08-21,1,1,\"Weighted Pull-Ups\",true,1,40,"))
        assertTrue(lines[2].startsWith("2026-08-21,2,1,\"Weighted Pull-Ups\",true,2,32,"))
        assertTrue(lines[3].startsWith("2026-08-21,3,4,\"Bicep Curls\",false,1,19,"))

        val pullUp2 = lines[1].split(",")
        // date,timestamp,id,name(quoted split separately),isBw,setNo,added,bw,system,pct,reps,volume,e1rm,e1rmAdded
        assertEquals("70", pullUp2[7])
        assertEquals("110", pullUp2[8])
        assertEquals("157.14", pullUp2[9])
        assertEquals("2", pullUp2[10])
        assertEquals("220", pullUp2[11])
        assertEquals(110 * MovementFactors.tswFactor(2), pullUp2[12].toDouble(), 0.01)
        assertEquals(110 * MovementFactors.tswFactor(2) - 70, pullUp2[13].toDouble(), 0.01)

        val curl = lines[3].split(",")
        assertEquals("19", curl[6])
        assertEquals("70", curl[7])
        assertEquals("19", curl[8])
        assertEquals("27.14", curl[9])
        assertEquals("171", curl[11])
        assertEquals(MovementFactors.freeWeightE1RM(19.0, 9), curl[12].toDouble(), 0.01)
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
    fun unknownBodyWeightLeavesBodyweightFieldsBlank() {
        val csv = CsvExporter.buildSetsCsv(
            listOf(set(1, 1, "2026-08-21", 20.0, 5, 1)),
            listOf(pullUps),
            emptyList()
        )
        val row = csv.trim().lines()[1].split(",")
        // date,timestamp,id,name,isBw,setNo,added,bw,system,pct,reps,volume,e1rm,e1rmAdded
        assertEquals("20", row[6])
        assertEquals("", row[7])
        assertEquals("", row[8])
        assertEquals("", row[9])
        assertEquals("5", row[10])
        assertEquals("", row[11])
        assertEquals("", row[12])
        assertEquals("", row[13])
    }
}
