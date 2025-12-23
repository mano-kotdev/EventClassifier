package com.manoj.eventclassifier.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.temporal.ChronoField
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONObject

class FlowMeterManager(context: Context) : BaseClassifierManager(context) {

    override val modelPath: String = "flowmeter_event_classifier.tflite"
    override val paramsPath: String = "flowmeter_scaler_params.json"
    private val labelsPath: String = "flowmeter_event_labels.txt"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()
    private var labels = listOf<String>()


    /**
     * Parses the scaler parameters from a [JSONObject] to initialize the mean and scale arrays
     * used for feature normalization. It also triggers the loading of class labels.
     *
     * @param json A [JSONObject] containing "mean" and "scale" keys with corresponding numeric arrays.

     */
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
    }

    /**
     * Loads the event labels from a text file stored in the application's assets.
     *
     * This function reads the file line by line, where each line represents a specific
     * classification label. If the file cannot be read, it logs an error and returns
     * an empty list.
     *
     * @return A list of strings containing the classification labels, or an empty list if loading fails.
     */
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

    /**
     * Classifies a flow meter event based on the provided flow value and the current time of day.
     *
     * This function performs the following steps:
     * 1. Checks if the model and parameters are initialized.
     * 2. Captures the current time to extract temporal features (hour, sine and cosine of the hour).
     * 3. Normalizes the combined features using pre-loaded mean and scale parameters.
     * 4. Executes the TFLite model inference on the IO dispatcher.
     * 5. Maps the model output to the corresponding label and confidence score.
     *
     * @param flowValue The current reading from the flow meter to be classified.
     * @return A [Pair] containing the predicted event label (String) and the confidence score (Float).
     * Returns `Pair("Error", -1f)` if the manager is not initialized, or `Pair("Unknown", 0.0f)`
     * if the classification fails to yield a valid index.
     */
    suspend fun classify(flowValue: Float): Pair<String, Float> {
        if (!isInitialized || model == null) {
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
            val inputBuffers = model!!.createInputBuffers()
            val outputBuffers = model!!.createOutputBuffers()
            inputBuffers[0].writeFloat(normalizedFeatures)
            model!!.run(inputBuffers, outputBuffers)
            val probabilities = outputBuffers[0].readFloat()
            val predictedClassIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val predictedLabel =
                if (predictedClassIndex != -1) labels[predictedClassIndex] else "Unknown"
            val confidence =
                if (predictedClassIndex != -1) probabilities[predictedClassIndex] else 0.0f
            Pair(predictedLabel, confidence)
        }
    }
}