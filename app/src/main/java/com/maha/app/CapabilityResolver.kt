package com.maha.app

/**
 * CapabilityResolver
 *
 * Orchestrator / Capability Layer 후속 구현을 위한 진단용 skeleton입니다.
 *
 * 현재 단계의 원칙:
 * - ConversationEngine / ViewModel / UI 실행 흐름 / 저장 schema와 연결하지 않습니다.
 * - 실제 Provider / RAG / Web Search / Tool 실행 상태를 조회하지 않습니다.
 * - 사용자 입력을 keyword 기반으로 분석해 capability 요구와 계획 preview만 만듭니다.
 * - 기존 ModelCapability V2의 CapabilityStatus가 아니라 CapabilityLayerStatus를 사용합니다.
 */
object CapabilityResolver {

    fun inferRequirements(userInput: String): List<CapabilityRequirement> {
        val lower = userInput.trim().lowercase()
        val requirements = mutableListOf<CapabilityRequirement>()

        fun add(
            capabilityType: CapabilityType,
            required: Boolean = true,
            priority: Int = 0,
            reason: String = "",
            acceptableFallbacks: List<CapabilityType> = emptyList(),
            label: String = capabilityType.name
        ) {
            if (requirements.none { it.capabilityType == capabilityType }) {
                requirements.add(
                    CapabilityRequirement(
                        capabilityType = capabilityType,
                        required = required,
                        priority = priority,
                        reason = reason,
                        acceptableFallbacks = acceptableFallbacks,
                        userVisibleLabel = label
                    )
                )
            }
        }

        val wantsJson = containsAny(lower, listOf("json", "제이슨"))
        val wantsTable = containsAny(lower, listOf("마크다운 표", "markdown table", "비교표", "표로", "표 ", " table")) || lower == "표"
        val wantsCodeBlock = containsAny(lower, listOf("코드블록", "코드 블록", "code block", "```"))
        val wantsSearch = containsAny(lower, listOf("검색", "최신", "오늘", "뉴스", "날씨", "현재", "실시간", "웹 검색", "web search"))
        val wantsRag = containsAny(lower, listOf("rag", "내 문서", "자료 기반", "참조 문서", "저장된 문서", "인덱스"))
        val wantsMemory = containsAny(lower, listOf("기억", "이전 대화", "전에 말한", "지난번", "대화 이력"))
        val wantsFileWrite = containsAny(lower, listOf("파일 수정", "파일 저장", "저장해", "수정해", "작성해서 저장"))
        val wantsFileRead = containsAny(lower, listOf("파일", "저장소", "문서 읽", "읽어", "열어"))
        val wantsCodeCheck = containsAny(lower, listOf("검증", "빌드 오류", "컴파일", "코드 확인", "코드 검토", "테스트해", "수정안"))
        val wantsTool = containsAny(lower, listOf("도구", "tool", "function_call", "function call", "tool_call", "tool calls", "함수 호출"))
        val wantsComparison = containsAny(lower, listOf("비교", "여러 관점", "세 가지 관점", "각각 분석", "동시에", "병렬", "여러 모델", "여러 자료"))
        val wantsReview = containsAny(lower, listOf("검토", "리뷰", "최종 확인", "누락 확인"))

        add(
            capabilityType = CapabilityType.TEXT_GENERATION,
            priority = 0,
            reason = "기본 답변 생성을 위해 필요합니다.",
            label = "일반 답변 생성"
        )

        if (wantsJson) {
            add(
                capabilityType = CapabilityType.JSON_OUTPUT,
                priority = 20,
                reason = "사용자가 JSON 형식 출력을 요청했습니다.",
                label = "JSON 출력"
            )
            add(
                capabilityType = CapabilityType.STRUCTURED_OUTPUT,
                required = false,
                priority = 12,
                reason = "JSON 출력은 구조화된 응답 형식에 해당합니다.",
                label = "구조화 출력"
            )
        }

        if (wantsTable) {
            add(
                capabilityType = CapabilityType.TABLE_OUTPUT,
                priority = 20,
                reason = "사용자가 표 형식 출력을 요청했습니다.",
                label = "테이블 출력"
            )
            add(
                capabilityType = CapabilityType.STRUCTURED_OUTPUT,
                required = false,
                priority = 12,
                reason = "표 출력은 구조화된 응답 형식에 해당합니다.",
                label = "구조화 출력"
            )
        }

        if (wantsCodeBlock || wantsCodeCheck) {
            add(
                capabilityType = CapabilityType.CODE_BLOCK_OUTPUT,
                priority = if (wantsCodeCheck) 18 else 20,
                reason = if (wantsCodeCheck) {
                    "코드 검증 또는 수정 결과를 코드블록으로 보여줄 수 있습니다."
                } else {
                    "사용자가 코드블록 형식 출력을 요청했습니다."
                },
                label = "코드블록 출력"
            )
        }

        if (wantsSearch) {
            add(
                capabilityType = CapabilityType.WEB_SEARCH_PROVIDER_NATIVE,
                priority = 30,
                reason = "날씨, 뉴스, 최신 정보처럼 외부 정보 확인이 필요할 수 있습니다.",
                acceptableFallbacks = listOf(CapabilityType.WEB_SEARCH_MAHA_NATIVE),
                label = "Provider Web Search"
            )
            add(
                capabilityType = CapabilityType.WEB_SEARCH_MAHA_NATIVE,
                required = false,
                priority = 25,
                reason = "Provider-native 검색이 없을 때 MAHA 내부 검색 도구가 대체 후보가 될 수 있습니다.",
                label = "MAHA Web Search"
            )
        }

        if (wantsRag) {
            add(
                capabilityType = CapabilityType.RAG_SEARCH,
                priority = 26,
                reason = "앱 내부 문서 또는 RAG 인덱스 기반 검색이 필요할 수 있습니다.",
                label = "RAG 검색"
            )
        }

        if (wantsMemory) {
            add(
                capabilityType = CapabilityType.MEMORY_RECALL,
                priority = 24,
                reason = "장기 기억 또는 이전 대화 내용을 참조해야 할 수 있습니다.",
                acceptableFallbacks = listOf(CapabilityType.CONVERSATION_HISTORY_SEARCH),
                label = "기억 호출"
            )
            add(
                capabilityType = CapabilityType.CONVERSATION_HISTORY_SEARCH,
                required = false,
                priority = 18,
                reason = "장기 기억이 없을 때 대화 이력 검색이 대체 후보가 될 수 있습니다.",
                label = "대화 이력 검색"
            )
        }

        if (wantsFileRead) {
            add(
                capabilityType = CapabilityType.LOCAL_FILE_READ,
                priority = 24,
                reason = "로컬 파일 또는 저장소의 내용을 읽어야 할 수 있습니다.",
                label = "로컬 파일 읽기"
            )
        }

        if (wantsFileWrite) {
            add(
                capabilityType = CapabilityType.LOCAL_FILE_WRITE,
                priority = 28,
                reason = "로컬 파일을 작성하거나 수정해야 할 수 있습니다.",
                label = "로컬 파일 쓰기"
            )
        }

        if (wantsCodeCheck) {
            add(
                capabilityType = CapabilityType.CODE_CHECK,
                priority = 30,
                reason = "코드 검증 또는 오류 분석 단계가 필요할 수 있습니다.",
                label = "코드 검증"
            )
            add(
                capabilityType = CapabilityType.SEQUENTIAL_EXECUTION,
                required = false,
                priority = 16,
                reason = "코드 분석 후 수정안 작성처럼 단계적 처리가 어울립니다.",
                label = "순차 실행"
            )
        }

        if (wantsTool) {
            add(
                capabilityType = CapabilityType.TOOL_CALL_DETECTION,
                priority = 24,
                reason = "tool_call 또는 function_call 형식 감지가 필요할 수 있습니다.",
                label = "Tool 요청 감지"
            )
            add(
                capabilityType = CapabilityType.TOOL_EXECUTION,
                required = false,
                priority = 24,
                reason = "실제 Tool 실행은 Tool Registry 구현 이후의 후속 기능입니다.",
                label = "Tool 실행"
            )
        }

        if (wantsComparison) {
            add(
                capabilityType = CapabilityType.PARALLEL_EXECUTION,
                required = false,
                priority = 28,
                reason = "여러 관점 또는 여러 자료를 독립적으로 나눠 볼 수 있습니다.",
                label = "병렬 실행 후보"
            )
            add(
                capabilityType = CapabilityType.SYNTHESIS,
                required = false,
                priority = 26,
                reason = "나뉜 결과를 하나로 통합하는 단계가 필요할 수 있습니다.",
                label = "결과 통합"
            )
        }

        if (wantsReview) {
            add(
                capabilityType = CapabilityType.REVIEW,
                required = false,
                priority = 18,
                reason = "최종 결과 검토가 필요할 수 있습니다.",
                label = "검토"
            )
        }

        return requirements.sortedWith(
            compareByDescending<CapabilityRequirement> { it.priority }
                .thenBy { it.capabilityType.name }
        )
    }

