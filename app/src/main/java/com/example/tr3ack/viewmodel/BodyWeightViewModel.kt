package com.example.tr3ack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tr3ack.data.entity.BodyWeightEntry
import com.example.tr3ack.repository.Tr3ackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BodyWeightViewModel(private val repository: Tr3ackRepository) : ViewModel() {

    val allEntries: StateFlow<List<BodyWeightEntry>> = repository.allBodyWeightEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingDate = MutableStateFlow<LocalDate?>(null)
    val editingDate: StateFlow<LocalDate?> = _editingDate.asStateFlow()

    private val _editingWeight = MutableStateFlow("")
    val editingWeight: StateFlow<String> = _editingWeight.asStateFlow()

    private val _editingEntryId = MutableStateFlow<Long>(0)

    fun startEditing(date: LocalDate, currentWeight: Double?) {
        val existing = allEntries.value.find { it.date == date.toString() }
        _editingEntryId.value = existing?.id ?: 0
        _editingDate.value = date
        _editingWeight.value = currentWeight?.toString() ?: ""
    }

    fun cancelEditing() {
        _editingDate.value = null
        _editingWeight.value = ""
        _editingEntryId.value = 0
    }

    fun setEditingWeight(value: String) {
        _editingWeight.value = value.filter { it.isDigit() || it == '.' }
    }

    fun saveWeight() {
        val date = _editingDate.value ?: return
        val weight = _editingWeight.value.toDoubleOrNull() ?: return

        viewModelScope.launch {
            val entryId = _editingEntryId.value
            if (entryId > 0) {
                repository.updateBodyWeight(
                    BodyWeightEntry(
                        id = entryId,
                        date = date.toString(),
                        bodyWeightKg = weight
                    )
                )
            } else {
                repository.insertBodyWeight(
                    BodyWeightEntry(
                        id = 0,
                        date = date.toString(),
                        bodyWeightKg = weight
                    )
                )
            }
            cancelEditing()
        }
    }

    fun deleteEntry(entry: BodyWeightEntry) {
        viewModelScope.launch {
            repository.deleteBodyWeight(entry)
        }
    }

    fun getWeightForDate(date: LocalDate): Double? {
        return allEntries.value.find { it.date == date.toString() }?.bodyWeightKg
    }

    class Factory(private val repository: Tr3ackRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BodyWeightViewModel::class.java)) {
                return BodyWeightViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
