# MAHA Capability Layer 최소 모델/DTO 설계 v1

## 1. 목적

이 문서는 MAHA 대화모드의 Orchestrator Capability Layer를 후속 구현할 때 사용할 최소 모델/DTO 후보를 정의한다.

목표는 다음이다.

- 사용자 요청이 요구하는 capability를 명확히 표현한다.
- Provider-native capability와 MAHA-native capability를 분리한다.
- Provider 호출 성공과 사용자 목표 수행 성공을 분리한다.
- 단일 / 순차 / 병렬 / 혼합 실행 계획을 표현할 수 있는 최소 구조를 마련한다.
- 실제 실행 기록과 실행 계획을 혼동하지 않도록 한다.
- 기존 ConversationEngine, ConversationRequestResponse, ConversationModels 저장 schema를 즉시 변경하지 않는다.

이 문서는 실제 Kotlin 구현 파일이 아니라 후속 구현 기준 문서다.

---

## 2. 설계 전제

현재 MAHA는 Provider / Model / RAG / Web Search / 실행정보 / 코드·JSON·테이블 렌더링을 갖추고 있다.

다만 대화모드는 장기적으로 단순 Provider 호출 흐름이 아니라 다음 구조로 확장되어야 한다.

```text
사용자 입력
→ Orchestrator 요청 분석
→ Capability Layer 판단
→ 실행 방식 선택
→ WorkerPlan 생성
→ Worker 실행
→ Synthesis / Review
→ 최종 답변 + 실행정보 표시
```

이번 문서는 위 흐름 중 **요청 분석 결과와 실행 계획을 표현하는 최소 모델**만 정의한다.

금지 범위:

- 실제 Kotlin 코드 구현 없음
- ConversationEngine 구조 변경 없음
- 저장 schema 변경 없음
- Provider 호출 로직 변경 없음
- 병렬 실행 실제 구현 없음
- Tool 실행 구현 없음

---

## 3. CapabilityType

`CapabilityType`은 사용자 요청이 요구하는 능력을 표현한다.

실제 실행 가능 여부는 `CapabilityStatus`와 `CapabilityResolution`에서 판단한다.

### 후보 enum

```kotlin
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
```

### 설계 원칙

- `CapabilityType`은 “무엇이 필요한가”를 표현한다.
- “가능한가”는 별도 상태로 판단한다.
- Provider-native 기능과 MAHA-native 기능은 타입 단계에서도 분리한다.
- Tool call detection과 tool execution은 별도 capability다.

---

## 4. CapabilityStatus

`CapabilityStatus`는 특정 capability가 현재 환경에서 어떤 상태인지 표현한다.

### 후보 enum

```kotlin
enum class CapabilityStatus {
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
```

### 상태 의미

| Status | 의미 |
|---|---|
| AVAILABLE | 현재 요청에서 사용 가능 |
| LIMITED | 일부 조건에서만 가능하거나 결과 품질 제한 있음 |
| NOT_AVAILABLE | 현재 환경에서 불가능 |
| NOT_IMPLEMENTED | 설계상 필요하지만 앱에 미구현 |
| NEED_USER_PERMISSION | 사용자 권한 또는 승인 필요 |
| NEED_API_KEY | API Key 필요 |
| PROVIDER_NATIVE_ONLY | Provider 기능으로만 가능 |
| MAHA_NATIVE_AVAILABLE | MAHA 내부 기능으로 가능 |
| USER_ENABLED | 사용자가 켰지만 실제 실행 가능 여부는 별도 판단 필요 |
| UNKNOWN | 아직 확정할 수 없음 |

### 주의

- `UNKNOWN`은 실패가 아니다.
- `USER_ENABLED`는 실행 가능을 의미하지 않는다.
- `NOT_IMPLEMENTED`는 사용자 목표 수행 상태에서 `LIMITED` 또는 `NOT_IMPLEMENTED`로 이어질 수 있다.

---

## 5. CapabilitySource

Provider-native와 MAHA-native를 구분하기 위한 source 모델이다.

### 후보 enum

```kotlin
enum class CapabilitySource {
    PROVIDER_NATIVE,
    MAHA_NATIVE,
    MODEL_METADATA,
    USER_OVERRIDE,
    RUNTIME_DETECTION,
    NOT_IMPLEMENTED,
    UNKNOWN
}
```

### 예시

| Capability | Source 예시 |
|---|---|
| Gemini native Web Search | PROVIDER_NATIVE |
| MAHA RAG | MAHA_NATIVE |
| ModelProfile webSearch USER_ENABLED | USER_OVERRIDE |
| tool_calls 감지 | RUNTIME_DETECTION |
| Tool execution 미구현 | NOT_IMPLEMENTED |

---

## 6. ExecutionMode

`ExecutionMode`는 Orchestrator가 선택한 실행 방식을 표현한다.

### 후보 enum

```kotlin
enum class ExecutionMode {
    SINGLE,
    SEQUENTIAL,
    PARALLEL,
    MIXED
}
```

### 판단 기준

