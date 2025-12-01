package com.manoj.eventclassifier.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.eventclassifier.domain.ClassificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseEventViewModel<S, I, O>(
    private val useCase: ClassificationUseCase<I, O>,
    initialState: S
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.initialize()
        }
    }

    protected fun updateUiState(update: (S) -> S) {
        _uiState.update(update)
    }

    protected fun classify(input: I, updateAction: (S, O) -> S) {
        viewModelScope.launch {
            try {
                val result = useCase(input)
                updateUiState { currentState ->
                    updateAction(currentState, result)
                }
            } catch (e: Exception) {

            }
        }
    }
}
