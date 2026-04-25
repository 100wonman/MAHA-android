// NvidiaModelDiscoveryProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NvidiaModelDiscoveryProvider {

    private const val TAG = "NvidiaModelDiscovery"
    private const val MODELS_URL = "https://integrate.api.nvidia.com/v1/models"

    suspend fun fetchModels(): List<DiscoveredModel> {
        val apiKey = ApiKeyManager.getNvidiaApiKey()

        if (apiKey.isBlank()) {
            Log.d(TAG, "NVIDIA API key not set.")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(MODELS_URL).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode

                val responseText = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }

                Log.d(TAG, "NVIDIA models status: $responseCode")
                Log.d(TAG, "NVIDIA models body: $responseText")

                if (responseCode !in 200..299) {
                    return@runCatching emptyList()
                }

                parseModels(responseText)
            }.getOrElse {
                Log.e(TAG, "NVIDIA model fetch failed: ${it.message}")
                emptyList()
            }
        }
    }

    private fun parseModels(response: String): List<DiscoveredModel> {
        val result = mutableListOf<DiscoveredModel>()

        val root = JSONObject(response)
        val data = root.optJSONArray("data") ?: return emptyList()

        for (i in 0 until data.length()) {
            val model = data.optJSONObject(i) ?: continue

            val id = model.optString("id")
            val ownedBy = model.optString("owned_by")

            result.add(
                DiscoveredModel(
                    modelName = id,
                    displayName = id,
                    description = "NVIDIA model ($ownedBy)",
                    supportedGenerationMethods = listOf("chat/completions"),
                    inputTokenLimit = 0,
                    outputTokenLimit = 0,
                    isGenerateContentSupported = true,
                    lastFetchedAt = System.currentTimeMillis().toString(),
                    tags = listOf("NVIDIA", "API"),
                    providerName = ModelProviderType.NVIDIA,
                    isFreeCandidate = true
                )
            )
        }

        return result
    }
}