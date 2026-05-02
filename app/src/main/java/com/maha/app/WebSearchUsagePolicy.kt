package com.maha.app

/**
 * WebSearchUsagePolicy
 *
 * 쉬운 설명:
 * - RAG 검색은 앱 내부 자료 검색이다.
 * - Web Search grounding은 외부 Google Search 기반 검색이다.
 * - Web Search 실제 실행 가능 여부와 fallback 허용 여부를 실행정보에 남긴다.
 */
data class WebSearchUsagePolicy(
    val enabledByUser: Boolean,
    val providerType: ProviderType?,
    val modelWebSearchStatus: CapabilityStatus?,
    val nativeGroundingAvailable: Boolean,
    val canAttemptGrounding: Boolean,
    val reason: String?,
    val fallbackAllowed: Boolean
) {
    fun toTraceSummary(): String {
        return buildString {
            appendLine("[WEB_SEARCH_GROUNDING]")
            appendLine("requested: $enabledByUser")
            appendLine("providerType: ${providerType?.name ?: "UNKNOWN"}")
            appendLine("modelWebSearchStatus: ${modelWebSearchStatus?.name ?: "UNKNOWN"}")
            appendLine("nativeGroundingAvailable: $nativeGroundingAvailable")
            appendLine("canAttemptGrounding: $canAttemptGrounding")
            appendLine("groundingExecuted: false")
            appendLine("citationCount: 0")
            appendLine("searchQueryCount: 0")
            appendLine("fallbackAllowed: $fallbackAllowed")
            appendLine("fallbackAttempted: false")
            appendLine("fallbackSucceeded: false")
            appendLine("groundingErrorType: NONE")
            appendLine("fallbackErrorType: NONE")
            appendLine("finalAnswerSource: ${if (enabledByUser) "ERROR" else "NONE"}")
            appendLine("reason: ${reason ?: "NONE"}")
        }.trimEnd()
    }
}

object WebSearchUsageResolver {
    const val REASON_DISABLED_BY_USER = "DISABLED_BY_USER"
    const val REASON_PROVIDER_NOT_GOOGLE = "WEB_SEARCH_NOT_SUPPORTED_FOR_PROVIDER"
    const val REASON_MODEL_CAPABILITY_NOT_ENABLED = "MODEL_WEB_SEARCH_CAPABILITY_NOT_ENABLED"
    const val REASON_NATIVE_GROUNDING_NOT_IMPLEMENTED = "NATIVE_GEMINI_GROUNDING_NOT_IMPLEMENTED"

    fun resolve(
        enabledByUser: Boolean,
        providerType: ProviderType?,
        modelWebSearchStatus: CapabilityStatus?,
        nativeGroundingAvailable: Boolean = false,
        fallbackAllowed: Boolean = true
    ): WebSearchUsagePolicy {
        val modelAllowsWebSearch = modelWebSearchStatus == CapabilityStatus.USER_ENABLED ||
                modelWebSearchStatus == CapabilityStatus.SUPPORTED

        val canAttemptGrounding = enabledByUser &&
                providerType == ProviderType.GOOGLE &&
                modelAllowsWebSearch &&
                nativeGroundingAvailable

        val reason = when {
            !enabledByUser -> REASON_DISABLED_BY_USER
            providerType != ProviderType.GOOGLE -> REASON_PROVIDER_NOT_GOOGLE
            !modelAllowsWebSearch -> REASON_MODEL_CAPABILITY_NOT_ENABLED
            !nativeGroundingAvailable -> REASON_NATIVE_GROUNDING_NOT_IMPLEMENTED
            else -> null
        }

        return WebSearchUsagePolicy(
            enabledByUser = enabledByUser,
            providerType = providerType,
            modelWebSearchStatus = modelWebSearchStatus,
            nativeGroundingAvailable = nativeGroundingAvailable,
            canAttemptGrounding = canAttemptGrounding,
            reason = reason,
            fallbackAllowed = fallbackAllowed
        )
    }
}
