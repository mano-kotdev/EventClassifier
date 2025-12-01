package com.manoj.eventclassifier.ui.feature.energyMeter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.eventclassifier.ViewModelFactory
import com.manoj.eventclassifier.ui.feature.ClassifierScreen

@Composable
fun EnergyMeterScreen(
    modifier: Modifier = Modifier,
    viewModel: EnergyMeterViewModel = viewModel(
        factory = ViewModelFactory(
            LocalContext.current
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    ClassifierScreen(
        modifier = modifier,
        inputLabel = "Enter energy meter base value",
        input = uiState.input,
        onInputChange = { newText ->
            viewModel.onInputChanged(newText)
        },
        onClassifyClick = {
            viewModel.classifyEvents(uiState.input.toFloatOrNull() ?: 2.5f)
        },
        isLoading = uiState.isLoading,
        predictions = uiState.prediction
    )
}