| Mode | 판단 기준 |
|---|---|
| SINGLE | 단일 Worker / 단일 Provider 호출로 충분 |
| SEQUENTIAL | 이전 Worker 결과가 다음 Worker 입력으로 필요 |
| PARALLEL | 하위 작업들이 서로 독립적 |
| MIXED | 일부 병렬, 일부 순차, 마지막에 Synthesis 필요 |

### 주의

이번 설계는 실행 방식을 표현할 뿐, 실제 병렬 실행은 구현하지 않는다.

---

## 7. UserGoalStatus

`UserGoalStatus`는 Provider 호출 성공과 별도로 사용자 목표 수행 상태를 표현한다.

### 후보 enum

```kotlin
enum class UserGoalStatus {
    SUCCESS,
    LIMITED,
    FAILED,
    BLOCKED,
    NOT_IMPLEMENTED,
    NEED_PERMISSION,
    NEED_API_KEY
}
```

### 예시

```text
Provider 호출: SUCCESS
Worker 실행: SUCCESS
UserGoalStatus: LIMITED
제한 사유: 현재 Provider/Model에서 Web Search 실행 미지원
```

### 설계 원칙

- Provider 호출 성공과 사용자 목표 성공은 다르다.
- 실행정보 UI는 `UserGoalStatus`를 핵심 요약으로 표시해야 한다.
- “앱은 성공했지만 목표는 제한됨” 같은 상태를 표현해야 한다.

---

## 8. WorkerRole

`WorkerRole`은 실행 계획 안에서 Worker가 맡는 역할을 표현한다.

### 후보 enum

```kotlin
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
```

### 역할 요약

| Role | 역할 |
|---|---|
| ORCHESTRATOR | 요청 분석, capability 판단, 실행 계획 생성 |
| MAIN | 핵심 답변 생성 |
| RAG | 내부 지식 검색 |
| MEMORY | 장기 기억 회수 |
| WEB_SEARCH | 외부 검색 또는 Provider-native 검색 |
| TOOL | Tool call 감지/실행 후보 |
| CODE_CHECK | 코드 검토/검증 |
| COMPARISON | 여러 결과 비교 |
| SYNTHESIS | 결과 통합 |
| REVIEWER | 최종 검토 |

---

## 9. LimitationReason

`LimitationReason`은 사용자 목표 수행이 제한되거나 실패한 이유를 구조화한다.

### 후보 data class

```kotlin
data class LimitationReason(
    val capabilityType: CapabilityType,
    val status: CapabilityStatus,
    val reasonCode: String,
    val userMessage: String,
    val technicalMessage: String?,
    val source: CapabilitySource,
    val providerId: String?,
    val modelId: String?,
    val recoverable: Boolean,
    val suggestedAction: String?
)
```

### 예시

```text
capabilityType = WEB_SEARCH_MAHA_NATIVE
status = NOT_IMPLEMENTED
reasonCode = WEB_SEARCH_NOT_CONNECTED
userMessage = 현재 Provider/Model에서는 Web Search 실행이 연결되어 있지 않습니다.
source = NOT_IMPLEMENTED
recoverable = true
suggestedAction = Web Search 지원 Provider를 선택하거나 MAHA-native Web Search 구현 후 사용하세요.
```

---

## 10. CapabilityRequirement

`CapabilityRequirement`는 사용자 요청을 처리하기 위해 필요한 capability를 표현한다.

### 후보 data class

```kotlin
data class CapabilityRequirement(
    val capabilityType: CapabilityType,
    val required: Boolean,
    val priority: Int,
    val reason: String,
    val acceptableFallbacks: List<CapabilityType>,
    val userVisibleLabel: String
)
```

### 필드 의미

| 필드 | 의미 |
|---|---|
| capabilityType | 필요한 능력 |
| required | 필수 여부 |
| priority | 중요도 |
| reason | 왜 필요한지 |
| acceptableFallbacks | 대체 가능한 capability |
| userVisibleLabel | 실행정보에 표시할 이름 |

---

## 11. CapabilityResolution

`CapabilityResolution`은 요구 capability가 현재 환경에서 어떻게 해결되는지 표현한다.

### 후보 data class

```kotlin
data class CapabilityResolution(
    val requirement: CapabilityRequirement,
    val status: CapabilityStatus,
    val source: CapabilitySource,
    val providerNativeAvailable: Boolean,
    val mahaNativeAvailable: Boolean,
    val selectedProviderId: String?,
    val selectedModelId: String?,
    val executionAvailable: Boolean,
    val limitationReason: LimitationReason?
)
```

### 설계 원칙

- 요구 capability와 실제 해결 상태를 분리한다.
- Provider-native와 MAHA-native 가능 여부를 동시에 표현한다.
- 사용자가 capability를 켰더라도 `executionAvailable=false`일 수 있다.

---

## 12. WorkerPlan

`WorkerPlan`은 실제 실행 기록이 아니라 실행 전 계획이다.

### 후보 data class

