package com.maha.app

/**
 * GeminiNativeRequest
 *
 * 쉬운 설명:
 * Gemini native generateContent 호출에 필요한 최소 요청 데이터다.
 *
 * 현재 단계 정책:
 * - ConversationEngine에는 아직 연결하지 않는다.
 * - Web Search grounding 요청 body는 GeminiNativeProviderAdapter 내부에서만 만든다.
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
 * Gemini native groundingMetadata에서 추출한 출처 요약 정보다.
 *
 * 주의:
 * - citation은 ASSISTANT 본문에 직접 섞지 않는다.
 * - 후속 UX에서 실행정보 또는 접힘 출처 섹션에 표시한다.
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
 * Gemini native adapter가 반환하는 결과다.
 *
 * 주의:
 * - rawText에는 thought part를 제외한 최종 답변 text만 들어간다.
 * - thinkingSummary에는 thought 원문 전체가 아니라 제한된 preview만 들어간다.
 * - rawMetadataSummary에는 grounding metadata 전체가 아니라 count/key 중심 요약만 들어간다.
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
    val rawMetadataSummary: String?,
    val finishReason: String? = null,
    val answerPartCount: Int = 0,
    val thoughtPartCount: Int = 0
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
                rawMetadataSummary = "nativeGroundingAvailable=false",
                finishReason = null,
                answerPartCount = 0,
                thoughtPartCount = 0
            )
        }

        fun apiKeyMissing(latencySec: Double = 0.0): GeminiNativeResult {
            return GeminiNativeResult(
                success = false,
                rawText = "",
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList(),
                thinkingSummary = null,
                latencySec = latencySec,
                errorType = "API_KEY_MISSING",
                errorMessage = "Gemini native 호출을 위한 API Key가 설정되지 않았습니다.",
                rawMetadataSummary = "apiKey: blank",
                finishReason = null,
                answerPartCount = 0,
                thoughtPartCount = 0
            )
        }

        fun failure(
            latencySec: Double,
            errorType: String,
            errorMessage: String,
            rawMetadataSummary: String? = null,
            finishReason: String? = null,
            answerPartCount: Int = 0,
            thoughtPartCount: Int = 0
        ): GeminiNativeResult {
            return GeminiNativeResult(
                success = false,
                rawText = "",
                groundingUsed = false,
                citations = emptyList(),
                searchQueries = emptyList(),
                thinkingSummary = null,
                latencySec = latencySec,
                errorType = errorType,
                errorMessage = errorMessage,
                rawMetadataSummary = rawMetadataSummary,
                finishReason = finishReason,
                answerPartCount = answerPartCount,
                thoughtPartCount = thoughtPartCount
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
