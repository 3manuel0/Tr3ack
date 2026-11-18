package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.backup.BackupJson
import com.example.tr3ack.data.entity.CsvExporter
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HistoryViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val allDates: StateFlow<List<String>> = repository.workoutDatesCached
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _selectedDateSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val selectedDateSets: StateFlow<List<WorkoutSet>> = _selectedDateSets.asStateFlow()

    private val _exportCsv = MutableStateFlow<String?>(null)
    val exportCsv: StateFlow<String?> = _exportCsv.asStateFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    private val _deletedSet = MutableStateFlow<WorkoutSet?>(null)
    val deletedSet: StateFlow<WorkoutSet?> = _deletedSet.asStateFlow()

    private var selectionJob: Job? = null

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            repository.getSetsForDate(date.toString()).collect { _selectedDateSets.value = it }
        }
    }

    fun deleteSet(set: WorkoutSet) {
        _deletedSet.value = set
        viewModelScope.launch {
            repository.deleteWorkoutSet(set)
        }
    }

    fun undoDeleteSet(set: WorkoutSet) {
        _deletedSet.value = null
        viewModelScope.launch {
            repository.insertWorkoutSet(set)
        }
    }

    fun clearDeletedSet() {
        _deletedSet.value = null
    }

    fun generateCsv() {
        viewModelScope.launch(Dispatchers.Default) {
            _exportCsv.value = buildCsv()
        }
    }

    suspend fun buildCsv(): String {
        return CsvExporter.buildSetsCsv(
            sets = repository.allWorkoutSets.first(),
            exercises = repository.allExercises.first(),
            bodyWeightEntries = repository.allBodyWeightEntries.first()
        )
    }

    fun consumeCsv() {
        _exportCsv.value = null
    }

    fun generateBackupJson() {
        viewModelScope.launch(Dispatchers.Default) {
            _exportJson.value = buildBackupJson()
        }
    }

    suspend fun buildBackupJson(): String {
        return BackupJson.build(
            allExercises = repository.allExercises.first(),
            allSets = repository.allWorkoutSets.first(),
            allBodyWeight = repository.allBodyWeightEntries.first()
        )
    }

    fun consumeJson() {
        _exportJson.value = null
    }

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val payload = BackupJson.parse(jsonString)
                repository.replaceAllData(
                    payload.exercises,
                    payload.sets,
                    payload.bodyWeightEntries
                )
                _importResult.value =
                    "Restored ${payload.exercises.size} exercises, ${payload.sets.size} sets, ${payload.bodyWeightEntries.size} weight entries"
            } catch (e: Exception) {
                _importResult.value = "Import failed: ${e.message}"
            }
        }
    }

    fun consumeImportResult() {
        _importResult.value = null
    }
}
