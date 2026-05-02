package com.maha.app

/**
 * GeminiNativeRequest
 *
 * 쉬운 설명:
 * Gemini native generateContent 호출을 후속 단계에서 만들기 위한 요청 데이터 구조다.
 *
 * 현재 단계 정책:
 * - 실제 HTTP request body 변환은 구현하지 않는다.
 * - Google Search grounding request body도 구현하지 않는다.
 */
data class GeminiNativeRequest(
    val requestId: String,
    val modelName: String,
    val prompt: String,
    val systemInstruction: String? = null,
    val enableGoogleSearch: Boolean = false,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * GroundingCitation
 *
 * 쉬운 설명:
 * Google Search grounding 결과에서 출처 정보를 담기 위한 후보 구조다.
 *
 * 현재 단계 정책:
 * - 실제 citation 파싱은 구현하지 않는다.
 * - GeminiNativeProviderAdapter skeleton은 빈 리스트만 반환한다.
 */
data class GroundingCitation(
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null,
    val sourceIndex: Int? = null
)

/**
 * GeminiNativeResult
 *
 * 쉬운 설명:
 * Gemini native adapter가 ConversationEngine 쪽으로 돌려줄 결과 후보 구조다.
 *
 * 현재 단계 정책:
 * - 실제 성공 응답은 아직 만들지 않는다.
 * - execute(...)는 NOT_IMPLEMENTED 또는 API_KEY_MISSING 결과만 반환한다.
 */
data class GeminiNativeResult(
    val success: Boolean,
    val rawText: String,
    val groundingUsed: Boolean,
    val citations: List<GroundingCitation>,
    val searchQueries: List<String>,
    val thinkingSummary: String?,
    val latencySec: Double,
    val errorType: String?,
    val errorMessage: String?,
    val rawMetadataSummary: String?
) {
    companion object {
        fun notImplemented(): GeminiNativeResult {
            return GeminiNativeResult(
                success = false,
                rawText = "",
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList(),
                thinkingSummary = null,
                latencySec = 0.0,
                errorType = "NATIVE_GEMINI_NOT_IMPLEMENTED",
                errorMessage = "Gemini native grounding 호출은 아직 구현되지 않았습니다.",
                rawMetadataSummary = "nativeGroundingAvailable=false"
            )
        }

        fun apiKeyMissing(): GeminiNativeResult {
            return GeminiNativeResult(
                success = false,
                rawText = "",
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList(),
                thinkingSummary = null,
                latencySec = 0.0,
                errorType = "API_KEY_MISSING",
                errorMessage = "Gemini native 호출을 위한 API Key가 설정되지 않았습니다.",
                rawMetadataSummary = "nativeGroundingAvailable=false"
            )
        }
    }
}

/*
 * 후속 확장 후보:
 * - recentMessages
 * - ragContext
 * - safetySettings
 * - thinkingConfig
 * - responseMimeType
 * - structuredOutputSchema
 */
