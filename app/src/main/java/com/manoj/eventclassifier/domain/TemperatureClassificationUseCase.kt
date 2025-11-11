package com.manoj.eventclassifier.domain

import com.manoj.eventclassifier.data.TemperatureSensorManager

class TemperatureClassificationUseCase(private val sensorManager: TemperatureSensorManager) {

    suspend fun initialize() {
        sensorManager.initialize()
    }

    suspend operator fun invoke(input: List<Float>): Boolean {
        if (input.size != 3) {
            throw IllegalArgumentException("Input list must contain 3 elements")
        }
        return sensorManager.classify(input[0], input[1], input[2])
    }
}