    fun resolveRequirements(
        requirements: List<CapabilityRequirement>,
        selectedProviderId: String? = null,
        selectedModelId: String? = null
    ): List<CapabilityResolution> {
        return requirements.map { requirement ->
            val status = defaultStatusFor(requirement.capabilityType)
            val source = defaultSourceFor(requirement.capabilityType)
            val executionAvailable = when (status) {
                CapabilityLayerStatus.AVAILABLE,
                CapabilityLayerStatus.LIMITED,
                CapabilityLayerStatus.MAHA_NATIVE_AVAILABLE,
                CapabilityLayerStatus.PROVIDER_NATIVE_ONLY -> true
                else -> false
            }

            CapabilityResolution(
                requirement = requirement,
                status = status,
                source = source,
                providerNativeAvailable = source == CapabilitySource.PROVIDER_NATIVE && status != CapabilityLayerStatus.NOT_IMPLEMENTED,
                mahaNativeAvailable = source == CapabilitySource.MAHA_NATIVE && status != CapabilityLayerStatus.NOT_IMPLEMENTED,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                executionAvailable = executionAvailable,
                limitationReason = buildLimitationReasonIfNeeded(
                    requirement = requirement,
                    status = status,
                    source = source,
                    selectedProviderId = selectedProviderId,
                    selectedModelId = selectedModelId
                )
            )
        }
    }

    fun recommendExecutionMode(requirements: List<CapabilityRequirement>): ExecutionMode {
        val types = requirements.map { it.capabilityType }.toSet()

        if (CapabilityType.PARALLEL_EXECUTION in types && CapabilityType.SYNTHESIS in types) {
            return ExecutionMode.MIXED
        }
        if (CapabilityType.PARALLEL_EXECUTION in types) {
            return ExecutionMode.PARALLEL
        }
        if (CapabilityType.CODE_CHECK in types || CapabilityType.SEQUENTIAL_EXECUTION in types) {
            return ExecutionMode.SEQUENTIAL
        }
        if (CapabilityType.RAG_SEARCH in types ||
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE in types ||
            CapabilityType.WEB_SEARCH_MAHA_NATIVE in types ||
            CapabilityType.MEMORY_RECALL in types ||
            CapabilityType.CONVERSATION_HISTORY_SEARCH in types ||
            CapabilityType.LOCAL_FILE_READ in types ||
            CapabilityType.LOCAL_FILE_WRITE in types
        ) {
            return ExecutionMode.SEQUENTIAL
        }
        return ExecutionMode.SINGLE
    }

    fun buildPlan(
        userInput: String,
        requestId: String = "request_${System.currentTimeMillis()}",
        selectedProviderId: String? = null,
        selectedModelId: String? = null
    ): OrchestratorPlan {
        val requirements = inferRequirements(userInput)
        val resolutions = resolveRequirements(
            requirements = requirements,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId
        )
        val executionMode = recommendExecutionMode(requirements)
        val limitationReasons = resolutions.mapNotNull { it.limitationReason }

        return OrchestratorPlan(
            planId = "plan_${System.currentTimeMillis()}",
            requestId = requestId,
            userInputPreview = userInput.trim().take(160),
            requestedCapabilities = requirements,
            resolvedCapabilities = resolutions,
            executionMode = executionMode,
            userGoalStatus = inferUserGoalStatus(resolutions),
            limitationReasons = limitationReasons,
            workerPlans = buildWorkerPlans(
                requirements = requirements,
                resolutions = resolutions,
                executionMode = executionMode,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId
            ),
            providerNativeUsed = resolutions.any { it.source == CapabilitySource.PROVIDER_NATIVE },
            mahaNativeUsed = resolutions.any { it.source == CapabilitySource.MAHA_NATIVE },
            createdAt = System.currentTimeMillis()
        )
    }


