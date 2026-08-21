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
import kotlin.math.roundToInt

data class BestSetRecord(
    val estimatedOneRM: Double = 0.0,
    val addedWeight: Double = 0.0,
    val reps: Int = 0,
    val totalSystemWeight: Double = 0.0,
    val percentBodyWeight: Double = 0.0,
    val date: String = "",
)

enum class MovementType { DIPS, PULLUPS, CHIN_UPS, UNKNOWN }

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

class ProgressViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    companion object {
        private val DIP_COEFFICIENTS = doubleArrayOf(
            1.000, 1.035, 1.068, 1.100, 1.130,
            1.160, 1.190, 1.220, 1.250, 1.280
        )

        private val PULLUP_COEFFICIENTS = doubleArrayOf(
            1.000, 1.038, 1.073, 1.108, 1.142,
            1.175, 1.208, 1.240, 1.272, 1.304
        )

        private val CHINUP_COEFFICIENTS = doubleArrayOf(
            1.000, 1.042, 1.082, 1.120, 1.158,
            1.194, 1.229, 1.263, 1.296, 1.328
        )

        fun getMovementType(exerciseName: String): MovementType {
            val lower = exerciseName.lowercase()
            return when {
                lower.contains("dip") -> MovementType.DIPS
                lower.contains("chin") -> MovementType.CHIN_UPS
                lower.contains("pull") -> MovementType.PULLUPS
                else -> MovementType.UNKNOWN
            }
        }

        fun getMovementFactor(movementType: MovementType, reps: Int): Double {
            val cappedReps = (reps - 1).coerceIn(0, 9)
            return when (movementType) {
                MovementType.DIPS -> DIP_COEFFICIENTS[cappedReps]
                MovementType.PULLUPS -> PULLUP_COEFFICIENTS[cappedReps]
                MovementType.CHIN_UPS -> CHINUP_COEFFICIENTS[cappedReps]
                MovementType.UNKNOWN -> 1.0 + (reps - 1) * 0.0333
            }
        }

