package com.example.tr3ack

import android.app.Application
import android.content.Context
import com.example.tr3ack.data.database.Tr3ackDatabase
import com.example.tr3ack.data.entity.StatsCalculator
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
            statsCacheDao = database.statsCacheDao(),
            goalDao = database.goalDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Rebuild the stats caches once per formula version (fresh install or right
        // after an E1RM/tonnage formula change). The caches are kept in sync on every
        // write, so a full rebuild on each launch would be wasted I/O.
        val prefs = getSharedPreferences("tr3ack", Context.MODE_PRIVATE)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val cacheKey = "stats_cache_built_v${StatsCalculator.CACHE_VERSION}"
            if (prefs.getBoolean(cacheKey, false)) return@launch
            runCatching { repository.refreshStatsCache() }
                .onSuccess { prefs.edit().putBoolean(cacheKey, true).apply() }
        }
    }
}