    fun buildPlanForScenario(
        userInput: String,
        scenario: ConversationScenarioProfile,
        workerProfiles: List<ConversationWorkerProfile>,
        requestId: String = "scenario_preview_${System.currentTimeMillis()}"
    ): OrchestratorPlan {
        val requirements = inferRequirements(userInput)
        val resolutions = resolveRequirements(requirements = requirements)
        val userInputExecutionMode = recommendExecutionMode(requirements)
        val workersById = workerProfiles.associateBy { it.workerProfileId }
        val orderedScenarioWorkerIds = scenario.workerProfileIds
        val orderedScenarioWorkers = orderedScenarioWorkerIds.mapNotNull { workersById[it] }
        val scenarioLimitations = mutableListOf<LimitationReason>()

        scenarioLimitations.add(
            previewOnlyLimitation(
                technicalMessage = "buildPlanForScenario preview only. actualProviderCall=false; workerExecution=false; previewOnly=true; ragExecuted=false; webSearchExecuted=false; toolExecuted=false; scenarioId=${scenario.scenarioId}; scenarioDefaultExecutionMode=${scenario.defaultExecutionMode.name}; userInputRecommendedExecutionMode=${userInputExecutionMode.name}"
            )
        )

        if (!scenario.enabled) {
            scenarioLimitations.add(
                scenarioLimitation(
                    reasonCode = "SCENARIO_DISABLED",
                    userMessage = "선택한 Scenario가 비활성 상태입니다.",
                    technicalMessage = "scenarioId=${scenario.scenarioId}",
                    recoverable = true
                )
            )
        }

        if (orderedScenarioWorkerIds.isEmpty()) {
            scenarioLimitations.add(
                scenarioLimitation(
                    reasonCode = "SCENARIO_HAS_NO_WORKERS",
                    userMessage = "Scenario에 포함된 Worker가 없습니다.",
                    technicalMessage = "scenarioId=${scenario.scenarioId}",
                    recoverable = true
                )
            )
        }

        orderedScenarioWorkerIds.forEach { workerId ->
            if (!workersById.containsKey(workerId)) {
                scenarioLimitations.add(
                    workerLimitation(
                        reasonCode = "WORKER_PROFILE_NOT_FOUND",
                        userMessage = "Scenario가 참조하는 Worker를 찾을 수 없습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; missingWorkerProfileId=$workerId",
                        recoverable = true
                    )
                )
            }
        }

        orderedScenarioWorkers.forEach { worker ->
            scenarioLimitations.addAll(profileLimitationsFor(worker))
        }

        scenario.orchestratorProfileId?.let { orchestratorId ->
            if (!workersById.containsKey(orchestratorId)) {
                scenarioLimitations.add(
                    workerLimitation(
                        reasonCode = "ORCHESTRATOR_WORKER_NOT_FOUND",
                        userMessage = "Scenario가 지정한 Orchestrator Worker를 찾을 수 없습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; orchestratorProfileId=$orchestratorId",
                        recoverable = true
                    )
                )
            } else if (orchestratorId !in orderedScenarioWorkerIds) {
                scenarioLimitations.add(
                    workerLimitation(
                        reasonCode = "ORCHESTRATOR_NOT_IN_SCENARIO_WORKER_SET",
                        userMessage = "Orchestrator로 지정된 Worker가 현재 Scenario WorkerSet에 포함되어 있지 않습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; orchestratorProfileId=$orchestratorId",
                        recoverable = true
                    )
                )
            }
        }

        scenario.synthesisProfileId?.let { synthesisId ->
            if (!workersById.containsKey(synthesisId)) {
                scenarioLimitations.add(
                    workerLimitation(
                        reasonCode = "SYNTHESIS_WORKER_NOT_FOUND",
                        userMessage = "Scenario가 지정한 Synthesis Worker를 찾을 수 없습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; synthesisProfileId=$synthesisId",
                        recoverable = true
                    )
                )
            } else if (synthesisId !in orderedScenarioWorkerIds) {
                scenarioLimitations.add(
                    workerLimitation(
                        reasonCode = "SYNTHESIS_NOT_IN_SCENARIO_WORKER_SET",
                        userMessage = "Synthesis로 지정된 Worker가 현재 Scenario WorkerSet에 포함되어 있지 않습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; synthesisProfileId=$synthesisId",
                        recoverable = true
                    )
                )
            }
        }

        val scenarioWorkerPlans = buildScenarioWorkerPlans(
            scenario = scenario,
            orderedScenarioWorkerIds = orderedScenarioWorkerIds,
            workersById = workersById,
            requirements = requirements
        )

        val executionMode = recommendScenarioPreviewExecutionMode(
            scenario = scenario,
            requirements = requirements,
            userInputExecutionMode = userInputExecutionMode,
            scenarioWorkers = orderedScenarioWorkers
        )

        val capabilityLimitations = resolutions.mapNotNull { it.limitationReason }
        val allLimitations = (scenarioLimitations + capabilityLimitations).dedupeLimitations()
        val planStatus = inferScenarioPreviewGoalStatus(
            scenario = scenario,
            scenarioWorkerPlans = scenarioWorkerPlans,
            limitations = allLimitations
        )

        return OrchestratorPlan(
            planId = "scenario_plan_${System.currentTimeMillis()}",
            requestId = requestId,
            userInputPreview = userInput.trim().take(160),
            requestedCapabilities = requirements,
            resolvedCapabilities = resolutions,
            executionMode = executionMode,
            userGoalStatus = planStatus,
            limitationReasons = allLimitations,
            workerPlans = scenarioWorkerPlans,
            providerNativeUsed = resolutions.any { it.source == CapabilitySource.PROVIDER_NATIVE },
            mahaNativeUsed = resolutions.any { it.source == CapabilitySource.MAHA_NATIVE },
            createdAt = System.currentTimeMillis()
        )
    }

