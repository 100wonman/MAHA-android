package com.maha.app

/**
 * CapabilityType
 *
 * 사용자 요청이 요구하는 능력을 표현하는 최소 분류입니다.
 * 이 enum은 실제 실행 가능 여부가 아니라 "필요한 능력"을 나타냅니다.
 */
enum class CapabilityType {
    TEXT_GENERATION,
    STRUCTURED_OUTPUT,
    CODE_BLOCK_OUTPUT,
    JSON_OUTPUT,
    TABLE_OUTPUT,
    RAG_SEARCH,
    MEMORY_RECALL,
    CONVERSATION_HISTORY_SEARCH,
    WEB_SEARCH_PROVIDER_NATIVE,
    WEB_SEARCH_MAHA_NATIVE,
    LOCAL_FILE_READ,
    LOCAL_FILE_WRITE,
    CODE_CHECK,
    CODE_EXECUTION,
    TOOL_CALL_DETECTION,
    TOOL_EXECUTION,
    TOOL_RESULT_REINJECTION,
    TOOL_LOOP,
    SYNTHESIS,
    REVIEW,
    SINGLE_EXECUTION,
    SEQUENTIAL_EXECUTION,
    PARALLEL_EXECUTION,
    MIXED_EXECUTION
}

/**
 * CapabilityLayerStatus
 *
 * 특정 capability가 현재 환경에서 어떤 상태인지 나타냅니다.
 * 기존 ModelCapability V2의 CapabilityStatus와 이름 충돌을 피하기 위해 별도 이름을 사용합니다.
 */
enum class CapabilityLayerStatus {
    AVAILABLE,
    LIMITED,
    NOT_AVAILABLE,
    NOT_IMPLEMENTED,
    NEED_USER_PERMISSION,
    NEED_API_KEY,
    PROVIDER_NATIVE_ONLY,
    MAHA_NATIVE_AVAILABLE,
    USER_ENABLED,
    UNKNOWN
}

/**
 * CapabilitySource
 *
 * capability 판단 근거가 어디에서 왔는지 나타냅니다.
 */
enum class CapabilitySource {
    PROVIDER_NATIVE,
    MAHA_NATIVE,
    MODEL_PROFILE,
    USER_OVERRIDE,
    RUNTIME_DETECTION,
    UNKNOWN
}

/**
 * ExecutionMode
 *
 * Orchestrator가 선택할 수 있는 실행 방식 후보입니다.
 * 현재 단계에서는 실제 실행 로직과 연결하지 않습니다.
 */
enum class ExecutionMode {
    SINGLE,
    SEQUENTIAL,
    PARALLEL,
    MIXED
}

/**
 * UserGoalStatus
 *
 * Provider 호출 성공 여부와 별도로 사용자 목표 수행 상태를 표현합니다.
 */
enum class UserGoalStatus {
    SUCCESS,
    LIMITED,
    FAILED,
    BLOCKED,
    NOT_IMPLEMENTED,
    NEED_PERMISSION,
    NEED_API_KEY,
    UNKNOWN
}

/**
 * WorkerRole
 *
 * Orchestrator가 계획할 수 있는 Worker 역할 후보입니다.
 */
enum class WorkerRole {
    ORCHESTRATOR,
    MAIN,
    RAG,
    MEMORY,
    WEB_SEARCH,
    TOOL,
    CODE_CHECK,
    COMPARISON,
    SYNTHESIS,
    REVIEWER
}

/**
 * LimitationReason
 *
 * capability가 제한되거나 실행 불가능한 이유를 사용자용/기술용으로 분리해 기록합니다.
 */
data class LimitationReason(
    val capabilityType: CapabilityType = CapabilityType.TEXT_GENERATION,
    val status: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val reasonCode: String = "UNKNOWN",
    val userMessage: String = "제한 사유가 아직 정리되지 않았습니다.",
    val technicalMessage: String = "",
    val source: CapabilitySource = CapabilitySource.UNKNOWN,
    val providerId: String? = null,
    val modelId: String? = null,
    val recoverable: Boolean = false,
    val suggestedAction: String? = null
)

/**
 * CapabilityRequirement
 *
 * 사용자 요청을 처리하기 위해 필요한 capability 요구사항입니다.
 */
data class CapabilityRequirement(
    val capabilityType: CapabilityType = CapabilityType.TEXT_GENERATION,
    val required: Boolean = true,
    val priority: Int = 0,
    val reason: String = "",
    val acceptableFallbacks: List<CapabilityType> = emptyList(),
    val userVisibleLabel: String = capabilityType.name
)

/**
 * CapabilityResolution
 *
 * 요구된 capability가 Provider-native 또는 MAHA-native로 해결 가능한지 판단한 결과입니다.
 */
data class CapabilityResolution(
    val requirement: CapabilityRequirement = CapabilityRequirement(),
    val status: CapabilityLayerStatus = CapabilityLayerStatus.UNKNOWN,
    val source: CapabilitySource = CapabilitySource.UNKNOWN,
    val providerNativeAvailable: Boolean = false,
    val mahaNativeAvailable: Boolean = false,
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val executionAvailable: Boolean = false,
    val limitationReason: LimitationReason? = null
)

/**
 * WorkerPlan
 *
 * 실제 실행 결과가 아니라 Orchestrator가 세우는 Worker 실행 계획 후보입니다.
 */
data class WorkerPlan(
    val workerId: String = "worker_${System.currentTimeMillis()}",
    val workerRole: WorkerRole = WorkerRole.MAIN,
    val displayName: String = workerRole.name,
    val inputSource: String = "USER_INPUT",
    val requiredCapabilities: List<CapabilityType> = listOf(CapabilityType.TEXT_GENERATION),
    val assignedProviderId: String? = null,
    val assignedModelId: String? = null,
    val executionOrder: Int = 0,
    val canRunInParallel: Boolean = false,
    val dependsOnWorkerIds: List<String> = emptyList(),
    val expectedOutputType: CapabilityType = CapabilityType.TEXT_GENERATION,
    val plannedStatus: UserGoalStatus = UserGoalStatus.UNKNOWN,
    val limitationReason: LimitationReason? = null
)

/**
 * OrchestratorPlan
 *
 * 사용자 요청을 어떤 capability와 Worker 계획으로 처리할지 나타내는 최소 계획 모델입니다.
 * 현재 단계에서는 ConversationEngine, ViewModel, 저장 schema와 연결하지 않습니다.
 */
data class OrchestratorPlan(
    val planId: String = "plan_${System.currentTimeMillis()}",
    val requestId: String = "",
    val userInputPreview: String = "",
    val requestedCapabilities: List<CapabilityRequirement> = emptyList(),
    val resolvedCapabilities: List<CapabilityResolution> = emptyList(),
    val executionMode: ExecutionMode = ExecutionMode.SINGLE,
    val userGoalStatus: UserGoalStatus = UserGoalStatus.UNKNOWN,
    val limitationReasons: List<LimitationReason> = emptyList(),
    val workerPlans: List<WorkerPlan> = emptyList(),
    val providerNativeUsed: Boolean = false,
    val mahaNativeUsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
