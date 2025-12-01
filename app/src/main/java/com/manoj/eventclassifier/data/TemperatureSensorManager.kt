package com.manoj.eventclassifier.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class TemperatureSensorManager(context: Context) : BaseClassifierManager(context) {

    override val modelPath: String = "temperature_event_classifier.tflite"
    override val paramsPath: String = "temperature_scaler_params.json"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()

    override fun parseParams(json: JSONObject) {
        val meanJsonArray = json.getJSONArray("mean")
        val scaleJsonArray = json.getJSONArray("scale")
        mean = FloatArray(meanJsonArray.length()) {
            meanJsonArray.getDouble(it).toFloat()
        }
        scale = FloatArray(scaleJsonArray.length()) {
            scaleJsonArray.getDouble(it).toFloat()
        }
    }

    private fun preProcess(
        value: Float,
        lowerThreshold: Float,
        upperThreshold: Float,
        mean: FloatArray,
        scale: FloatArray
    ): TensorBuffer {
        val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 3), DataType.FLOAT32)
        val inputValues = floatArrayOf(value, lowerThreshold, upperThreshold)
        for (i in inputValues.indices) {
            if (i < scale.size && scale[i] != 0f) {
                inputValues[i] = (inputValues[i] - mean[i]) / scale[i]
            } else if (i < mean.size) {
                inputValues[i] = inputValues[i] - mean[i]
            } else {
                inputValues[i]
            }
        }
        inputBuffer.loadArray(inputValues)
        return inputBuffer
    }

    suspend fun classify(value: Float, lowerThreshold: Float, upperThreshold: Float): Boolean {
        if (!isInitialized || interpreter == null) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val preprocessedInput = preProcess(value, lowerThreshold, upperThreshold, mean, scale)
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
            interpreter?.run(preprocessedInput.buffer, outputBuffer.buffer)
            val prediction = outputBuffer.floatArray[0]
            prediction > 0.5
        }
    }
}