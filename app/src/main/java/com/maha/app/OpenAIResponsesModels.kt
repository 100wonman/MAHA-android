package com.maha.app

/**
 * OpenAIResponsesRequest
 *
 * 쉬운 설명:
 * OpenAI 공식 Responses API 기본 대화 호출에 사용하는 요청 모델이다.
 * 이번 단계에서는 Web Search, streaming, tool/function execution은 넣지 않는다.
 */
data class OpenAIResponsesRequest(
    val requestId: String,
    val modelName: String,
    val prompt: String,
    val enableWebSearch: Boolean,
    val baseUrl: String? = null,
    val temperature: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * OpenAIResponseCitation
 *
 * 쉬운 설명:
 * 후속 OpenAI Web Search annotations/citations를 title/url/snippet 중심으로 변환하기 위한 모델이다.
 * 이번 기본 대화 호출에서는 citations를 채우지 않는다.
 */
data class OpenAIResponseCitation(
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val sourceIndex: Int? = null
)

/**
 * OpenAIResponsesResult
 *
 * 쉬운 설명:
 * OpenAI Responses API 호출 결과를 ConversationEngine으로 전달하는 결과 모델이다.
 */
data class OpenAIResponsesResult(
    val success: Boolean,
    val rawText: String,
    val citations: List<OpenAIResponseCitation>,
    val webSearchUsed: Boolean,
    val latencySec: Double,
    val errorType: String?,
    val errorMessage: String?,
    val rawMetadataSummary: String?,
    val endpoint: String? = null,
    val responseId: String? = null,
    val responseStatus: String? = null,
    val outputTextParsed: Boolean = false,
    val outputContentParsed: Boolean = false,
    val actualApiCall: Boolean = false
) {
    companion object {
        fun success(
            rawText: String,
            latencySec: Double,
            endpoint: String,
            responseId: String?,
            responseStatus: String?,
            rawMetadataSummary: String?,
            outputTextParsed: Boolean,
            outputContentParsed: Boolean
        ): OpenAIResponsesResult {
            return OpenAIResponsesResult(
                success = true,
                rawText = rawText,
                citations = emptyList(),
                webSearchUsed = false,
                latencySec = latencySec,
                errorType = null,
                errorMessage = null,
                rawMetadataSummary = rawMetadataSummary,
                endpoint = endpoint,
                responseId = responseId,
                responseStatus = responseStatus,
                outputTextParsed = outputTextParsed,
                outputContentParsed = outputContentParsed,
                actualApiCall = true
            )
        }

        fun failure(
            errorType: String,
            errorMessage: String,
            latencySec: Double = 0.0,
            endpoint: String? = null,
            rawMetadataSummary: String? = null,
            actualApiCall: Boolean = false
        ): OpenAIResponsesResult {
            return OpenAIResponsesResult(
                success = false,
                rawText = "",
                citations = emptyList(),
                webSearchUsed = false,
                latencySec = latencySec,
                errorType = errorType,
                errorMessage = errorMessage,
                rawMetadataSummary = rawMetadataSummary,
                endpoint = endpoint,
                actualApiCall = actualApiCall
            )
        }

        fun notImplemented(): OpenAIResponsesResult {
            return failure(
                errorType = "OPENAI_RESPONSES_NOT_IMPLEMENTED",
                errorMessage = "OpenAI Responses API 호출은 아직 구현되지 않았습니다.",
                rawMetadataSummary = "adapterSkeleton=true",
                actualApiCall = false
            )
        }

        fun apiKeyMissing(): OpenAIResponsesResult {
            return failure(
                errorType = "API_KEY_MISSING",
                errorMessage = "OpenAI Provider API Key가 설정되지 않았습니다.",
                rawMetadataSummary = "apiKey=missing",
                actualApiCall = false
            )
        }
    }
}
