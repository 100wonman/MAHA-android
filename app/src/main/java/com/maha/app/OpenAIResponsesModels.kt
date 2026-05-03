package com.maha.app

/**
 * OpenAIResponsesRequest
 *
 * 쉬운 설명:
 * OpenAI 공식 Responses API 호출을 후속 단계에서 구현하기 위한 요청 모델이다.
 * 현재 단계에서는 실제 네트워크 호출에 사용하지 않는다.
 */
data class OpenAIResponsesRequest(
    val requestId: String,
    val modelName: String,
    val prompt: String,
    val enableWebSearch: Boolean,
    val temperature: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * OpenAIResponseCitation
 *
 * 쉬운 설명:
 * OpenAI Responses API의 annotations/citations를 후속 단계에서 title/url/snippet 중심으로
 * 변환하기 위한 skeleton 모델이다.
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
 * OpenAI Responses API 호출 결과를 후속 ConversationEngine 연결 단계에서 전달하기 위한 결과 모델이다.
 * 현재는 NOT_IMPLEMENTED 결과만 반환한다.
 */
data class OpenAIResponsesResult(
    val success: Boolean,
    val rawText: String,
    val citations: List<OpenAIResponseCitation>,
    val webSearchUsed: Boolean,
    val latencySec: Double,
    val errorType: String?,
    val errorMessage: String?,
    val rawMetadataSummary: String?
) {
    companion object {
        fun notImplemented(): OpenAIResponsesResult {
            return OpenAIResponsesResult(
                success = false,
                rawText = "",
                citations = emptyList(),
                webSearchUsed = false,
                latencySec = 0.0,
                errorType = "OPENAI_RESPONSES_NOT_IMPLEMENTED",
                errorMessage = "OpenAI Responses API 호출은 아직 구현되지 않았습니다.",
                rawMetadataSummary = "adapterSkeleton=true"
            )
        }

        fun apiKeyMissing(): OpenAIResponsesResult {
            return OpenAIResponsesResult(
                success = false,
                rawText = "",
                citations = emptyList(),
                webSearchUsed = false,
                latencySec = 0.0,
                errorType = "API_KEY_MISSING",
                errorMessage = "OpenAI Provider API Key가 설정되지 않았습니다.",
                rawMetadataSummary = "adapterSkeleton=true"
            )
        }
    }
}
