package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class LogWorkoutViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _reps = MutableStateFlow("")
    val reps: StateFlow<String> = _reps.asStateFlow()

    private val _addedWeight = MutableStateFlow("")
    val addedWeight: StateFlow<String> = _addedWeight.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _lastUsedWeight = MutableStateFlow<Double?>(null)
    val lastUsedWeight: StateFlow<Double?> = _lastUsedWeight.asStateFlow()

    private val _bodyWeight = MutableStateFlow<Double?>(null)
    val bodyWeight: StateFlow<Double?> = _bodyWeight.asStateFlow()

    private val _savedSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val savedSets: StateFlow<List<WorkoutSet>> = _savedSets.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
        viewModelScope.launch {
            val lastSet = repository.getLastSetForExercise(exerciseId)
            if (lastSet != null) {
                _lastUsedWeight.value = lastSet.addedWeightKg
                _addedWeight.value = if (lastSet.addedWeightKg > 0) lastSet.addedWeightKg.toString() else ""
            } else {
                _lastUsedWeight.value = null
                _addedWeight.value = ""
            }
            loadBodyWeight()
            loadSavedSets()
        }
    }

    fun setReps(value: String) {
        _reps.value = value.filter { it.isDigit() }
    }

    fun setAddedWeight(value: String) {
        _addedWeight.value = value.filter { it.isDigit() || it == '.' }
    }

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
        loadBodyWeight()
        loadSavedSets()
    }

    private fun loadBodyWeight() {
        viewModelScope.launch {
            _bodyWeight.value = repository.getEffectiveBodyWeight(_selectedDate.value.toString())
        }
    }

    private fun loadSavedSets() {
        viewModelScope.launch {
            val exerciseId = _selectedExerciseId.value ?: return@launch
            repository.getSetsForExerciseOnDate(exerciseId, _selectedDate.value.toString())
                .collect { _savedSets.value = it }
        }
    }

    fun saveSet() {
        val exerciseId = _selectedExerciseId.value ?: return
        val repsValue = _reps.value.toIntOrNull() ?: return
        val weightValue = _addedWeight.value.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            val set = WorkoutSet(
                id = 0,
                exerciseId = exerciseId,
                date = _selectedDate.value.toString(),
                addedWeightKg = weightValue,
                reps = repsValue,
                timestamp = Instant.now().toEpochMilli()
            )
            repository.insertWorkoutSet(set)
            _reps.value = ""
            _lastUsedWeight.value = weightValue
            _saveSuccess.value = true
            loadSavedSets()

            // Reload body weight for the selected date
            loadBodyWeight()
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun getTotalSystemWeight(): Double? {
        val bw = _bodyWeight.value ?: return null
        val aw = _addedWeight.value.toDoubleOrNull() ?: return null
        val exerciseId = _selectedExerciseId.value ?: return null
        val exercise = exercises.value.find { it.id == exerciseId } ?: return null
        if (!exercise.isBodyweightBased) return null
        return bw + aw
    }

    fun getPercentOfBodyWeight(): Double? {
        val bw = _bodyWeight.value ?: return null
        if (bw <= 0) return null
        val aw = _addedWeight.value.toDoubleOrNull() ?: return null
        val exerciseId = _selectedExerciseId.value ?: return null
        val exercise = exercises.value.find { it.id == exerciseId } ?: return null
        if (!exercise.isBodyweightBased) return null
        return ((bw + aw) / bw) * 100.0
    }

    fun getDailyVolume(): Double? {
        val bw = _bodyWeight.value ?: return null
        val exerciseId = _selectedExerciseId.value ?: return null
        val exercise = exercises.value.find { it.id == exerciseId } ?: return null
        if (!exercise.isBodyweightBased) return null
        return _savedSets.value.sumOf { (bw + it.addedWeightKg) * it.reps }
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogWorkoutViewModel::class.java)) {
                return LogWorkoutViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
