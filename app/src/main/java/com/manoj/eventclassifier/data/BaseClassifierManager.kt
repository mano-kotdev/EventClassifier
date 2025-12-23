package com.manoj.eventclassifier.data

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

abstract class BaseClassifierManager(protected val context: Context) {

    protected var model: CompiledModel? = null
    var isInitialized = false
        protected set

    protected abstract val modelPath: String
    protected abstract val paramsPath: String
    /**
     * Initializes the LiteRT [CompiledModel] using the model file located in the assets.
     * This method can be overridden by subclasses to provide custom initialization logic.
     */
    protected open fun initializeModel() {
        model = CompiledModel.create(
            context.assets,
            modelPath,
        )
    }

    protected abstract fun parseParams(json: JSONObject)

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            isInitialized = try {
                initializeModel()
                loadAndParseParams()
                true
            } catch (e: Exception) {
                Log.e(this::class.java.simpleName, "Failed to initialize interpreter", e)
                false
            }
        }
    }

    /**
     * Loads the configuration parameters from a JSON file located in the app assets
     * and parses them using [parseParams].
     *
     * This method reads the file specified by [paramsPath], converts it into a
     * [JSONObject], and delegates the actual parameter extraction to the subclass
     * implementation of [parseParams].
     *
     * @throws Exception if the file cannot be read or if the JSON is malformed.
     */
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
            model?.close()
            isInitialized = false
            Log.d(this::class.java.simpleName, "Model closed")
        }
    }
}
