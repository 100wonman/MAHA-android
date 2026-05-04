package com.maha.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * WorkerProfileStore persistence implementation for the variable Worker Profile system.
 *
 * Scope:
 * - Reads/writes worker_profiles.json and conversation_scenarios.json in the MAHA/settings folder.
 * - Keeps an in-memory cache after first load.
 * - Creates default Worker/Scenario envelopes when files do not exist.
 * - Avoids crashes on parse/save failure and preserves existing files where possible.
 *
 * Not connected to ConversationEngine, ConversationViewModel, Provider calls, RAG, Web Search,
 * SAF backup/restore, Worker execution, or editing flows.
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

    private var appContext: Context? = null
    private var workerEnvelopeCache: WorkerProfileStoreEnvelope? = null
    private var scenarioEnvelopeCache: ConversationScenarioStoreEnvelope? = null

    /**
     * Initializes file persistence. Existing no-arg APIs remain safe when this is not called;
     * they use in-memory fallback envelopes without touching disk.
     */
    fun initialize(context: Context) {
        val newContext = context.applicationContext
        if (appContext !== newContext) {
            appContext = newContext
            workerEnvelopeCache = null
            scenarioEnvelopeCache = null
        }
        ensureSettingsDirOnly()
        ensureWorkerProfilesFile()
        ensureConversationScenariosFile()
    }

    fun clearCache() {
        workerEnvelopeCache = null
        scenarioEnvelopeCache = null
    }

    fun loadWorkerProfiles(forceReload: Boolean = false): WorkerProfileStoreEnvelope {
        if (!forceReload) {
            workerEnvelopeCache?.let { return it }
        }

        val context = appContext
        if (context == null) {
            val fallback = workerEnvelopeCache ?: createDefaultWorkerEnvelope()
            workerEnvelopeCache = fallback
            return fallback
        }

        ensureSettingsDirOnly()
        val file = workerProfilesFile(context)
        if (!file.exists()) {
            val envelope = createDefaultWorkerEnvelope()
            writeWorkerEnvelopeSafely(file, envelope)
            workerEnvelopeCache = envelope
            return envelope
        }

        val envelope = runCatching {
            val raw = file.readText()
            if (raw.isBlank()) throw JSONException("worker_profiles.json is blank")
            JSONObject(raw).toWorkerProfileStoreEnvelope()
        }.getOrElse {
            // Preserve the existing broken file. Return safe defaults for app continuity.
            createDefaultWorkerEnvelope()
        }.repairWorkerEnvelopeDefaults()

        workerEnvelopeCache = envelope
        return envelope
    }

    fun saveWorkerProfiles(envelope: WorkerProfileStoreEnvelope) {
        val normalized = envelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
        )

        val context = appContext
        if (context == null) {
            workerEnvelopeCache = normalized
            return
        }

        ensureSettingsDirOnly()
        val file = workerProfilesFile(context)
        val saved = writeWorkerEnvelopeSafely(file, normalized)
        if (saved) {
            workerEnvelopeCache = normalized
        }
    }

    fun loadConversationScenarios(forceReload: Boolean = false): ConversationScenarioStoreEnvelope {
        if (!forceReload) {
            scenarioEnvelopeCache?.let { return it }
        }

        val context = appContext
        if (context == null) {
            val fallback = scenarioEnvelopeCache ?: createDefaultScenarioEnvelope()
            scenarioEnvelopeCache = fallback
            return fallback
        }

        ensureSettingsDirOnly()
        val file = conversationScenariosFile(context)
        if (!file.exists()) {
            val envelope = createDefaultScenarioEnvelope()
            writeScenarioEnvelopeSafely(file, envelope)
            scenarioEnvelopeCache = envelope
            return envelope
        }

        val envelope = runCatching {
            val raw = file.readText()
            if (raw.isBlank()) throw JSONException("conversation_scenarios.json is blank")
            JSONObject(raw).toConversationScenarioStoreEnvelope()
        }.getOrElse {
            // Preserve the existing broken file. Return safe defaults for app continuity.
            createDefaultScenarioEnvelope()
        }.repairScenarioEnvelopeDefaults()

        scenarioEnvelopeCache = envelope
        return envelope
    }

    fun saveConversationScenarios(envelope: ConversationScenarioStoreEnvelope) {
        val normalized = envelope.copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
        )

        val context = appContext
        if (context == null) {
            scenarioEnvelopeCache = normalized
            return
        }

        ensureSettingsDirOnly()
        val file = conversationScenariosFile(context)
        val saved = writeScenarioEnvelopeSafely(file, normalized)
        if (saved) {
            scenarioEnvelopeCache = normalized
        }
    }

    fun getWorkerProfile(workerProfileId: String): ConversationWorkerProfile? {
        return loadWorkerProfiles().workerProfiles.firstOrNull { profile ->
            profile.workerProfileId == workerProfileId
        }
    }

    fun upsertWorkerProfile(profile: ConversationWorkerProfile) {
        val currentEnvelope = loadWorkerProfiles()
        val timestamp = now()
        val normalizedProfile = profile.copy(
            createdAt = if (profile.createdAt == 0L) timestamp else profile.createdAt,
            updatedAt = timestamp,
        )
        val nextProfiles = currentEnvelope.workerProfiles
            .filterNot { it.workerProfileId == normalizedProfile.workerProfileId } + normalizedProfile

        saveWorkerProfiles(currentEnvelope.copy(workerProfiles = nextProfiles))
    }

    fun deleteWorkerProfile(workerProfileId: String) {
        val currentEnvelope = loadWorkerProfiles()
        val nextProfiles = currentEnvelope.workerProfiles.filterNot { profile ->
            profile.workerProfileId == workerProfileId
        }
        saveWorkerProfiles(currentEnvelope.copy(workerProfiles = nextProfiles))
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
        val currentEnvelope = loadConversationScenarios()
        val timestamp = now()
        val normalizedScenario = scenario.copy(
            createdAt = if (scenario.createdAt == 0L) timestamp else scenario.createdAt,
            updatedAt = timestamp,
        )
        val nextScenarios = currentEnvelope.scenarios
            .filterNot { it.scenarioId == normalizedScenario.scenarioId } + normalizedScenario

        saveConversationScenarios(currentEnvelope.copy(scenarios = nextScenarios))
    }

    fun deleteScenario(scenarioId: String) {
        val currentEnvelope = loadConversationScenarios()
        val nextScenarios = currentEnvelope.scenarios.filterNot { scenario ->
            scenario.scenarioId == scenarioId
        }
        saveConversationScenarios(currentEnvelope.copy(scenarios = nextScenarios))
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

        val nextEnvelope = currentEnvelope.copy(workerProfiles = currentProfiles + missingTemplates)
        saveWorkerProfiles(nextEnvelope)
        return loadWorkerProfiles(forceReload = true)
    }

    fun ensureDefaultScenarios(): ConversationScenarioStoreEnvelope {
        val currentEnvelope = loadConversationScenarios()
        val currentScenarios = currentEnvelope.scenarios
        val currentIds = currentScenarios.map { it.scenarioId }.toSet()
        val missingScenarios = createDefaultConversationScenarios().filterNot { scenario ->
            scenario.scenarioId in currentIds
        }

        if (missingScenarios.isEmpty()) return currentEnvelope

        val nextEnvelope = currentEnvelope.copy(scenarios = currentScenarios + missingScenarios)
        saveConversationScenarios(nextEnvelope)
        return loadConversationScenarios(forceReload = true)
    }

    fun createDefaultWorkerTemplates(): List<ConversationWorkerProfile> {
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
        val issues = mutableListOf<String>()
        issues += findOrphanProviderReferences(knownProviderIds)
        issues += findOrphanModelReferences(knownModelIds)
        issues += findOrphanWorkerDependencyReferences()
        return issues
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

    fun findOrphanWorkerDependencyReferences(): List<String> {
        val workers = loadWorkerProfiles().workerProfiles
        val knownWorkerIds = workers.map { it.workerProfileId }.toSet()
        return workers.flatMap { profile ->
            profile.dependsOnWorkerIds
                .filterNot { dependencyId -> dependencyId in knownWorkerIds }
                .map { missingId ->
                    "WorkerProfile '${profile.workerProfileId}' references missing dependsOnWorkerId '$missingId'."
                }
        }
    }

    private fun createDefaultWorkerEnvelope(): WorkerProfileStoreEnvelope {
        return WorkerProfileStoreEnvelope(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
            workerProfiles = createDefaultWorkerTemplates(),
        )
    }

    private fun createDefaultScenarioEnvelope(): ConversationScenarioStoreEnvelope {
        return ConversationScenarioStoreEnvelope(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = now(),
            scenarios = createDefaultConversationScenarios(),
        )
    }

    private fun WorkerProfileStoreEnvelope.repairWorkerEnvelopeDefaults(): WorkerProfileStoreEnvelope {
        val repairedProfiles = workerProfiles
            .filter { it.workerProfileId.isNotBlank() }
            .distinctBy { it.workerProfileId }

        return copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = if (updatedAt == 0L) now() else updatedAt,
            workerProfiles = repairedProfiles.ifEmpty { createDefaultWorkerTemplates() },
        )
    }

    private fun ConversationScenarioStoreEnvelope.repairScenarioEnvelopeDefaults(): ConversationScenarioStoreEnvelope {
        val repairedScenarios = scenarios
            .filter { it.scenarioId.isNotBlank() }
            .distinctBy { it.scenarioId }

        return copy(
            schemaVersion = SCHEMA_VERSION,
            updatedAt = if (updatedAt == 0L) now() else updatedAt,
            scenarios = repairedScenarios.ifEmpty { createDefaultConversationScenarios() },
        )
    }

    private fun settingsDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(File(baseDir, "MAHA"), "settings")
    }

    private fun workerProfilesFile(context: Context): File {
        return File(settingsDir(context), WORKER_PROFILES_FILE_NAME)
    }

    private fun conversationScenariosFile(context: Context): File {
        return File(settingsDir(context), CONVERSATION_SCENARIOS_FILE_NAME)
    }

    private fun ensureSettingsDirOnly() {
        val context = appContext ?: return
        val dir = settingsDir(context)
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun ensureWorkerProfilesFile() {
        val context = appContext ?: return
        val file = workerProfilesFile(context)
        if (!file.exists()) {
            writeWorkerEnvelopeSafely(file, createDefaultWorkerEnvelope())
        }
    }

    private fun ensureConversationScenariosFile() {
        val context = appContext ?: return
        val file = conversationScenariosFile(context)
        if (!file.exists()) {
            writeScenarioEnvelopeSafely(file, createDefaultScenarioEnvelope())
        }
    }

    private fun writeWorkerEnvelopeSafely(file: File, envelope: WorkerProfileStoreEnvelope): Boolean {
        return runCatching {
            writeTextAtomic(file, envelope.toJsonObject().toString(2))
            true
        }.getOrElse { false }
    }

    private fun writeScenarioEnvelopeSafely(file: File, envelope: ConversationScenarioStoreEnvelope): Boolean {
        return runCatching {
            writeTextAtomic(file, envelope.toJsonObject().toString(2))
            true
        }.getOrElse { false }
    }

    private fun writeTextAtomic(target: File, text: String) {
        val parent = target.parentFile ?: return
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val temp = File(parent, "${target.name}.tmp")
        temp.writeText(text)
        if (target.exists()) {
            target.delete()
        }
        temp.renameTo(target)
    }

    private fun JSONObject.toWorkerProfileStoreEnvelope(): WorkerProfileStoreEnvelope {
        val array = optJSONArray("workerProfiles") ?: JSONArray()
        val profiles = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toConversationWorkerProfile())
            }
        }
        return WorkerProfileStoreEnvelope(
            schemaVersion = optInt("schemaVersion", SCHEMA_VERSION),
            updatedAt = optLong("updatedAt", now()),
            workerProfiles = profiles,
        )
    }

    private fun WorkerProfileStoreEnvelope.toJsonObject(): JSONObject {
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("updatedAt", updatedAt.ifZero { now() })
            .put("workerProfiles", workerProfiles.toJsonArray { it.toJsonObject() })
    }

    private fun JSONObject.toConversationWorkerProfile(): ConversationWorkerProfile {
        val timestamp = now()
        return ConversationWorkerProfile(
            workerProfileId = optString("workerProfileId"),
            displayName = optString("displayName"),
            roleLabel = optString("roleLabel"),
            roleDescription = optString("roleDescription"),
            systemInstruction = optString("systemInstruction"),
            providerId = optNullableString("providerId"),
            modelId = optNullableString("modelId"),
            capabilityOverrides = optJSONObject("capabilityOverrides")?.toWorkerCapabilityOverrides()
                ?: WorkerCapabilityOverrides(),
            inputPolicy = optJSONObject("inputPolicy")?.toWorkerInputPolicy() ?: WorkerInputPolicy(),
            outputPolicy = optJSONObject("outputPolicy")?.toWorkerOutputPolicy() ?: WorkerOutputPolicy(),
            executionOrder = optInt("executionOrder", 0),
            canRunInParallel = optBoolean("canRunInParallel", false),
            dependsOnWorkerIds = optStringList("dependsOnWorkerIds"),
            enabled = optBoolean("enabled", true),
            isDefaultTemplate = optBoolean("isDefaultTemplate", false),
            userModified = optBoolean("userModified", false),
            createdAt = optLong("createdAt", timestamp),
            updatedAt = optLong("updatedAt", timestamp),
        )
    }

    private fun ConversationWorkerProfile.toJsonObject(): JSONObject {
        return JSONObject()
            .put("workerProfileId", workerProfileId)
            .put("displayName", displayName)
            .put("roleLabel", roleLabel)
            .put("roleDescription", roleDescription)
            .put("systemInstruction", systemInstruction)
            .put("providerId", providerId)
            .put("modelId", modelId)
            .put("capabilityOverrides", capabilityOverrides.toJsonObject())
            .put("inputPolicy", inputPolicy.toJsonObject())
            .put("outputPolicy", outputPolicy.toJsonObject())
            .put("executionOrder", executionOrder)
            .put("canRunInParallel", canRunInParallel)
            .put("dependsOnWorkerIds", dependsOnWorkerIds.toJsonArray())
            .put("enabled", enabled)
            .put("isDefaultTemplate", isDefaultTemplate)
            .put("userModified", userModified)
            .put("createdAt", createdAt.ifZero { now() })
            .put("updatedAt", updatedAt.ifZero { now() })
    }

    private fun JSONObject.toConversationScenarioStoreEnvelope(): ConversationScenarioStoreEnvelope {
        val array = optJSONArray("scenarios") ?: JSONArray()
        val scenarios = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toConversationScenarioProfile())
            }
        }
        return ConversationScenarioStoreEnvelope(
            schemaVersion = optInt("schemaVersion", SCHEMA_VERSION),
            updatedAt = optLong("updatedAt", now()),
            scenarios = scenarios,
        )
    }

    private fun ConversationScenarioStoreEnvelope.toJsonObject(): JSONObject {
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("updatedAt", updatedAt.ifZero { now() })
            .put("scenarios", scenarios.toJsonArray { it.toJsonObject() })
    }

    private fun JSONObject.toConversationScenarioProfile(): ConversationScenarioProfile {
        val timestamp = now()
        return ConversationScenarioProfile(
            scenarioId = optString("scenarioId"),
            name = optString("name"),
            description = optString("description"),
            workerProfileIds = optStringList("workerProfileIds"),
            defaultExecutionMode = optEnum("defaultExecutionMode", ExecutionMode.SINGLE),
            orchestratorProfileId = optNullableString("orchestratorProfileId"),
            synthesisProfileId = optNullableString("synthesisProfileId"),
            userEditable = optBoolean("userEditable", true),
            isDefaultTemplate = optBoolean("isDefaultTemplate", false),
            userModified = optBoolean("userModified", false),
            enabled = optBoolean("enabled", true),
            createdAt = optLong("createdAt", timestamp),
            updatedAt = optLong("updatedAt", timestamp),
        )
    }

    private fun ConversationScenarioProfile.toJsonObject(): JSONObject {
        return JSONObject()
            .put("scenarioId", scenarioId)
            .put("name", name)
            .put("description", description)
            .put("workerProfileIds", workerProfileIds.toJsonArray())
            .put("defaultExecutionMode", defaultExecutionMode.name)
            .put("orchestratorProfileId", orchestratorProfileId)
            .put("synthesisProfileId", synthesisProfileId)
            .put("userEditable", userEditable)
            .put("isDefaultTemplate", isDefaultTemplate)
            .put("userModified", userModified)
            .put("enabled", enabled)
            .put("createdAt", createdAt.ifZero { now() })
            .put("updatedAt", updatedAt.ifZero { now() })
    }

    private fun JSONObject.toWorkerCapabilityOverrides(): WorkerCapabilityOverrides {
        return WorkerCapabilityOverrides(
            functionCalling = optEnum("functionCalling", CapabilityLayerStatus.UNKNOWN),
            webSearch = optEnum("webSearch", CapabilityLayerStatus.UNKNOWN),
            codeExecution = optEnum("codeExecution", CapabilityLayerStatus.UNKNOWN),
            structuredOutput = optEnum("structuredOutput", CapabilityLayerStatus.UNKNOWN),
            thinkingSummary = optEnum("thinkingSummary", CapabilityLayerStatus.UNKNOWN),
            ragSearch = optEnum("ragSearch", CapabilityLayerStatus.UNKNOWN),
            memoryRecall = optEnum("memoryRecall", CapabilityLayerStatus.UNKNOWN),
            fileRead = optEnum("fileRead", CapabilityLayerStatus.UNKNOWN),
            fileWrite = optEnum("fileWrite", CapabilityLayerStatus.UNKNOWN),
            codeCheck = optEnum("codeCheck", CapabilityLayerStatus.UNKNOWN),
            parallelExecution = optEnum("parallelExecution", CapabilityLayerStatus.UNKNOWN),
        )
    }

    private fun WorkerCapabilityOverrides.toJsonObject(): JSONObject {
        return JSONObject()
            .put("functionCalling", functionCalling.name)
            .put("webSearch", webSearch.name)
            .put("codeExecution", codeExecution.name)
            .put("structuredOutput", structuredOutput.name)
            .put("thinkingSummary", thinkingSummary.name)
            .put("ragSearch", ragSearch.name)
            .put("memoryRecall", memoryRecall.name)
            .put("fileRead", fileRead.name)
            .put("fileWrite", fileWrite.name)
            .put("codeCheck", codeCheck.name)
            .put("parallelExecution", parallelExecution.name)
    }

    private fun JSONObject.toWorkerInputPolicy(): WorkerInputPolicy {
        return WorkerInputPolicy(
            userInputOnly = optBoolean("userInputOnly", true),
            previousWorkerOutput = optBoolean("previousWorkerOutput", false),
            selectedWorkerOutputs = optStringList("selectedWorkerOutputs"),
            ragContextAllowed = optBoolean("ragContextAllowed", false),
            memoryContextAllowed = optBoolean("memoryContextAllowed", false),
            webSearchContextAllowed = optBoolean("webSearchContextAllowed", false),
            maxInputChars = optInt("maxInputChars", 0),
            includeRunHistory = optBoolean("includeRunHistory", false),
        )
    }

    private fun WorkerInputPolicy.toJsonObject(): JSONObject {
        return JSONObject()
            .put("userInputOnly", userInputOnly)
            .put("previousWorkerOutput", previousWorkerOutput)
            .put("selectedWorkerOutputs", selectedWorkerOutputs.toJsonArray())
            .put("ragContextAllowed", ragContextAllowed)
            .put("memoryContextAllowed", memoryContextAllowed)
            .put("webSearchContextAllowed", webSearchContextAllowed)
            .put("maxInputChars", maxInputChars)
            .put("includeRunHistory", includeRunHistory)
    }

    private fun JSONObject.toWorkerOutputPolicy(): WorkerOutputPolicy {
        return WorkerOutputPolicy(
            expectedOutputType = optEnum("expectedOutputType", CapabilityType.TEXT_GENERATION),
            requireJson = optBoolean("requireJson", false),
            requireMarkdownTable = optBoolean("requireMarkdownTable", false),
            requireCodeBlock = optBoolean("requireCodeBlock", false),
            allowPlainText = optBoolean("allowPlainText", true),
            maxOutputChars = optInt("maxOutputChars", 0),
            passToNextWorker = optBoolean("passToNextWorker", true),
            exposeToUser = optBoolean("exposeToUser", true),
            saveAsMemoryCandidate = optBoolean("saveAsMemoryCandidate", false),
        )
    }

    private fun WorkerOutputPolicy.toJsonObject(): JSONObject {
        return JSONObject()
            .put("expectedOutputType", expectedOutputType.name)
            .put("requireJson", requireJson)
            .put("requireMarkdownTable", requireMarkdownTable)
            .put("requireCodeBlock", requireCodeBlock)
            .put("allowPlainText", allowPlainText)
            .put("maxOutputChars", maxOutputChars)
            .put("passToNextWorker", passToNextWorker)
            .put("exposeToUser", exposeToUser)
            .put("saveAsMemoryCandidate", saveAsMemoryCandidate)
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

    private fun JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        return optJSONArray(key)?.toStringList() ?: emptyList()
    }

    private fun List<String>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { value -> array.put(value) }
        return array
    }

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { item -> array.put(mapper(item)) }
        return array
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T {
        val rawValue = optString(key).takeIf { it.isNotBlank() } ?: return fallback
        return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(fallback)
    }

    private fun Long.ifZero(defaultValue: () -> Long): Long {
        return if (this == 0L) defaultValue() else this
    }

    private fun now(): Long = System.currentTimeMillis()
}
