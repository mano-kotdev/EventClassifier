package com.manoj.eventclassifier.domain

import com.manoj.eventclassifier.data.FlowMeterManager

class FlowMeterClassificationUseCase(private val sensorManager: FlowMeterManager) : ClassificationUseCase<Float, Pair<String, Float>> {
    override suspend fun initialize() {
        sensorManager.initialize()
    }

    override suspend operator fun invoke(input: Float): Pair<String, Float> {
        return sensorManager.classify(input)
    }
}