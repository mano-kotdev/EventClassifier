package com.manoj.eventclassifier.domain

import com.manoj.eventclassifier.data.AnomalyResult
import com.manoj.eventclassifier.data.EnergyMeterManager

data class EnergyInput(val timestamp: Long, val energy: Float)

class EnergyMeterClassificationUseCase(private val sensorManager: EnergyMeterManager) : ClassificationUseCase<EnergyInput, AnomalyResult?> {
    override suspend fun initialize() {
        sensorManager.initialize()
    }

    fun clear(){
        sensorManager.clearBuffer()
    }

    override suspend operator fun invoke(input: EnergyInput): AnomalyResult? {
        return sensorManager.classifyEvent(input.timestamp, input.energy)
    }
}