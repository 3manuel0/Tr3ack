package com.example.tr3ack

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tr3ack.data.database.Tr3ackDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Tr3ackDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        Tr3ackDatabase::class.java
    )

    private fun populateV3(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val bwTrue = 1; val bwFalse = 0

        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (1, 'Weighted Pull-Ups', $bwTrue)")
        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (2, 'Weighted Dips', $bwTrue)")
        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (3, 'Weighted Chin-Ups', $bwTrue)")
        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (4, 'Bicep Curls', $bwFalse)")
        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (5, 'Hammer Curls', $bwFalse)")
        db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (6, 'Lateral Raises', $bwFalse)")

        // Pull-ups: 32 kg @ 8 + 40 kg @ 2
        db.execSQL("INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) VALUES (1, 1, '2026-08-21', 32.0, 8, 1)")
        db.execSQL("INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) VALUES (2, 1, '2026-08-21', 40.0, 2, 2)")
        // Bicep Curls: 30 kg @ 10 + 16 kg @ 10
        db.execSQL("INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) VALUES (3, 4, '2026-08-21', 30.0, 10, 3)")
        db.execSQL("INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) VALUES (4, 4, '2026-08-21', 16.0, 10, 4)")
        // Hammer Curls: 10 kg @ 12 (should be dropped by v4)
        db.execSQL("INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) VALUES (5, 5, '2026-08-21', 10.0, 12, 5)")

        db.execSQL("INSERT INTO body_weight_entries (id, date, bodyWeightKg) VALUES (1, '2026-08-21', 70.0)")
    }

    @Test
    fun migrate3to4_removesHammerCurlsCreatesCacheTables() {
        helper.createDatabase("test", 3).apply {
            populateV3(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test", 4, /* validateDroppedTables = */ true,
            Tr3ackDatabase.MIGRATION_3_4
        )

        // Hammer Curls gone
        val cursor = db.query("SELECT COUNT(*) FROM exercises WHERE id = 5")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        // All other exercises intact
        val exCount = db.query("SELECT COUNT(*) FROM exercises")
        exCount.moveToFirst()
        assertEquals(5, exCount.getInt(0))
        exCount.close()

        // Hammer sets removed; other sets intact
        val hammerSets = db.query("SELECT COUNT(*) FROM workout_sets WHERE exerciseId = 5")
        hammerSets.moveToFirst()
        assertEquals(0, hammerSets.getInt(0))
        hammerSets.close()

        val totalSets = db.query("SELECT COUNT(*) FROM workout_sets")
        totalSets.moveToFirst()
        assertEquals(4, totalSets.getInt(0))
        totalSets.close()

        // Body-weight data intact
        val bwCount = db.query("SELECT COUNT(*) FROM body_weight_entries")
        bwCount.moveToFirst()
        assertEquals(1, bwCount.getInt(0))
        bwCount.close()

        // Cache tables exist (room_validation checks the full v4 JSON; here we do explicit smoke checks)
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('exercise_stats','exercise_daily_stats','workout_days')")
        var found = 0
        while (tables.moveToNext()) found++
        tables.close()
        assertEquals(3, found)

        db.close()
    }

    @Test
    fun migrate3to4_setsUserVersion4() {
        helper.createDatabase("test", 3).close()
        val db = helper.runMigrationsAndValidate(
            "test", 4, true,
            Tr3ackDatabase.MIGRATION_3_4
        )
        val pragma = db.query("PRAGMA user_version")
        pragma.moveToFirst()
        assertEquals(4, pragma.getInt(0))
        pragma.close()
        db.close()
    }

    @Test
    fun migrate4to5_addsQueryIndices() {
        helper.createDatabase("test", 4).close()
        val db = helper.runMigrationsAndValidate(
            "test", 5, true,
            Tr3ackDatabase.MIGRATION_4_5
        )

        val expectedIndexes = setOf(
            "index_workout_sets_date",
            "index_workout_sets_exerciseId",
            "index_body_weight_entries_date"
        )
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN " +
                "('index_workout_sets_date','index_workout_sets_exerciseId','index_body_weight_entries_date')"
        )
        val found = mutableSetOf<String>()
        while (cursor.moveToNext()) found.add(cursor.getString(0))
        cursor.close()
        assertEquals(expectedIndexes, found)

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(5, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrateChain_toV4_succeeds() {
        // Start from empty v1 schema; apply every migration in sequence
        helper.createDatabase("test", 1).close()
        val db = helper.runMigrationsAndValidate(
            "test", 4, true,
            Tr3ackDatabase.MIGRATION_1_2,
            Tr3ackDatabase.MIGRATION_2_3,
            Tr3ackDatabase.MIGRATION_3_4
        )

        // v2_3 re-seeds 6 exercises; v3_4 removes id 5 → 5 remain
        val exCount = db.query("SELECT COUNT(*) FROM exercises")
        exCount.moveToFirst()
        assertEquals(5, exCount.getInt(0))
        exCount.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(4, version.getInt(0))
        version.close()

        db.close()
    }
}