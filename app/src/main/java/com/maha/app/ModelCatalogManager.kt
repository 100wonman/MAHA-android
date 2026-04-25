// ModelCatalogManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelCatalogManager {

    private const val PREFS_NAME = "maha_model_catalog_prefs"
    private const val KEY_DISCOVERED_MODELS = "discovered_models_json_v2"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun saveDiscoveredModels(
        context: Context,
        models: List<DiscoveredModel>
    ) {
        initialize(context)
        saveDiscoveredModels(models)
    }

    fun saveDiscoveredModels(models: List<DiscoveredModel>) {
        val context = appContext ?: return

        val jsonArray = JSONArray()

        models
            .filter { it.modelName.isNotBlank() }
            .distinctBy { "${it.providerName}:${it.modelName}" }
            .forEach { model ->
                jsonArray.put(discoveredModelToJson(model))
            }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISCOVERED_MODELS, jsonArray.toString())
            .apply()
    }

    fun appendDiscoveredModels(models: List<DiscoveredModel>) {
        val currentModels = getDiscoveredModels()
        val mergedModels = (currentModels + models)
            .filter { it.modelName.isNotBlank() }
            .distinctBy { "${it.providerName}:${it.modelName}" }

        saveDiscoveredModels(mergedModels)
    }

    fun getDiscoveredModels(context: Context): List<DiscoveredModel> {
        initialize(context)
        return getDiscoveredModels()
    }

    fun getDiscoveredModels(): List<DiscoveredModel> {
        val context = appContext ?: return emptyList()

        val jsonString = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISCOVERED_MODELS, "[]")
            ?: "[]"

        return runCatching {
            val jsonArray = JSONArray(jsonString)
            val result = mutableListOf<DiscoveredModel>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                result.add(jsonToDiscoveredModel(item))
            }

            result
                .filter { it.modelName.isNotBlank() }
                .distinctBy { "${it.providerName}:${it.modelName}" }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        val context = appContext ?: return

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISCOVERED_MODELS, "[]")
            .apply()
    }

    private fun discoveredModelToJson(model: DiscoveredModel): JSONObject {
        return JSONObject().apply {
            put("modelName", model.modelName)
            put("displayName", model.displayName)
            put("description", model.description)
            put("supportedGenerationMethods", stringListToJsonArray(model.supportedGenerationMethods))
            put("inputTokenLimit", model.inputTokenLimit)
            put("outputTokenLimit", model.outputTokenLimit)
            put("isGenerateContentSupported", model.isGenerateContentSupported)
            put("lastFetchedAt", model.lastFetchedAt)
            put("tags", stringListToJsonArray(model.tags))
            put("providerName", model.providerName)
            put("isFreeCandidate", model.isFreeCandidate)
        }
    }

    private fun jsonToDiscoveredModel(json: JSONObject): DiscoveredModel {
        val methods = jsonArrayToStringList(
            json.optJSONArray("supportedGenerationMethods") ?: JSONArray()
        )

        return DiscoveredModel(
            modelName = json.optString("modelName", "").removePrefix("models/").trim(),
            displayName = json.optString("displayName", ""),
            description = json.optString("description", ""),
            supportedGenerationMethods = methods,
            inputTokenLimit = json.optInt("inputTokenLimit", 0),
            outputTokenLimit = json.optInt("outputTokenLimit", 0),
            isGenerateContentSupported = json.optBoolean(
                "isGenerateContentSupported",
                methods.contains("generateContent") || methods.contains("chat/completions")
            ),
            lastFetchedAt = json.optString("lastFetchedAt", ""),
            tags = jsonArrayToStringList(json.optJSONArray("tags") ?: JSONArray()),
            providerName = json.optString("providerName", ModelProviderType.GOOGLE),
            isFreeCandidate = json.optBoolean("isFreeCandidate", false)
        )
    }

    private fun stringListToJsonArray(values: List<String>): JSONArray {
        val jsonArray = JSONArray()

        values.forEach { value ->
            jsonArray.put(value)
        }

        return jsonArray
    }

    private fun jsonArrayToStringList(jsonArray: JSONArray): List<String> {
        val result = mutableListOf<String>()

        for (index in 0 until jsonArray.length()) {
            val value = jsonArray.optString(index, "")
            if (value.isNotBlank()) {
                result.add(value)
            }
        }

        return result
    }
}