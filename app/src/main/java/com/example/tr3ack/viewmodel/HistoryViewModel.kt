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
import java.time.LocalDate

class HistoryViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val allDates: StateFlow<List<String>> = repository.allWorkoutDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _selectedDateSets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val selectedDateSets: StateFlow<List<WorkoutSet>> = _selectedDateSets.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch {
            repository.getSetsForDate(date.toString()).collect { _selectedDateSets.value = it }
        }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch {
            repository.deleteWorkoutSet(set)
        }
    }

    fun getExerciseName(exerciseId: Long): String {
        return exercises.value.find { it.id == exerciseId }?.name ?: "Unknown"
    }

    fun isBodyweightExercise(exerciseId: Long): Boolean {
        return exercises.value.find { it.id == exerciseId }?.isBodyweightBased ?: false
    }

    fun getEffectiveBodyWeight(date: String): Double? {
        // This is a simplified version - in a real app, we'd query the repository
        return null
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                return HistoryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
