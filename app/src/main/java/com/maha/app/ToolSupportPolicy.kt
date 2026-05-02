package com.maha.app

/**
 * ToolSupportStatus
 *
 * 쉬운 설명:
 * - SUPPORTED: 지원함
 * - UNSUPPORTED: 지원하지 않음
 * - UNKNOWN: 아직 확실하지 않음
 * - NOT_IMPLEMENTED: Provider나 모델이 지원할 가능성이 있어도 현재 앱에서는 아직 구현하지 않음
 */
enum class ToolSupportStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
    NOT_IMPLEMENTED
}

/**
 * ToolSupportPolicy
 *
 * 쉬운 설명:
 * ProviderType별로 대화모드에서 tool 관련 기능을 어떻게 취급할지 기록하는 정책 객체다.
 *
 * 주의:
 * - ModelCapability 체크값과 실제 ToolSupportPolicy는 다르다.
 * - Capability는 모델 기능 표시 및 향후 호출 정책 참고값이다.
 * - ToolSupportPolicy는 현재 대화모드의 실제 Provider/Adapter 지원 정책이다.
 * - 현재 1차 구현에서는 requestToolCallsEnabled=false 고정이다.
 */
data class ToolSupportPolicy(
    val providerType: ProviderType,
    val supportsFunctionCalling: ToolSupportStatus,
    val supportsWebSearch: ToolSupportStatus,
    val supportsCodeExecution: ToolSupportStatus,
    val supportsStructuredOutput: ToolSupportStatus,
    val supportsThinkingSummary: ToolSupportStatus,
    val requestToolCallsEnabled: Boolean,
    val notes: String
) {
    fun toTraceSummary(): String {
        return buildString {
            appendLine("TOOL_SUPPORT_SUMMARY")
            appendLine("providerType=${providerType.name}")
            appendLine("requestToolCallsEnabled=$requestToolCallsEnabled")
            appendLine("functionCalling=${supportsFunctionCalling.name}")
            appendLine("webSearch=${supportsWebSearch.name}")
            appendLine("codeExecution=${supportsCodeExecution.name}")
            appendLine("structuredOutput=${supportsStructuredOutput.name}")
            appendLine("thinkingSummary=${supportsThinkingSummary.name}")
            appendLine("notes=$notes")
        }.trimEnd()
    }
}

/**
 * ToolSupportResolver
 *
 * 쉬운 설명:
 * ProviderType을 넣으면 현재 MAHA 대화모드에서 사용할 tool 정책을 반환한다.
 */
object ToolSupportResolver {

    fun resolve(providerType: ProviderType): ToolSupportPolicy {
        return when (providerType) {
            ProviderType.GOOGLE -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsWebSearch = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsCodeExecution = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.NOT_IMPLEMENTED,
                requestToolCallsEnabled = false,
                notes = "Google provider normal conversation calls are enabled, but native tool execution is not implemented in conversation mode."
            )

            ProviderType.OPENAI_COMPATIBLE -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "OpenAI-compatible providers may return tool-like responses, but MAHA conversation mode does not execute tools yet."
            )

            ProviderType.NVIDIA -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "NVIDIA provider tool behavior is treated as OpenAI-compatible policy, but tool execution is not implemented in MAHA conversation mode."
            )

            ProviderType.LOCAL -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsCodeExecution = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "Local provider tool support depends on the local runtime, but webSearch/codeExecution are not implemented in MAHA conversation mode."
            )

            ProviderType.CUSTOM -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "Custom provider tool support is unknown. MAHA conversation mode currently treats actual tool execution as unsupported."
            )
        }
    }
}