    private fun buildWorkerPlans(
        requirements: List<CapabilityRequirement>,
        resolutions: List<CapabilityResolution>,
        executionMode: ExecutionMode,
        selectedProviderId: String?,
        selectedModelId: String?
    ): List<WorkerPlan> {
        val types = requirements.map { it.capabilityType }.toSet()
        val workers = mutableListOf<WorkerPlan>()

        workers.add(
            WorkerPlan(
                workerId = "worker_orchestrator",
                workerRole = WorkerRole.ORCHESTRATOR,
                displayName = "Orchestrator",
                inputSource = "USER_INPUT",
                requiredCapabilities = listOf(CapabilityType.TEXT_GENERATION),
                assignedProviderId = selectedProviderId,
                assignedModelId = selectedModelId,
                executionOrder = 0,
                canRunInParallel = false,
                dependsOnWorkerIds = emptyList(),
                expectedOutputType = CapabilityType.TEXT_GENERATION,
                plannedStatus = UserGoalStatus.SUCCESS
            )
        )

        var order = 1
        val contextWorkerIds = mutableListOf<String>()

        if (CapabilityType.RAG_SEARCH in types) {
            val workerId = "worker_rag"
            contextWorkerIds.add(workerId)
            workers.add(
                plannedWorker(
                    workerId = workerId,
                    role = WorkerRole.RAG,
                    displayName = "RAG Worker",
                    requiredCapabilities = listOf(CapabilityType.RAG_SEARCH),
                    order = order++,
                    canRunInParallel = executionMode == ExecutionMode.PARALLEL || executionMode == ExecutionMode.MIXED,
                    limitationReason = limitationFor(resolutions, CapabilityType.RAG_SEARCH)
                )
            )
        }

        if (CapabilityType.MEMORY_RECALL in types || CapabilityType.CONVERSATION_HISTORY_SEARCH in types) {
            val workerId = "worker_memory"
            contextWorkerIds.add(workerId)
            workers.add(
                plannedWorker(
                    workerId = workerId,
                    role = WorkerRole.MEMORY,
                    displayName = "Memory Worker",
                    requiredCapabilities = listOf(CapabilityType.MEMORY_RECALL, CapabilityType.CONVERSATION_HISTORY_SEARCH),
                    order = order++,
                    canRunInParallel = executionMode == ExecutionMode.PARALLEL || executionMode == ExecutionMode.MIXED,
                    limitationReason = limitationFor(resolutions, CapabilityType.MEMORY_RECALL)
                        ?: limitationFor(resolutions, CapabilityType.CONVERSATION_HISTORY_SEARCH)
                )
            )
        }

        if (CapabilityType.WEB_SEARCH_PROVIDER_NATIVE in types || CapabilityType.WEB_SEARCH_MAHA_NATIVE in types) {
            val workerId = "worker_web_search"
            contextWorkerIds.add(workerId)
            workers.add(
                plannedWorker(
                    workerId = workerId,
                    role = WorkerRole.WEB_SEARCH,
                    displayName = "Web Search Worker",
                    requiredCapabilities = listOf(CapabilityType.WEB_SEARCH_PROVIDER_NATIVE, CapabilityType.WEB_SEARCH_MAHA_NATIVE),
                    order = order++,
                    canRunInParallel = executionMode == ExecutionMode.PARALLEL || executionMode == ExecutionMode.MIXED,
                    limitationReason = limitationFor(resolutions, CapabilityType.WEB_SEARCH_MAHA_NATIVE)
                        ?: limitationFor(resolutions, CapabilityType.WEB_SEARCH_PROVIDER_NATIVE)
                )
            )
        }

        if (CapabilityType.TOOL_CALL_DETECTION in types || CapabilityType.TOOL_EXECUTION in types) {
            val workerId = "worker_tool"
            contextWorkerIds.add(workerId)
            workers.add(
                plannedWorker(
                    workerId = workerId,
                    role = WorkerRole.TOOL,
                    displayName = "Tool Worker",
                    requiredCapabilities = listOf(CapabilityType.TOOL_CALL_DETECTION, CapabilityType.TOOL_EXECUTION),
                    order = order++,
                    canRunInParallel = false,
                    limitationReason = limitationFor(resolutions, CapabilityType.TOOL_EXECUTION)
                        ?: limitationFor(resolutions, CapabilityType.TOOL_CALL_DETECTION)
                )
            )
        }

        if (CapabilityType.PARALLEL_EXECUTION in types) {
            val workerId = "worker_comparison"
            contextWorkerIds.add(workerId)
            workers.add(
                plannedWorker(
                    workerId = workerId,
                    role = WorkerRole.COMPARISON,
                    displayName = "Comparison Worker",
                    requiredCapabilities = listOf(CapabilityType.PARALLEL_EXECUTION),
                    order = order++,
                    canRunInParallel = true,
                    limitationReason = limitationFor(resolutions, CapabilityType.PARALLEL_EXECUTION)
                )
            )
        }

        workers.add(
            WorkerPlan(
                workerId = "worker_main",
                workerRole = WorkerRole.MAIN,
                displayName = "Main Worker",
                inputSource = if (contextWorkerIds.isEmpty()) "USER_INPUT" else "ORCHESTRATOR_AND_CONTEXT_WORKERS",
                requiredCapabilities = outputCapabilitiesFor(requirements),
                assignedProviderId = selectedProviderId,
                assignedModelId = selectedModelId,
                executionOrder = order++,
                canRunInParallel = false,
                dependsOnWorkerIds = contextWorkerIds,
                expectedOutputType = expectedOutputTypeFor(requirements),
                plannedStatus = UserGoalStatus.SUCCESS
            )
        )

        if (CapabilityType.CODE_CHECK in types) {
            workers.add(
                plannedWorker(
                    workerId = "worker_code_check",
                    role = WorkerRole.CODE_CHECK,
                    displayName = "Code Check Worker",
                    requiredCapabilities = listOf(CapabilityType.CODE_CHECK),
                    order = order++,
                    canRunInParallel = false,
                    dependsOnWorkerIds = listOf("worker_main"),
                    limitationReason = limitationFor(resolutions, CapabilityType.CODE_CHECK)
                )
            )
        }

        if (CapabilityType.SYNTHESIS in types || executionMode == ExecutionMode.MIXED) {
            val dependencyIds = workers
                .filter { it.workerRole != WorkerRole.ORCHESTRATOR && it.workerRole != WorkerRole.SYNTHESIS }
                .map { it.workerId }
            workers.add(
                WorkerPlan(
                    workerId = "worker_synthesis",
                    workerRole = WorkerRole.SYNTHESIS,
                    displayName = "Synthesis Worker",
                    inputSource = "WORKER_RESULTS",
                    requiredCapabilities = listOf(CapabilityType.SYNTHESIS),
                    assignedProviderId = selectedProviderId,
                    assignedModelId = selectedModelId,
                    executionOrder = order++,
                    canRunInParallel = false,
                    dependsOnWorkerIds = dependencyIds,
                    expectedOutputType = expectedOutputTypeFor(requirements),
                    plannedStatus = if (limitationFor(resolutions, CapabilityType.SYNTHESIS) == null) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED,
                    limitationReason = limitationFor(resolutions, CapabilityType.SYNTHESIS)
                )
            )
        }

        if (CapabilityType.REVIEW in types) {
            workers.add(
                WorkerPlan(
                    workerId = "worker_reviewer",
                    workerRole = WorkerRole.REVIEWER,
                    displayName = "Reviewer Worker",
                    inputSource = "FINAL_DRAFT",
                    requiredCapabilities = listOf(CapabilityType.REVIEW),
                    assignedProviderId = selectedProviderId,
                    assignedModelId = selectedModelId,
                    executionOrder = order,
                    canRunInParallel = false,
                    dependsOnWorkerIds = listOf(workers.last().workerId),
                    expectedOutputType = CapabilityType.REVIEW,
                    plannedStatus = if (limitationFor(resolutions, CapabilityType.REVIEW) == null) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED,
                    limitationReason = limitationFor(resolutions, CapabilityType.REVIEW)
                )
            )
        }

        return workers
    }

