package com.manoj.eventclassifier.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.LinkedList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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

    override val modelPath: String = "energy_anomaly_model_new.tflite"
    override val paramsPath: String = "energy_scaler_params.json"

    private var mean = floatArrayOf()
    private var scale = floatArrayOf()
    private val sequenceLength = 96
    private val nFeatures = 14
    private val readingsBuffer = LinkedList<EnergyMeterReading>()
    private var ema4h = 0f
    private var ema24h = 0f
    private var emaSq4h = 0f
    private var emaInitialized = false
    var anomalyThreshold = 0.001f

    override fun initializeInterpreter(model: java.nio.MappedByteBuffer) {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseNNAPI(false)
        }
        interpreter = Interpreter(model, options)
    }

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

    private fun detectAnomaly(): AnomalyResult {
        val featureSequence = generateFeaturesSequence()
        val scaledSequence = scaleFeatureSequence(featureSequence)
        val inputBuffer = prepareInputBuffer(scaledSequence)
        val outputBuffer = Array(1) { Array(sequenceLength) { FloatArray(nFeatures) } }
        interpreter?.run(inputBuffer, outputBuffer)
        val reconstructionError = calculateReconstructionError(scaledSequence, outputBuffer[0])
        val isAnomaly = reconstructionError > anomalyThreshold
        val confidence = if (isAnomaly) {
            min(1f, (reconstructionError - anomalyThreshold) / anomalyThreshold)
        } else {
            min(1f, (anomalyThreshold - reconstructionError) / anomalyThreshold)
        }
        Log.d("EnergyMeterManager", "Reconstruction Error: $reconstructionError, Threshold: $anomalyThreshold, Anomaly: $isAnomaly")
        return AnomalyResult(isAnomaly, reconstructionError, anomalyThreshold, confidence)
    }

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

    private fun prepareInputBuffer(scaledSequence: Array<FloatArray>): ByteBuffer {
        val bufferSize = 1 * sequenceLength * nFeatures * 4
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                buffer.putFloat(scaledSequence[i][j])
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun calculateReconstructionError(
        original: Array<FloatArray>,
        reconstructed: Array<FloatArray>
    ): Float {
        var sumSquaredError = 0f
        var count = 0
        for (i in 0 until sequenceLength) {
            for (j in 0 until nFeatures) {
                val diff = original[i][j] - reconstructed[i][j]
                sumSquaredError = diff * diff
                count++
            }
        }
        return sumSquaredError / count
    }

    fun clearBuffer() {
        readingsBuffer.clear()
        emaInitialized = false
        ema4h = 0f
        ema24h = 0f
        ema24h = 0f
        Log.d("EnergyMeterManager", "Buffer cleared")
    }

    fun getBufferSize(): Int = readingsBuffer.size

    fun isBufferReady(): Boolean = readingsBuffer.size >= sequenceLength

    private fun generateFeaturesSequence(): Array<FloatArray> {
        val features = Array(sequenceLength) { FloatArray(nFeatures) }
        readingsBuffer.forEachIndexed { index, reading ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = reading.timestamp
            }
            features[index][0] = reading.energyInKwh
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            features[index][1] =
                if (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY) 1f else 0f
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            features[index][2] = sin(2 * PI.toFloat() * hour / 24f)
            features[index][3] = cos(2 * PI.toFloat() * hour / 24f)
            val dayOfWeekNum = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            features[index][4] = sin(2 * PI.toFloat() * dayOfWeekNum / 7f)
            features[index][5] = cos(2 * PI.toFloat() * dayOfWeekNum / 7f)
            val month = calendar.get(Calendar.MONTH)
            features[index][6] = sin(2 * PI.toFloat() * month / 12f)
            features[index][7] = cos(2 * PI.toFloat() * month / 12f)
            features[index][8] =
                if (index > 0) readingsBuffer[index - 1].energyInKwh else reading.energyInKwh
            features[index][9] =
                if (index > 4) readingsBuffer[index - 4].energyInKwh else reading.energyInKwh
            val alpha4h = 2f / (16f + 1f)
            if (!emaInitialized && index == 0) {
                ema4h = reading.energyInKwh
                emaSq4h = reading.energyInKwh * reading.energyInKwh
                emaInitialized = true
            } else {
                ema4h = alpha4h * reading.energyInKwh + (1 - alpha4h) * ema4h
                emaSq4h =
                    alpha4h * (reading.energyInKwh * reading.energyInKwh) + (1 - alpha4h) * emaSq4h
            }
            features[index][10] = ema4h
            val variance = max(0f, emaSq4h - ema4h * ema4h)
            features[index][11] = sqrt(variance)
            val alpha24h = 2f / (96f + 1f)
            ema24h = alpha24h * reading.energyInKwh + (1 - alpha24h) * ema24h
            features[index][12] = ema24h
            features[index][13] = if (index > 0) {
                reading.energyInKwh - readingsBuffer[index - 1].energyInKwh
            } else {
                0f
            }
        }
        return features
    }
}
