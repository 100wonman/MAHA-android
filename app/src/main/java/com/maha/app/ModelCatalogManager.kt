// ModelCatalogManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelCatalogManager {

    private const val PREFS_NAME = "maha_model_catalog_prefs"
    private const val KEY_DISCOVERED_MODELS = "discovered_models_json_v1"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun saveDiscoveredModels(
        context: Context,
        models: List<DiscoveredModel>
    ) {
        initialize(context)

        val jsonArray = JSONArray()

        models
            .filter { it.modelName.isNotBlank() }
            .distinctBy { it.modelName }
            .forEach { model ->
                jsonArray.put(discoveredModelToJson(model))
            }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISCOVERED_MODELS, jsonArray.toString())
            .apply()
    }

    fun getDiscoveredModels(context: Context): List<DiscoveredModel> {
        initialize(context)
        return loadDiscoveredModels(context)
    }

    fun getDiscoveredModels(): List<DiscoveredModel> {
        val context = appContext ?: return emptyList()
        return loadDiscoveredModels(context)
    }

    fun getMergedCatalog(context: Context): List<ModelCatalogItem> {
        val manualItems = GeminiModelType.getCatalog()
        val discoveredItems = getDiscoveredModels(context)
            .filter { it.isGenerateContentSupported }
            .map { discovered ->
                ModelCatalogItem(
                    modelName = discovered.modelName,
                    displayName = discovered.displayName.ifBlank { discovered.modelName },
                    description = discovered.description.ifBlank {
                        "API에서 검색된 generateContent 지원 모델입니다."
                    },
                    stabilityStatus = "Discovered",
                    recommendedWorker = "Manual Review",
                    estimatedDailyLimit = 100
                )
            }

        return (manualItems + discoveredItems)
            .distinctBy { it.modelName }
    }

    private fun loadDiscoveredModels(context: Context): List<DiscoveredModel> {
        val jsonString = context.applicationContext
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
                .distinctBy { it.modelName }
        }.getOrDefault(emptyList())
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
        }
    }

    private fun jsonToDiscoveredModel(json: JSONObject): DiscoveredModel {
        val methods = jsonArrayToStringList(
            json.optJSONArray("supportedGenerationMethods") ?: JSONArray()
        )

        return DiscoveredModel(
            modelName = normalizeModelName(json.optString("modelName", "")),
            displayName = json.optString("displayName", ""),
            description = json.optString("description", ""),
            supportedGenerationMethods = methods,
            inputTokenLimit = json.optInt("inputTokenLimit", 0),
            outputTokenLimit = json.optInt("outputTokenLimit", 0),
            isGenerateContentSupported = json.optBoolean(
                "isGenerateContentSupported",
                methods.contains("generateContent")
            ),
            lastFetchedAt = json.optString("lastFetchedAt", ""),
            tags = jsonArrayToStringList(json.optJSONArray("tags") ?: JSONArray())
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

    private fun normalizeModelName(rawName: String): String {
        return rawName.removePrefix("models/").trim()
    }
}