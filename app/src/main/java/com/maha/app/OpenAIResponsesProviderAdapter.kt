package com.maha.app

/**
 * OpenAIResponsesProviderAdapter
 *
 * 쉬운 설명:
 * OpenAI 공식 Responses API(/v1/responses)와 web_search built-in tool을
 * 후속 단계에서 연결하기 위한 skeleton Adapter다.
 *
 * 이번 단계 정책:
 * - 실제 HTTP 호출 금지
 * - OpenAI Web Search 실제 호출 금지
 * - ConversationEngine 연결 금지
 * - API Key 원문 출력 금지
 */
class OpenAIResponsesProviderAdapter {

    fun execute(
        request: OpenAIResponsesRequest,
        apiKey: String
    ): OpenAIResponsesResult {
        if (apiKey.isBlank()) {
            return OpenAIResponsesResult.apiKeyMissing()
        }

        // request 파라미터는 후속 실제 구현을 위한 자리만 확보한다.
        // 현재 단계에서는 네트워크 호출을 하지 않고 NOT_IMPLEMENTED를 반환한다.
        @Suppress("UNUSED_VARIABLE")
        val reservedRequest = request

        return OpenAIResponsesResult.notImplemented()
    }
}
