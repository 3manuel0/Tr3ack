package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.BodyWeightEntity
import com.example.tr3ack.data.entity.Exercise
import com.example.tr3ack.data.entity.ExerciseEntity
import com.example.tr3ack.data.entity.ExerciseIds
import com.example.tr3ack.data.entity.WorkoutSet
import com.example.tr3ack.data.entity.WorkoutSetEntity
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
import org.json.JSONArray
import org.json.JSONObject
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

    private var selectionJob: Job? = null

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            repository.getSetsForDate(date.toString()).collect { _selectedDateSets.value = it }
        }
    }

    fun deleteSet(set: WorkoutSet) {
        viewModelScope.launch {
            repository.deleteWorkoutSet(set)
        }
    }

    fun generateCsv() {
        viewModelScope.launch(Dispatchers.Default) {
            val allSets = repository.allWorkoutSets.first()
            val allExercises = repository.allExercises.first()
            val allBodyWeight = repository.allBodyWeightEntries.first()

            val bwMap = allBodyWeight.associateBy { it.date }
            val exerciseMap = allExercises.associateBy { it.id }

            val sb = StringBuilder()
            sb.appendLine("Date,Exercise,Added Weight (kg),Total System Weight (kg),% of Body Weight,Reps,Volume (kg)")

            val grouped = allSets
                .sortedWith(compareByDescending<WorkoutSet> { it.date }.thenBy { it.timestamp })
                .groupBy { set -> "${set.date}_${set.exerciseId}" }

            for ((_, sets) in grouped) {
                val firstSet = sets.first()
                val exercise = exerciseMap[firstSet.exerciseId]
                val isBodyweight = exercise?.isBodyweightBased ?: false
                val bwEntry = bwMap[firstSet.date]
                val bodyWeight = bwEntry?.bodyWeightKg

                val repsList = sets.map { it.reps }
                val repsStr = repsList.joinToString("-")

                val addedWeights = sets.map { it.addedWeightKg }
                val avgAddedWeight = addedWeights.average()

                val totalSystemWeights = sets.map { set ->
                    if (isBodyweight && bodyWeight != null) bodyWeight + set.addedWeightKg else set.addedWeightKg
                }
                val avgTotalSystemWeight = totalSystemWeights.average()

                val pctBw = if (isBodyweight && bodyWeight != null && bodyWeight > 0) {
                    ((bodyWeight + avgAddedWeight) / bodyWeight) * 100.0
                } else {
                    null
                }

                val volume = totalSystemWeights.zip(repsList.map { it.toDouble() }).sumOf { (w, r) -> w * r }

                sb.append(firstSet.date)
                sb.append(",")
                sb.append("\"${exercise?.name ?: "Unknown"}\"")
                sb.append(",")
                sb.append("%.1f".format(avgAddedWeight))
                sb.append(",")
                sb.append("%.1f".format(avgTotalSystemWeight))
                sb.append(",")
                sb.append(pctBw?.let { "%.1f".format(it) } ?: "")
                sb.append(",")
                sb.append(repsStr)
                sb.append(",")
                sb.append("%.1f".format(volume))
                sb.appendLine()
            }

            _exportCsv.value = sb.toString()
        }
    }

    fun consumeCsv() {
        _exportCsv.value = null
    }

    fun generateBackupJson() {
        viewModelScope.launch(Dispatchers.Default) {
            val allExercises = repository.allExercises.first()
            val allSets = repository.allWorkoutSets.first()
            val allBodyWeight = repository.allBodyWeightEntries.first()

            val root = JSONObject()
            root.put("meta", JSONObject().apply {
                put("version", 1)
                put("exportDate", LocalDate.now().toString())
                put("exerciseCount", allExercises.size)
                put("workoutSetCount", allSets.size)
                put("bodyWeightEntryCount", allBodyWeight.size)
            })

            val exercisesArr = JSONArray()
            for (ex in allExercises) {
                exercisesArr.put(JSONObject().apply {
                    put("id", ex.id)
                    put("name", ex.name)
                    put("isBodyweightBased", ex.isBodyweightBased)
                })
            }
            root.put("exercises", exercisesArr)

            val setsArr = JSONArray()
            for (set in allSets) {
                setsArr.put(JSONObject().apply {
                    put("id", set.id)
                    put("exerciseId", set.exerciseId)
                    put("date", set.date)
                    put("addedWeightKg", set.addedWeightKg)
                    put("reps", set.reps)
                    put("timestamp", set.timestamp)
                })
            }
            root.put("workoutSets", setsArr)

            val bwArr = JSONArray()
            for (bw in allBodyWeight) {
                bwArr.put(JSONObject().apply {
                    put("id", bw.id)
                    put("date", bw.date)
                    put("bodyWeightKg", bw.bodyWeightKg)
                })
            }
            root.put("bodyWeightEntries", bwArr)

            _exportJson.value = root.toString(2)
        }
    }

    fun consumeJson() {
        _exportJson.value = null
    }

    private val knownExerciseIds = setOf(
        ExerciseIds.WEIGHTED_PULL_UPS,
        ExerciseIds.WEIGHTED_DIPS,
        ExerciseIds.WEIGHTED_CHIN_UPS,
        ExerciseIds.BICEP_CURLS,
        ExerciseIds.LATERAL_RAISES
    )

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val root = JSONObject(jsonString)

                val exercisesArr = root.getJSONArray("exercises")
                val setsArr = root.getJSONArray("workoutSets")
                val bwArr = root.getJSONArray("bodyWeightEntries")

                val exercises = mutableListOf<ExerciseEntity>()
                for (i in 0 until exercisesArr.length()) {
                    val obj = exercisesArr.getJSONObject(i)
                    val id = obj.getLong("id")
                    if (id !in knownExerciseIds) continue
                    exercises.add(ExerciseEntity(
                        id = id,
                        name = obj.getString("name"),
                        isBodyweightBased = obj.getBoolean("isBodyweightBased")
                    ))
                }

                val sets = mutableListOf<WorkoutSetEntity>()
                for (i in 0 until setsArr.length()) {
                    val obj = setsArr.getJSONObject(i)
                    val exerciseId = obj.getLong("exerciseId")
                    if (exerciseId !in knownExerciseIds) continue
                    sets.add(WorkoutSetEntity(
                        id = obj.getLong("id"),
                        exerciseId = exerciseId,
                        date = obj.getString("date"),
                        addedWeightKg = obj.getDouble("addedWeightKg"),
                        reps = obj.getInt("reps"),
                        timestamp = obj.getLong("timestamp")
                    ))
                }

                val bwEntries = mutableListOf<BodyWeightEntity>()
                for (i in 0 until bwArr.length()) {
                    val obj = bwArr.getJSONObject(i)
                    bwEntries.add(BodyWeightEntity(
                        id = obj.getLong("id"),
                        date = obj.getString("date"),
                        bodyWeightKg = obj.getDouble("bodyWeightKg")
                    ))
                }

                repository.replaceAllData(exercises, sets, bwEntries)

                _importResult.value = "Restored ${exercises.size} exercises, ${sets.size} sets, ${bwEntries.size} weight entries"
            } catch (e: Exception) {
                _importResult.value = "Import failed: ${e.message}"
            }
        }
    }

    fun consumeImportResult() {
        _importResult.value = null
    }
}