    private fun plannedWorker(
        workerId: String,
        role: WorkerRole,
        displayName: String,
        requiredCapabilities: List<CapabilityType>,
        order: Int,
        canRunInParallel: Boolean,
        dependsOnWorkerIds: List<String> = emptyList(),
        limitationReason: LimitationReason? = null
    ): WorkerPlan {
        return WorkerPlan(
            workerId = workerId,
            workerRole = role,
            displayName = displayName,
            inputSource = "ORCHESTRATOR_PLAN",
            requiredCapabilities = requiredCapabilities,
            executionOrder = order,
            canRunInParallel = canRunInParallel,
            dependsOnWorkerIds = dependsOnWorkerIds,
            expectedOutputType = requiredCapabilities.firstOrNull() ?: CapabilityType.TEXT_GENERATION,
            plannedStatus = if (limitationReason == null) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED,
            limitationReason = limitationReason
        )
    }

    private fun outputCapabilitiesFor(requirements: List<CapabilityRequirement>): List<CapabilityType> {
        val outputTypes = requirements
            .map { it.capabilityType }
            .filter {
                it == CapabilityType.TEXT_GENERATION ||
                        it == CapabilityType.CODE_BLOCK_OUTPUT ||
                        it == CapabilityType.JSON_OUTPUT ||
                        it == CapabilityType.TABLE_OUTPUT ||
                        it == CapabilityType.STRUCTURED_OUTPUT
            }
            .ifEmpty { listOf(CapabilityType.TEXT_GENERATION) }
        return outputTypes.distinct()
    }

    private fun expectedOutputTypeFor(requirements: List<CapabilityRequirement>): CapabilityType {
        val types = requirements.map { it.capabilityType }.toSet()
        return when {
            CapabilityType.JSON_OUTPUT in types -> CapabilityType.JSON_OUTPUT
            CapabilityType.TABLE_OUTPUT in types -> CapabilityType.TABLE_OUTPUT
            CapabilityType.CODE_BLOCK_OUTPUT in types -> CapabilityType.CODE_BLOCK_OUTPUT
            CapabilityType.STRUCTURED_OUTPUT in types -> CapabilityType.STRUCTURED_OUTPUT
            else -> CapabilityType.TEXT_GENERATION
        }
    }

    private fun defaultStatusFor(capabilityType: CapabilityType): CapabilityLayerStatus {
        return when (capabilityType) {
            CapabilityType.TEXT_GENERATION,
            CapabilityType.STRUCTURED_OUTPUT,
            CapabilityType.CODE_BLOCK_OUTPUT,
            CapabilityType.JSON_OUTPUT,
            CapabilityType.TABLE_OUTPUT,
            CapabilityType.TOOL_CALL_DETECTION,
            CapabilityType.SYNTHESIS,
            CapabilityType.REVIEW,
            CapabilityType.SINGLE_EXECUTION,
            CapabilityType.SEQUENTIAL_EXECUTION -> CapabilityLayerStatus.AVAILABLE

            CapabilityType.RAG_SEARCH,
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE -> CapabilityLayerStatus.UNKNOWN

            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH,
            CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE,
            CapabilityType.CODE_CHECK,
            CapabilityType.CODE_EXECUTION,
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP,
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> CapabilityLayerStatus.NOT_IMPLEMENTED
        }
    }

    private fun defaultSourceFor(capabilityType: CapabilityType): CapabilitySource {
        return when (capabilityType) {
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE -> CapabilitySource.PROVIDER_NATIVE
            CapabilityType.TOOL_CALL_DETECTION -> CapabilitySource.RUNTIME_DETECTION
            CapabilityType.TEXT_GENERATION,
            CapabilityType.STRUCTURED_OUTPUT,
            CapabilityType.CODE_BLOCK_OUTPUT,
            CapabilityType.JSON_OUTPUT,
            CapabilityType.TABLE_OUTPUT -> CapabilitySource.MODEL_PROFILE
            CapabilityType.RAG_SEARCH,
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH,
            CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE,
            CapabilityType.CODE_CHECK,
            CapabilityType.CODE_EXECUTION,
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP,
            CapabilityType.SYNTHESIS,
            CapabilityType.REVIEW,
            CapabilityType.SINGLE_EXECUTION,
            CapabilityType.SEQUENTIAL_EXECUTION,
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> CapabilitySource.MAHA_NATIVE
        }
    }

