package com.manoj.eventclassifier.ui.feature.flowMeter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.eventclassifier.ViewModelFactory
import com.manoj.eventclassifier.ui.feature.ClassifierScreen

@Composable
fun FlowMeterScreen(
    modifier: Modifier = Modifier,
    viewModel: FlowMeterViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    val predictions = if (uiState.prediction.first.isNotEmpty()) {
        val confidencePercentage = (uiState.prediction.second * 100).toInt()
        listOf("${uiState.prediction.first} (Confidence: $confidencePercentage%)")
    } else {
        emptyList()
    }

    ClassifierScreen(
        modifier = modifier,
        inputLabel = "Enter flow meter value",
        input = uiState.input,
        onInputChange = { newText ->
            viewModel.onInputChanged(newText)
        },
        onClassifyClick = { viewModel.classifyEvent() },
        isLoading = false,
        predictions = predictions
    )
}
