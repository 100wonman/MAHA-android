package com.maha.app

/**
 * GeminiNativeProviderAdapter
 *
 * 쉬운 설명:
 * 후속 단계에서 Gemini native generateContent와 Google Search grounding을 담당할 Adapter skeleton이다.
 *
 * 현재 단계 정책:
 * - 실제 네트워크 호출을 하지 않는다.
 * - generateContent endpoint를 호출하지 않는다.
 * - Google Search grounding request body를 만들지 않는다.
 * - citation/grounding metadata를 파싱하지 않는다.
 * - ConversationEngine에는 아직 연결하지 않는다.
 */
class GeminiNativeProviderAdapter {

    fun execute(
        request: GeminiNativeRequest,
        apiKey: String
    ): GeminiNativeResult {
        // request 파라미터는 후속 실제 native 호출 구현을 위한 자리다.
        // 현재 skeleton 단계에서는 request body 변환이나 HTTP 호출을 하지 않는다.
        val ignoredRequest = request
        if (ignoredRequest.modelName.isBlank()) {
            return GeminiNativeResult.notImplemented()
        }

        // API Key는 로그/출력/metadata에 노출하지 않는다.
        if (apiKey.isBlank()) {
            return GeminiNativeResult.apiKeyMissing()
        }

        return GeminiNativeResult.notImplemented()
    }
}
