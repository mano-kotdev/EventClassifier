package com.manoj.eventclassifier

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.manoj.eventclassifier.data.TemperatureSensorManager
import com.manoj.eventclassifier.domain.TemperatureClassificationUseCase
import com.manoj.eventclassifier.ui.TemperatureViewModel

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TemperatureViewModel::class.java)) {
            val temperatureSensorManager = TemperatureSensorManager(context.applicationContext)
            val temperatureClassificationUseCase = TemperatureClassificationUseCase(temperatureSensorManager)
            @Suppress("UNCHECKED_CAST")
            return TemperatureViewModel(temperatureClassificationUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}