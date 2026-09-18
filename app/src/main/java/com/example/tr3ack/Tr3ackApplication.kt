package com.example.tr3ack

import android.app.Application
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Tr3ackApplication : Application() {
    val database by lazy { Tr3ackDatabase.getDatabase(this) }
    val repository by lazy {
        Tr3ackRepository(
            database = database,
            exerciseDao = database.exerciseDao(),
            bodyWeightDao = database.bodyWeightDao(),
            workoutSetDao = database.workoutSetDao(),
            statsCacheDao = database.statsCacheDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Backfill / rebuild the stats caches so screens only ever read materialized data.
        // Opening the DB here may run MIGRATION_3_4 on existing installs.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.refreshStatsCache()
        }
    }
}
