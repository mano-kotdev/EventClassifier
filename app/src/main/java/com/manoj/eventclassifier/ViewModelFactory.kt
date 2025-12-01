package com.manoj.eventclassifier

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.manoj.eventclassifier.data.EnergyMeterManager
import com.manoj.eventclassifier.data.FlowMeterManager
import com.manoj.eventclassifier.data.TemperatureSensorManager
import com.manoj.eventclassifier.domain.EnergyMeterClassificationUseCase
import com.manoj.eventclassifier.domain.FlowMeterClassificationUseCase
import com.manoj.eventclassifier.domain.TemperatureClassificationUseCase
import com.manoj.eventclassifier.ui.feature.energyMeter.EnergyMeterViewModel
import com.manoj.eventclassifier.ui.feature.flowMeter.FlowMeterViewModel
import com.manoj.eventclassifier.ui.feature.temperature.TemperatureViewModel

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TemperatureViewModel::class.java)) {
            val temperatureSensorManager =
                TemperatureSensorManager(context.applicationContext)
            val temperatureClassificationUseCase =
                TemperatureClassificationUseCase(temperatureSensorManager)
            @Suppress("UNCHECKED_CAST")
            return TemperatureViewModel(temperatureClassificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(FlowMeterViewModel::class.java)) {
            val flowMeterManager = FlowMeterManager(context.applicationContext)
            val flowMeterClassificationUseCase =
                FlowMeterClassificationUseCase(flowMeterManager)
            @Suppress("UNCHECKED_CAST")
            return FlowMeterViewModel(flowMeterClassificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(EnergyMeterViewModel::class.java)) {
            val energyMeterManager = EnergyMeterManager(context.applicationContext)
            val energyMeterClassificationUseCase =
                EnergyMeterClassificationUseCase(energyMeterManager)
            @Suppress("UNCHECKED_CAST")
            return EnergyMeterViewModel(energyMeterClassificationUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}