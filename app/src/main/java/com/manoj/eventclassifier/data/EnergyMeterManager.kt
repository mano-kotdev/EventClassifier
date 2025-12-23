package com.manoj.eventclassifier.data

import android.content.Context
import android.util.Log
import android.util.Log.e
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.LinkedList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class EnergyMeterReading(
    val timestamp: Long,
    val energyInKwh: Float
)

data class AnomalyResult(
    val isAnomaly: Boolean,
    val reconstructionError: Float,
    val threshold: Float,
    val confidence: Float
)

class EnergyMeterManager(context: Context) : BaseClassifierManager(context) {

    override val modelPath: String = "energy_event_classifier.tflite"
    override val paramsPath: String = "energy_scaler_params.json"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()
    private val sequenceLength = 96
    private val nFeatures = 7
    private val readingsBuffer = LinkedList<EnergyMeterReading>()
    var anomalyThreshold = 0.1f

    /**
     * Parses the scaling parameters and anomaly detection threshold from a JSON configuration object.
     * Extracts the "mean" and "scale" arrays used for feature normalization, as well as the
     * "threshold" value used to identify anomalous reconstruction errors.
     *
     * @param json A [JSONObject] containing the "mean" (array), "scale" (array), and "threshold" (double) keys.
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
        anomalyThreshold = json.getDouble("threshold").toFloat()
    }

    /**
     * Classifies a new energy reading by adding it to the rolling buffer and performing
     * anomaly detection if the buffer is full.
     *
     * This function handles the ingestion of time-series data, maintains a window of
     * [sequenceLength] readings, and invokes the TFLite model to evaluate if the
     * current sequence represents an anomaly based on reconstruction error.
     *
     * @param timestamp The epoch time in milliseconds of the energy reading.
     * @param energy The energy value in kWh recorded at the given timestamp.
     * @return An [AnomalyResult] containing detection status and confidence if the buffer
     * is full and the model is initialized; otherwise, null.
     */
    suspend fun classifyEvent(timestamp: Long, energy: Float): AnomalyResult? {
        if (!isInitialized) return null
        return withContext(Dispatchers.IO) {
            val reading = EnergyMeterReading(timestamp, energy)
            readingsBuffer.add(reading)
            while (readingsBuffer.size > sequenceLength) {
                readingsBuffer.removeFirst()
            }
            if (readingsBuffer.size < sequenceLength) {
                Log.d("EnergyMeterManager", "Buffer size: ${readingsBuffer.size}/$sequenceLength")
                return@withContext null
            }
            detectAnomaly()
        }
    }

    /**
     * Performs anomaly detection on the current sequence of energy meter readings.
     *
     * This function processes the buffered readings through several steps:
     * 1. Generates a feature sequence (including time-based cyclical features and EMA).
     * 2. Scales the features using pre-configured mean and scale parameters.
     * 3. Runs the TFLite model to reconstruct the input sequence.
     * 4. Calculates the Mean Squared Error (reconstruction error) between the input and output.
     * 5. Compares the error against [anomalyThreshold] to determine if the pattern is anomalous.
     * 6. Calculates a confidence score based on the distance from the threshold.
     *
     * @return An [AnomalyResult] containing detection status, reconstruction error, and confidence,
     * or `null` if the model is not initialized.
     */
    private fun detectAnomaly(): AnomalyResult? {
        if(model == null){
            e("EnergyMeterManager", "Anomaly detection not possible as model is null")
            return null
        }
        val inputBuffers = model!!.createInputBuffers()
        val outputBuffers = model!!.createOutputBuffers()
        val featureSequence = generateFeaturesSequence()
        val scaledSequence = scaleFeatureSequence(featureSequence)
        val inputFloatArray = prepareInputFloatArray(scaledSequence)
        inputBuffers[0].writeFloat(inputFloatArray)
        model!!.run(inputBuffers, outputBuffers)
        val outputFloatArray = outputBuffers[0].readFloat()
        val outputBuffer = convertToArray(outputFloatArray, sequenceLength, nFeatures)
        val reconstructionError = calculateReconstructionError(scaledSequence, outputBuffer[0])
        val isAnomaly = reconstructionError > anomalyThreshold
        val confidence = if (isAnomaly) {
            min(1f, (reconstructionError - anomalyThreshold) / anomalyThreshold)
        } else {
            min(1f, (anomalyThreshold - reconstructionError) / anomalyThreshold)
        }
        Log.d(
            "EnergyMeterManager",
            "Reconstruction Error: $reconstructionError, Threshold: $anomalyThreshold, Anomaly: $isAnomaly"
        )
        return AnomalyResult(isAnomaly, reconstructionError, anomalyThreshold, confidence)
    }

