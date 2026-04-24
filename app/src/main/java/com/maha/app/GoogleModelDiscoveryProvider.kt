// GoogleModelDiscoveryProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleModelDiscoveryProvider {

    private const val TAG = "GoogleModelDiscovery"
    private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun fetchModels(): List<DiscoveredModel> {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            Log.d(TAG, "Google API key is not set.")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                callListModels(apiKey)
            }.getOrElse { exception ->
                Log.e(TAG, "listModels exception: ${exception.message}", exception)
                emptyList()
            }
        }
    }

    private fun callListModels(apiKey: String): List<DiscoveredModel> {
        val encodedApiKey = URLEncoder.encode(apiKey, "UTF-8")
        val maskedApiKey = maskApiKey(apiKey)

        val logUrl = "$MODELS_URL?key=$maskedApiKey"
        val realUrl = "$MODELS_URL?key=$encodedApiKey"

        Log.d(TAG, "listModels request URL: $logUrl")

        val connection = URL(realUrl).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            } else {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use(BufferedReader::readText)
                    ?: ""
            }

            Log.d(TAG, "listModels HTTP status code: $responseCode")
            Log.d(TAG, "listModels response body: $responseText")

            if (responseCode !in 200..299) {
                return emptyList()
            }

            return parseModels(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseModels(responseText: String): List<DiscoveredModel> {
        val root = JSONObject(responseText)
        val modelsArray = root.optJSONArray("models") ?: JSONArray()
        val result = mutableListOf<DiscoveredModel>()
        val fetchedAt = getCurrentTimeText()

        for (index in 0 until modelsArray.length()) {
            val item = modelsArray.optJSONObject(index) ?: continue

            val rawName = item.optString("name", "")
            val modelName = rawName.removePrefix("models/").trim()

            if (modelName.isBlank()) continue

            val methods = jsonArrayToStringList(
                item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            )

            val isGenerateContentSupported = methods.contains("generateContent")

            result.add(
                DiscoveredModel(
                    modelName = modelName,
                    displayName = item.optString("displayName", modelName),
                    description = item.optString("description", ""),
                    supportedGenerationMethods = methods,
                    inputTokenLimit = item.optInt("inputTokenLimit", 0),
                    outputTokenLimit = item.optInt("outputTokenLimit", 0),
                    isGenerateContentSupported = isGenerateContentSupported,
                    lastFetchedAt = fetchedAt,
                    tags = buildTags(
                        modelName = modelName,
                        methods = methods,
                        isGenerateContentSupported = isGenerateContentSupported
                    )
                )
            )
        }

        return result.distinctBy { it.modelName }
    }

    private fun buildTags(
        modelName: String,
        methods: List<String>,
        isGenerateContentSupported: Boolean
    ): List<String> {
        val lowerName = modelName.lowercase()
        val tags = mutableListOf<String>()

        if (isGenerateContentSupported) tags.add("TEXT_GENERATION")
        if (methods.contains("embedContent")) tags.add("EMBEDDING")
        if (methods.contains("streamGenerateContent")) tags.add("STREAMING")
        if (lowerName.contains("gemini")) tags.add("GEMINI")
        if (lowerName.contains("gemma")) tags.add("GEMMA")
        if (lowerName.contains("embedding")) tags.add("EMBEDDING")
        if (lowerName.contains("image") || lowerName.contains("imagen")) tags.add("IMAGE")
        if (lowerName.contains("live")) tags.add("LIVE")
        if (lowerName.contains("audio") || lowerName.contains("tts")) tags.add("AUDIO")
        if (lowerName.contains("search")) tags.add("SEARCH_GROUNDING_CANDIDATE")

        if (tags.isEmpty()) {
            tags.add("UNKNOWN")
        }

        return tags.distinct()
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

    private fun maskApiKey(apiKey: String): String {
        val prefix = apiKey.take(4)
        return "$prefix****"
    }
}