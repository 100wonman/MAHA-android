package com.maha.app

/**
 * CapabilityResolver
 *
 * 후속 Orchestrator 구현을 위한 capability 판단 skeleton입니다.
 *
 * 현재 단계의 원칙:
 * - ConversationEngine / ViewModel / UI / 저장 schema와 연결하지 않습니다.
 * - 실제 Provider / Model / RAG / Web Search / Tool 실행 상태를 조회하지 않습니다.
 * - keyword 기반으로 요구 capability를 추정하는 최소 구조만 제공합니다.
 * - CapabilityStatus가 아니라 CapabilityLayerStatus를 사용해 기존 ModelCapability V2와 충돌하지 않습니다.
 */
object CapabilityResolver {

    fun inferRequirements(userInput: String): List<CapabilityRequirement> {
        val text = userInput.trim()
        val lower = text.lowercase()
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

        add(
            capabilityType = CapabilityType.TEXT_GENERATION,
            priority = 0,
            reason = "기본 대화 응답 생성",
            label = "일반 텍스트 생성"
        )

        if (containsAny(lower, listOf("코드블록", "code block", "```", "코드 블록"))) {
            add(
                capabilityType = CapabilityType.CODE_BLOCK_OUTPUT,
                priority = 10,
                reason = "사용자가 코드블록 형태의 출력을 요구함",
                label = "코드블록 출력"
            )
        }

        if (containsAny(lower, listOf("json", "제이슨"))) {
            add(
                capabilityType = CapabilityType.JSON_OUTPUT,
                priority = 10,
                reason = "사용자가 JSON 형태의 출력을 요구함",
                label = "JSON 출력"
            )
            add(
                capabilityType = CapabilityType.STRUCTURED_OUTPUT,
                required = false,
                priority = 8,
                reason = "JSON 출력을 위한 구조화 응답 후보",
                label = "구조화 출력"
            )
        }

        if (containsAny(lower, listOf("표", "table", "마크다운 표", "markdown table"))) {
            add(
                capabilityType = CapabilityType.TABLE_OUTPUT,
                priority = 10,
                reason = "사용자가 표 형태의 출력을 요구함",
                label = "테이블 출력"
            )
            add(
                capabilityType = CapabilityType.STRUCTURED_OUTPUT,
                required = false,
                priority = 8,
                reason = "테이블 출력을 위한 구조화 응답 후보",
                label = "구조화 출력"
            )
        }

        if (containsAny(lower, listOf("rag", "내 문서", "자료 기반", "참조 문서", "저장된 문서", "인덱스"))) {
            add(
                capabilityType = CapabilityType.RAG_SEARCH,
                priority = 20,
                reason = "앱 내부 RAG 검색이 필요할 수 있음",
                label = "RAG 검색"
            )
        }

        if (containsAny(lower, listOf("검색", "최신", "오늘", "뉴스", "날씨", "현재", "실시간", "웹 검색", "web search"))) {
            add(
                capabilityType = CapabilityType.WEB_SEARCH_PROVIDER_NATIVE,
                priority = 20,
                reason = "Provider-native Web Search가 필요할 수 있음",
                acceptableFallbacks = listOf(CapabilityType.WEB_SEARCH_MAHA_NATIVE),
                label = "Provider Web Search"
            )
            add(
                capabilityType = CapabilityType.WEB_SEARCH_MAHA_NATIVE,
                required = false,
                priority = 18,
                reason = "MAHA-native Web Search 대체 후보",
                label = "MAHA Web Search"
            )
        }

        if (containsAny(lower, listOf("기억", "이전 대화", "전에 말한", "지난번", "대화 이력"))) {
            add(
                capabilityType = CapabilityType.MEMORY_RECALL,
                priority = 20,
                reason = "장기 기억 또는 이전 대화 참조가 필요할 수 있음",
                acceptableFallbacks = listOf(CapabilityType.CONVERSATION_HISTORY_SEARCH),
                label = "기억 호출"
            )
            add(
                capabilityType = CapabilityType.CONVERSATION_HISTORY_SEARCH,
                required = false,
                priority = 18,
                reason = "대화 이력 검색 대체 후보",
                label = "대화 이력 검색"
            )
        }

        if (containsAny(lower, listOf("파일", "저장소", "문서 읽", "읽어", "열어"))) {
            add(
                capabilityType = CapabilityType.LOCAL_FILE_READ,
                priority = 20,
                reason = "로컬 파일 또는 저장소 읽기가 필요할 수 있음",
                label = "로컬 파일 읽기"
            )
        }

        if (containsAny(lower, listOf("파일 수정", "파일 저장", "저장해", "수정해", "작성해서 저장"))) {
            add(
                capabilityType = CapabilityType.LOCAL_FILE_WRITE,
                priority = 25,
                reason = "로컬 파일 쓰기 또는 수정이 필요할 수 있음",
                label = "로컬 파일 쓰기"
            )
        }

        if (containsAny(lower, listOf("검증", "빌드 오류", "컴파일", "코드 확인", "코드 검토", "테스트해"))) {
            add(
                capabilityType = CapabilityType.CODE_CHECK,
                priority = 20,
                reason = "코드 검증 또는 오류 분석이 필요할 수 있음",
                label = "코드 검증"
            )
            add(
                capabilityType = CapabilityType.SEQUENTIAL_EXECUTION,
                required = false,
                priority = 15,
                reason = "생성 후 검증 같은 순차 실행 후보",
                label = "순차 실행"
            )
        }

        if (containsAny(lower, listOf("도구", "tool", "function_call", "tool_call", "tool calls", "함수 호출"))) {
            add(
                capabilityType = CapabilityType.TOOL_CALL_DETECTION,
                priority = 20,
                reason = "tool_call 또는 function_call 감지가 필요할 수 있음",
                label = "Tool 호출 감지"
            )
            add(
                capabilityType = CapabilityType.TOOL_EXECUTION,
                required = false,
                priority = 25,
                reason = "실제 Tool 실행은 후속 구현 대상",
                label = "Tool 실행"
            )
        }

        if (containsAny(lower, listOf("비교", "여러 관점", "각각 분석", "동시에", "병렬", "여러 모델", "여러 자료"))) {
            add(
                capabilityType = CapabilityType.PARALLEL_EXECUTION,
                required = false,
                priority = 30,
                reason = "독립 하위 작업을 병렬로 나눌 수 있음",
                label = "병렬 실행"
            )
            add(
                capabilityType = CapabilityType.SYNTHESIS,
                required = false,
                priority = 30,
                reason = "병렬 결과 통합이 필요할 수 있음",
                label = "결과 통합"
            )
        }

        if (containsAny(lower, listOf("검토", "리뷰", "최종 확인", "누락 확인"))) {
            add(
                capabilityType = CapabilityType.REVIEW,
                required = false,
                priority = 20,
                reason = "최종 검토 Worker 후보",
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
            val executionAvailable = status == CapabilityLayerStatus.AVAILABLE ||
                    status == CapabilityLayerStatus.LIMITED ||
                    status == CapabilityLayerStatus.MAHA_NATIVE_AVAILABLE ||
                    status == CapabilityLayerStatus.PROVIDER_NATIVE_ONLY

            CapabilityResolution(
                requirement = requirement,
                status = status,
                source = source,
                providerNativeAvailable = source == CapabilitySource.PROVIDER_NATIVE,
                mahaNativeAvailable = source == CapabilitySource.MAHA_NATIVE,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                executionAvailable = executionAvailable,
                limitationReason = buildLimitationReasonIfNeeded(requirement, status, source, selectedProviderId, selectedModelId)
            )
        }
    }

    fun recommendExecutionMode(requirements: List<CapabilityRequirement>): ExecutionMode {
        val types = requirements.map { it.capabilityType }.toSet()

        if (CapabilityType.MIXED_EXECUTION in types) return ExecutionMode.MIXED
        if (CapabilityType.PARALLEL_EXECUTION in types &&
            (CapabilityType.SYNTHESIS in types || CapabilityType.REVIEW in types)
        ) {
            return ExecutionMode.MIXED
        }
        if (CapabilityType.PARALLEL_EXECUTION in types) return ExecutionMode.PARALLEL

        val sequentialHints = setOf(
            CapabilityType.RAG_SEARCH,
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE,
            CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH,
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE,
            CapabilityType.CODE_CHECK,
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.SEQUENTIAL_EXECUTION
        )
        if (types.any { it in sequentialHints }) return ExecutionMode.SEQUENTIAL

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
        val workerPlans = buildWorkerPlans(requirements, resolutions, executionMode, selectedProviderId, selectedModelId)

        return OrchestratorPlan(
            planId = "plan_${System.currentTimeMillis()}",
            requestId = requestId,
            userInputPreview = userInput.trim().take(160),
            requestedCapabilities = requirements,
            resolvedCapabilities = resolutions,
            executionMode = executionMode,
            userGoalStatus = inferUserGoalStatus(resolutions),
            limitationReasons = limitationReasons,
            workerPlans = workerPlans,
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

        if (CapabilityType.RAG_SEARCH in types) {
            workers.add(
                plannedWorker(
                    workerId = "worker_rag",
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
            workers.add(
                plannedWorker(
                    workerId = "worker_memory",
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
            workers.add(
                plannedWorker(
                    workerId = "worker_web_search",
                    role = WorkerRole.WEB_SEARCH,
                    displayName = "Web Search Worker",
                    requiredCapabilities = listOf(CapabilityType.WEB_SEARCH_PROVIDER_NATIVE, CapabilityType.WEB_SEARCH_MAHA_NATIVE),
                    order = order++,
                    canRunInParallel = executionMode == ExecutionMode.PARALLEL || executionMode == ExecutionMode.MIXED,
                    limitationReason = limitationFor(resolutions, CapabilityType.WEB_SEARCH_PROVIDER_NATIVE)
                        ?: limitationFor(resolutions, CapabilityType.WEB_SEARCH_MAHA_NATIVE)
                )
            )
        }

        if (CapabilityType.TOOL_CALL_DETECTION in types || CapabilityType.TOOL_EXECUTION in types) {
            workers.add(
                plannedWorker(
                    workerId = "worker_tool",
                    role = WorkerRole.TOOL,
                    displayName = "Tool Worker",
                    requiredCapabilities = listOf(CapabilityType.TOOL_CALL_DETECTION, CapabilityType.TOOL_EXECUTION),
                    order = order++,
                    canRunInParallel = false,
                    limitationReason = limitationFor(resolutions, CapabilityType.TOOL_EXECUTION)
                )
            )
        }

        val mainDependencies = workers
            .filter { it.workerRole != WorkerRole.ORCHESTRATOR }
            .map { it.workerId }

        workers.add(
            WorkerPlan(
                workerId = "worker_main",
                workerRole = WorkerRole.MAIN,
                displayName = "Main Worker",
                inputSource = if (mainDependencies.isEmpty()) "USER_INPUT" else "ORCHESTRATOR_AND_CONTEXT_WORKERS",
                requiredCapabilities = listOf(CapabilityType.TEXT_GENERATION),
                assignedProviderId = selectedProviderId,
                assignedModelId = selectedModelId,
                executionOrder = order++,
                canRunInParallel = false,
                dependsOnWorkerIds = mainDependencies,
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
                    dependsOnWorkerIds = workers.filter { it.workerRole != WorkerRole.ORCHESTRATOR }.map { it.workerId },
                    expectedOutputType = CapabilityType.TEXT_GENERATION,
                    plannedStatus = UserGoalStatus.SUCCESS,
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
                    plannedStatus = UserGoalStatus.SUCCESS,
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
            expectedOutputType = CapabilityType.TEXT_GENERATION,
            plannedStatus = if (limitationReason == null) UserGoalStatus.SUCCESS else UserGoalStatus.LIMITED,
            limitationReason = limitationReason
        )
    }

    private fun defaultStatusFor(capabilityType: CapabilityType): CapabilityLayerStatus {
        return when (capabilityType) {
            CapabilityType.TEXT_GENERATION,
            CapabilityType.STRUCTURED_OUTPUT,
            CapabilityType.CODE_BLOCK_OUTPUT,
            CapabilityType.JSON_OUTPUT,
            CapabilityType.TABLE_OUTPUT,
            CapabilityType.TOOL_CALL_DETECTION,
            CapabilityType.SINGLE_EXECUTION -> CapabilityLayerStatus.AVAILABLE

            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE,
            CapabilityType.RAG_SEARCH -> CapabilityLayerStatus.UNKNOWN

            CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH,
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE,
            CapabilityType.CODE_CHECK,
            CapabilityType.CODE_EXECUTION,
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP,
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> CapabilityLayerStatus.NOT_IMPLEMENTED

            CapabilityType.SYNTHESIS,
            CapabilityType.REVIEW,
            CapabilityType.SEQUENTIAL_EXECUTION -> CapabilityLayerStatus.LIMITED
        }
    }

    private fun defaultSourceFor(capabilityType: CapabilityType): CapabilitySource {
        return when (capabilityType) {
            CapabilityType.WEB_SEARCH_PROVIDER_NATIVE -> CapabilitySource.PROVIDER_NATIVE
            CapabilityType.RAG_SEARCH,
            CapabilityType.WEB_SEARCH_MAHA_NATIVE,
            CapabilityType.MEMORY_RECALL,
            CapabilityType.CONVERSATION_HISTORY_SEARCH,
            CapabilityType.LOCAL_FILE_READ,
            CapabilityType.LOCAL_FILE_WRITE,
            CapabilityType.CODE_CHECK,
            CapabilityType.CODE_EXECUTION,
            CapabilityType.TOOL_EXECUTION,
            CapabilityType.TOOL_RESULT_REINJECTION,
            CapabilityType.TOOL_LOOP,
            CapabilityType.PARALLEL_EXECUTION,
            CapabilityType.MIXED_EXECUTION -> CapabilitySource.MAHA_NATIVE
            else -> CapabilitySource.UNKNOWN
        }
    }

    private fun buildLimitationReasonIfNeeded(
        requirement: CapabilityRequirement,
        status: CapabilityLayerStatus,
        source: CapabilitySource,
        providerId: String?,
        modelId: String?
    ): LimitationReason? {
        if (status == CapabilityLayerStatus.AVAILABLE || status == CapabilityLayerStatus.LIMITED) {
            return null
        }

        return LimitationReason(
            capabilityType = requirement.capabilityType,
            status = status,
            reasonCode = status.name,
            userMessage = when (status) {
                CapabilityLayerStatus.UNKNOWN -> "${requirement.userVisibleLabel} 지원 여부는 아직 확정되지 않았습니다."
                CapabilityLayerStatus.NOT_IMPLEMENTED -> "${requirement.userVisibleLabel} 기능은 아직 구현되지 않았습니다."
                CapabilityLayerStatus.NEED_API_KEY -> "${requirement.userVisibleLabel} 기능을 사용하려면 API Key가 필요합니다."
                CapabilityLayerStatus.NEED_USER_PERMISSION -> "${requirement.userVisibleLabel} 기능을 사용하려면 사용자 권한이 필요합니다."
                else -> "${requirement.userVisibleLabel} 기능을 현재 사용할 수 없습니다."
            },
            technicalMessage = "CapabilityResolver skeleton default policy: ${requirement.capabilityType.name} -> ${status.name}",
            source = source,
            providerId = providerId,
            modelId = modelId,
            recoverable = status == CapabilityLayerStatus.UNKNOWN ||
                    status == CapabilityLayerStatus.NEED_API_KEY ||
                    status == CapabilityLayerStatus.NEED_USER_PERMISSION,
            suggestedAction = when (status) {
                CapabilityLayerStatus.UNKNOWN -> "후속 Capability Layer 연결에서 Provider/Model 상태를 확인해야 합니다."
                CapabilityLayerStatus.NOT_IMPLEMENTED -> "후속 구현 단계에서 MAHA-native 기능 연결이 필요합니다."
                CapabilityLayerStatus.NEED_API_KEY -> "Provider 관리에서 API Key를 설정해야 합니다."
                CapabilityLayerStatus.NEED_USER_PERMISSION -> "사용자 승인 UI가 필요합니다."
                else -> null
            }
        )
    }

    private fun inferUserGoalStatus(resolutions: List<CapabilityResolution>): UserGoalStatus {
        if (resolutions.isEmpty()) return UserGoalStatus.UNKNOWN
        val requiredResolutions = resolutions.filter { it.requirement.required }
        if (requiredResolutions.all { it.executionAvailable }) return UserGoalStatus.SUCCESS
        if (requiredResolutions.any { it.status == CapabilityLayerStatus.NEED_API_KEY }) return UserGoalStatus.NEED_API_KEY
        if (requiredResolutions.any { it.status == CapabilityLayerStatus.NEED_USER_PERMISSION }) return UserGoalStatus.NEED_PERMISSION
        if (requiredResolutions.any { it.status == CapabilityLayerStatus.NOT_IMPLEMENTED }) return UserGoalStatus.NOT_IMPLEMENTED
        if (requiredResolutions.any { it.status == CapabilityLayerStatus.NOT_AVAILABLE }) return UserGoalStatus.BLOCKED
        return UserGoalStatus.LIMITED
    }

    private fun limitationFor(
        resolutions: List<CapabilityResolution>,
        capabilityType: CapabilityType
    ): LimitationReason? {
        return resolutions.firstOrNull { it.requirement.capabilityType == capabilityType }?.limitationReason
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

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> text.contains(keyword.lowercase()) }
    }
}
