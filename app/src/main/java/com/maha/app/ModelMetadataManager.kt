// ModelMetadataManager.kt

package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ModelMetadataManager {

    private const val PREFS_NAME = "maha_model_metadata_prefs"
    private const val KEY_MODEL_METADATA = "model_metadata_json_v1"
    private const val KEY_MODEL_METADATA_BACKUP = "model_metadata_json_v1_backup"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun saveMetadata(
        context: Context,
        metadata: ModelMetadata
    ) {
        initialize(context)

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

    fun saveMetadataIfInitialized(metadata: ModelMetadata) {
        val context = appContext ?: return
        saveMetadata(context, metadata)
    }

    fun saveSelfReportedInfoIfInitialized(
        providerName: String,
        modelName: String,
        rawText: String
    ) {
        val context = appContext ?: return
        saveSelfReportedInfo(
            context = context,
            providerName = providerName,
            modelName = modelName,
            rawText = rawText
        )
    }

    fun saveSelfReportedInfo(
        context: Context,
        providerName: String,
        modelName: String,
        rawText: String
    ) {
        initialize(context)

        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")
        val cleanedRawText = rawText.trim()

        if (safeModelName.isBlank() || cleanedRawText.isBlank()) return

        val currentMetadata = getMetadata(
            context = context,
            providerName = safeProviderName,
            modelName = safeModelName
        )

        val updatedMetadata = currentMetadata.copy(
            selfReportedRawText = cleanedRawText,
            selfReportedCapabilities = extractSelfReportedCapabilities(cleanedRawText),
            updatedAt = getCurrentTimeText()
        )

        saveMetadata(
            context = context,
            metadata = updatedMetadata
        )
    }

    fun addTestedCapability(
        context: Context,
        providerName: String,
        modelName: String,
        capability: String
    ) {
        initialize(context)

        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")
        val safeCapability = capability.trim()

        if (safeModelName.isBlank() || safeCapability.isBlank()) return
        if (safeCapability == ModelCapability.UNKNOWN) return

        val currentMetadata = getMetadata(
            context = context,
            providerName = safeProviderName,
            modelName = safeModelName
        )

        val updatedTestedCapabilities = (currentMetadata.testedCapabilities + safeCapability)
            .filter { it.isNotBlank() }
            .distinct()

        val updatedMetadata = currentMetadata.copy(
            testedCapabilities = updatedTestedCapabilities,
            updatedAt = getCurrentTimeText()
        )

        saveMetadata(
            context = context,
            metadata = updatedMetadata
        )
    }

    fun getMetadata(
        context: Context,
        providerName: String,
        modelName: String
    ): ModelMetadata {
        initialize(context)

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
        initialize(context)

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

        return sanitizeMetadata(
            ModelMetadata(
                providerName = safeProviderName,
                modelName = safeModelName,
                apiCapabilities = inferCapabilitiesFromText(
                    modelName = safeModelName,
                    displayName = "",
                    description = "",
                    supportedGenerationMethods = emptyList(),
                    tags = emptyList(),
                    inputTokenLimit = 0,
                    outputTokenLimit = 0
                ),
                updatedAt = ""
            )
        )
    }

    fun buildFromApiMetadata(
        providerName: String,
        modelName: String,
        displayName: String,
        description: String,
        supportedGenerationMethods: List<String>,
        tags: List<String>,
        inputTokenLimit: Int,
        outputTokenLimit: Int,
        apiRawText: String
    ): ModelMetadata {
        val safeModelName = modelName.trim().removePrefix("models/")

        val inferredCapabilities = inferCapabilitiesFromText(
            modelName = safeModelName,
            displayName = displayName,
            description = description,
            supportedGenerationMethods = supportedGenerationMethods,
            tags = tags,
            inputTokenLimit = inputTokenLimit,
            outputTokenLimit = outputTokenLimit
        )

        return sanitizeMetadata(
            ModelMetadata(
                providerName = providerName,
                modelName = safeModelName,
                apiCapabilities = inferredCapabilities,
                testedCapabilities = emptyList(),
                selfReportedCapabilities = emptyList(),
                manualCapabilities = emptyList(),
                apiRawText = apiRawText,
                selfReportedRawText = "",
                updatedAt = getCurrentTimeText()
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

    private fun extractSelfReportedCapabilities(rawText: String): List<String> {
        val text = rawText.lowercase()
        val result = mutableListOf<String>()

        if (
            text.contains("text") ||
            text.contains("summarization") ||
            text.contains("summary") ||
            text.contains("translation")
        ) {
            result.add(ModelCapability.TEXT_ONLY)
        }

        if (
            text.contains("coding") ||
            text.contains("code") ||
            text.contains("programming")
        ) {
            result.add(ModelCapability.CODING)
        }

        if (
            text.contains("reasoning") ||
            text.contains("logic") ||
            text.contains("math")
        ) {
            result.add(ModelCapability.REASONING)
        }

        if (
            text.contains("json") ||
            text.contains("structured")
        ) {
            result.add(ModelCapability.JSON_OUTPUT)
            result.add(ModelCapability.STRUCTURED_OUTPUT)
        }

        if (
            text.contains("image") ||
            text.contains("vision")
        ) {
            result.add(ModelCapability.IMAGE_INPUT)
            result.add(ModelCapability.VISION)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (text.contains("audio")) {
            result.add(ModelCapability.AUDIO_INPUT)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (text.contains("video")) {
            result.add(ModelCapability.VIDEO_INPUT)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (
            text.contains("search") ||
            text.contains("browse") ||
            text.contains("web")
        ) {
            result.add(ModelCapability.WEB_GROUNDING)
        }

        return result.distinct()
    }

    private fun inferCapabilitiesFromText(
        modelName: String,
        displayName: String,
        description: String,
        supportedGenerationMethods: List<String>,
        tags: List<String>,
        inputTokenLimit: Int,
        outputTokenLimit: Int
    ): List<String> {
        val joinedText = buildString {
            append(modelName)
            append(" ")
            append(displayName)
            append(" ")
            append(description)
            append(" ")
            append(supportedGenerationMethods.joinToString(" "))
            append(" ")
            append(tags.joinToString(" "))
        }.lowercase()

        val result = mutableListOf<String>()

        if (
            joinedText.contains("generatecontent") ||
            joinedText.contains("generate_content") ||
            joinedText.contains("chat.completions") ||
            joinedText.contains("chat") ||
            joinedText.contains("completion")
        ) {
            result.add(ModelCapability.TEXT_ONLY)
        }

        if (
            joinedText.contains("embed") ||
            joinedText.contains("embedding")
        ) {
            result.add(ModelCapability.EMBEDDING)
        }

        if (
            joinedText.contains("rerank") ||
            joinedText.contains("reranker")
        ) {
            result.add(ModelCapability.RERANKING)
        }

        if (
            joinedText.contains("vision") ||
            joinedText.contains("image") ||
            joinedText.contains("vl")
        ) {
            result.add(ModelCapability.IMAGE_INPUT)
            result.add(ModelCapability.VISION)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (joinedText.contains("audio")) {
            result.add(ModelCapability.AUDIO_INPUT)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (joinedText.contains("video")) {
            result.add(ModelCapability.VIDEO_INPUT)
            result.add(ModelCapability.MULTIMODAL_INPUT)
        }

        if (
            joinedText.contains("search") ||
            joinedText.contains("grounding") ||
            joinedText.contains("browse")
        ) {
            result.add(ModelCapability.WEB_GROUNDING)
        }

        if (
            joinedText.contains("coder") ||
            joinedText.contains("code") ||
            joinedText.contains("programming")
        ) {
            result.add(ModelCapability.CODING)
        }

        if (
            joinedText.contains("reasoning") ||
            joinedText.contains("logic") ||
            joinedText.contains("deepseek-r1") ||
            joinedText.contains("r1")
        ) {
            result.add(ModelCapability.REASONING)
        }

        if (
            joinedText.contains("json") ||
            joinedText.contains("structured")
        ) {
            result.add(ModelCapability.JSON_OUTPUT)
            result.add(ModelCapability.STRUCTURED_OUTPUT)
        }

        if (
            joinedText.contains("flash") ||
            joinedText.contains("lite") ||
            joinedText.contains("small") ||
            joinedText.contains("8b")
        ) {
            result.add(ModelCapability.FAST)
        }

        if (
            joinedText.contains("long") ||
            joinedText.contains("large context") ||
            joinedText.contains("70b") ||
            joinedText.contains("405b") ||
            inputTokenLimit >= 128_000 ||
            outputTokenLimit >= 32_000
        ) {
            result.add(ModelCapability.LONG_CONTEXT)
        }

        if (result.isEmpty()) {
            result.add(ModelCapability.TEXT_ONLY)
        }

        return result.distinct()
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