    private fun buildLimitationReasonIfNeeded(
        requirement: CapabilityRequirement,
        status: CapabilityLayerStatus,
        source: CapabilitySource,
        selectedProviderId: String?,
        selectedModelId: String?
    ): LimitationReason? {
        val needsReason = when (status) {
            CapabilityLayerStatus.AVAILABLE,
            CapabilityLayerStatus.MAHA_NATIVE_AVAILABLE -> false
            CapabilityLayerStatus.LIMITED,
            CapabilityLayerStatus.UNKNOWN,
            CapabilityLayerStatus.NOT_IMPLEMENTED,
            CapabilityLayerStatus.NOT_AVAILABLE,
            CapabilityLayerStatus.NEED_USER_PERMISSION,
            CapabilityLayerStatus.NEED_API_KEY,
            CapabilityLayerStatus.PROVIDER_NATIVE_ONLY,
            CapabilityLayerStatus.USER_ENABLED -> true
        }
        if (!needsReason) return null

        val capabilityType = requirement.capabilityType
        val reasonCode = when (status) {
            CapabilityLayerStatus.UNKNOWN -> "UNKNOWN_CAPABILITY"
            CapabilityLayerStatus.NOT_IMPLEMENTED -> "NOT_IMPLEMENTED"
            CapabilityLayerStatus.LIMITED -> "LIMITED"
            CapabilityLayerStatus.NEED_API_KEY -> "NEED_API_KEY"
            CapabilityLayerStatus.NEED_USER_PERMISSION -> "NEED_USER_PERMISSION"
            CapabilityLayerStatus.PROVIDER_NATIVE_ONLY -> "PROVIDER_NATIVE_ONLY"
            CapabilityLayerStatus.USER_ENABLED -> "USER_ENABLED_NOT_EXECUTABLE"
            CapabilityLayerStatus.NOT_AVAILABLE -> "NOT_AVAILABLE"
            CapabilityLayerStatus.AVAILABLE,
            CapabilityLayerStatus.MAHA_NATIVE_AVAILABLE -> "NO_LIMITATION"
        }

        val userMessage = when (capabilityType) {
            CapabilityType.WEB_SEARCH_MAHA_NATIVE -> "MAHA 내부 Web Search는 아직 연결되지 않았습니다."
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE -> "선택한 Provider/Model이 자체 Web Search를 지원하는지 아직 확정할 수 없습니다."
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP -> "Tool 실행은 아직 구현되지 않았습니다."
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH -> "장기 기억 또는 대화 이력 검색 기능은 아직 구현되지 않았습니다."
            CapabilityType.LOCAL_FILE_READ -> "로컬 파일 읽기 기능은 아직 구현되지 않았습니다."
            CapabilityType.LOCAL_FILE_WRITE -> "로컬 파일 쓰기 기능은 아직 구현되지 않았습니다."
            CapabilityType.CODE_CHECK -> "코드 검증 Worker는 아직 실제 실행 흐름에 연결되지 않았습니다."
            CapabilityType.RAG_SEARCH -> "RAG 기능은 존재하지만 Capability Resolver 진단 UI에는 아직 실제 실행 상태가 연결되지 않았습니다."
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> "병렬/혼합 실행은 설계 후보이며 아직 실제 실행은 구현되지 않았습니다."
            else -> "현재 capability 상태가 ${status.name}입니다."
        }

        val suggestedAction = when (capabilityType) {
            CapabilityType.WEB_SEARCH_MAHA_NATIVE -> "현재는 Gemini native Web Search가 가능한 모델을 사용하거나, 후속 MAHA-native Web Search 구현을 기다려야 합니다."
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE -> "Provider 문서 또는 Model capability 설정을 확인하세요."
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP -> "현재는 tool 요청 감지만 가능하며 실제 실행은 후속 Tool Registry 구현 후 지원됩니다."
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH -> "후속 Memory/Conversation History 기능 구현이 필요합니다."
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE -> "후속 File Control 기능 구현이 필요합니다."
            CapabilityType.CODE_CHECK -> "후속 Code Check Worker 구현이 필요합니다."
            CapabilityType.RAG_SEARCH -> "실제 대화방 RAG 토글과는 별개입니다. 현재 화면은 계획 preview만 표시합니다."
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> "후속 Execution Planner에서 실제 병렬/혼합 실행을 구현해야 합니다."
            else -> null
        }

        return LimitationReason(
            capabilityType = capabilityType,
            status = status,
            reasonCode = reasonCode,
            userMessage = userMessage,
            technicalMessage = "CapabilityResolver skeleton only. source=${source.name}",
            source = source,
            providerId = selectedProviderId,
            modelId = selectedModelId,
            recoverable = status == CapabilityLayerStatus.UNKNOWN || status == CapabilityLayerStatus.NOT_IMPLEMENTED,
            suggestedAction = suggestedAction
        )
    }

    private fun inferUserGoalStatus(resolutions: List<CapabilityResolution>): UserGoalStatus {
        val relevantLimitations = resolutions.filter { resolution ->
            val priority = resolution.requirement.priority
            val status = resolution.status
            priority >= 20 && status != CapabilityLayerStatus.AVAILABLE && status != CapabilityLayerStatus.MAHA_NATIVE_AVAILABLE
        }
        return if (relevantLimitations.isEmpty()) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED
    }

    private fun limitationFor(
        resolutions: List<CapabilityResolution>,
        capabilityType: CapabilityType
    ): LimitationReason? {
        return resolutions.firstOrNull { it.requirement.capabilityType == capabilityType }?.limitationReason
    }


