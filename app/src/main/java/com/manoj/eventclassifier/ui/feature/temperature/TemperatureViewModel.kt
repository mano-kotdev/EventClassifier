package com.manoj.eventclassifier.ui.feature.temperature

import com.manoj.eventclassifier.domain.TemperatureClassificationUseCase
import com.manoj.eventclassifier.ui.BaseEventViewModel

data class TemperatureUiState(
    var inputText: String = "",
    var isAnomaly: Boolean = false,
    var hasClassified: Boolean = false
)

class TemperatureViewModel(useCase: TemperatureClassificationUseCase) :
    BaseEventViewModel<TemperatureUiState, List<Float>, Boolean>(
        useCase,
        TemperatureUiState()
    ) {

    fun onInputTextChanged(inputText: String) {
        updateUiState { it.copy(inputText = inputText) }
    }

    fun classifyEvent() {
        try {
            val inputList = uiState.value.inputText.split(",").map { it.trim().toFloat() }
            if(inputList.size < 3) return
            classify(inputList) { currentState, result ->
                currentState.copy(isAnomaly = result, hasClassified = true)
            }
        } catch (e: Exception) {
            // TODO: Handle parsing error
        }
    }
}