```kotlin
data class WorkerPlan(
    val workerId: String,
    val workerRole: WorkerRole,
    val displayName: String,
    val inputSource: String,
    val requiredCapabilities: List<CapabilityRequirement>,
    val assignedProviderId: String?,
    val assignedModelId: String?,
    val executionOrder: Int,
    val canRunInParallel: Boolean,
    val dependsOnWorkerIds: List<String>,
    val expectedOutputType: String,
    val plannedStatus: CapabilityStatus,
    val limitationReason: LimitationReason?
)
```

### 핵심 필드

| 필드 | 의미 |
|---|---|
| executionOrder | 순차 실행 순서 |
| canRunInParallel | 병렬 실행 가능 여부 |
| dependsOnWorkerIds | 의존 Worker 목록 |
| expectedOutputType | text/json/table/code/summary 등 |
| plannedStatus | 계획 단계에서의 상태 |

### 주의

- WorkerPlan은 실행 결과가 아니다.
- 실제 실행 결과는 별도의 WorkerRunResult 또는 기존 runInfo 구조와 연결한다.

---

## 13. OrchestratorPlan

`OrchestratorPlan`은 사용자 요청 하나에 대해 Orchestrator가 생성한 실행 계획이다.

### 후보 data class

```kotlin
data class OrchestratorPlan(
    val planId: String,
    val requestId: String,
    val userInputPreview: String,
    val requestedCapabilities: List<CapabilityRequirement>,
    val resolvedCapabilities: List<CapabilityResolution>,
    val executionMode: ExecutionMode,
    val userGoalStatus: UserGoalStatus,
    val limitationReasons: List<LimitationReason>,
    val workerPlans: List<WorkerPlan>,
    val providerNativeUsed: Boolean,
    val mahaNativeUsed: Boolean,
    val createdAt: Long
)
```

### 설계 원칙

- 사용자 목표 수행 상태를 별도 필드로 가진다.
- 실행 방식과 Worker 계획을 함께 가진다.
- Provider-native와 MAHA-native 사용 여부를 요약한다.
- 실행정보 UI에 표시할 수 있어야 한다.

---

## 14. 실행정보 표시 계약

Capability Layer가 도입되면 실행정보 기본 화면에는 다음을 표시한다.

### 기본 요약

```text
사용자 목표: 성공 / 제한 / 실패
실행 방식: 단일 / 순차 / 병렬 / 혼합
요구 능력: RAG, Web Search, JSON 출력
충족 능력: TEXT_GENERATION, JSON_OUTPUT
제한 능력: WEB_SEARCH_MAHA_NATIVE
제한 사유: 현재 Provider/Model에서 Web Search 실행 미지원
```

### Worker 계획 요약

```text
Orchestrator: 계획 생성 완료
Main Worker: 실행 예정 · Provider: Groq · Model: qwen/...
RAG Worker: 건너뜀 · RAG OFF
Web Search Worker: 제한 · Provider 미지원
Synthesis Worker: 필요 없음
```

### 상세 접힘 영역

- OrchestratorPlan 전체 요약
- WorkerPlan 목록
- CapabilityResolution 목록
- Provider-native / MAHA-native 구분
- 미구현 capability
- 권한/API Key 필요 항목

### 표시 원칙

- 기본 화면은 요약 중심이다.
- raw debug log처럼 과도하게 펼치지 않는다.
- 상세 trace는 접힘/복사 영역에 둔다.

---

## 15. 저장 schema 변경 여부

이번 설계 단계에서는 저장 schema를 변경하지 않는다.

후속 구현 시 선택지는 다음이다.

### 1차 후보

- OrchestratorPlan은 runtime-only로 생성한다.
- 실행정보 trace에 요약 문자열로만 기록한다.
- 기존 messages.jsonl schema는 변경하지 않는다.

### 2차 후보

- ConversationRunInfo 또는 별도 plan 파일에 저장한다.
- 기존 메시지 저장 schema와 분리한다.
- 마이그레이션 정책을 별도로 둔다.

### 권장

1차 구현은 runtime-only + trace summary 방식으로 시작한다.

---

## 16. 후속 구현 단계

### 1단계: DTO Kotlin 파일 추가

- CapabilityLayerModels.kt 추가
- enum/data class만 추가
- 기존 호출 로직 연결 없음

### 2단계: 요청 분석 skeleton

- UserInputCapabilityAnalyzer skeleton 추가
- keyword/heuristic 기반 최소 분석
- 실제 Provider 호출 영향 없음

### 3단계: 실행정보 trace 연결

- OrchestratorPlan summary를 실행정보에 표시
- 사용자 목표 수행 상태와 제한 사유 표시

### 4단계: WorkerPlan 실제 연결

- 단일 실행 계획부터 연결
- 기존 Main Worker 흐름과 충돌 방지

### 5단계: 순차/병렬/혼합 확장

- 실제 병렬 실행은 별도 설계 후 구현
- Worker 결과 전달 구조 정의 후 연결

---

## 17. 기존 기능 영향

이번 문서는 설계 산출물이다.

- 코드 변경 없음
- 빌드 영향 없음
- Provider 호출 변경 없음
- RAG 변경 없음
- Web Search 변경 없음
- 작업모드 변경 없음
- 저장 schema 변경 없음

