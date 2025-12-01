package com.manoj.eventclassifier.data

import android.content.Context
import android.util.Log
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

abstract class BaseClassifierManager(protected val context: Context) {

    protected var interpreter: Interpreter? = null
    var isInitialized = false
        protected set

    protected abstract val modelPath: String
    protected abstract val paramsPath: String
    
    protected open fun initializeInterpreter(model: MappedByteBuffer) {
        interpreter = Interpreter(model)
    }

    protected abstract fun parseParams(json: JSONObject)

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            isInitialized = try {
                val model = loadModel()
                initializeInterpreter(model)
                loadAndParseParams()
                true
            } catch (e: Exception) {
                Log.e(this::class.java.simpleName, "Failed to initialize interpreter", e)
                false
            }
        }
    }

    private fun loadModel(): MappedByteBuffer {
        val assetsManager = context.assets
        val fileDescriptor = assetsManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadAndParseParams() {
        try {
            val jsonString = context.assets.open(paramsPath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            parseParams(jsonObject)
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Failed to load or parse parameters from $paramsPath", e)
            throw e
        }
    }

    fun close() {
        if (isInitialized) {
            interpreter?.close()
            isInitialized = false
            Log.d(this::class.java.simpleName, "Interpreter closed")
        }
    }
}
