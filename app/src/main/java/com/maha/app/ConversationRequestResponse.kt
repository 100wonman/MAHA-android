package com.maha.app

data class ConversationRequest(
    val requestId: String,
    val sessionId: String,
    val userInput: String,
    val selectedMode: String,
    val searchEnabled: Boolean,
    val ragContext: RagContext?,
    val recentMessages: List<ConversationMessage>,
    val systemInstruction: String?,
    val selectedProvider: String?,
    val selectedModel: String?,
    val createdAt: Long,
    val webSearchEnabled: Boolean = false,
    val selectedModelWebSearchStatus: CapabilityStatus? = null
)

data class ConversationResponse(
    val responseId: String,
    val requestId: String,
    val status: String,
    val blocks: List<ConversationOutputBlock>,
    val rawText: String,
    val providerName: String,
    val modelName: String,
    val latencySec: Double,
    val errorType: String?,
    val runInfo: ConversationRun?,
    val createdAt: Long
)
