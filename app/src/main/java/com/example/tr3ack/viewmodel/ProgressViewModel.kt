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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonalRecords(
    val maxAddedWeight: Double = 0.0,
    val maxTotalSystemWeight: Double = 0.0,
    val maxPercentBodyWeight: Double = 0.0,
    val maxDailyVolume: Double = 0.0,
    val maxReps: Int = 0,
)

data class OneRepMax(
    val oneRepMaxWeight: Double = 0.0,
    val basedOnWeight: Double = 0.0,
    val basedOnReps: Int = 0,
    val basedOnDate: String = "",
    val addedWeightAtCurrentBW: Double = 0.0,
    val currentBodyWeight: Double = 0.0,
)

data class ChartPoint(
    val date: String,
    val totalSystemWeight: Double,
    val reps: Int,
    val addedWeight: Double,
    val percentBodyWeight: Double,
)

class ProgressViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _chartData = MutableStateFlow<List<ChartPoint>>(emptyList())
    val chartData: StateFlow<List<ChartPoint>> = _chartData.asStateFlow()

    private val _freeWeightData = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val freeWeightData: StateFlow<List<WorkoutSet>> = _freeWeightData.asStateFlow()

    private val _personalRecords = MutableStateFlow(PersonalRecords())
    val personalRecords: StateFlow<PersonalRecords> = _personalRecords.asStateFlow()

    private val _oneRepMax = MutableStateFlow<OneRepMax?>(null)
    val oneRepMax: StateFlow<OneRepMax?> = _oneRepMax.asStateFlow()

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
        viewModelScope.launch {
            val exercise = exercises.value.find { it.id == exerciseId } ?: return@launch
            repository.getSetsForExercise(exerciseId).collect { sets ->
                if (exercise.isBodyweightBased) {
                    processWeightedExercise(sets)
                } else {
                    _freeWeightData.value = sets
                    calculateFreeWeightPRs(sets)
                    calculateFreeWeight1RM(sets)
                }
            }
        }
    }

    private suspend fun processWeightedExercise(sets: List<WorkoutSet>) {
        val grouped = sets.groupBy { it.date }
        val points = mutableListOf<ChartPoint>()

        for (entry in grouped.entries.sortedBy { it.key }) {
            val date = entry.key
            val daySets = entry.value
            val bodyWeight = repository.getEffectiveBodyWeight(date) ?: continue
            val firstSet = daySets.minByOrNull { it.timestamp } ?: continue
            val tsw = bodyWeight + firstSet.addedWeightKg
            val pct = if (bodyWeight > 0) (tsw / bodyWeight) * 100 else 0.0

            points.add(
                ChartPoint(
                    date = date,
                    totalSystemWeight = tsw,
                    reps = firstSet.reps,
                    addedWeight = firstSet.addedWeightKg,
                    percentBodyWeight = pct,
                )
            )
        }

        _chartData.value = points
        calculateWeightedPRs(points)
        calculateWeighted1RM(sets)
    }

    private fun calculateWeightedPRs(data: List<ChartPoint>) {
        if (data.isEmpty()) {
            _personalRecords.value = PersonalRecords()
            return
        }
        _personalRecords.value = PersonalRecords(
            maxAddedWeight = data.maxOf { it.addedWeight },
            maxTotalSystemWeight = data.maxOf { it.totalSystemWeight },
            maxPercentBodyWeight = data.maxOf { it.percentBodyWeight },
            maxReps = data.maxOf { it.reps }
        )
    }

    private fun calculateFreeWeightPRs(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        _personalRecords.value = PersonalRecords(
            maxAddedWeight = sets.maxOf { it.addedWeightKg },
            maxReps = sets.maxOf { it.reps }
        )
    }

    private suspend fun calculateWeighted1RM(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) {
            _oneRepMax.value = null
            return
        }

        var best1RM = 0.0
        var bestWeight = 0.0
        var bestReps = 0
        var bestDate = ""

        for (set in sets) {
            val bodyWeight = repository.getEffectiveBodyWeight(set.date) ?: continue
            val totalSystemWeight = bodyWeight + set.addedWeightKg
            val est1RM = totalSystemWeight * (1.0 + set.reps / 30.0)
            if (est1RM > best1RM) {
                best1RM = est1RM
                bestWeight = totalSystemWeight
                bestReps = set.reps
                bestDate = set.date
            }
        }

        val currentBW = repository.getEffectiveBodyWeight(
            java.time.LocalDate.now().toString()
        ) ?: repository.allBodyWeightEntries.first().maxByOrNull { it.date }?.bodyWeightKg
        ?: 0.0

        val addedAtCurrentBW = if (currentBW > 0) (best1RM - currentBW).coerceAtLeast(0.0) else 0.0

        _oneRepMax.value = OneRepMax(
            oneRepMaxWeight = best1RM,
            basedOnWeight = bestWeight,
            basedOnReps = bestReps,
            basedOnDate = bestDate,
            addedWeightAtCurrentBW = addedAtCurrentBW,
            currentBodyWeight = currentBW,
        )
    }

    private suspend fun calculateFreeWeight1RM(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) {
            _oneRepMax.value = null
            return
        }

        var best1RM = 0.0
        var bestWeight = 0.0
        var bestReps = 0
        var bestDate = ""

        for (set in sets) {
            val est1RM = set.addedWeightKg * (1.0 + set.reps / 30.0)
            if (est1RM > best1RM) {
                best1RM = est1RM
                bestWeight = set.addedWeightKg
                bestReps = set.reps
                bestDate = set.date
            }
        }

        _oneRepMax.value = OneRepMax(
            oneRepMaxWeight = best1RM,
            basedOnWeight = bestWeight,
            basedOnReps = bestReps,
            basedOnDate = bestDate,
        )
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
                return ProgressViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
