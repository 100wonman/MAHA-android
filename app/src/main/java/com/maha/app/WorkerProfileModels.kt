package com.maha.app

/**
 * Worker Profile skeleton models for the future variable multi-agent harness.
 *
 * This file is intentionally not connected to ConversationEngine, persistence,
 * Provider calls, RAG, Web Search, or any UI flow.
 */
data class ConversationWorkerProfile(
    val workerProfileId: String = "",
    val displayName: String = "",
    val roleLabel: String = "",
    val roleDescription: String = "",
    val systemInstruction: String = "",
    val providerId: String? = null,
    val modelId: String? = null,
    val capabilityOverrides: WorkerCapabilityOverrides = WorkerCapabilityOverrides(),
    val inputPolicy: WorkerInputPolicy = WorkerInputPolicy(),
    val outputPolicy: WorkerOutputPolicy = WorkerOutputPolicy(),
    val executionOrder: Int = 0,
    val canRunInParallel: Boolean = false,
    val dependsOnWorkerIds: List<String> = emptyList(),
    val enabled: Boolean = true,
    val isDefaultTemplate: Boolean = false,
    val userModified: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ConversationScenarioProfile(
    val scenarioId: String = "",
    val name: String = "",
    val description: String = "",
    val workerProfileIds: List<String> = emptyList(),
    val defaultExecutionMode: ExecutionMode = ExecutionMode.SINGLE,
    val orchestratorProfileId: String? = null,
    val synthesisProfileId: String? = null,
    val userEditable: Boolean = true,
    val isDefaultTemplate: Boolean = false,
    val userModified: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class WorkerCapabilityOverrides(
    val functionCalling: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val webSearch: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val codeExecution: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val structuredOutput: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val thinkingSummary: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val ragSearch: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val memoryRecall: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val fileRead: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val fileWrite: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val codeCheck: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val parallelExecution: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
)

data class WorkerInputPolicy(
    val userInputOnly: Boolean = true,
    val previousWorkerOutput: Boolean = false,
    val selectedWorkerOutputs: List<String> = emptyList(),
    val ragContextAllowed: Boolean = false,
    val memoryContextAllowed: Boolean = false,
    val webSearchContextAllowed: Boolean = false,
    val maxInputChars: Int = 0,
    val includeRunHistory: Boolean = false,
)

data class WorkerOutputPolicy(
    val expectedOutputType: CapabilityType = CapabilityType.TEXT_GENERATION,
    val requireJson: Boolean = false,
    val requireMarkdownTable: Boolean = false,
    val requireCodeBlock: Boolean = false,
    val allowPlainText: Boolean = true,
    val maxOutputChars: Int = 0,
    val passToNextWorker: Boolean = true,
    val exposeToUser: Boolean = true,
    val saveAsMemoryCandidate: Boolean = false,
)

data class WorkerProfileStoreEnvelope(
    val schemaVersion: Int = 1,
    val updatedAt: Long = 0L,
    val workerProfiles: List<ConversationWorkerProfile> = emptyList(),
)

data class ConversationScenarioStoreEnvelope(
    val schemaVersion: Int = 1,
    val updatedAt: Long = 0L,
    val scenarios: List<ConversationScenarioProfile> = emptyList(),
)
