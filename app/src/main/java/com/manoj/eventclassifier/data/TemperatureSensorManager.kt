package com.manoj.eventclassifier.data

import android.content.Context
import android.util.Log.e
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TemperatureSensorManager(private val context: Context) {

    private var interpreter: Interpreter? = null
    var isInitialized = false
        private set

    private var mean = floatArrayOf()

    private var scale = floatArrayOf()

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            isInitialized = try {
                val model = loadModel()
                val params = loadParams()
                mean = params.first
                scale = params.second
                interpreter = Interpreter(model)
                true
            } catch (e: Exception) {
                e("TemperatureSensorManager", "Error initializing classifier")
                false
            }
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
            if (scale[i] != 0f) {
                inputValues[i] = (inputValues[i] - mean[i]) / scale[i]
            } else {
                inputValues[i] = inputValues[i] - mean[i]
            }
        }
        inputBuffer.loadArray(inputValues)
        return inputBuffer
    }

    private fun loadParams(): Pair<FloatArray, FloatArray> {
        var mean = floatArrayOf()
        var scale = floatArrayOf()

        try {
            val inputStream = context.assets.open("temperature_scaler_params.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonString = String(buffer, Charsets.UTF_8)
            val jsonObject = JSONObject(jsonString)
            val meanJsonArray = jsonObject.getJSONArray("mean")
            val scaleJsonArray = jsonObject.getJSONArray("scale")
            mean = FloatArray(meanJsonArray.length()) {
                meanJsonArray.getDouble(it).toFloat()
            }
            scale = FloatArray(scaleJsonArray.length()) {
                scaleJsonArray.getDouble(it).toFloat()
            }

        } catch (e: Exception) {
            e("TemperatureSensorManager", "Error loading parameters")
        }
        return Pair(mean, scale)
    }

    private fun loadModel(): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd("temperature_event_classifier.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun classify(value: Float, lowerThreshold: Float, upperThreshold: Float): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isInitialized) {
                return@withContext false
            }
            val preprocessedInput = preProcess(value, lowerThreshold, upperThreshold, mean, scale)
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1), DataType.FLOAT32)
            interpreter?.run(preprocessedInput.buffer, outputBuffer.buffer)
            val prediction = outputBuffer.floatArray[0]
            prediction > 0.5
        }
    }
}