    private fun buildScenarioWorkerPlans(
        scenario: ConversationScenarioProfile,
        orderedScenarioWorkerIds: List<String>,
        workersById: Map<String, ConversationWorkerProfile>,
        requirements: List<CapabilityRequirement>
    ): List<WorkerPlan> {
        if (orderedScenarioWorkerIds.isEmpty()) {
            return emptyList()
        }

        return orderedScenarioWorkerIds.mapIndexed { index, workerId ->
            val worker = workersById[workerId]
            if (worker == null) {
                WorkerPlan(
                    workerId = "missing_worker_$index",
                    workerRole = WorkerRole.MAIN,
                    displayName = "Missing Worker: $workerId",
                    inputSource = "SCENARIO_WORKER_SET",
                    requiredCapabilities = listOf(CapabilityType.TEXT_GENERATION),
                    assignedProviderId = null,
                    assignedModelId = null,
                    executionOrder = index,
                    canRunInParallel = false,
                    dependsOnWorkerIds = emptyList(),
                    expectedOutputType = CapabilityType.TEXT_GENERATION,
                    plannedStatus = UserGoalStatus.BLOCKED,
                    limitationReason = workerLimitation(
                        reasonCode = "WORKER_PROFILE_NOT_FOUND",
                        userMessage = "Scenario가 참조하는 Worker를 찾을 수 없습니다.",
                        technicalMessage = "scenarioId=${scenario.scenarioId}; missingWorkerProfileId=$workerId",
                        recoverable = true
                    )
                )
            } else {
                val profileLimitations = profileLimitationsFor(worker)
                val firstLimitation = profileLimitations.firstOrNull()
                WorkerPlan(
                    workerId = worker.workerProfileId.ifBlank { "scenario_worker_$index" },
                    workerRole = roleForWorkerProfile(worker),
                    displayName = worker.displayName.ifBlank { worker.workerProfileId.ifBlank { "Scenario Worker ${index + 1}" } },
                    inputSource = inputSourceFor(worker),
                    requiredCapabilities = capabilitiesForWorkerProfile(worker, requirements),
                    assignedProviderId = worker.providerId,
                    assignedModelId = worker.modelId,
                    executionOrder = index,
                    canRunInParallel = worker.canRunInParallel,
                    dependsOnWorkerIds = worker.dependsOnWorkerIds,
                    expectedOutputType = worker.outputPolicy.expectedOutputType,
                    plannedStatus = when {
                        !worker.enabled -> UserGoalStatus.LIMITED
                        firstLimitation?.reasonCode == "NEED_PROVIDER" -> UserGoalStatus.LIMITED
                        firstLimitation?.reasonCode == "NEED_MODEL" -> UserGoalStatus.LIMITED
                        else -> UserGoalStatus.SUCCESS
                    },
                    limitationReason = firstLimitation
                )
            }
        }
    }

    private fun profileLimitationsFor(worker: ConversationWorkerProfile): List<LimitationReason> {
        val limitations = mutableListOf<LimitationReason>()
        val workerLabel = worker.displayName.ifBlank { worker.workerProfileId.ifBlank { "Worker" } }

        if (!worker.enabled) {
            limitations.add(
                workerLimitation(
                    reasonCode = "WORKER_DISABLED",
                    userMessage = "Scenario에 비활성 Worker가 포함되어 있습니다.",
                    technicalMessage = "workerProfileId=${worker.workerProfileId}; displayName=$workerLabel",
                    providerId = worker.providerId,
                    modelId = worker.modelId,
                    recoverable = true
                )
            )
        }

        if (worker.providerId.isNullOrBlank()) {
            limitations.add(
                workerLimitation(
                    reasonCode = "NEED_PROVIDER",
                    userMessage = "Worker에 Provider가 지정되지 않았습니다.",
                    technicalMessage = "workerProfileId=${worker.workerProfileId}; displayName=$workerLabel",
                    providerId = null,
                    modelId = worker.modelId,
                    recoverable = true
                )
            )
        }

        if (worker.modelId.isNullOrBlank()) {
            limitations.add(
                workerLimitation(
                    reasonCode = "NEED_MODEL",
                    userMessage = "Worker에 Model이 지정되지 않았습니다.",
                    technicalMessage = "workerProfileId=${worker.workerProfileId}; displayName=$workerLabel",
                    providerId = worker.providerId,
                    modelId = null,
                    recoverable = true
                )
            )
        }

        return limitations
    }

    private fun recommendScenarioPreviewExecutionMode(
        scenario: ConversationScenarioProfile,
        requirements: List<CapabilityRequirement>,
        userInputExecutionMode: ExecutionMode,
        scenarioWorkers: List<ConversationWorkerProfile>
    ): ExecutionMode {
        if (scenario.workerProfileIds.isEmpty()) {
            return scenario.defaultExecutionMode
        }

        val hasDependencies = scenarioWorkers.any { worker ->
            worker.dependsOnWorkerIds.isNotEmpty() ||
                    worker.inputPolicy.previousWorkerOutput ||
                    worker.inputPolicy.selectedWorkerOutputs.isNotEmpty() ||
                    worker.outputPolicy.passToNextWorker
        }
        val parallelCapableWorkers = scenarioWorkers.count { it.enabled && it.canRunInParallel }
        val requestedTypes = requirements.map { it.capabilityType }.toSet()

        return when {
            CapabilityType.PARALLEL_EXECUTION in requestedTypes && CapabilityType.SYNTHESIS in requestedTypes -> ExecutionMode.MIXED
            CapabilityType.PARALLEL_EXECUTION in requestedTypes -> ExecutionMode.PARALLEL
            hasDependencies && parallelCapableWorkers > 1 -> ExecutionMode.MIXED
            hasDependencies -> ExecutionMode.SEQUENTIAL
            parallelCapableWorkers > 1 -> ExecutionMode.PARALLEL
            scenario.defaultExecutionMode != ExecutionMode.SINGLE -> scenario.defaultExecutionMode
            else -> userInputExecutionMode
        }
    }

    private fun inferScenarioPreviewGoalStatus(
        scenario: ConversationScenarioProfile,
        scenarioWorkerPlans: List<WorkerPlan>,
        limitations: List<LimitationReason>
    ): UserGoalStatus {
        if (!scenario.enabled || scenario.workerProfileIds.isEmpty()) {
            return UserGoalStatus.BLOCKED
        }
        if (scenarioWorkerPlans.any { it.plannedStatus == UserGoalStatus.BLOCKED }) {
            return UserGoalStatus.BLOCKED
        }
        return if (limitations.isEmpty()) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED
    }

