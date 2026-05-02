package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Google Gemini models endpoint reader.
 *
 * This fetcher is intentionally limited to model-list lookup only.
 * It does not call chat/completions and does not validate or expose the API key.
 */
class GoogleModelListFetcher {
    suspend fun fetchModels(
        apiKey: String,
        endpoint: String? = null
    ): GoogleModelListFetchResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext GoogleModelListFetchResult(
                success = false,
                models = emptyList(),
                errorType = "API_KEY_MISSING",
                errorMessage = "API Key가 설정되지 않았습니다."
            )
        }

        val url = buildGoogleModelsUrl(endpoint, trimmedKey)
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            val responseText = readConnectionText(connection, code)
            connection.disconnect()

            if (code !in 200..299) {
                return@withContext GoogleModelListFetchResult(
                    success = false,
                    models = emptyList(),
                    errorType = mapHttpErrorType(code),
                    errorMessage = parseGoogleErrorMessage(responseText, code)
                )
            }

            val models = parseModels(responseText)
            GoogleModelListFetchResult(
                success = true,
                models = models,
                errorType = null,
                errorMessage = null
            )
        }.getOrElse { throwable ->
            GoogleModelListFetchResult(
                success = false,
                models = emptyList(),
                errorType = "NETWORK_ERROR",
                errorMessage = throwable.message?.take(240) ?: "모델 목록 조회 중 네트워크 오류가 발생했습니다."
            )
        }
    }

    private fun buildGoogleModelsUrl(endpoint: String?, apiKey: String): String {
        // Google model list endpoint is not the OpenAI-compatible /openai/models path.
        val normalizedEndpoint = endpoint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace("/openai/models", "/models")
            ?: "https://generativelanguage.googleapis.com/v1beta/models"

        val separator = if (normalizedEndpoint.contains("?")) "&" else "?"
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        return "$normalizedEndpoint${separator}key=$encodedKey"
    }

    private fun readConnectionText(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.readText()
        }
    }

    private fun parseModels(responseText: String): List<GoogleModelListItem> {
        val root = JSONObject(responseText)
        val modelsArray = root.optJSONArray("models") ?: JSONArray()
        val output = mutableListOf<GoogleModelListItem>()

        for (index in 0 until modelsArray.length()) {
            val item = modelsArray.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue

            val rawModelName = name.removePrefix("models/")
            val methods = item.optJSONArray("supportedGenerationMethods").toStringList()

            output += GoogleModelListItem(
                name = name,
                rawModelName = rawModelName,
                displayName = item.optString("displayName").ifBlank { rawModelName },
                description = item.optString("description"),
                supportedGenerationMethods = methods,
                inputTokenLimit = item.optNullableInt("inputTokenLimit"),
                outputTokenLimit = item.optNullableInt("outputTokenLimit")
            )
        }

        return output.sortedBy { it.displayName.lowercase() }
    }

    private fun parseGoogleErrorMessage(responseText: String, httpCode: Int): String {
        if (responseText.isBlank()) return "모델 목록 조회 실패: HTTP $httpCode"
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            val message = error?.optString("message")?.takeIf { it.isNotBlank() }
            val status = error?.optString("status")?.takeIf { it.isNotBlank() }
            val code = error?.optInt("code", httpCode) ?: httpCode
            buildString {
                append("모델 목록 조회 실패")
                append(" · code=").append(code)
                if (!status.isNullOrBlank()) append(" · status=").append(status)
                if (!message.isNullOrBlank()) append(" · ").append(message.take(240))
            }
        }.getOrElse {
            "모델 목록 조회 실패: HTTP $httpCode"
        }
    }

    private fun mapHttpErrorType(code: Int): String {
        return when (code) {
            400 -> "INVALID_REQUEST"
            401, 403 -> "API_KEY_MISSING"
            408 -> "TIMEOUT"
            429 -> "RATE_LIMIT"
            in 500..599 -> "SERVER_ERROR"
            else -> "UNKNOWN"
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) list += value
        }
        return list
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}

data class GoogleModelListFetchResult(
    val success: Boolean,
    val models: List<GoogleModelListItem>,
    val errorType: String?,
    val errorMessage: String?
)

data class GoogleModelListItem(
    val name: String,
    val rawModelName: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>,
    val inputTokenLimit: Int?,
    val outputTokenLimit: Int?
)
