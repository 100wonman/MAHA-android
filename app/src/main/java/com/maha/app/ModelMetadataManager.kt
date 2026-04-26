// ModelMetadataManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelMetadataManager {

    private const val PREFS_NAME = "maha_model_metadata_prefs"
    private const val KEY_MODEL_METADATA = "model_metadata_json_v1"
    private const val KEY_MODEL_METADATA_BACKUP = "model_metadata_json_v1_backup"

    fun saveMetadata(
        context: Context,
        metadata: ModelMetadata
    ) {
        val safeMetadata = sanitizeMetadata(metadata)
        val currentList = getAllMetadata(context).toMutableList()

        val index = currentList.indexOfFirst {
            it.providerName == safeMetadata.providerName &&
                    it.modelName == safeMetadata.modelName
        }

        if (index >= 0) {
            currentList[index] = safeMetadata
        } else {
            currentList.add(safeMetadata)
        }

        val jsonArray = JSONArray()
        currentList.forEach { item ->
            jsonArray.put(metadataToJson(item))
        }

        saveJsonWithBackup(
            context = context,
            value = jsonArray.toString()
        )
    }

    fun getMetadata(
        context: Context,
        providerName: String,
        modelName: String
    ): ModelMetadata {
        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")

        return getAllMetadata(context).firstOrNull {
            it.providerName == safeProviderName &&
                    it.modelName == safeModelName
        } ?: buildDefaultMetadata(
            providerName = safeProviderName,
            modelName = safeModelName
        )
    }

    fun getAllMetadata(context: Context): List<ModelMetadata> {
        return loadJsonArrayWithBackup(context) { jsonArray ->
            val result = mutableListOf<ModelMetadata>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                result.add(jsonToMetadata(item))
            }

            result
                .map { sanitizeMetadata(it) }
                .distinctBy { "${it.providerName}:${it.modelName}" }
        }
    }

    fun buildDefaultMetadata(
        providerName: String,
        modelName: String
    ): ModelMetadata {
        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")

        val inferredCapabilities = inferCapabilitiesFromName(
            modelName = safeModelName
        )

        return sanitizeMetadata(
            ModelMetadata(
                providerName = safeProviderName,
                modelName = safeModelName,
                apiCapabilities = inferredCapabilities,
                testedCapabilities = emptyList(),
                selfReportedCapabilities = emptyList(),
                manualCapabilities = emptyList(),
                apiRawText = "",
                selfReportedRawText = "",
                updatedAt = ""
            )
        )
    }

    fun calculateFinalCapabilities(metadata: ModelMetadata): List<String> {
        val source = when {
            metadata.manualCapabilities.isNotEmpty() -> metadata.manualCapabilities
            metadata.testedCapabilities.isNotEmpty() -> metadata.testedCapabilities
            metadata.apiCapabilities.isNotEmpty() -> metadata.apiCapabilities
            metadata.selfReportedCapabilities.isNotEmpty() -> metadata.selfReportedCapabilities
            else -> listOf(ModelCapability.UNKNOWN)
        }

        return source
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(ModelCapability.UNKNOWN) }
    }

    fun calculateConfidenceLevel(metadata: ModelMetadata): String {
        return when {
            metadata.manualCapabilities.isNotEmpty() -> ModelMetadataConfidence.HIGH
            metadata.testedCapabilities.isNotEmpty() -> ModelMetadataConfidence.MEDIUM_HIGH
            metadata.apiCapabilities.isNotEmpty() -> ModelMetadataConfidence.MEDIUM
            metadata.selfReportedCapabilities.isNotEmpty() -> ModelMetadataConfidence.LOW
            else -> ModelMetadataConfidence.UNKNOWN
        }
    }

    private fun sanitizeMetadata(metadata: ModelMetadata): ModelMetadata {
        val safeProviderName = metadata.providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = metadata.modelName.trim().removePrefix("models/")

        val cleaned = metadata.copy(
            providerName = safeProviderName,
            modelName = safeModelName,
            apiCapabilities = cleanCapabilities(metadata.apiCapabilities),
            testedCapabilities = cleanCapabilities(metadata.testedCapabilities),
            selfReportedCapabilities = cleanCapabilities(metadata.selfReportedCapabilities),
            manualCapabilities = cleanCapabilities(metadata.manualCapabilities)
        )

        val finalCapabilities = calculateFinalCapabilities(cleaned)
        val confidenceLevel = calculateConfidenceLevel(cleaned)

        return cleaned.copy(
            finalCapabilities = finalCapabilities,
            confidenceLevel = confidenceLevel
        )
    }

    private fun cleanCapabilities(source: List<String>): List<String> {
        return source
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun inferCapabilitiesFromName(modelName: String): List<String> {
        val lower = modelName.lowercase()
        val result = mutableListOf<String>()

        when {
            lower.contains("embedding") || lower.contains("embed") -> {
                result.add(ModelCapability.EMBEDDING)
            }

            lower.contains("rerank") || lower.contains("reranker") -> {
                result.add(ModelCapability.RERANKING)
            }

            else -> {
                result.add(ModelCapability.TEXT_ONLY)
            }
        }

        if (
            lower.contains("vision") ||
            lower.contains("vl") ||
            lower.contains("image")
        ) {
            result.add(ModelCapability.VISION)
            result.add(ModelCapability.IMAGE_INPUT)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (
            lower.contains("coder") ||
            lower.contains("code") ||
            lower.contains("programming")
        ) {
            result.add(ModelCapability.CODING)
        }

        if (
            lower.contains("reasoning") ||
            lower.contains("deepseek-r1") ||
            lower.contains("r1") ||
            lower.contains("math")
        ) {
            result.add(ModelCapability.REASONING)
        }

        if (
            lower.contains("flash") ||
            lower.contains("lite") ||
            lower.contains("small") ||
            lower.contains("8b")
        ) {
            result.add(ModelCapability.FAST)
        }

        if (
            lower.contains("long") ||
            lower.contains("70b") ||
            lower.contains("405b") ||
            lower.contains("1m")
        ) {
            result.add(ModelCapability.LONG_CONTEXT)
        }

        return result.distinct().ifEmpty { listOf(ModelCapability.UNKNOWN) }
    }

    private fun metadataToJson(metadata: ModelMetadata): JSONObject {
        val safeMetadata = sanitizeMetadata(metadata)

        return JSONObject().apply {
            put("providerName", safeMetadata.providerName)
            put("modelName", safeMetadata.modelName)
            put("apiCapabilities", stringListToJsonArray(safeMetadata.apiCapabilities))
            put("testedCapabilities", stringListToJsonArray(safeMetadata.testedCapabilities))
            put("selfReportedCapabilities", stringListToJsonArray(safeMetadata.selfReportedCapabilities))
            put("manualCapabilities", stringListToJsonArray(safeMetadata.manualCapabilities))
            put("finalCapabilities", stringListToJsonArray(safeMetadata.finalCapabilities))
            put("apiRawText", safeMetadata.apiRawText)
            put("selfReportedRawText", safeMetadata.selfReportedRawText)
            put("confidenceLevel", safeMetadata.confidenceLevel)
            put("updatedAt", safeMetadata.updatedAt)
        }
    }

    private fun jsonToMetadata(json: JSONObject): ModelMetadata {
        return ModelMetadata(
            providerName = json.optString("providerName", ModelProviderType.DUMMY),
            modelName = json.optString("modelName", ""),
            apiCapabilities = jsonArrayToStringList(json.optJSONArray("apiCapabilities")),
            testedCapabilities = jsonArrayToStringList(json.optJSONArray("testedCapabilities")),
            selfReportedCapabilities = jsonArrayToStringList(json.optJSONArray("selfReportedCapabilities")),
            manualCapabilities = jsonArrayToStringList(json.optJSONArray("manualCapabilities")),
            finalCapabilities = jsonArrayToStringList(json.optJSONArray("finalCapabilities")),
            apiRawText = json.optString("apiRawText", ""),
            selfReportedRawText = json.optString("selfReportedRawText", ""),
            confidenceLevel = json.optString("confidenceLevel", ModelMetadataConfidence.UNKNOWN),
            updatedAt = json.optString("updatedAt", "")
        )
    }

    private fun stringListToJsonArray(source: List<String>): JSONArray {
        val jsonArray = JSONArray()
        source.forEach { item ->
            jsonArray.put(item)
        }
        return jsonArray
    }

    private fun jsonArrayToStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()

        val result = mutableListOf<String>()

        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optString(index, "")
            if (item.isNotBlank()) {
                result.add(item)
            }
        }

        return result.distinct()
    }

    private fun saveJsonWithBackup(
        context: Context,
        value: String
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_METADATA_BACKUP, value)
            .putString(KEY_MODEL_METADATA, value)
            .apply()
    }

    private fun <T> loadJsonArrayWithBackup(
        context: Context,
        parser: (JSONArray) -> List<T>
    ): List<T> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val primary = runCatching {
            val jsonString = prefs.getString(KEY_MODEL_METADATA, "[]") ?: "[]"
            parser(JSONArray(jsonString))
        }.getOrNull()

        if (primary != null) return primary

        val backup = runCatching {
            val jsonString = prefs.getString(KEY_MODEL_METADATA_BACKUP, "[]") ?: "[]"
            parser(JSONArray(jsonString))
        }.getOrNull()

        return backup ?: emptyList()
    }
}