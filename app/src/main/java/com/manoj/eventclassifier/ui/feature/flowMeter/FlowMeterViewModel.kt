package com.manoj.eventclassifier.ui.feature.flowMeter

import com.manoj.eventclassifier.domain.FlowMeterClassificationUseCase

data class FlowMeterUiState(
    var input: String = "",
    var prediction: Pair<String, Float> = Pair("", 0f)
)

class FlowMeterViewModel(useCase: FlowMeterClassificationUseCase) : com.manoj.eventclassifier.ui.BaseEventViewModel<FlowMeterUiState, Float, Pair<String, Float>>(
    useCase,
    FlowMeterUiState()
) {

    fun onInputChanged(input: String) {
        updateUiState { it.copy(input = input) }
    }

    fun classifyEvent() {
        val input = uiState.value.input.toFloatOrNull() ?: return
        classify(input) { currentState, result ->
            currentState.copy(prediction = result)
        }
    }
}