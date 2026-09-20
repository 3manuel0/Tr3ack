package com.example.tr3ack

import android.app.Application
import android.content.Context
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
        // Rebuild the stats caches once per schema version (fresh install or right
        // after a migration). The caches are kept in sync on every write, so a full
        // rebuild on each launch would be wasted I/O.
        val prefs = getSharedPreferences("tr3ack", Context.MODE_PRIVATE)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val version = database.openHelper.readableDatabase.version
            val cacheKey = "stats_cache_built_v$version"
            if (prefs.getBoolean(cacheKey, false)) return@launch
            runCatching { repository.refreshStatsCache() }
                .onSuccess { prefs.edit().putBoolean(cacheKey, true).apply() }
        }
    }
}
