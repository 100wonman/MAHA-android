package com.maha.app

enum class ProviderType {
    GOOGLE,
    OPENAI,
    OPENAI_COMPATIBLE,
    NVIDIA,
    LOCAL,
    CUSTOM
}

enum class CapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
    USER_ENABLED,
    USER_DISABLED
}

data class ProviderProfile(
    val providerId: String,
    val displayName: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKeyAlias: String?,
    val modelListEndpoint: String?,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class ConversationModelCapability(
    val text: Boolean = true,
    val code: Boolean = false,
    val vision: Boolean = false,
    val audio: Boolean = false,
    val video: Boolean = false,
    val toolCalling: Boolean = false,
    val functionCalling: Boolean = false,
    val webSearch: Boolean = false,
    val jsonMode: Boolean = false,
    val imageGeneration: Boolean = false,
    val structuredOutput: Boolean = false
)

data class ModelInputCapability(
    val text: CapabilityStatus = CapabilityStatus.SUPPORTED,
    val image: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val audio: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val video: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val file: CapabilityStatus = CapabilityStatus.UNKNOWN
)

data class ModelOutputCapability(
    val text: CapabilityStatus = CapabilityStatus.SUPPORTED,
    val code: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val json: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val image: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val audio: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val video: CapabilityStatus = CapabilityStatus.UNKNOWN
)

data class ModelToolCapability(
    val functionCalling: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val webSearch: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val codeExecution: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val structuredOutput: CapabilityStatus = CapabilityStatus.UNKNOWN
)

data class ModelReasoningCapability(
    val thinking: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val thinkingSummary: CapabilityStatus = CapabilityStatus.UNKNOWN,
    val chainOfThoughtRawAllowed: CapabilityStatus = CapabilityStatus.UNSUPPORTED
)

data class ModelCapabilityV2(
    val input: ModelInputCapability = ModelInputCapability(),
    val output: ModelOutputCapability = ModelOutputCapability(),
    val tools: ModelToolCapability = ModelToolCapability(),
    val reasoning: ModelReasoningCapability = ModelReasoningCapability()
)

data class ConversationModelProfile(
    val modelId: String,
    val providerId: String,
    val displayName: String,
    val rawModelName: String,
    val contextWindow: Int?,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val capabilities: ConversationModelCapability,
    val isFavorite: Boolean,
    val isDefaultForConversation: Boolean,
    val lastUsedAt: Long?,
    val enabled: Boolean,
    val capabilitiesV2: ModelCapabilityV2? = null,
    val capabilitySource: String = "USER",
    val supportedGenerationMethods: List<String> = emptyList(),
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val metadataRawSummary: String? = null,
    val lastMetadataFetchedAt: Long? = null
)

fun ConversationModelCapability.toModelCapabilityV2(): ModelCapabilityV2 {
    return ModelCapabilityV2(
        input = ModelInputCapability(
            text = if (text) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN,
            image = if (vision) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            audio = if (audio) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            video = if (video) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            file = CapabilityStatus.UNKNOWN
        ),
        output = ModelOutputCapability(
            text = if (text) CapabilityStatus.SUPPORTED else CapabilityStatus.UNKNOWN,
            code = if (code) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            json = if (jsonMode) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            image = if (imageGeneration) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            audio = CapabilityStatus.UNKNOWN,
            video = CapabilityStatus.UNKNOWN
        ),
        tools = ModelToolCapability(
            functionCalling = if (functionCalling || toolCalling) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            webSearch = if (webSearch) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN,
            codeExecution = CapabilityStatus.UNKNOWN,
            structuredOutput = if (structuredOutput) CapabilityStatus.USER_ENABLED else CapabilityStatus.UNKNOWN
        ),
        reasoning = ModelReasoningCapability(
            thinking = CapabilityStatus.UNKNOWN,
            thinkingSummary = CapabilityStatus.UNKNOWN,
            chainOfThoughtRawAllowed = CapabilityStatus.UNSUPPORTED
        )
    )
}
