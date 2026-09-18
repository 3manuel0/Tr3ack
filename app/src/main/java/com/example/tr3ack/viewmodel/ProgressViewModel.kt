package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseDailyStatsEntity
import com.example.tr3ack.data.entity.ExerciseStatsEntity
import com.example.tr3ack.data.entity.MovementFactors
import com.example.tr3ack.data.entity.MovementType
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

data class BestSetRecord(
    val estimatedOneRM: Double = 0.0,
    val addedWeight: Double = 0.0,
    val reps: Int = 0,
    val totalSystemWeight: Double = 0.0,
    val percentBodyWeight: Double = 0.0,
    val date: String = "",
)

data class OneRepMax(
    val oneRepMaxTSL: Double = 0.0,
    val oneRepMaxAddedWeight: Double = 0.0,
    val basedOnTSL: Double = 0.0,
    val basedOnReps: Int = 0,
    val basedOnDate: String = "",
    val currentBodyWeight: Double = 0.0,
    val movementType: MovementType = MovementType.UNKNOWN,
    val strengthMultiplier: Double = 0.0,
    val workingLoad85: Double = 0.0,
    val workingLoad80: Double = 0.0,
    val workingLoad75: Double = 0.0,
    val bodyweightPercentage: Double = 0.0,
)

data class ChartPoint(
    val date: String,
    val totalSystemWeight: Double,
    val reps: Int,
    val addedWeight: Double,
    val percentBodyWeight: Double,
    val estimatedOneRM: Double = 0.0,
    val sessionTonnage: Double = 0.0,
    val beltLoad: Double = 0.0,
    val bodyWeightKg: Double = 0.0,
)

/** Per-exercise E1RM history (date -> e1RM) for the cross-exercise comparison chart. */
data class ExerciseTrend(
    val exerciseId: Long,
    val name: String,
    val series: Map<String, Double>,
)

class ProgressViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exerciseTrends = MutableStateFlow<List<ExerciseTrend>>(emptyList())
    val exerciseTrends: StateFlow<List<ExerciseTrend>> = _exerciseTrends.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _chartData = MutableStateFlow<List<ChartPoint>>(emptyList())
    val chartData: StateFlow<List<ChartPoint>> = _chartData.asStateFlow()

    private val _freeWeightData = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val freeWeightData: StateFlow<List<WorkoutSet>> = _freeWeightData.asStateFlow()

    private val _bestSet = MutableStateFlow(BestSetRecord())
    val bestSet: StateFlow<BestSetRecord> = _bestSet.asStateFlow()

    private val _oneRepMax = MutableStateFlow<OneRepMax?>(null)
    val oneRepMax: StateFlow<OneRepMax?> = _oneRepMax.asStateFlow()

    private var selectionJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            repository.allExercises.collect { exercises ->
                _exerciseTrends.value = exercises.mapNotNull { exercise ->
                    val daily = repository.getDailyStatsForExercise(exercise.id).first()
                    val series = daily
                        .filter { it.e1rm > 0.0 }
                        .associate { it.date to it.e1rm }
                    if (series.isEmpty()) null else ExerciseTrend(exercise.id, exercise.name, series)
                }
            }
        }
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            val exercise = exercises.value.find { it.id == exerciseId } ?: return@launch
            coroutineScope {
                launch {
                    repository.getDailyStatsForExercise(exerciseId).collect { daily ->
                        _chartData.value = daily.map { buildChartPoint(it) }
                    }
                }
                launch {
                    repository.exerciseStats.collect { stats ->
                        val row = stats.find { it.exerciseId == exerciseId }
                        _bestSet.value = buildBestSet(row)
                        _oneRepMax.value = if (exercise.isBodyweightBased) {
                            buildWeightedOneRepMax(row, exercise)
                        } else {
                            buildFreeWeightOneRepMax(row, exercise)
                        }
                    }
                }
                if (!exercise.isBodyweightBased) {
                    launch {
                        repository.getSetsForExercise(exerciseId).collect { sets ->
                            _freeWeightData.value = sets
                        }
                    }
                } else {
                    _freeWeightData.value = emptyList()
                }
            }
        }
    }

    private fun buildChartPoint(daily: ExerciseDailyStatsEntity): ChartPoint = ChartPoint(
        date = daily.date,
        totalSystemWeight = daily.firstSetTSW,
        reps = daily.firstSetReps,
        addedWeight = daily.firstSetAddedWeight,
        percentBodyWeight = daily.firstSetPercentBodyWeight,
        estimatedOneRM = daily.e1rm,
        sessionTonnage = daily.tonnage,
        beltLoad = if (daily.isBodyweightBased) daily.firstSetAddedWeight else 0.0,
        bodyWeightKg = daily.bodyWeightKg
    )

    private fun buildBestSet(stats: ExerciseStatsEntity?): BestSetRecord {
        if (stats == null || !stats.hasData || stats.bestE1RM <= 0.0) return BestSetRecord()
        return BestSetRecord(
            estimatedOneRM = stats.bestE1RM,
            addedWeight = stats.bestE1RMAddedWeight,
            reps = stats.bestE1RMReps,
            totalSystemWeight = stats.bestE1RMTotalSystemWeight,
            percentBodyWeight = stats.bestE1RMPercentBodyWeight,
            date = stats.bestE1RMDate
        )
    }

    private suspend fun buildWeightedOneRepMax(
        stats: ExerciseStatsEntity?,
        exercise: Exercise
    ): OneRepMax? {
        if (stats == null || !stats.hasData || stats.bestE1RM <= 0.0) return null
        val e1rm = stats.bestE1RM
        val movementType = MovementFactors.getMovementType(exercise.name)
        val currentBW = repository.getEffectiveBodyWeight(
            java.time.LocalDate.now().toString()
        ) ?: repository.allBodyWeightEntries.first().maxByOrNull { it.date }?.bodyWeightKg ?: 0.0

        if (currentBW > 0) {
            return OneRepMax(
                oneRepMaxTSL = e1rm,
                oneRepMaxAddedWeight = (e1rm - currentBW).coerceAtLeast(0.0),
                basedOnTSL = stats.bestE1RMTotalSystemWeight,
                basedOnReps = stats.bestE1RMReps,
                basedOnDate = stats.bestE1RMDate,
                currentBodyWeight = currentBW,
                movementType = movementType,
                strengthMultiplier = e1rm / currentBW,
                workingLoad85 = e1rm * 0.85,
                workingLoad80 = e1rm * 0.80,
                workingLoad75 = e1rm * 0.75,
                bodyweightPercentage = ((e1rm - currentBW) / currentBW) * 100.0
            )
        }
        val setTimeBW = stats.bestE1RMBodyWeightKg
        return OneRepMax(
            oneRepMaxTSL = e1rm,
            oneRepMaxAddedWeight = if (setTimeBW > 0) (e1rm - setTimeBW).coerceAtLeast(0.0) else 0.0,
            basedOnTSL = stats.bestE1RMTotalSystemWeight,
            basedOnReps = stats.bestE1RMReps,
            basedOnDate = stats.bestE1RMDate,
            currentBodyWeight = setTimeBW,
            movementType = movementType,
            strengthMultiplier = if (setTimeBW > 0) e1rm / setTimeBW else 0.0,
            workingLoad85 = e1rm * 0.85,
            workingLoad80 = e1rm * 0.80,
            workingLoad75 = e1rm * 0.75,
            bodyweightPercentage = if (setTimeBW > 0) ((e1rm - setTimeBW) / setTimeBW) * 100.0 else 0.0
        )
    }

    private fun buildFreeWeightOneRepMax(
        stats: ExerciseStatsEntity?,
        exercise: Exercise
    ): OneRepMax? {
        if (stats == null || !stats.hasData || stats.bestE1RM <= 0.0) return null
        val e1rm = stats.bestE1RM
        return OneRepMax(
            oneRepMaxTSL = e1rm,
            oneRepMaxAddedWeight = e1rm,
            basedOnTSL = stats.bestE1RMAddedWeight,
            basedOnReps = stats.bestE1RMReps,
            basedOnDate = stats.bestE1RMDate,
            movementType = MovementFactors.getMovementType(exercise.name),
            strengthMultiplier = 0.0,
            workingLoad85 = e1rm * 0.85,
            workingLoad80 = e1rm * 0.80,
            workingLoad75 = e1rm * 0.75,
        )
    }
}