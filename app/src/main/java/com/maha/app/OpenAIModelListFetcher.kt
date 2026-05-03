package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * OpenAIModelListItem
 *
 * 쉬운 설명:
 * OpenAI 공식 /v1/models 응답에서 앱의 ModelProfile로 추가하기 전 임시로 들고 있는 모델 후보다.
 */
data class OpenAIModelListItem(
    val id: String,
    val displayName: String,
    val rawModelName: String,
    val createdAt: Long?,
    val metadataRawSummary: String?
)

/**
 * OpenAIModelListFetchResult
 *
 * 쉬운 설명:
 * OpenAI 공식 모델 목록 조회 성공/실패 결과를 담는 객체다.
 */
data class OpenAIModelListFetchResult(
    val success: Boolean,
    val models: List<OpenAIModelListItem> = emptyList(),
    val errorType: String? = null,
    val errorMessage: String? = null,
    val latencySec: Double = 0.0,
    val endpoint: String? = null
) {
    companion object {
        fun success(
            models: List<OpenAIModelListItem>,
            latencySec: Double,
            endpoint: String
        ): OpenAIModelListFetchResult {
            return OpenAIModelListFetchResult(
                success = true,
                models = models,
                latencySec = latencySec,
                endpoint = endpoint
            )
        }

        fun failure(
            errorType: String,
            errorMessage: String,
            latencySec: Double = 0.0,
            endpoint: String? = null
        ): OpenAIModelListFetchResult {
            return OpenAIModelListFetchResult(
                success = false,
                errorType = errorType,
                errorMessage = errorMessage,
                latencySec = latencySec,
                endpoint = endpoint
            )
        }
    }
}

/**
 * OpenAIModelListFetcher
 *
 * 쉬운 설명:
 * OpenAI 공식 Provider에서만 /v1/models 목록을 불러온다.
 * OpenAI-compatible Provider용 OpenAiCompatibleModelListFetcher와 분리한다.
 */
class OpenAIModelListFetcher {

    suspend fun fetchModels(
        provider: ProviderProfile,
        apiKey: String?
    ): OpenAIModelListFetchResult = withContext(Dispatchers.IO) {
        val endpoint = buildModelsEndpoint(provider)
        val startedAt = System.nanoTime()

        if (provider.providerType != ProviderType.OPENAI) {
            return@withContext OpenAIModelListFetchResult.failure(
                errorType = "PROVIDER_NOT_SUPPORTED",
                errorMessage = "ProviderType.${provider.providerType.name}은 OpenAI 공식 모델 목록 조회 대상이 아닙니다.",
                endpoint = endpoint
            )
        }

        if (apiKey.isNullOrBlank()) {
            return@withContext OpenAIModelListFetchResult.failure(
                errorType = "API_KEY_MISSING",
                errorMessage = "OpenAI 공식 모델 목록 조회에는 API Key가 필요합니다.",
                endpoint = endpoint
            )
        }

        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            try {
                val statusCode = connection.responseCode
                val responseText = readResponseText(connection, statusCode)
                val latencySec = elapsedSec(startedAt)

                if (statusCode !in 200..299) {
                    val providerError = ProviderErrorFormatter.fromHttpError(
                        httpStatusCode = statusCode,
                        responseText = responseText,
                        fallbackMessage = "OpenAI 모델 목록 조회에 실패했습니다."
                    )
                    OpenAIModelListFetchResult.failure(
                        errorType = providerError.errorType,
                        errorMessage = providerError.toUserMessage("OpenAI 모델 목록 조회에 실패했습니다."),
                        latencySec = latencySec,
                        endpoint = endpoint
                    )
                } else {
                    val models = parseModelsResponse(responseText)
                    if (models.isEmpty()) {
                        OpenAIModelListFetchResult.failure(
                            errorType = "PARSE_ERROR",
                            errorMessage = "OpenAI 응답에서 data[].id를 찾을 수 없습니다.",
                            latencySec = latencySec,
                            endpoint = endpoint
                        )
                    } else {
                        OpenAIModelListFetchResult.success(
                            models = models,
                            latencySec = latencySec,
                            endpoint = endpoint
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { throwable ->
            OpenAIModelListFetchResult.failure(
                errorType = mapThrowableToErrorType(throwable),
                errorMessage = throwable.message?.take(240) ?: "OpenAI 모델 목록 조회 중 오류가 발생했습니다.",
                latencySec = elapsedSec(startedAt),
                endpoint = endpoint
            )
        }
    }

    private fun buildModelsEndpoint(provider: ProviderProfile): String {
        val rawEndpoint = provider.modelListEndpoint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: provider.baseUrl.trim().ifBlank { "https://api.openai.com/v1" }

        val endpoint = rawEndpoint.trimEnd('/')
        return if (endpoint.endsWith("/models")) endpoint else "$endpoint/models"
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

    private fun parseModelsResponse(responseText: String): List<OpenAIModelListItem> {
        val root = JSONObject(responseText)
        val data = root.optJSONArray("data") ?: throw JSONException("data array missing")

        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                add(
                    OpenAIModelListItem(
                        id = id,
                        displayName = id,
                        rawModelName = id,
                        createdAt = item.optLongOrNull("created"),
                        metadataRawSummary = buildMetadataSummary(item)
                    )
                )
            }
        }
    }

    private fun buildMetadataSummary(item: JSONObject): String {
        return buildString {
            append("id=").append(item.optString("id"))
            item.optString("object").takeIf { it.isNotBlank() }?.let { append("\nobject=").append(it) }
            item.optLongOrNull("created")?.let { append("\ncreated=").append(it) }
            item.optString("owned_by").takeIf { it.isNotBlank() }?.let { append("\nowned_by=").append(it) }
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

    private fun mapHttpStatusToErrorType(statusCode: Int): String {
        return ProviderErrorFormatter.mapHttpStatusToErrorType(statusCode)
    }

    private fun mapThrowableToErrorType(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "TIMEOUT"
            is JSONException -> "PARSE_ERROR"
            else -> "UNKNOWN_ERROR"
        }
    }

    private fun elapsedSec(startedAt: Long): Double {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }
}
