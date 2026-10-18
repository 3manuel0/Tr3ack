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
    fun migrate5to6_createsGoalsTableWithoutTouchingExistingData() {
        // Seed a populated v5 database (the schema a phone running the previous
        // release would have) so we can prove the migration preserves it.
        helper.createDatabase("test", 5).apply {
            execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (1, 'Weighted Pull-Ups', 1)")
            execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (4, 'Bicep Curls', 0)")
            execSQL("INSERT INTO body_weight_entries (id, date, bodyWeightKg) VALUES (1, '2026-08-21', 70.0)")
            execSQL(
                "INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) " +
                    "VALUES (1, 1, '2026-08-21', 32.0, 8, 1)"
            )
            execSQL(
                "INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) " +
                    "VALUES (2, 4, '2026-08-21', 30.0, 10, 2)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test", 6, /* validateDroppedTables = */ true,
            Tr3ackDatabase.MIGRATION_5_6
        )

        // New table exists and is empty right after the migration.
        val goalCount = db.query("SELECT COUNT(*) FROM goals")
        goalCount.moveToFirst()
        assertEquals(0, goalCount.getInt(0))
        goalCount.close()

        // Existing logged data is fully intact.
        val exCount = db.query("SELECT COUNT(*) FROM exercises")
        exCount.moveToFirst()
        assertEquals(2, exCount.getInt(0))
        exCount.close()

        val setCount = db.query("SELECT COUNT(*) FROM workout_sets")
        setCount.moveToFirst()
        assertEquals(2, setCount.getInt(0))
        setCount.close()

        val bw = db.query("SELECT bodyWeightKg FROM body_weight_entries WHERE date = '2026-08-21'")
        bw.moveToFirst()
        assertEquals(70.0, bw.getDouble(0), 1e-9)
        bw.close()

        val set = db.query("SELECT addedWeightKg, reps FROM workout_sets WHERE id = 1")
        set.moveToFirst()
        assertEquals(32.0, set.getDouble(0), 1e-9)
        assertEquals(8, set.getInt(1))
        set.close()

        // Round-trips a goal row using the expected columns.
        db.execSQL("INSERT INTO goals (exerciseId, type, targetValue, createdAt) VALUES (1, 'E1RM', 130.0, 42)")
        val row = db.query("SELECT type, targetValue, createdAt FROM goals WHERE exerciseId = 1")
        row.moveToFirst()
        assertEquals("E1RM", row.getString(0))
        assertEquals(130.0, row.getDouble(1), 1e-9)
        assertEquals(42L, row.getLong(2))
        row.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(6, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrate6to7_addsCompositeIndexWithoutTouchingData() {
        helper.createDatabase("test", 6).apply {
            execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (1, 'Weighted Pull-Ups', 1)")
            execSQL("INSERT INTO body_weight_entries (id, date, bodyWeightKg) VALUES (1, '2026-08-21', 70.0)")
            execSQL(
                "INSERT INTO workout_sets (id, exerciseId, date, addedWeightKg, reps, timestamp) " +
                    "VALUES (1, 1, '2026-08-21', 32.0, 8, 1)"
            )
            execSQL("INSERT INTO goals (exerciseId, type, targetValue, createdAt) VALUES (1, 'E1RM', 130.0, 42)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test", 7, /* validateDroppedTables = */ true,
            Tr3ackDatabase.MIGRATION_6_7
        )

        val index = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_workout_sets_exerciseId_date'"
        )
        var indexCount = 0
        while (index.moveToNext()) indexCount++
        index.close()
        assertEquals(1, indexCount)

        // Existing data is untouched.
        val setCount = db.query("SELECT COUNT(*) FROM workout_sets")
        setCount.moveToFirst()
        assertEquals(1, setCount.getInt(0))
        setCount.close()

        val goalCount = db.query("SELECT COUNT(*) FROM goals")
        goalCount.moveToFirst()
        assertEquals(1, goalCount.getInt(0))
        goalCount.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(7, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrate7to8_addsTargetRepsDefaultingToZero() {
        helper.createDatabase("test", 7).apply {
            execSQL(
                "INSERT INTO goals (exerciseId, type, targetValue, createdAt) VALUES (1, 'WEIGHT', 40.0, 42)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "test", 8, /* validateDroppedTables = */ true,
            Tr3ackDatabase.MIGRATION_7_8
        )

        // Existing goal survives and gets reps = 0 (weight-only).
        val row = db.query("SELECT type, targetValue, targetReps, createdAt FROM goals WHERE exerciseId = 1")
        row.moveToFirst()
        assertEquals("WEIGHT", row.getString(0))
        assertEquals(40.0, row.getDouble(1), 1e-9)
        assertEquals(0, row.getInt(2))
        assertEquals(42L, row.getLong(3))
        row.close()

        // The new column accepts an explicit rep target.
        db.execSQL("UPDATE goals SET targetReps = 8 WHERE exerciseId = 1")
        val updated = db.query("SELECT targetReps FROM goals WHERE exerciseId = 1")
        updated.moveToFirst()
        assertEquals(8, updated.getInt(0))
        updated.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(8, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrateChain_toV8_succeeds() {
        helper.createDatabase("test", 1).close()
        val db = helper.runMigrationsAndValidate(
            "test", 8, true,
            Tr3ackDatabase.MIGRATION_1_2,
            Tr3ackDatabase.MIGRATION_2_3,
            Tr3ackDatabase.MIGRATION_3_4,
            Tr3ackDatabase.MIGRATION_4_5,
            Tr3ackDatabase.MIGRATION_5_6,
            Tr3ackDatabase.MIGRATION_6_7,
            Tr3ackDatabase.MIGRATION_7_8
        )

        val goals = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='goals'"
        )
        assertEquals(1, goals.count)
        goals.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(8, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrateChain_toV7_succeeds() {
        helper.createDatabase("test", 1).close()
        val db = helper.runMigrationsAndValidate(
            "test", 7, true,
            Tr3ackDatabase.MIGRATION_1_2,
            Tr3ackDatabase.MIGRATION_2_3,
            Tr3ackDatabase.MIGRATION_3_4,
            Tr3ackDatabase.MIGRATION_4_5,
            Tr3ackDatabase.MIGRATION_5_6,
            Tr3ackDatabase.MIGRATION_6_7
        )

        val index = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_workout_sets_exerciseId_date'"
        )
        var indexCount = 0
        while (index.moveToNext()) indexCount++
        index.close()
        assertEquals(1, indexCount)

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(7, version.getInt(0))
        version.close()

        db.close()
    }

    @Test
    fun migrateChain_toV6_succeeds() {
        // Start from empty v1 schema; apply every migration in sequence.
        helper.createDatabase("test", 1).close()
        val db = helper.runMigrationsAndValidate(
            "test", 6, true,
            Tr3ackDatabase.MIGRATION_1_2,
            Tr3ackDatabase.MIGRATION_2_3,
            Tr3ackDatabase.MIGRATION_3_4,
            Tr3ackDatabase.MIGRATION_4_5,
            Tr3ackDatabase.MIGRATION_5_6
        )

        // v2_3 re-seeds 6 exercises; v3_4 removes id 5 -> 5 remain.
        val exCount = db.query("SELECT COUNT(*) FROM exercises")
        exCount.moveToFirst()
        assertEquals(5, exCount.getInt(0))
        exCount.close()

        val goalsExists = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='goals'")
        assertEquals(1, goalsExists.count)
        goalsExists.close()

        val version = db.query("PRAGMA user_version")
        version.moveToFirst()
        assertEquals(6, version.getInt(0))
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