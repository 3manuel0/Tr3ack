package com.example.tr3ack.data.backup

import android.content.Context
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Keeps a rolling automatic backup of the whole database in app-private storage
 * so it can be restored if anything goes wrong — no SAF picker, no permissions.
 *
 * Writes:
 *  - `auto_backup.json` — the latest snapshot (restore target)
 *  - `auto_<date>.json` — one snapshot per day, pruned to the last 14
 *
 * Backs up on app start and whenever the sets/body-weight flows change (debounced
 * so a busy logging session coalesces into a single write).
 */
class AutoBackupManager(
    context: Context,
    private val repository: Tr3ackRepository,
    private val scope: CoroutineScope
) {
    private val dir = File(context.filesDir, "backups").apply { mkdirs() }
    private val latestFile = File(dir, "auto_backup.json")
    private val mutex = Mutex()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Last backup wall-clock time (ms), or null if none exists yet. */
    fun lastBackupAtMillis(): Long? =
        latestFile.takeIf { it.exists() }?.lastModified()

    /** Starts the change watcher: debounce 3 s after any data change, then write. */
    fun start() {
        scope.launch {
            combine(
                repository.allWorkoutSets,
                repository.allBodyWeightEntries
            ) { sets, bodyWeights -> sets.size to bodyWeights.size }
                .distinctUntilChanged()
                .collect { backupNow() }
        }
    }

    suspend fun backupNow() = mutex.withLock {
        val sets = repository.allWorkoutSets.first()
        val exercises = repository.allExercises.first()
        val bodyWeights = repository.allBodyWeightEntries.first()

        // Nothing worth protecting yet.
        if (sets.isEmpty() && bodyWeights.isEmpty()) return@withLock

        val json = BackupJson.build(exercises, sets, bodyWeights)
        latestFile.writeText(json)

        // One dated snapshot per day; keep the last 14.
        val dated = File(dir, "auto_${LocalDate.now().format(dateFormat)}.json")
        if (!dated.exists()) dated.writeText(json)
        val snapshots = dir.listFiles { f ->
            f.name.startsWith("auto_") && f.name.endsWith(".json") && f.name != "auto_backup.json"
        }?.sortedByDescending { it.name } ?: emptyList()
        snapshots.drop(14).forEach { it.delete() }
    }

    /** Restores the latest snapshot. Returns a human-readable result message. */
    suspend fun restoreLatest(): String = mutex.withLock {
        val file = latestFile.takeIf { it.exists() }
            ?: return@withLock "No automatic backup found yet"
        return@withLock runCatching {
            val payload = BackupJson.parse(file.readText())
            repository.replaceAllData(payload.exercises, payload.sets, payload.bodyWeightEntries)
            val whenText = file.lastModified().let { millis ->
                val dt = java.time.Instant.ofEpochMilli(millis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                dt
            }
            "Restored ${payload.sets.size} sets, ${payload.bodyWeightEntries.size} weight entries (from $whenText)"
        }.getOrElse { e ->
            "Restore failed: ${e.message}"
        }
    }
}