    /**
     * Scales the generated feature sequence using the pre-computed mean and scale parameters
     * (Standardization).
     *
     * For each feature in the sequence, the transformation applied is:
     * `scaled = (original - mean) / scale`.
     *
     * @param features The raw 2D array of features where the first dimension is the sequence
     * length and the second dimension is the feature index.
     * @return A 2D array of the same dimensions containing the normalized/scaled feature values.
     */
    private fun scaleFeatureSequence(features: Array<FloatArray>): Array<FloatArray> {
        val scaled = Array(sequenceLength) { FloatArray(nFeatures) }
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                if (scale[j] != 0f) {
                    scaled[i][j] = (features[i][j] - mean[j]) / scale[j]
                } else {
                    scaled[i][j] = features[i][j] - mean[j]
                }
            }
        }
        return scaled
    }

    /**
     * Converts a flat [FloatArray] back into a 3D [Array] structure (batch, sequence, features).
     *
     * This utility function reshapes the flat output from the TFLite model inference into
     * a structured format that matches the temporal sequence and feature dimensions used
     * for reconstruction error calculations.
     *
     * @param outputFloatArray The flat array containing the raw model output.
     * @param sequenceLength The number of time steps in the input sequence (e.g., 96).
     * @param nFeatures The number of features per time step (e.g., 7).
     * @return A 3D array of shape [1][sequenceLength][nFeatures] containing the reconstructed values.
     */
    private fun convertToArray(
        outputFloatArray: FloatArray,
        sequenceLength: Int,
        nFeatures: Int
    ): Array<Array<FloatArray>> {
        val output = Array(1) { Array(sequenceLength) { FloatArray(nFeatures) } }
        var index = 0
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                output[0][i][j] = outputFloatArray[index++]
            }
        }
        return output
    }

    /**
     * Flattens the 2D scaled feature sequence into a 1D FloatArray for TFLite model input.
     *
     * This method transforms the sequence data from a [sequenceLength] x [nFeatures]
     * structure into a contiguous array of floats to be written into the model's input buffer.
     *
     * @param scaledSequence The 2D array of normalized features of size [sequenceLength] x [nFeatures].
     * @return A 1D [FloatArray] containing the flattened feature sequence.
     */
    private fun prepareInputFloatArray(scaledSequence: Array<FloatArray>): FloatArray {
        val totalSize = sequenceLength * nFeatures
        val floatArray = FloatArray(totalSize)
        var index = 0
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                floatArray[index++] = (scaledSequence[i][j])
            }
        }
        return floatArray
    }

    /**
     * Calculates the Mean Squared Error (MSE) between the original input features
     * and the reconstructed output from the autoencoder model.
     *
     * This error serves as an anomaly score: a higher reconstruction error indicates that
     * the input data point deviates significantly from the patterns learned by the model
     * during training.
     *
     * @param original The scaled input feature sequence used for inference.
     * @param reconstructed The reconstructed feature sequence produced by the model.
     * @return The average squared difference (MSE) across all features in the sequence.
     */
    private fun calculateReconstructionError(
        original: Array<FloatArray>,
        reconstructed: Array<FloatArray>
    ): Float {
        var sumSquaredError = 0f
        var count = 0
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                val diff = original[i][j] - reconstructed[i][j]
                sumSquaredError += diff * diff
                count++
            }
        }
        return sumSquaredError / count
    }

    fun clearBuffer() {
        readingsBuffer.clear()
        Log.d("EnergyMeterManager", "Buffer cleared")
    }

    fun getBufferSize(): Int = readingsBuffer.size

    fun isBufferReady(): Boolean = readingsBuffer.size >= sequenceLength

    /**
     * Generates a sequence of features from the buffered energy meter readings to be used as model input.
     *
     * For each reading in the [readingsBuffer], it calculates:
     * 1. Raw energy consumption (kWh)
     * 2. Sine and Cosine transformations of the hour of the day (cyclical time features)
     * 3. Sine and Cosine transformations of the day of the week (cyclical time features)
     * 4. A Short-term Exponential Moving Average (EMA) of the energy consumption
     * 5. The difference between the current energy reading and the short-term EMA
     *
     * @return A 2D array of shape [sequenceLength] x [nFeatures] containing the calculated features.
     */
    private fun generateFeaturesSequence(): Array<FloatArray> {
        val features = Array(sequenceLength) { FloatArray(nFeatures) }
        var emaShort = 0f
        val alphaShort = 0.2f
        var emaInitialized = false
        readingsBuffer.forEachIndexed { index, reading ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = reading.timestamp
            }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Mon
            val hourSin = sin(2f * PI.toFloat() * hour / 24f)
            val hourCos = cos(2f * PI.toFloat() * hour / 24f)
            val daySin  = sin(2f * PI.toFloat() * dayOfWeek / 7f)
            val dayCos  = cos(2f * PI.toFloat() * dayOfWeek / 7f)
            if (!emaInitialized) {
                emaShort = reading.energyInKwh
                emaInitialized = true
            } else {
                emaShort = alphaShort * reading.energyInKwh + (1f - alphaShort) * emaShort
            }
            val energyDiff = reading.energyInKwh - emaShort
            features[index][0] = reading.energyInKwh
            features[index][1] = hourSin
            features[index][2] = hourCos
            features[index][3] = daySin
            features[index][4] = dayCos
            features[index][5] = emaShort
            features[index][6] = energyDiff
        }
        return features
    }
}
