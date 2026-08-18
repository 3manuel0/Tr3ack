package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.BodyWeightEntry
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
import java.time.temporal.ChronoUnit

data class PersonalBest(
    val maxTotalSystemWeight: Double = 0.0,
    val maxPercentBodyWeight: Double = 0.0,
    val reps: Int = 0,
    val exerciseName: String = "",
    val dateAchieved: String = ""
)

class DashboardViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    private val today = LocalDate.now().toString()

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySets: StateFlow<List<WorkoutSet>> = repository.getSetsForDate(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutDates: StateFlow<List<String>> = repository.allWorkoutDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSets: StateFlow<List<WorkoutSet>> = repository.allWorkoutSets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayBodyWeight = MutableStateFlow<Double?>(null)
    val todayBodyWeightLive: StateFlow<Double?> = _todayBodyWeight.asStateFlow()

    private val _pullUpsPB = MutableStateFlow(PersonalBest())
    val pullUpsPB: StateFlow<PersonalBest> = _pullUpsPB.asStateFlow()

    private val _dipsPB = MutableStateFlow(PersonalBest())
    val dipsPB: StateFlow<PersonalBest> = _dipsPB.asStateFlow()

    init {
        viewModelScope.launch {
            _todayBodyWeight.value = repository.getEffectiveBodyWeight(today)
        }
    }

    fun saveBodyWeight(weightKg: Double) {
        viewModelScope.launch {
            val existing = repository.getTodayBodyWeightEntry(today)
            if (existing != null) {
                repository.updateBodyWeight(
                    BodyWeightEntry(
                        id = existing.id,
                        date = today,
                        bodyWeightKg = weightKg
                    )
                )
            } else {
                repository.insertBodyWeight(
                    BodyWeightEntry(
                        id = 0,
                        date = today,
                        bodyWeightKg = weightKg
                    )
                )
            }
            _todayBodyWeight.value = weightKg
        }
    }

    fun recalculatePersonalBest() {
        viewModelScope.launch {
            val bwExercises = exercises.value.filter { it.isBodyweightBased }
            if (bwExercises.isEmpty()) return@launch

            for (exercise in bwExercises) {
                val exerciseSets = allSets.value.filter { it.exerciseId == exercise.id }
                var bestSet: WorkoutSet? = null
                var bestTSW = 0.0

                for (set in exerciseSets) {
                    val bodyWeight = repository.getEffectiveBodyWeight(set.date) ?: continue
                    if (bodyWeight <= 0) continue
                    val tsw = bodyWeight + set.addedWeightKg
                    if (tsw > bestTSW) {
                        bestTSW = tsw
                        bestSet = set
                    }
                }

                val pb = if (bestSet != null && bestTSW > 0) {
                    val bodyWeight = repository.getEffectiveBodyWeight(bestSet.date) ?: 0.0
                    val pct = if (bodyWeight > 0) (bestTSW / bodyWeight) * 100.0 else 0.0
                    PersonalBest(
                        maxTotalSystemWeight = bestTSW,
                        maxPercentBodyWeight = pct,
                        reps = bestSet.reps,
                        exerciseName = exercise.name,
                        dateAchieved = bestSet.date
                    )
                } else {
                    PersonalBest(exerciseName = exercise.name)
                }

                when (exercise.name) {
                    "Weighted Pull-Ups" -> _pullUpsPB.value = pb
                    "Weighted Dips" -> _dipsPB.value = pb
                }
            }
        }
    }

    fun daysSinceLastSession(): Long {
        val dates = allWorkoutDates.value.sortedDescending()
        if (dates.isEmpty()) return -1
        val lastDate = LocalDate.parse(dates.first())
        return ChronoUnit.DAYS.between(lastDate, LocalDate.now())
    }

    fun lastLoggedForExercise(exerciseId: Long): Long? {
        val sets = allSets.value.filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) return null
        val mostRecent = sets.maxByOrNull { it.date } ?: return null
        val lastDate = LocalDate.parse(mostRecent.date)
        return ChronoUnit.DAYS.between(lastDate, LocalDate.now())
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
