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
    version = 2,
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
                db.execSQL("INSERT INTO exercises (name, isBodyweightBased) VALUES ('Weighted Chin-Ups', 1)")
            }
        }

        fun getDatabase(context: Context): Tr3ackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Tr3ackDatabase::class.java,
                    "tr3ack_database"
                )
                    .addMigrations(MIGRATION_1_2)
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
            ExerciseEntity(name = "Weighted Pull-Ups", isBodyweightBased = true),
            ExerciseEntity(name = "Weighted Dips", isBodyweightBased = true),
            ExerciseEntity(name = "Bicep Curls", isBodyweightBased = false),
            ExerciseEntity(name = "Hammer Curls", isBodyweightBased = false),
            ExerciseEntity(name = "Lateral Raises", isBodyweightBased = false),
        )
    }
}
