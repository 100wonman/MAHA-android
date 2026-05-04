package com.maha.app

/**
 * WorkerProfileStore skeleton for the future variable Worker Profile system.
 *
 * Important:
 * - This file does not read or write JSON files.
 * - This file does not create worker_profiles.json or conversation_scenarios.json.
 * - This file is not connected to ConversationEngine, ViewModel, Provider calls,
 *   RAG, Web Search, SAF backup/restore, or any Worker management UI.
 * - The in-memory envelopes below are only a safe skeleton so future UI/store
 *   work can call the functions without crashing before persistence is added.
 */
object WorkerProfileStore {
    const val SCHEMA_VERSION: Int = 1

    const val WORKER_PROFILES_FILE_NAME: String = "worker_profiles.json"
    const val CONVERSATION_SCENARIOS_FILE_NAME: String = "conversation_scenarios.json"

    const val DEFAULT_ORCHESTRATOR_ID: String = "default_orchestrator"
    const val DEFAULT_MAIN_WORKER_ID: String = "default_main_worker"
    const val DEFAULT_SYNTHESIS_WORKER_ID: String = "default_synthesis_worker"
    const val DEFAULT_RESEARCH_WORKER_ID: String = "default_research_worker"
    const val DEFAULT_REVIEWER_WORKER_ID: String = "default_reviewer_worker"
    const val DEFAULT_RAG_WORKER_ID: String = "default_rag_worker"
    const val DEFAULT_MEMORY_WORKER_ID: String = "default_memory_worker"
    const val DEFAULT_WEB_SEARCH_WORKER_ID: String = "default_web_search_worker"
    const val DEFAULT_TOOL_WORKER_ID: String = "default_tool_worker"
    const val DEFAULT_CODE_CHECK_WORKER_ID: String = "default_code_check_worker"
    const val DEFAULT_COMPARISON_WORKER_ID: String = "default_comparison_worker"

    const val DEFAULT_SINGLE_SCENARIO_ID: String = "default_single_conversation"
    const val DEFAULT_RESEARCH_SCENARIO_ID: String = "default_research_summary"
    const val DEFAULT_CODE_REVIEW_SCENARIO_ID: String = "default_code_review"
    const val DEFAULT_WRITING_REVIEW_SCENARIO_ID: String = "default_writing_review"
    const val DEFAULT_RAG_SCENARIO_ID: String = "default_rag_centered"
    const val DEFAULT_COMPARISON_SCENARIO_ID: String = "default_comparison_parallel"

    private var workerEnvelope: WorkerProfileStoreEnvelope = WorkerProfileStoreEnvelope(
        schemaVersion = SCHEMA_VERSION,
        updatedAt = now(),
        workerProfiles = createDefaultWorkerTemplates(),
    )

    private var scenarioEnvelope: ConversationScenarioStoreEnvelope = ConversationScenarioStoreEnvelope(
        schemaVersion = SCHEMA_VERSION,
        updatedAt = now(),
        scenarios = createDefaultConversationScenarios(),
    )

    fun loadWorkerProfiles(): WorkerProfileStoreEnvelope {
        // TODO: Replace this in-memory skeleton with JSON file loading.
        return workerEnvelope
    }

