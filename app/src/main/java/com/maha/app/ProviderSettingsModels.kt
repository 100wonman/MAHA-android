package com.maha.app

enum class ProviderType {
    GOOGLE,
    OPENAI_COMPATIBLE,
    NVIDIA,
    LOCAL,
    CUSTOM
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
    val enabled: Boolean
)
