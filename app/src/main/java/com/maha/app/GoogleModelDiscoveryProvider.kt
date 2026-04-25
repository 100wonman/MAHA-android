// GoogleModelDiscoveryProvider.kt

package com.maha.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object GoogleModelDiscoveryProvider {

    private const val TAG = "GoogleModelDiscovery"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun fetchModels(): List<DiscoveredModel> {
        val apiKey = ApiKeyManager.getGoogleApiKey()

        if (apiKey.isBlank()) {
            Log.d(TAG, "Google API key not set.")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL?key=$apiKey"
                Log.d(TAG, "Fetching Google models: $url")

                val response = URL(url).readText()
                parseModels(response)
            }.getOrElse {
                Log.e(TAG, "Google model fetch failed: ${it.message}")
                emptyList()
            }
        }
    }

    private fun parseModels(response: String): List<DiscoveredModel> {
        val result = mutableListOf<DiscoveredModel>()

        val root = JSONObject(response)
        val models = root.optJSONArray("models") ?: return emptyList()

        for (i in 0 until models.length()) {
            val model = models.optJSONObject(i) ?: continue

            val name = model.optString("name").removePrefix("models/")
            val displayName = model.optString("displayName", name)
            val description = model.optString("description", "")

            val methodsArray = model.optJSONArray("supportedGenerationMethods")
            val methods = mutableListOf<String>()

            if (methodsArray != null) {
                for (j in 0 until methodsArray.length()) {
                    methods.add(methodsArray.optString(j))
                }
            }

            val supportsGenerateContent = methods.contains("generateContent")

            result.add(
                DiscoveredModel(
                    modelName = name,
                    displayName = displayName,
                    description = description,
                    supportedGenerationMethods = methods,
                    inputTokenLimit = 0,
                    outputTokenLimit = 0,
                    isGenerateContentSupported = supportsGenerateContent,
                    lastFetchedAt = System.currentTimeMillis().toString(),
                    tags = listOf("Google", "API"),
                    providerName = ModelProviderType.GOOGLE,
                    isFreeCandidate = supportsGenerateContent
                )
            )
        }

        return result
    }
}