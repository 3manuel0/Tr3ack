package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PersonalBest(
    val maxTotalSystemWeight: Double = 0.0,
    val maxPercentBodyWeight: Double = 0.0,
    val estimatedOneRM: Double = 0.0,
    val addedWeight: Double = 0.0,
    val addedWeightPercentBodyWeight: Double = 0.0,
    val reps: Int = 0,
    val isBodyweightBased: Boolean = true,
    val exerciseName: String = "",
    val dateAchieved: String = ""
)

class DashboardViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now().toString())

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySets: StateFlow<List<WorkoutSet>> = _today
        .flatMapLatest { date -> repository.getSetsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayBodyWeight = MutableStateFlow<Double?>(null)
    val todayBodyWeightLive: StateFlow<Double?> = _todayBodyWeight.asStateFlow()

    private val _bodyWeightHistory = MutableStateFlow<List<BodyWeightEntry>>(emptyList())
    val bodyWeightHistory: StateFlow<List<BodyWeightEntry>> = _bodyWeightHistory.asStateFlow()

    private val _pullUpsPB = MutableStateFlow(PersonalBest())
    val pullUpsPB: StateFlow<PersonalBest> = _pullUpsPB.asStateFlow()

    private val _dipsPB = MutableStateFlow(PersonalBest())
    val dipsPB: StateFlow<PersonalBest> = _dipsPB.asStateFlow()

    private val _bicepCurlsPB = MutableStateFlow(PersonalBest())
    val bicepCurlsPB: StateFlow<PersonalBest> = _bicepCurlsPB.asStateFlow()

    private val _lateralRaisesPB = MutableStateFlow(PersonalBest())
    val lateralRaisesPB: StateFlow<PersonalBest> = _lateralRaisesPB.asStateFlow()

    private val _exerciseTrends = MutableStateFlow<Map<Long, List<Double>>>(emptyMap())
    val exerciseTrends: StateFlow<Map<Long, List<Double>>> = _exerciseTrends.asStateFlow()

    private val _lastLoggedDateByExercise = MutableStateFlow<Map<Long, String>>(emptyMap())
    val lastLoggedDateByExercise: StateFlow<Map<Long, String>> = _lastLoggedDateByExercise.asStateFlow()

    private val _lastSessionDate = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            _todayBodyWeight.value = repository.getEffectiveBodyWeight(_today.value)
        }
        viewModelScope.launch {
            repository.allBodyWeightEntries.collect { entries ->
                val cutoff = LocalDate.now().minusDays(89).toString()
                _bodyWeightHistory.value = entries
                    .filter { it.date >= cutoff }
                    .sortedBy { it.date }
            }
        }
        viewModelScope.launch {
            combine(repository.exerciseStats, exercises) { stats, current -> stats to current }.collect { (stats, current) ->
                _lastLoggedDateByExercise.value = stats.associate { it.exerciseId to it.lastLoggedDate }
                val byId = stats.associateBy { it.exerciseId }
                _pullUpsPB.value = buildPersonalBest(byId[ExerciseIds.WEIGHTED_PULL_UPS], current)
                _dipsPB.value = buildPersonalBest(byId[ExerciseIds.WEIGHTED_DIPS], current)
                _bicepCurlsPB.value = buildPersonalBest(byId[ExerciseIds.BICEP_CURLS], current)
                _lateralRaisesPB.value = buildPersonalBest(byId[ExerciseIds.LATERAL_RAISES], current)
            }
        }
        viewModelScope.launch {
            repository.workoutDays.collect { days ->
                _lastSessionDate.value = days.maxOfOrNull { it.date }
            }
        }
        viewModelScope.launch {
            listOf(
                ExerciseIds.WEIGHTED_PULL_UPS,
                ExerciseIds.WEIGHTED_DIPS,
                ExerciseIds.BICEP_CURLS,
                ExerciseIds.LATERAL_RAISES
            ).forEach { exerciseId ->
                repository.getDailyStatsForExercise(exerciseId).collect { daily ->
                    val series = daily.filter { it.e1rm > 0.0 }.map { it.e1rm }.takeLast(30)
                    _exerciseTrends.value = _exerciseTrends.value + (exerciseId to series)
                }
            }
        }
    }

    private fun buildPersonalBest(
        stats: ExerciseStatsEntity?,
        allExercises: List<Exercise>
    ): PersonalBest {
        val exercise = allExercises.find { it.id == stats?.exerciseId }
        if (stats == null || !stats.hasData || stats.bestE1RM <= 0.0) {
            return PersonalBest(exerciseName = exercise?.name ?: "")
        }
        return if (stats.isBodyweightBased) {
            PersonalBest(
                maxTotalSystemWeight = stats.bestE1RMTotalSystemWeight,
                maxPercentBodyWeight = stats.bestE1RMPercentBodyWeight,
                estimatedOneRM = stats.bestE1RM,
                addedWeight = stats.bestE1RMAddedWeight,
                reps = stats.bestE1RMReps,
                isBodyweightBased = true,
                exerciseName = exercise?.name ?: "",
                dateAchieved = stats.bestE1RMDate
            )
        } else {
            PersonalBest(
                maxTotalSystemWeight = stats.bestE1RMTotalSystemWeight,
                estimatedOneRM = stats.bestE1RM,
                addedWeight = stats.bestE1RMAddedWeight,
                addedWeightPercentBodyWeight = stats.bestE1RMPercentBodyWeight,
                reps = stats.bestE1RMReps,
                isBodyweightBased = false,
                exerciseName = exercise?.name ?: "",
                dateAchieved = stats.bestE1RMDate
            )
        }
    }

    fun saveBodyWeight(weightKg: Double) {
        viewModelScope.launch {
            val today = _today.value
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

    fun refreshToday() {
        viewModelScope.launch {
            val now = LocalDate.now().toString()
            if (now != _today.value) {
                _today.value = now
            }
            _todayBodyWeight.value = repository.getEffectiveBodyWeight(now)
        }
    }

    fun daysSinceLastSession(): Long {
        val last = _lastSessionDate.value ?: return -1
        return ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.now())
    }

    fun lastLoggedForExercise(exerciseId: Long): Long? {
        val last = _lastLoggedDateByExercise.value[exerciseId] ?: return null
        val lastDate = LocalDate.parse(last)
        return ChronoUnit.DAYS.between(lastDate, LocalDate.now())
    }
}