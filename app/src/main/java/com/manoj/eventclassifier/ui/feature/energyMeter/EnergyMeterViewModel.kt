package com.manoj.eventclassifier.ui.feature.energyMeter

import android.util.Log.d
import android.util.Log.e
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.eventclassifier.domain.EnergyInput
import com.manoj.eventclassifier.domain.EnergyMeterClassificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class EnergyMeterUiState(
    var input: String = "",
    var prediction: List<String> = emptyList(),
    var isLoading: Boolean = false
)

class EnergyMeterViewModel(private val useCase: EnergyMeterClassificationUseCase) : ViewModel() {
    private var _uiState = MutableStateFlow(EnergyMeterUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            useCase.initialize()
        }
    }

    fun onInputChanged(input: String) {
        _uiState.update {
            it.copy(
                input = input
            )
        }
    }

    fun classifyEvents(baseValue: Float) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        prediction = emptyList(),
                        isLoading = true
                    )
                }
                useCase.clear()
                val currentTime = System.currentTimeMillis()
                val readingInterval = 15 * 60 * 1000L
                val resultList = mutableListOf<String>()
                for (i in 0 until 100) {
                    val timestamp = currentTime - ((100 - i) * readingInterval)
                    val energyKwh = if (i < 97) {
                        baseValue + (Math.random().toFloat() * 0.5f)
                    } else {
                        baseValue + Random.nextFloat() * 10f
                    }
                    d("EnergyMeterViewModel", "Energy Reading $timestamp $energyKwh")
                    val result = useCase(EnergyInput(timestamp, energyKwh))
                    result?.let {
                        d("EnergyMeterViewModel", "Reading ${i + 1}:")
                        d("EnergyMeterViewModel", "  Energy: $energyKwh kWh")
                        d("EnergyMeterViewModel", "  Anomaly: ${result.isAnomaly}")
                        d(
                            "EnergyMeterViewModel",
                            "  Reconstruction Error: ${result.reconstructionError}"
                        )
                        d("EnergyMeterViewModel", "  Confidence: ${result.confidence}")
                        resultList.add("$energyKwh kwh: isAnomaly=${result.isAnomaly}")
                    } ?: {
                        d("EnergyMeterViewModel", "Buffering...")
                    }
                }
                _uiState.update {
                    d("EnergyMeterViewModel", " list size- ${resultList.size}")
                    it.copy(
                        prediction = resultList.toList(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                e("EnergyMeterViewModel", "Error: ${e.message}")
            }
        }
    }
}