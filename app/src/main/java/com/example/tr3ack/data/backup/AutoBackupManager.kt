package com.example.tr3ack.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
 *  - `Download/Tr3ack/Tr3ack_AutoBackup.json` — a user-accessible copy (via
 *    MediaStore) so the data survives uninstall and can be re-imported later
 *
 * Backs up on app start and whenever the sets/body-weight flows change (debounced
 * so a busy logging session coalesces into a single write).
 */
class AutoBackupManager(
    context: Context,
    private val repository: Tr3ackRepository,
    private val scope: CoroutineScope
) {
    private val resolver = context.applicationContext.contentResolver
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
                .debounce(3_000)
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
        writeToDownloads(json)

        // One dated snapshot per day; keep the last 14.
        val dated = File(dir, "auto_${LocalDate.now().format(dateFormat)}.json")
        if (!dated.exists()) dated.writeText(json)
        val snapshots = dir.listFiles { f ->
            f.name.startsWith("auto_") && f.name.endsWith(".json") && f.name != "auto_backup.json"
        }?.sortedByDescending { it.name } ?: emptyList()
        snapshots.drop(14).forEach { it.delete() }
    }

    /**
     * Also writes a user-accessible copy to `Download/Tr3ack/` via MediaStore, so
     * the data survives an uninstall and can be re-imported from any future build.
     * MediaStore.Downloads needs API 29+; older devices keep only the app-private
     * snapshot (which still covers in-app restores).
     */
    private fun writeToDownloads(json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/Tr3ack/"
            val displayName = "Tr3ack_AutoBackup.json"

            // Remove any previous copy so we don't accumulate "(1)" duplicates.
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(displayName, relativePath),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(collection, values) ?: return@runCatching
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
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