    fun saveWorkerProfiles(envelope: WorkerProfileStoreEnvelope) {
        // TODO: Replace this in-memory skeleton with JSON file saving.
        workerEnvelope = envelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
        )
    }

    fun loadConversationScenarios(): ConversationScenarioStoreEnvelope {
        // TODO: Replace this in-memory skeleton with JSON file loading.
        return scenarioEnvelope
    }

    fun saveConversationScenarios(envelope: ConversationScenarioStoreEnvelope) {
        // TODO: Replace this in-memory skeleton with JSON file saving.
        scenarioEnvelope = envelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
        )
    }

    fun getWorkerProfile(workerProfileId: String): ConversationWorkerProfile? {
        return loadWorkerProfiles().workerProfiles.firstOrNull { profile ->
            profile.workerProfileId == workerProfileId
        }
    }

    fun upsertWorkerProfile(profile: ConversationWorkerProfile) {
        val current = loadWorkerProfiles().workerProfiles
        val next = current.filterNot { it.workerProfileId == profile.workerProfileId } + profile.copy(
            updatedAt = if (profile.updatedAt == 0L) now() else profile.updatedAt,
        )
        saveWorkerProfiles(loadWorkerProfiles().copy(workerProfiles = next))
    }

    fun deleteWorkerProfile(workerProfileId: String) {
        val next = loadWorkerProfiles().workerProfiles.filterNot { profile ->
            profile.workerProfileId == workerProfileId
        }
        saveWorkerProfiles(loadWorkerProfiles().copy(workerProfiles = next))
        // Scenario references are intentionally not auto-deleted here.
        // Future validation UI should show missing Worker references as limitations.
    }

    fun listEnabledWorkerProfiles(): List<ConversationWorkerProfile> {
        return loadWorkerProfiles().workerProfiles.filter { it.enabled }
    }

    fun listDefaultWorkerTemplates(): List<ConversationWorkerProfile> {
        return loadWorkerProfiles().workerProfiles.filter { it.isDefaultTemplate }
    }

    fun getScenario(scenarioId: String): ConversationScenarioProfile? {
        return loadConversationScenarios().scenarios.firstOrNull { scenario ->
            scenario.scenarioId == scenarioId
        }
    }

    fun upsertScenario(scenario: ConversationScenarioProfile) {
        val current = loadConversationScenarios().scenarios
        val next = current.filterNot { it.scenarioId == scenario.scenarioId } + scenario.copy(
            updatedAt = if (scenario.updatedAt == 0L) now() else scenario.updatedAt,
        )
        saveConversationScenarios(loadConversationScenarios().copy(scenarios = next))
    }

    fun deleteScenario(scenarioId: String) {
        val next = loadConversationScenarios().scenarios.filterNot { scenario ->
            scenario.scenarioId == scenarioId
        }
        saveConversationScenarios(loadConversationScenarios().copy(scenarios = next))
    }

    fun listEnabledScenarios(): List<ConversationScenarioProfile> {
        return loadConversationScenarios().scenarios.filter { it.enabled }
    }

    fun getDefaultScenario(): ConversationScenarioProfile? {
        return loadConversationScenarios().scenarios.firstOrNull { scenario ->
            scenario.enabled && scenario.isDefaultTemplate && scenario.scenarioId == DEFAULT_SINGLE_SCENARIO_ID
        } ?: loadConversationScenarios().scenarios.firstOrNull { scenario ->
            scenario.enabled && scenario.isDefaultTemplate
        }
    }

    fun ensureDefaultWorkerTemplates(): WorkerProfileStoreEnvelope {
        val currentEnvelope = loadWorkerProfiles()
        val currentProfiles = currentEnvelope.workerProfiles
        val currentIds = currentProfiles.map { it.workerProfileId }.toSet()
        val missingTemplates = createDefaultWorkerTemplates().filterNot { template ->
            template.workerProfileId in currentIds
        }

        if (missingTemplates.isEmpty()) return currentEnvelope

        val nextEnvelope = currentEnvelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
            workerProfiles = currentProfiles + missingTemplates,
        )
        saveWorkerProfiles(nextEnvelope)
        return loadWorkerProfiles()
    }

    fun ensureDefaultScenarios(): ConversationScenarioStoreEnvelope {
        val currentEnvelope = loadConversationScenarios()
        val currentScenarios = currentEnvelope.scenarios
        val currentIds = currentScenarios.map { it.scenarioId }.toSet()
        val missingScenarios = createDefaultConversationScenarios().filterNot { scenario ->
            scenario.scenarioId in currentIds
        }

        if (missingScenarios.isEmpty()) return currentEnvelope

        val nextEnvelope = currentEnvelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
            scenarios = currentScenarios + missingScenarios,
        )
        saveConversationScenarios(nextEnvelope)
        return loadConversationScenarios()
    }

    fun createDefaultWorkerTemplates(): List<ConversationWorkerProfile> {
        val createdAt = now()
        return listOf(
            defaultWorker(
                workerProfileId = DEFAULT_ORCHESTRATOR_ID,
                displayName = "Orchestrator",
                roleLabel = "ORCHESTRATOR",
                roleDescription = "사용자 요청을 분석하고 필요한 capability와 실행 방식을 추정합니다.",
                systemInstruction = "You are the orchestrator. Analyze the user request, identify required capabilities, and plan the Worker flow. Do not execute tools directly.",
                executionOrder = 0,
                expectedOutputType = CapabilityType.STRUCTURED_OUTPUT,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_MAIN_WORKER_ID,
                displayName = "Main Worker",
                roleLabel = "MAIN",
                roleDescription = "핵심 답변 생성을 담당합니다.",
                systemInstruction = "You are the main worker. Produce the primary answer for the user request using the assigned model.",
                executionOrder = 1,
                expectedOutputType = CapabilityType.TEXT_GENERATION,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                displayName = "Synthesis Worker",
                roleLabel = "SYNTHESIS",
                roleDescription = "여러 Worker 결과를 통합하고 최종 답변을 정리합니다.",
                systemInstruction = "You are the synthesis worker. Combine worker outputs, remove conflicts, and produce a clear final response.",
                executionOrder = 90,
                expectedOutputType = CapabilityType.SYNTHESIS,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_RESEARCH_WORKER_ID,
                displayName = "Research Worker",
                roleLabel = "RESEARCH",
                roleDescription = "조사와 자료 정리를 담당하는 후보 Worker입니다.",
                systemInstruction = "You are the research worker. Gather and summarize relevant information from available context. Do not perform external search unless a search capability is provided.",
                executionOrder = 10,
                canRunInParallel = true,
                expectedOutputType = CapabilityType.TEXT_GENERATION,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_REVIEWER_WORKER_ID,
                displayName = "Reviewer Worker",
                roleLabel = "REVIEWER",
                roleDescription = "결과 검토와 누락 확인을 담당합니다.",
                systemInstruction = "You are the reviewer worker. Check the answer for omissions, inconsistencies, and unsupported claims.",
                executionOrder = 95,
                expectedOutputType = CapabilityType.REVIEW,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_RAG_WORKER_ID,
                displayName = "RAG Worker",
                roleLabel = "RAG",
                roleDescription = "MAHA-native RAG context 사용을 담당하는 후보 Worker입니다.",
                systemInstruction = "You are the RAG worker. Use provided retrieval context to extract relevant facts. Do not invent unavailable context.",
                executionOrder = 20,
                canRunInParallel = true,
                overrides = WorkerCapabilityOverrides(ragSearch = CapabilityLayerStatus.USER_ENABLED),
                inputPolicy = WorkerInputPolicy(userInputOnly = false, ragContextAllowed = true),
                expectedOutputType = CapabilityType.RAG_SEARCH,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_MEMORY_WORKER_ID,
                displayName = "Memory Worker",
                roleLabel = "MEMORY",
                roleDescription = "장기 기억 후보 정보를 다루기 위한 Worker 템플릿입니다.",
                systemInstruction = "You are the memory worker. Identify useful memory context when memory capability is available.",
                executionOrder = 25,
                canRunInParallel = true,
                overrides = WorkerCapabilityOverrides(memoryRecall = CapabilityLayerStatus.UNKNOWN),
                inputPolicy = WorkerInputPolicy(userInputOnly = false, memoryContextAllowed = true),
                expectedOutputType = CapabilityType.MEMORY_RECALL,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_WEB_SEARCH_WORKER_ID,
                displayName = "Web Search Worker",
                roleLabel = "WEB_SEARCH",
                roleDescription = "Web Search capability가 연결될 때 검색 계획을 담당할 Worker 템플릿입니다.",
                systemInstruction = "You are the web search worker. Prepare search-oriented subtasks when web search capability is available. Do not claim that search was executed unless it was actually executed.",
                executionOrder = 30,
                canRunInParallel = true,
                overrides = WorkerCapabilityOverrides(webSearch = CapabilityLayerStatus.UNKNOWN),
                inputPolicy = WorkerInputPolicy(userInputOnly = false, webSearchContextAllowed = true),
                expectedOutputType = CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_TOOL_WORKER_ID,
                displayName = "Tool Worker",
                roleLabel = "TOOL",
                roleDescription = "Tool Registry 후속 구현을 위한 Worker 템플릿입니다.",
                systemInstruction = "You are the tool worker. Detect tool needs and prepare tool calls when tool execution becomes available. Do not execute tools in the skeleton stage.",
                executionOrder = 35,
                canRunInParallel = true,
                overrides = WorkerCapabilityOverrides(functionCalling = CapabilityLayerStatus.UNKNOWN),
                expectedOutputType = CapabilityType.TOOL_CALL_DETECTION,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_CODE_CHECK_WORKER_ID,
                displayName = "Code Check Worker",
                roleLabel = "CODE_CHECK",
                roleDescription = "코드 검증과 수정 제안을 담당하는 Worker 템플릿입니다.",
                systemInstruction = "You are the code check worker. Inspect code for syntax, logic, and integration risks, then propose safe fixes.",
                executionOrder = 40,
                overrides = WorkerCapabilityOverrides(codeCheck = CapabilityLayerStatus.USER_ENABLED),
                outputPolicy = WorkerOutputPolicy(
                    expectedOutputType = CapabilityType.CODE_CHECK,
                    requireCodeBlock = true,
                    allowPlainText = true,
                    passToNextWorker = true,
                    exposeToUser = true,
                ),
                expectedOutputType = CapabilityType.CODE_CHECK,
            ),
            defaultWorker(
                workerProfileId = DEFAULT_COMPARISON_WORKER_ID,
                displayName = "Comparison Worker",
                roleLabel = "COMPARISON",
                roleDescription = "여러 관점 비교와 병렬 분석 후보 Worker입니다.",
                systemInstruction = "You are the comparison worker. Compare multiple perspectives independently and provide structured observations.",
                executionOrder = 50,
                canRunInParallel = true,
                overrides = WorkerCapabilityOverrides(parallelExecution = CapabilityLayerStatus.UNKNOWN),
                outputPolicy = WorkerOutputPolicy(
                    expectedOutputType = CapabilityType.TABLE_OUTPUT,
                    requireMarkdownTable = true,
                    allowPlainText = true,
                    passToNextWorker = true,
                    exposeToUser = true,
                ),
                expectedOutputType = CapabilityType.TABLE_OUTPUT,
            ),
        )
    }

    fun createDefaultConversationScenarios(): List<ConversationScenarioProfile> {
        val createdAt = now()
        return listOf(
            defaultScenario(
                scenarioId = DEFAULT_SINGLE_SCENARIO_ID,
                name = "기본 단일 대화",
                description = "Orchestrator와 Main Worker 중심의 안전한 단일 실행 템플릿입니다.",
                workerProfileIds = listOf(DEFAULT_ORCHESTRATOR_ID, DEFAULT_MAIN_WORKER_ID),
                defaultExecutionMode = ExecutionMode.SINGLE,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = null,
                createdAt = createdAt,
            ),
            defaultScenario(
                scenarioId = DEFAULT_RESEARCH_SCENARIO_ID,
                name = "조사 / 요약",
                description = "Research Worker와 Synthesis Worker를 포함하는 조사/요약 후보 템플릿입니다.",
                workerProfileIds = listOf(
                    DEFAULT_ORCHESTRATOR_ID,
                    DEFAULT_RESEARCH_WORKER_ID,
                    DEFAULT_SYNTHESIS_WORKER_ID,
                ),
                defaultExecutionMode = ExecutionMode.SEQUENTIAL,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                createdAt = createdAt,
            ),
            defaultScenario(
                scenarioId = DEFAULT_CODE_REVIEW_SCENARIO_ID,
                name = "코드 검토",
                description = "Main Worker와 Code Check Worker를 사용하는 코드 검토 후보 템플릿입니다.",
                workerProfileIds = listOf(
                    DEFAULT_ORCHESTRATOR_ID,
                    DEFAULT_MAIN_WORKER_ID,
                    DEFAULT_CODE_CHECK_WORKER_ID,
                    DEFAULT_SYNTHESIS_WORKER_ID,
                ),
                defaultExecutionMode = ExecutionMode.SEQUENTIAL,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                createdAt = createdAt,
            ),
            defaultScenario(
                scenarioId = DEFAULT_WRITING_REVIEW_SCENARIO_ID,
                name = "글쓰기 / 리뷰",
                description = "Main Worker, Reviewer Worker, Synthesis Worker를 포함하는 글쓰기/검토 후보 템플릿입니다.",
                workerProfileIds = listOf(
                    DEFAULT_ORCHESTRATOR_ID,
                    DEFAULT_MAIN_WORKER_ID,
                    DEFAULT_REVIEWER_WORKER_ID,
                    DEFAULT_SYNTHESIS_WORKER_ID,
                ),
                defaultExecutionMode = ExecutionMode.SEQUENTIAL,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                createdAt = createdAt,
            ),
            defaultScenario(
                scenarioId = DEFAULT_RAG_SCENARIO_ID,
                name = "RAG 중심",
                description = "RAG Worker를 포함하는 MAHA-native retrieval 후보 템플릿입니다.",
                workerProfileIds = listOf(
                    DEFAULT_ORCHESTRATOR_ID,
                    DEFAULT_RAG_WORKER_ID,
                    DEFAULT_MAIN_WORKER_ID,
                    DEFAULT_SYNTHESIS_WORKER_ID,
                ),
                defaultExecutionMode = ExecutionMode.SEQUENTIAL,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                createdAt = createdAt,
            ),
            defaultScenario(
                scenarioId = DEFAULT_COMPARISON_SCENARIO_ID,
                name = "비교 / 병렬 분석",
                description = "Comparison Worker와 Synthesis Worker를 포함하는 병렬/혼합 실행 후보 템플릿입니다.",
                workerProfileIds = listOf(
                    DEFAULT_ORCHESTRATOR_ID,
                    DEFAULT_COMPARISON_WORKER_ID,
                    DEFAULT_SYNTHESIS_WORKER_ID,
                ),
                defaultExecutionMode = ExecutionMode.MIXED,
                orchestratorProfileId = DEFAULT_ORCHESTRATOR_ID,
                synthesisProfileId = DEFAULT_SYNTHESIS_WORKER_ID,
                createdAt = createdAt,
            ),
        )
    }

    fun validateWorkerProfileReferences(
        knownProviderIds: Set<String> = emptySet(),
        knownModelIds: Set<String> = emptySet(),
    ): List<String> {
        return findOrphanProviderReferences(knownProviderIds) + findOrphanModelReferences(knownModelIds)
    }

    fun validateScenarioReferences(
        knownWorkerProfileIds: Set<String> = loadWorkerProfiles().workerProfiles.map { it.workerProfileId }.toSet(),
    ): List<String> {
        if (knownWorkerProfileIds.isEmpty()) return emptyList()

        val issues = mutableListOf<String>()
        loadConversationScenarios().scenarios.forEach { scenario ->
            scenario.workerProfileIds
                .filterNot { workerProfileId -> workerProfileId in knownWorkerProfileIds }
                .forEach { missingWorkerId ->
                    issues += "Scenario '${scenario.scenarioId}' references missing WorkerProfile '$missingWorkerId'."
                }

            val orchestratorId = scenario.orchestratorProfileId
            if (orchestratorId != null && orchestratorId !in knownWorkerProfileIds) {
                issues += "Scenario '${scenario.scenarioId}' references missing orchestratorProfileId '$orchestratorId'."
            }

            val synthesisId = scenario.synthesisProfileId
            if (synthesisId != null && synthesisId !in knownWorkerProfileIds) {
                issues += "Scenario '${scenario.scenarioId}' references missing synthesisProfileId '$synthesisId'."
            }
        }
        return issues
    }

    fun findOrphanProviderReferences(knownProviderIds: Set<String> = emptySet()): List<String> {
        if (knownProviderIds.isEmpty()) return emptyList()

        return loadWorkerProfiles().workerProfiles
            .filter { profile -> profile.providerId != null && profile.providerId !in knownProviderIds }
            .map { profile ->
                "WorkerProfile '${profile.workerProfileId}' references missing providerId '${profile.providerId}'."
            }
    }

    fun findOrphanModelReferences(knownModelIds: Set<String> = emptySet()): List<String> {
        if (knownModelIds.isEmpty()) return emptyList()

        return loadWorkerProfiles().workerProfiles
            .filter { profile -> profile.modelId != null && profile.modelId !in knownModelIds }
            .map { profile ->
                "WorkerProfile '${profile.workerProfileId}' references missing modelId '${profile.modelId}'."
            }
    }

    private fun defaultWorker(
        workerProfileId: String,
        displayName: String,
        roleLabel: String,
        roleDescription: String,
        systemInstruction: String,
        executionOrder: Int,
        canRunInParallel: Boolean = false,
        overrides: WorkerCapabilityOverrides = WorkerCapabilityOverrides(),
        inputPolicy: WorkerInputPolicy = WorkerInputPolicy(),
        outputPolicy: WorkerOutputPolicy? = null,
        expectedOutputType: CapabilityType = CapabilityType.TEXT_GENERATION,
    ): ConversationWorkerProfile {
        val createdAt = now()
        return ConversationWorkerProfile(
            workerProfileId = workerProfileId,
            displayName = displayName,
            roleLabel = roleLabel,
            roleDescription = roleDescription,
            systemInstruction = systemInstruction,
            providerId = null,
            modelId = null,
            capabilityOverrides = overrides,
            inputPolicy = inputPolicy,
            outputPolicy = outputPolicy ?: WorkerOutputPolicy(expectedOutputType = expectedOutputType),
            executionOrder = executionOrder,
            canRunInParallel = canRunInParallel,
            dependsOnWorkerIds = emptyList(),
            enabled = true,
            isDefaultTemplate = true,
            userModified = false,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private fun defaultScenario(
        scenarioId: String,
        name: String,
        description: String,
        workerProfileIds: List<String>,
        defaultExecutionMode: ExecutionMode,
        orchestratorProfileId: String?,
        synthesisProfileId: String?,
        createdAt: Long,
    ): ConversationScenarioProfile {
        return ConversationScenarioProfile(
            scenarioId = scenarioId,
            name = name,
            description = description,
            workerProfileIds = workerProfileIds,
            defaultExecutionMode = defaultExecutionMode,
            orchestratorProfileId = orchestratorProfileId,
            synthesisProfileId = synthesisProfileId,
            userEditable = true,
            isDefaultTemplate = true,
            userModified = false,
            enabled = true,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

    private fun now(): Long = System.currentTimeMillis()
}
