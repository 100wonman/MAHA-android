package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * OpenAiModelListCandidate
 *
 * 쉬운 설명:
 * OpenAI-compatible /models 응답에서 앱의 ModelProfile로 바꾸기 전 임시로 들고 있는 모델 후보다.
 */
data class OpenAiModelListCandidate(
    val rawModelName: String,
    val displayName: String,
    val description: String?,
    val contextWindow: Int?,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val ownedBy: String?,
    val metadataRawSummary: String?
)

/**
 * OpenAiCompatibleModelListResult
 *
 * 쉬운 설명:
 * 모델 목록 조회 성공/실패 결과를 한 번에 담는 객체다.
 */
data class OpenAiCompatibleModelListResult(
    val success: Boolean,
    val models: List<OpenAiModelListCandidate> = emptyList(),
    val errorType: String? = null,
    val errorMessage: String? = null,
    val endpoint: String? = null
) {
    companion object {
        fun success(
            models: List<OpenAiModelListCandidate>,
            endpoint: String
        ): OpenAiCompatibleModelListResult {
            return OpenAiCompatibleModelListResult(
                success = true,
                models = models,
                endpoint = endpoint
            )
        }

        fun failure(
            errorType: String,
            errorMessage: String,
            endpoint: String? = null
        ): OpenAiCompatibleModelListResult {
            return OpenAiCompatibleModelListResult(
                success = false,
                errorType = errorType,
                errorMessage = errorMessage,
                endpoint = endpoint
            )
        }
    }
}

/**
 * OpenAiCompatibleModelListFetcher
 *
 * 쉬운 설명:
 * Groq, OpenRouter, LM Studio, Ollama 같은 OpenAI-compatible Provider의 /models 목록을 불러온다.
 * Google 모델 목록 조회 로직은 건드리지 않는다.
 */
class OpenAiCompatibleModelListFetcher {