    private fun roleForWorkerProfile(worker: ConversationWorkerProfile): WorkerRole {
        val label = "${worker.roleLabel} ${worker.displayName}".lowercase()
        return when {
            label.contains("orchestrator") || label.contains("오케스트") -> WorkerRole.ORCHESTRATOR
            label.contains("synthesis") || label.contains("통합") -> WorkerRole.SYNTHESIS
            label.contains("reviewer") || label.contains("review") || label.contains("검토") -> WorkerRole.REVIEWER
            label.contains("rag") -> WorkerRole.RAG
            label.contains("memory") || label.contains("메모리") || label.contains("기억") -> WorkerRole.MEMORY
            label.contains("web") || label.contains("search") || label.contains("검색") -> WorkerRole.WEB_SEARCH
            label.contains("tool") || label.contains("도구") -> WorkerRole.TOOL
            label.contains("code") || label.contains("코드") -> WorkerRole.CODE_CHECK
            label.contains("comparison") || label.contains("compare") || label.contains("비교") -> WorkerRole.COMPARISON
            else -> WorkerRole.MAIN
        }
    }

    private fun inputSourceFor(worker: ConversationWorkerProfile): String {
        return when {
            worker.inputPolicy.selectedWorkerOutputs.isNotEmpty() -> "SELECTED_WORKER_OUTPUTS"
            worker.inputPolicy.previousWorkerOutput -> "PREVIOUS_WORKER_OUTPUT"
            worker.inputPolicy.ragContextAllowed ||
                    worker.inputPolicy.memoryContextAllowed ||
                    worker.inputPolicy.webSearchContextAllowed -> "USER_INPUT_WITH_CONTEXT"
            worker.inputPolicy.includeRunHistory -> "USER_INPUT_WITH_RUN_HISTORY"
            worker.inputPolicy.userInputOnly -> "USER_INPUT"
            else -> "SCENARIO_CONTEXT"
        }
    }

    private fun capabilitiesForWorkerProfile(
        worker: ConversationWorkerProfile,
        requirements: List<CapabilityRequirement>
    ): List<CapabilityType> {
        val capabilities = linkedSetOf<CapabilityType>()
        capabilities.add(CapabilityType.TEXT_GENERATION)

        fun addWhenEnabled(status: CapabilityLayerStatus, capabilityType: CapabilityType) {
            if (status != CapabilityLayerStatus.UNKNOWN && status != CapabilityLayerStatus.NOT_AVAILABLE) {
                capabilities.add(capabilityType)
            }
        }

        val overrides = worker.capabilityOverrides
        addWhenEnabled(overrides.functionCalling, CapabilityType.TOOL_CALL_DETECTION)
        addWhenEnabled(overrides.webSearch, CapabilityType.WEB_SEARCH_PROVIDER_NATIVE)
        addWhenEnabled(overrides.codeExecution, CapabilityType.CODE_EXECUTION)
        addWhenEnabled(overrides.structuredOutput, CapabilityType.STRUCTURED_OUTPUT)
        addWhenEnabled(overrides.ragSearch, CapabilityType.RAG_SEARCH)
        addWhenEnabled(overrides.memoryRecall, CapabilityType.MEMORY_RECALL)
        addWhenEnabled(overrides.fileRead, CapabilityType.LOCAL_FILE_READ)
        addWhenEnabled(overrides.fileWrite, CapabilityType.LOCAL_FILE_WRITE)
        addWhenEnabled(overrides.codeCheck, CapabilityType.CODE_CHECK)
        addWhenEnabled(overrides.parallelExecution, CapabilityType.PARALLEL_EXECUTION)

        capabilities.add(worker.outputPolicy.expectedOutputType)
        capabilities.addAll(outputCapabilitiesFor(requirements))
        return capabilities.distinct()
    }

    private fun scenarioLimitation(
        reasonCode: String,
        userMessage: String,
        technicalMessage: String,
        recoverable: Boolean
    ): LimitationReason {
        return LimitationReason(
            capabilityType = CapabilityType.MIXED_EXECUTION,
            status = CapabilityLayerStatus.LIMITED,
            reasonCode = reasonCode,
            userMessage = userMessage,
            technicalMessage = technicalMessage,
            source = CapabilitySource.USER_OVERRIDE,
            recoverable = recoverable,
            suggestedAction = "Worker Profile 관리 화면에서 Scenario 설정을 확인하세요."
        )
    }

    private fun workerLimitation(
        reasonCode: String,
        userMessage: String,
        technicalMessage: String,
        providerId: String? = null,
        modelId: String? = null,
        recoverable: Boolean
    ): LimitationReason {
        return LimitationReason(
            capabilityType = CapabilityType.TEXT_GENERATION,
            status = CapabilityLayerStatus.LIMITED,
            reasonCode = reasonCode,
            userMessage = userMessage,
            technicalMessage = technicalMessage,
            source = CapabilitySource.USER_OVERRIDE,
            providerId = providerId,
            modelId = modelId,
            recoverable = recoverable,
            suggestedAction = "Worker Profile 또는 Scenario 편집 화면에서 참조를 보정하세요."
        )
    }

    private fun previewOnlyLimitation(technicalMessage: String): LimitationReason {
        return LimitationReason(
            capabilityType = CapabilityType.MIXED_EXECUTION,
            status = CapabilityLayerStatus.LIMITED,
            reasonCode = "PREVIEW_ONLY",
            userMessage = "이 결과는 실행계획 preview이며 실제 Worker 실행은 수행하지 않습니다.",
            technicalMessage = technicalMessage,
            source = CapabilitySource.UNKNOWN,
            recoverable = false,
            suggestedAction = "실제 실행 연결은 후속 ConversationEngine / Orchestrator 단계에서 구현합니다."
        )
    }

    private fun List<LimitationReason>.dedupeLimitations(): List<LimitationReason> {
        return distinctBy { limitation ->
            listOf(
                limitation.reasonCode,
                limitation.capabilityType.name,
                limitation.providerId.orEmpty(),
                limitation.modelId.orEmpty(),
                limitation.technicalMessage
            ).joinToString("|")
        }
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword.lowercase()) }
    }
}
