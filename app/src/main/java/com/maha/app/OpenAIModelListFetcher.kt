package com.maha.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OpenAIModelListItem
 *
 * 쉬운 설명:
 * OpenAI 공식 /v1/models 응답을 후속 단계에서 앱 모델 후보로 바꾸기 위한 임시 구조다.
 * 현재 skeleton 단계에서는 실제 HTTP 호출을 하지 않으므로 빈 목록만 반환한다.
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
        fun notImplemented(endpoint: String): OpenAIModelListFetchResult {
            return OpenAIModelListFetchResult(
                success = false,
                models = emptyList(),
                errorType = "OPENAI_MODEL_LIST_NOT_IMPLEMENTED",
                errorMessage = "OpenAI 공식 모델 목록 조회는 아직 구현되지 않았습니다.",
                latencySec = 0.0,
                endpoint = endpoint
            )
        }

        fun unsupportedProvider(providerType: ProviderType): OpenAIModelListFetchResult {
            return OpenAIModelListFetchResult(
                success = false,
                models = emptyList(),
                errorType = "PROVIDER_NOT_SUPPORTED",
                errorMessage = "ProviderType.${providerType.name}은 OpenAI 공식 모델 목록 조회 대상이 아닙니다.",
                latencySec = 0.0,
                endpoint = null
            )
        }
    }
}

/**
 * OpenAIModelListFetcher
 *
 * 쉬운 설명:
 * OpenAI 공식 /v1/models 조회를 후속 단계에서 구현하기 위한 skeleton이다.
 *
 * 주의:
 * - 현재 단계에서는 실제 네트워크 호출을 하지 않는다.
 * - API Key를 로그나 UI에 노출하지 않는다.
 * - OpenAiCompatibleModelListFetcher와 분리한다.
 */
class OpenAIModelListFetcher {

    suspend fun fetchModels(
        provider: ProviderProfile,
        apiKey: String?
    ): OpenAIModelListFetchResult = withContext(Dispatchers.IO) {
        if (provider.providerType != ProviderType.OPENAI) {
            return@withContext OpenAIModelListFetchResult.unsupportedProvider(provider.providerType)
        }

        // 후속 실제 구현에서 x-goog/openai가 아닌 OpenAI Authorization header에 apiKey를 사용한다.
        // 현재 skeleton 단계에서는 apiKey 존재 여부를 검사하지 않고, 실제 HTTP 호출도 하지 않는다.
        @Suppress("UNUSED_VARIABLE")
        val apiKeyReservedForFuture = apiKey

        OpenAIModelListFetchResult.notImplemented(
            endpoint = buildModelsEndpoint(provider)
        )
    }

    private fun buildModelsEndpoint(provider: ProviderProfile): String {
        provider.modelListEndpoint
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val baseUrl = provider.baseUrl.trim().trimEnd('/').ifBlank {
            "https://api.openai.com/v1"
        }
        return "$baseUrl/models"
    }
}
