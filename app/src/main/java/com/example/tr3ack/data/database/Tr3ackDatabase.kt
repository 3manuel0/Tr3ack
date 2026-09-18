package com.example.tr3ack.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tr3ack.data.dao.BodyWeightDao
import com.example.tr3ack.data.dao.ExerciseDao
import com.example.tr3ack.data.dao.StatsCacheDao
import com.example.tr3ack.data.dao.WorkoutSetDao
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.WorkoutDayEntity
import com.example.tr3ack.data.entity.WorkoutSetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExerciseEntity::class,
        BodyWeightEntity::class,
        WorkoutSetEntity::class,
        ExerciseStatsEntity::class,
        ExerciseDailyStatsEntity::class,
        WorkoutDayEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class Tr3ackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun statsCacheDao(): StatsCacheDao

    companion object {
        @Volatile
        private var INSTANCE: Tr3ackDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE exercises SET id = id + 3 WHERE id >= 3")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (3, 'Weighted Chin-Ups', 1)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM exercises")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (1, 'Weighted Pull-Ups', 1)")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (2, 'Weighted Dips', 1)")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (3, 'Weighted Chin-Ups', 1)")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (4, 'Bicep Curls', 0)")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (5, 'Hammer Curls', 0)")
                db.execSQL("INSERT INTO exercises (id, name, isBodyweightBased) VALUES (6, 'Lateral Raises', 0)")
            }
        }

        /**
         * v3 -> v4: create the three materialized stats-cache tables and drop the
         * Hammer Curls exercise (id 5) along with any of its logged sets. The caches
         * are backfilled at startup by refreshStatsCache().
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_stats (" +
                        "exerciseId INTEGER NOT NULL PRIMARY KEY, " +
                        "isBodyweightBased INTEGER NOT NULL, " +
                        "hasData INTEGER NOT NULL, " +
                        "bestE1RM REAL NOT NULL, " +
                        "bestE1RMAddedWeight REAL NOT NULL, " +
                        "bestE1RMReps INTEGER NOT NULL, " +
                        "bestE1RMTotalSystemWeight REAL NOT NULL, " +
                        "bestE1RMPercentBodyWeight REAL NOT NULL, " +
                        "bestE1RMDate TEXT NOT NULL, " +
                        "bestE1RMBodyWeightKg REAL NOT NULL, " +
                        "maxAddedWeightGte6 REAL NOT NULL, " +
                        "maxBodyweightRatio REAL NOT NULL, " +
                        "lastLoggedDate TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_daily_stats (" +
                        "exerciseId INTEGER NOT NULL, " +
                        "date TEXT NOT NULL, " +
                        "isBodyweightBased INTEGER NOT NULL, " +
                        "firstSetTSW REAL NOT NULL, " +
                        "firstSetReps INTEGER NOT NULL, " +
                        "firstSetAddedWeight REAL NOT NULL, " +
                        "firstSetPercentBodyWeight REAL NOT NULL, " +
                        "e1rm REAL NOT NULL, " +
                        "tonnage REAL NOT NULL, " +
                        "bodyWeightKg REAL NOT NULL, " +
                        "PRIMARY KEY(exerciseId, date))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_days (" +
                        "date TEXT NOT NULL PRIMARY KEY, " +
                        "setCount INTEGER NOT NULL, " +
                        "tonnage REAL NOT NULL)"
                )
                db.execSQL("DELETE FROM workout_sets WHERE exerciseId = 5")
                db.execSQL("DELETE FROM exercises WHERE id = 5")
            }
        }

        fun getDatabase(context: Context): Tr3ackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Tr3ackDatabase::class.java,
                    "tr3ack_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    database.exerciseDao().insertAll(defaultExercises())
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun defaultExercises(): List<ExerciseEntity> = listOf(
            ExerciseEntity(id = 1, name = "Weighted Pull-Ups", isBodyweightBased = true),
            ExerciseEntity(id = 2, name = "Weighted Dips", isBodyweightBased = true),
            ExerciseEntity(id = 3, name = "Weighted Chin-Ups", isBodyweightBased = true),
            ExerciseEntity(id = 4, name = "Bicep Curls", isBodyweightBased = false),
            ExerciseEntity(id = 6, name = "Lateral Raises", isBodyweightBased = false),
        )
    }
}
