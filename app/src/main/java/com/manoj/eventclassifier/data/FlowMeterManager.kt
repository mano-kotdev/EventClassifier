package com.manoj.eventclassifier.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.temporal.ChronoField
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class FlowMeterManager(context: Context) : BaseClassifierManager(context) {

    override val modelPath: String = "flowmeter_event_classifier.tflite"
    override val paramsPath: String = "flowmeter_scaler_params.json"
    private val labelsPath: String = "flowmeter_event_labels.txt"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()
    private var labels = listOf<String>()
    private var inputFeatureSize = 0
    private var outputClassSize = 0

    override fun parseParams(json: JSONObject) {
        val meanJsonArray = json.getJSONArray("mean")
        val scaleJsonArray = json.getJSONArray("scale")
        mean = FloatArray(meanJsonArray.length()) {
            meanJsonArray.getDouble(it).toFloat()
        }
        scale = FloatArray(scaleJsonArray.length()) {
            scaleJsonArray.getDouble(it).toFloat()
        }
        labels = loadLabels()
        inputFeatureSize = interpreter?.getInputTensor(0)?.shape()?.get(1) ?: 0
        outputClassSize = interpreter?.getOutputTensor(0)?.shape()?.get(1) ?: 0
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(labelsPath).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            android.util.Log.e(
                this::class.java.simpleName,
                "Failed to load labels from $labelsPath",
                e
            )
            emptyList()
        }
    }

    suspend fun classify(flowValue: Float): Pair<String, Float> {
        if (!isInitialized || interpreter == null) {
            return Pair("Error", -1f)
        }
        return withContext(Dispatchers.IO) {
            val currentTime = LocalTime.now()
            val hour = currentTime.get(ChronoField.HOUR_OF_DAY).toFloat()
            val hourSin = sin(2 * Math.PI * hour / 24).toFloat()
            val hourCos = cos(2 * Math.PI * hour / 24).toFloat()
            val rawFeatures = floatArrayOf(flowValue, flowValue, hour, hourSin, hourCos)
            val normalizedFeatures = FloatArray(rawFeatures.size) { i ->
                if (i < scale.size && scale[i] != 0f) {
                    (rawFeatures[i] - mean[i]) / scale[i]
                } else if (i < mean.size) {
                    rawFeatures[i] - mean[i]
                } else {
                    rawFeatures[i]
                }
            }
            val inputTensorBuffer =
                TensorBuffer.createFixedSize(intArrayOf(1, inputFeatureSize), DataType.FLOAT32)
            inputTensorBuffer.loadArray(normalizedFeatures)
            val outputTensorBuffer =
                TensorBuffer.createFixedSize(intArrayOf(1, outputClassSize), DataType.FLOAT32)
            interpreter!!.run(inputTensorBuffer.buffer, outputTensorBuffer.buffer)
            val probabilities = outputTensorBuffer.floatArray
            val predictedClassIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val predictedLabel =
                if (predictedClassIndex != -1) labels[predictedClassIndex] else "Unknown"
            val confidence =
                if (predictedClassIndex != -1) probabilities[predictedClassIndex] else 0.0f
            Pair(predictedLabel, confidence)
        }
    }
}