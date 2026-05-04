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
 * - SUPPORTED는 모델/Provider가 tool-call 형식을 낼 수 있다는 뜻이지 앱이 tool을 실행한다는 뜻이 아니다.
 * - 실제 실행 가능 여부는 후속 Tool Registry와 execution adapter가 생긴 뒤 별도로 판단한다.
 * - 현재 1차 구현에서는 requestToolCallsEnabled=false 고정이며 actual tool execution은 항상 미구현이다.
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
            appendLine("toolExecutionImplemented=false")
            appendLine("toolExecutionBlockedReason=TOOL_EXECUTION_NOT_IMPLEMENTED")
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

            ProviderType.OPENAI -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.NOT_IMPLEMENTED,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "OpenAI official Responses API and hosted web_search are separated from OpenAI-compatible chat/completions. Actual OpenAI Web Search is not implemented yet."
            )

            ProviderType.OPENAI_COMPATIBLE -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "OpenAI-compatible providers differ by vendor/model. MAHA may detect tool_calls/function_call in responses, but actual tool execution is blocked until Tool Registry execution is implemented."
            )

            ProviderType.NVIDIA -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "NVIDIA provider tool support is model/runtime dependent. MAHA does not execute tools in conversation mode yet."
            )

            ProviderType.LOCAL -> ToolSupportPolicy(
                providerType = providerType,
                supportsFunctionCalling = ToolSupportStatus.UNKNOWN,
                supportsWebSearch = ToolSupportStatus.UNKNOWN,
                supportsCodeExecution = ToolSupportStatus.UNKNOWN,
                supportsStructuredOutput = ToolSupportStatus.UNKNOWN,
                supportsThinkingSummary = ToolSupportStatus.UNKNOWN,
                requestToolCallsEnabled = false,
                notes = "Local provider tool support depends on the local runtime and model. MAHA does not execute tools in conversation mode yet."
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
