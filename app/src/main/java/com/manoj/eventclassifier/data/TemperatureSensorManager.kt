package com.manoj.eventclassifier.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TemperatureSensorManager(context: Context) : BaseClassifierManager(context) {

    override val modelPath: String = "temperature_event_classifier.tflite"
    override val paramsPath: String = "temperature_scaler_params.json"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()

    /**
     * Parses the normalization parameters from a JSON object.
     *
     * This method extracts the "mean" and "scale" arrays from the provided [json]
     * and initializes the local [mean] and [scale] float arrays used for
     * preprocessing input data before classification.
     *
     * @param json The [JSONObject] containing the "mean" and "scale" scaling parameters.
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
    }

    /**
     * Normalizes input features using the provided mean and scale parameters.
     *
     * This method applies a standard scaling transformation: (value - mean) / scale.
     * It combines the raw sensor value and threshold values into a single array for model input.
     *
     * @param value The current temperature sensor reading.
     * @param lowerThreshold The lower bound threshold for the event.
     * @param upperThreshold The upper bound threshold for the event.
     * @param mean The array of mean values used for centering.
     * @param scale The array of scaling factors (standard deviation) used for normalization.
     * @return A [FloatArray] containing the normalized input features ready for model inference.
     */
    private fun preProcess(
        value: Float,
        lowerThreshold: Float,
        upperThreshold: Float,
        mean: FloatArray,
        scale: FloatArray
    ): FloatArray {
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
        return inputValues
    }

    /**
     * Classifies whether a temperature reading constitutes a specific event based on the
     * provided thresholds using the pre-trained TFLite model.
     *
     * @param value The current temperature sensor reading.
     * @param lowerThreshold The lower bound threshold for the temperature range.
     * @param upperThreshold The upper bound threshold for the temperature range.
     * @return True if the model predicts the event probability is greater than 0.5,
     *         false otherwise or if the manager is not initialized.
     */
    suspend fun classify(value: Float, lowerThreshold: Float, upperThreshold: Float): Boolean {
        if (!isInitialized || model == null) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val preprocessedInput = preProcess(value, lowerThreshold, upperThreshold, mean, scale)
            val inputBuffers = model!!.createInputBuffers()
            val outputBuffers = model!!.createOutputBuffers()
            inputBuffers[0].writeFloat(preprocessedInput)
            model!!.run(inputBuffers, outputBuffers)
            val prediction = outputBuffers[0].readFloat()[0]
            prediction > 0.5
        }
    }
}