    suspend fun fetchModels(
        provider: ProviderProfile,
        apiKey: String?
    ): OpenAiCompatibleModelListResult = withContext(Dispatchers.IO) {
        val endpoint = buildModelsEndpoint(provider)
        if (endpoint == null) {
            return@withContext OpenAiCompatibleModelListResult.failure(
                errorType = "BASE_URL_MISSING",
                errorMessage = "Model List Endpoint 또는 Base URL이 설정되지 않았습니다."
            )
        }

        if (!isSupportedProviderType(provider.providerType)) {
            return@withContext OpenAiCompatibleModelListResult.failure(
                errorType = "PROVIDER_NOT_SUPPORTED",
                errorMessage = "이 Provider Type은 OpenAI-compatible 모델 목록 조회 대상이 아닙니다.",
                endpoint = endpoint
            )
        }

        if (provider.providerType == ProviderType.OPENAI_COMPATIBLE && apiKey.isNullOrBlank()) {
            return@withContext OpenAiCompatibleModelListResult.failure(
                errorType = "API_KEY_MISSING",
                errorMessage = "OPENAI_COMPATIBLE Provider는 모델 목록 조회에 API Key가 필요합니다.",
                endpoint = endpoint
            )
        }

        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            val statusCode = connection.responseCode
            val responseText = readResponseText(connection, statusCode)

            if (statusCode !in 200..299) {
                OpenAiCompatibleModelListResult.failure(
                    errorType = mapHttpStatusToErrorType(statusCode),
                    errorMessage = parseErrorMessage(responseText)
                        ?: "모델 목록 조회에 실패했습니다. HTTP $statusCode",
                    endpoint = endpoint
                )
            } else {
                val models = parseModelsResponse(responseText)
                if (models.isEmpty()) {
                    OpenAiCompatibleModelListResult.failure(
                        errorType = "INVALID_RESPONSE",
                        errorMessage = "조회된 모델이 없거나 응답에서 data[].id를 찾을 수 없습니다.",
                        endpoint = endpoint
                    )
                } else {
                    OpenAiCompatibleModelListResult.success(
                        models = models,
                        endpoint = endpoint
                    )
                }
            }
        }.getOrElse { throwable ->
            val errorType = when (throwable) {
                is SocketTimeoutException -> "TIMEOUT"
                is JSONException -> "INVALID_RESPONSE"
                else -> "NETWORK_ERROR"
            }
            OpenAiCompatibleModelListResult.failure(
                errorType = errorType,
                errorMessage = throwable.message?.take(240) ?: "모델 목록 조회 중 오류가 발생했습니다.",
                endpoint = endpoint
            )
        }
    }

    private fun buildModelsEndpoint(provider: ProviderProfile): String? {
        provider.modelListEndpoint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return null

        return "$baseUrl/models"
    }

    private fun isSupportedProviderType(providerType: ProviderType): Boolean {
        return providerType == ProviderType.OPENAI_COMPATIBLE ||
            providerType == ProviderType.LOCAL ||
            providerType == ProviderType.CUSTOM
    }

    private fun readResponseText(
        connection: HttpURLConnection,
        statusCode: Int
    ): String {
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseModelsResponse(responseText: String): List<OpenAiModelListCandidate> {
        val root = JSONObject(responseText)
        val data = root.optJSONArray("data") ?: JSONArray()

        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                val architecture = item.optJSONObject("architecture")
                val displayName = item.optString("name")
                    .takeIf { it.isNotBlank() }
                    ?: id

                val inputModalities = architecture?.optStringList("input_modalities")
                    ?.takeIf { it.isNotEmpty() }
                    ?: item.optStringList("input_modalities").ifEmpty { listOf("text") }

                val outputModalities = architecture?.optStringList("output_modalities")
                    ?.takeIf { it.isNotEmpty() }
                    ?: item.optStringList("output_modalities").ifEmpty { listOf("text") }

                add(
                    OpenAiModelListCandidate(
                        rawModelName = id,
                        displayName = displayName,
                        description = item.optNullableString("description"),
                        contextWindow = item.optNullableInt("context_length")
                            ?: item.optNullableInt("contextWindow"),
                        inputModalities = inputModalities,
                        outputModalities = outputModalities,
                        ownedBy = item.optNullableString("owned_by")
                            ?: item.optNullableString("ownedBy"),
                        metadataRawSummary = buildMetadataSummary(item)
                    )
                )
            }
        }
    }

    private fun buildMetadataSummary(item: JSONObject): String {
        val supportedParameters = item.optJSONArray("supported_parameters")?.toStringList().orEmpty()
        return buildString {
            append("id=").append(item.optString("id"))
            item.optNullableString("name")?.let { append("\nname=").append(it) }
            item.optNullableString("owned_by")?.let { append("\nowned_by=").append(it) }
            item.optNullableInt("created")?.let { append("\ncreated=").append(it) }
            item.optNullableInt("context_length")?.let { append("\ncontext_length=").append(it) }
            item.optNullableString("description")?.let { append("\ndescription=").append(it.take(300)) }
            if (supportedParameters.isNotEmpty()) {
                append("\nsupported_parameters=").append(supportedParameters.take(20).joinToString())
            }
        }
    }

    private fun mapHttpStatusToErrorType(statusCode: Int): String {
        return when (statusCode) {
            400 -> "INVALID_REQUEST"
            401, 403 -> "API_KEY_MISSING"
            408 -> "TIMEOUT"
            429 -> "RATE_LIMIT"
            in 500..599 -> "SERVER_ERROR"
            else -> "UNKNOWN"
        }
    }

    private fun parseErrorMessage(responseText: String): String? {
        if (responseText.isBlank()) return null
        return runCatching {
            val root = JSONObject(responseText)
            val error = root.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }?.take(300)
                ?: root.optString("message").takeIf { it.isNotBlank() }?.take(300)
        }.getOrNull()
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return array.toStringList()
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { optInt(key) }.getOrNull()
    }
}
