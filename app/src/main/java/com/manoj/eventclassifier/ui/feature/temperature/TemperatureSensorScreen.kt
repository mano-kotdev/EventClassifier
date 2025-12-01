package com.manoj.eventclassifier.ui.feature.temperature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.eventclassifier.ViewModelFactory
import com.manoj.eventclassifier.ui.feature.ClassifierScreen

@Composable
fun TemperatureSensorScreen(
    modifier: Modifier = Modifier,
    viewModel: TemperatureViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    val predictions = if (uiState.inputText.isNotEmpty() && uiState.hasClassified) {
        listOf("isAnomaly -- ${uiState.isAnomaly}")
    } else {
        emptyList()
    }

    ClassifierScreen(
        modifier = modifier,
        inputLabel = "Enter comma-separated temperature values",
        input = uiState.inputText,
        onInputChange = { viewModel.onInputTextChanged(it) },
        onClassifyClick = {
            viewModel.classifyEvent()
                          },
        isLoading = false,
        predictions = predictions
    )
}