        fun calculate1RM(
            bodyWeight: Double,
            addedWeight: Double,
            reps: Int,
            movementType: MovementType
        ): OneRepMax {
            if (reps < 1 || bodyWeight <= 0) {
                return OneRepMax(currentBodyWeight = bodyWeight, movementType = movementType)
            }

            val tsl = bodyWeight + addedWeight
            val factor = getMovementFactor(movementType, reps)
            val oneRepMaxTSL = tsl * factor
            val oneRepMaxAW = (oneRepMaxTSL - bodyWeight).coerceAtLeast(0.0)

            return OneRepMax(
                oneRepMaxTSL = oneRepMaxTSL,
                oneRepMaxAddedWeight = oneRepMaxAW,
                basedOnTSL = tsl,
                basedOnReps = reps,
                currentBodyWeight = bodyWeight,
                movementType = movementType,
                strengthMultiplier = oneRepMaxTSL / bodyWeight,
                workingLoad85 = oneRepMaxTSL * 0.85,
                workingLoad80 = oneRepMaxTSL * 0.80,
                workingLoad75 = oneRepMaxTSL * 0.75,
                bodyweightPercentage = (oneRepMaxAW / bodyWeight) * 100.0,
            )
        }
    }

    val exercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            val exercise = exercises.value.find { it.id == exerciseId } ?: return@launch
            repository.getSetsForExercise(exerciseId).collect { sets ->
                if (exercise.isBodyweightBased) {
                    processWeightedExercise(sets)
                } else {
                    processFreeWeightExercise(sets)
                }
            }
        }
    }

    private suspend fun processWeightedExercise(sets: List<WorkoutSet>) {
        val grouped = sets.groupBy { it.date }
        val points = mutableListOf<ChartPoint>()

        val exercise = exercises.value.find { it.id == _selectedExerciseId.value }
        val movementType = getMovementType(exercise?.name ?: "")

        for (entry in grouped.entries.sortedBy { it.key }) {
            val date = entry.key
            val daySets = entry.value
            val bodyWeight = repository.getEffectiveBodyWeight(date) ?: continue
            val firstSet = daySets.minByOrNull { it.timestamp } ?: continue
            val tsw = bodyWeight + firstSet.addedWeightKg
            val pct = if (bodyWeight > 0) (tsw / bodyWeight) * 100 else 0.0

            val bestE1RM = daySets
                .filter { it.reps > 0 && bodyWeight > 0 }
                .maxOfOrNull { set ->
                    val tsl = bodyWeight + set.addedWeightKg
                    tsl * getMovementFactor(movementType, set.reps)
                } ?: 0.0

            val tonnage = daySets
                .filter { it.reps > 0 }
                .sumOf { set ->
                    val tsl = bodyWeight + set.addedWeightKg
                    tsl * set.reps
                }

            points.add(
                ChartPoint(
                    date = date,
                    totalSystemWeight = tsw,
                    reps = firstSet.reps,
                    addedWeight = firstSet.addedWeightKg,
                    percentBodyWeight = pct,
                    estimatedOneRM = bestE1RM,
                    sessionTonnage = tonnage,
                    beltLoad = firstSet.addedWeightKg,
                    bodyWeightKg = bodyWeight,
                )
            )
        }

        _chartData.value = points
        calculateBestSet(sets, movementType)
        calculateWeighted1RM(sets)
    }

    private fun processFreeWeightExercise(sets: List<WorkoutSet>) {
        _freeWeightData.value = sets

        if (sets.isEmpty()) {
            _chartData.value = emptyList()
            _bestSet.value = BestSetRecord()
            _oneRepMax.value = null
            return
        }

        calculateFreeWeightBestSet(sets)

        val grouped = sets.groupBy { it.date }
        val points = mutableListOf<ChartPoint>()

        for (entry in grouped.entries.sortedBy { it.key }) {
            val date = entry.key
            val daySets = entry.value
            val firstSet = daySets.minByOrNull { it.timestamp } ?: continue

            val bestE1RM = daySets
                .filter { it.reps > 0 }
                .maxOfOrNull { set ->
                    set.addedWeightKg * (1.0 + set.reps / 30.0)
                } ?: 0.0

            val tonnage = daySets
                .filter { it.reps > 0 }
                .sumOf { it.addedWeightKg * it.reps }

            points.add(
                ChartPoint(
                    date = date,
                    totalSystemWeight = firstSet.addedWeightKg,
                    reps = firstSet.reps,
                    addedWeight = firstSet.addedWeightKg,
                    percentBodyWeight = 0.0,
                    estimatedOneRM = bestE1RM,
                    sessionTonnage = tonnage,
                    beltLoad = 0.0,
                    bodyWeightKg = 0.0,
                )
            )
        }

        _chartData.value = points

        val exercise = exercises.value.find { it.id == _selectedExerciseId.value }
        val movementType = getMovementType(exercise?.name ?: "")
        var bestResult: OneRepMax? = null
        for (set in sets) {
            val e1rm = set.addedWeightKg * (1.0 + set.reps / 30.0)

            val isBetter = bestResult == null ||
                e1rm > bestResult.oneRepMaxTSL ||
                (e1rm == bestResult.oneRepMaxTSL && set.reps < bestResult.basedOnReps)

            if (isBetter) {
                bestResult = OneRepMax(
                    oneRepMaxTSL = e1rm,
                    oneRepMaxAddedWeight = e1rm,
                    basedOnTSL = set.addedWeightKg,
                    basedOnReps = set.reps,
                    basedOnDate = set.date,
                    movementType = movementType,
                    strengthMultiplier = 0.0,
                    workingLoad85 = e1rm * 0.85,
                    workingLoad80 = e1rm * 0.80,
                    workingLoad75 = e1rm * 0.75,
                )
            }
        }
        _oneRepMax.value = bestResult
    }

    private suspend fun calculateBestSet(sets: List<WorkoutSet>, movementType: MovementType) {
        var best: BestSetRecord? = null

        for (set in sets) {
            if (set.reps <= 0) continue
            val bodyWeight = repository.getEffectiveBodyWeight(set.date) ?: continue
            if (bodyWeight <= 0) continue

            val tsl = bodyWeight + set.addedWeightKg
            val e1rm = tsl * getMovementFactor(movementType, set.reps)

            val isBetter = best == null ||
                e1rm > best.estimatedOneRM ||
                (e1rm == best.estimatedOneRM && set.reps < best.reps)

            if (isBetter) {
                best = BestSetRecord(
                    estimatedOneRM = e1rm,
                    addedWeight = set.addedWeightKg,
                    reps = set.reps,
                    totalSystemWeight = tsl,
                    percentBodyWeight = (tsl / bodyWeight) * 100.0,
                    date = set.date,
                )
            }
        }

        _bestSet.value = best ?: BestSetRecord()
    }

    private fun calculateFreeWeightBestSet(sets: List<WorkoutSet>) {
        var best: BestSetRecord? = null

        for (set in sets) {
            if (set.reps <= 0) continue
            val e1rm = set.addedWeightKg * (1.0 + set.reps / 30.0)

            val isBetter = best == null ||
                e1rm > best.estimatedOneRM ||
                (e1rm == best.estimatedOneRM && set.reps < best.reps)

            if (isBetter) {
                best = BestSetRecord(
                    estimatedOneRM = e1rm,
                    addedWeight = set.addedWeightKg,
                    reps = set.reps,
                    totalSystemWeight = set.addedWeightKg,
                    date = set.date,
                )
            }
        }

        _bestSet.value = best ?: BestSetRecord()
    }

    private suspend fun calculateWeighted1RM(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) {
            _oneRepMax.value = null
            return
        }

        val exercise = exercises.value.find { it.id == sets.first().exerciseId }
        val movementType = getMovementType(exercise?.name ?: "")

        var bestResult: OneRepMax? = null

        for (set in sets) {
            val bodyWeight = repository.getEffectiveBodyWeight(set.date) ?: continue
            val result = calculate1RM(bodyWeight, set.addedWeightKg, set.reps, movementType)

            val isBetter = bestResult == null ||
                result.oneRepMaxTSL > bestResult.oneRepMaxTSL ||
                (result.oneRepMaxTSL == bestResult.oneRepMaxTSL && set.reps < bestResult.basedOnReps)

            if (isBetter) {
                bestResult = result.copy(
                    basedOnDate = set.date,
                    currentBodyWeight = bodyWeight,
                )
            }
        }

        val currentBW = repository.getEffectiveBodyWeight(
            java.time.LocalDate.now().toString()
        ) ?: repository.allBodyWeightEntries.first().maxByOrNull { it.date }?.bodyWeightKg
        ?: 0.0

        if (bestResult != null && currentBW > 0) {
            bestResult = bestResult.copy(
                currentBodyWeight = currentBW,
                oneRepMaxAddedWeight = (bestResult.oneRepMaxTSL - currentBW).coerceAtLeast(0.0),
                workingLoad85 = bestResult.oneRepMaxTSL * 0.85,
                workingLoad80 = bestResult.oneRepMaxTSL * 0.80,
                workingLoad75 = bestResult.oneRepMaxTSL * 0.75,
                bodyweightPercentage = ((bestResult.oneRepMaxTSL - currentBW) / currentBW) * 100.0,
            )
        }

        _oneRepMax.value = bestResult
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
