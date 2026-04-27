// NvidiaModelDiscoveryProvider.kt

package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NvidiaModelDiscoveryProvider {

    private const val MODELS_URL = "https://integrate.api.nvidia.com/v1/models"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    suspend fun fetchModels(): List<DiscoveredModel> {
        return fetchModelsInternal()
    }

    private suspend fun fetchModelsInternal(): List<DiscoveredModel> {
        val apiKey = ApiKeyManager.getNvidiaApiKey()

        if (apiKey.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(MODELS_URL).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
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
        val dataArray = root.optJSONArray("data") ?: JSONArray()
        val result = mutableListOf<DiscoveredModel>()

        for (index in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(index) ?: continue

            val modelName = item.optString("id", "").trim()
            if (modelName.isBlank()) continue

            val ownedBy = item.optString("owned_by", "")
            val displayName = modelName
            val description = buildDescription(
                modelName = modelName,
                ownedBy = ownedBy
            )

            val supportedGenerationMethods = inferSupportedMethods(modelName)
            val isGenerateContentSupported = supportedGenerationMethods.contains("chat.completions")

            val tags = buildTags(
                modelName = modelName,
                ownedBy = ownedBy
            )

            result.add(
                DiscoveredModel(
                    modelName = modelName,
                    displayName = displayName,
                    description = description,
                    supportedGenerationMethods = supportedGenerationMethods,
                    inputTokenLimit = 0,
                    outputTokenLimit = 0,
                    isGenerateContentSupported = isGenerateContentSupported,
                    tags = tags,
                    providerName = ModelProviderType.NVIDIA,
                    isFreeCandidate = true,
                    lastFetchedAt = getCurrentTimeText()
                )
            )

            val metadata = ModelMetadataManager.buildFromApiMetadata(
                providerName = ModelProviderType.NVIDIA,
                modelName = modelName,
                displayName = displayName,
                description = description,
                supportedGenerationMethods = supportedGenerationMethods,
                tags = tags,
                inputTokenLimit = 0,
                outputTokenLimit = 0,
                apiRawText = item.toString()
            )

            ModelMetadataManager.saveMetadataIfInitialized(metadata)
        }

        return result.distinctBy { "${it.providerName}:${it.modelName}" }
    }

    private fun inferSupportedMethods(modelName: String): List<String> {
        val lower = modelName.lowercase()

        return when {
            lower.contains("embed") || lower.contains("embedding") -> listOf("embeddings")
            lower.contains("rerank") || lower.contains("reranker") -> listOf("reranking")
            else -> listOf("chat.completions")
        }
    }

    private fun buildDescription(
        modelName: String,
        ownedBy: String
    ): String {
        return if (ownedBy.isNotBlank()) {
            "NVIDIA catalog model. owned_by=$ownedBy, model=$modelName"
        } else {
            "NVIDIA catalog model. model=$modelName"
        }
    }

    private fun buildTags(
        modelName: String,
        ownedBy: String
    ): List<String> {
        val lower = "$modelName $ownedBy".lowercase()
        val result = mutableListOf<String>()

        result.add(ModelProviderType.NVIDIA)

        when {
            lower.contains("embed") || lower.contains("embedding") -> result.add("임베딩")
            lower.contains("rerank") || lower.contains("reranker") -> result.add("리랭킹")
            else -> result.add("텍스트 생성")
        }

        if (lower.contains("vision") || lower.contains("image") || lower.contains("vl")) {
            result.add("비전")
        }

        if (lower.contains("coder") || lower.contains("code") || lower.contains("programming")) {
            result.add("코딩")
        }

        if (lower.contains("reasoning") || lower.contains("deepseek-r1") || lower.contains("r1")) {
            result.add("추론")
        }

        if (
            lower.contains("flash") ||
            lower.contains("lite") ||
            lower.contains("small") ||
            lower.contains("8b")
        ) {
            result.add("빠름")
        }

        if (lower.contains("long") || lower.contains("70b") || lower.contains("405b")) {
            result.add("긴 컨텍스트")
        }

        if (ownedBy.isNotBlank()) {
            result.add(ownedBy)
        }

        return result.distinct()
    }
}