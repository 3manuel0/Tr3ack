package com.example.tr3ack.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tr3ack.data.dao.BodyWeightDao
import com.example.tr3ack.data.dao.ExerciseDao
import com.example.tr3ack.data.dao.WorkoutSetDao
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.WorkoutSetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [ExerciseEntity::class, BodyWeightEntity::class, WorkoutSetEntity::class],
    version = 3,
    exportSchema = true
)
abstract class Tr3ackDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun workoutSetDao(): WorkoutSetDao

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

        fun getDatabase(context: Context): Tr3ackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Tr3ackDatabase::class.java,
                    "tr3ack_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
            ExerciseEntity(id = 5, name = "Hammer Curls", isBodyweightBased = false),
            ExerciseEntity(id = 6, name = "Lateral Raises", isBodyweightBased = false),
        )
    }
}
