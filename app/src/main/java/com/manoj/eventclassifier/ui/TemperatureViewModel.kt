package com.manoj.eventclassifier.ui

import android.util.Log.e
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.eventclassifier.domain.TemperatureClassificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemperatureUiState(
    var inputText: String = "",
    var isAnomaly: Boolean = false
)

class TemperatureViewModel(private val useCase: TemperatureClassificationUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(TemperatureUiState())
    val uiState: StateFlow<TemperatureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.initialize()
        }
    }

    fun onInputTextChanged(inputText: String) {
        _uiState.update { it.copy(inputText = inputText) }
    }

    fun classifyEvent() {
        viewModelScope.launch {
            try {
                val inputList = uiState.value.inputText.split(",").map { it.toFloat() }
                val result = useCase(inputList)
                _uiState.update {
                    it.copy(isAnomaly = result)
                }
            } catch (e: Exception) {
                e("TemperatureViewModel", "Error: ${e.message}")
            }
        }
    }
}
