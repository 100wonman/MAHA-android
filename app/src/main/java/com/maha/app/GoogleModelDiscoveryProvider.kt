// GoogleModelDiscoveryProvider.kt

package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GoogleModelDiscoveryProvider {

    private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    suspend fun fetchModels(): List<DiscoveredModel> {
        return fetchModelsInternal()
    }

    private suspend fun fetchModelsInternal(): List<DiscoveredModel> {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("$MODELS_URL?key=$apiKey").openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.setRequestProperty("Accept", "application/json")

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        connection.errorStream
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: ""
                    }

                    if (responseCode !in 200..299) {
                        return@withContext emptyList()
                    }

                    parseModels(responseText)
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun parseModels(responseText: String): List<DiscoveredModel> {
        val root = JSONObject(responseText)
        val modelsArray = root.optJSONArray("models") ?: JSONArray()
        val result = mutableListOf<DiscoveredModel>()

        for (index in 0 until modelsArray.length()) {
            val item = modelsArray.optJSONObject(index) ?: continue

            val rawName = item.optString("name", "")
            val modelName = rawName.removePrefix("models/").trim()

            if (modelName.isBlank()) continue

            val displayName = item.optString("displayName", modelName)
            val description = item.optString("description", "")
            val supportedGenerationMethods = jsonArrayToStringList(
                item.optJSONArray("supportedGenerationMethods")
            )
            val inputTokenLimit = item.optInt("inputTokenLimit", 0)
            val outputTokenLimit = item.optInt("outputTokenLimit", 0)

            val isGenerateContentSupported = supportedGenerationMethods.any {
                it.equals("generateContent", ignoreCase = true)
            }

            val tags = buildTags(
                modelName = modelName,
                displayName = displayName,
                description = description,
                supportedGenerationMethods = supportedGenerationMethods
            )

            result.add(
                DiscoveredModel(
                    modelName = modelName,
                    displayName = displayName,
                    description = description,
                    supportedGenerationMethods = supportedGenerationMethods,
                    inputTokenLimit = inputTokenLimit,
                    outputTokenLimit = outputTokenLimit,
                    isGenerateContentSupported = isGenerateContentSupported,
                    tags = tags,
                    providerName = ModelProviderType.GOOGLE,
                    isFreeCandidate = true,
                    lastFetchedAt = getCurrentTimeText()
                )
            )

            val metadata = ModelMetadataManager.buildFromApiMetadata(
                providerName = ModelProviderType.GOOGLE,
                modelName = modelName,
                displayName = displayName,
                description = description,
                supportedGenerationMethods = supportedGenerationMethods,
                tags = tags,
                inputTokenLimit = inputTokenLimit,
                outputTokenLimit = outputTokenLimit,
                apiRawText = item.toString()
            )

            ModelMetadataManager.saveMetadataIfInitialized(metadata)
        }

        return result.distinctBy { "${it.providerName}:${it.modelName}" }
    }

    private fun buildTags(
        modelName: String,
        displayName: String,
        description: String,
        supportedGenerationMethods: List<String>
    ): List<String> {
        val joinedText = "$modelName $displayName $description ${supportedGenerationMethods.joinToString(" ")}"
            .lowercase()

        val result = mutableListOf<String>()

        result.add(ModelProviderType.GOOGLE)

        if (supportedGenerationMethods.any { it.equals("generateContent", ignoreCase = true) }) {
            result.add("텍스트 생성")
        }

        if (supportedGenerationMethods.any { it.contains("embed", ignoreCase = true) }) {
            result.add("임베딩")
        }

        if (joinedText.contains("flash") || joinedText.contains("lite")) {
            result.add("빠름")
        }

        if (joinedText.contains("vision") || joinedText.contains("image")) {
            result.add("비전")
        }

        if (joinedText.contains("gemini") || joinedText.contains("gemma")) {
            result.add("Google")
        }

        return result.distinct()
    }

    private fun jsonArrayToStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()

        val result = mutableListOf<String>()

        for (index in 0 until jsonArray.length()) {
            val value = jsonArray.optString(index, "")
            if (value.isNotBlank()) {
                result.add(value)
            }
        }

        return result.distinct()
    }
}