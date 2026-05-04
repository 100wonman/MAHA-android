# MAHA_CAPABILITY_RESOLVER_SCENARIO_CONTEXT_v1

## 1. 목적

이 문서는 `CapabilityResolver`를 기존의 단순 `userInput` 기반 판단기에서 `Scenario-aware plan preview builder`로 확장하기 위한 설계 기준을 정의한다.

이번 설계의 목적은 다음과 같다.

- `userInput + ConversationScenarioProfile + ConversationWorkerProfile`을 함께 사용해 실행계획 preview를 생성하는 구조를 정의한다.
- 사용자가 구성한 Scenario / WorkerSet / WorkerProfile 설정을 OrchestratorPlan preview에 반영하는 방향을 정한다.
- WorkerProfile을 WorkerPlan preview로 변환하는 정책을 정의한다.
- disabled / missing / Provider 미지정 / Model 미지정 상태를 preview 제한 사유로 표시하는 정책을 정의한다.
- 실제 ConversationEngine 연결, Provider 호출, Worker 실행, 병렬 실행은 후속 단계로 분리한다.

---

## 2. 설계 전제

- Worker는 고정 개체가 아니라 사용자가 추가/삭제/수정 가능한 `ConversationWorkerProfile`이다.
- Scenario는 고정 프리셋이 아니라 사용자가 구성하는 `ConversationScenarioProfile`이며, WorkerProfile 목록을 `workerProfileIds`로 참조한다.
- Orchestrator / Synthesis profile은 고정 개체가 아니라 Scenario에서 지정 가능한 WorkerProfile 참조다.
- `OrchestratorPlan` / `WorkerPlan`은 이번 맥락에서 실제 실행 결과가 아니라 요청별 실행계획 preview다.
- preview는 실제 Provider 호출, RAG 검색, Web Search, Tool 실행, Worker 실행을 수행하지 않는다.
- preview 결과는 실행 가능성의 확정이 아니라 진단/계획 후보다.

---

## 3. 현재 CapabilityResolver 한계

현재 `CapabilityResolver`의 역할은 다음에 가깝다.

- `userInput` 기반 keyword 판단
- `CapabilityRequirement` 추정
- `ExecutionMode` 추천
- `OrchestratorPlan` skeleton 생성
- `WorkerPlan` skeleton 생성

한계는 다음과 같다.

- 사용자가 구성한 Scenario를 보지 않는다.
- WorkerProfile의 `systemInstruction`, `providerId`, `modelId`를 보지 않는다.
- WorkerProfile의 `capabilityOverrides`를 보지 않는다.
- Worker의 `inputPolicy` / `outputPolicy`를 보지 않는다.
- disabled Worker, missing Worker, Provider/Model 미지정 상태를 반영하지 못한다.
- Scenario의 `orchestratorProfileId` / `synthesisProfileId`를 반영하지 못한다.
- 가변 Worker 구조에서 사용자가 정한 `roleLabel`을 충분히 표현하기 어렵다.

따라서 후속 단계에서는 `CapabilityResolver`를 “입력 텍스트 판단기”에서 “Scenario-aware plan preview builder”로 확장해야 한다.

---

## 4. Scenario context 확장 필요성

Scenario context가 필요한 이유는 다음과 같다.

1. 같은 사용자 입력이라도 선택 Scenario에 따라 실행계획이 달라질 수 있다.
2. 사용자가 지정한 WorkerProfile의 Provider/Model 설정을 plan preview에 반영해야 한다.
3. Worker별 capability override는 실제 실행 가능 보장은 아니지만, 사용자의 의도를 preview에 반영할 수 있다.
4. Worker dependency, input/output policy, parallel 가능 여부가 실행 방식 추천에 영향을 준다.
5. Scenario가 disabled 상태이거나 Worker가 0개이면 preview는 제한 또는 차단 상태로 표시되어야 한다.

---

## 5. buildPlanForScenario(...) 후보 signature

### 5.1 1차 후보

```kotlin
fun buildPlanForScenario(
    userInput: String,
    scenario: ConversationScenarioProfile,
    workerProfiles: List<ConversationWorkerProfile>
): OrchestratorPlan
```

### 5.2 확장 후보

```kotlin
fun buildPlanForScenario(
    userInput: String,
    scenario: ConversationScenarioProfile,
    workerProfiles: List<ConversationWorkerProfile>,
    providerSummaries: List<ProviderSummary> = emptyList(),
    modelSummaries: List<ModelSummary> = emptyList()
): OrchestratorPlan
```

### 5.3 ProviderSummary / ModelSummary 개념 후보

`ProviderSummary`와 `ModelSummary`는 후속 후보이며, API Key 원문이나 민감정보를 포함하면 안 된다.

ProviderSummary 후보 필드:

- providerId
- displayName
- providerType
- enabled
- hasApiKey
- baseUrlConfigured

ModelSummary 후보 필드:

- modelId
- providerId
- displayName
- rawModelName
- enabled
- capability summary

이번 단계에서는 실제 Provider/Model Store 연결을 하지 않는다.

---

## 6. 입력 데이터 구조

### 6.1 userInput

역할:

- 사용자 요청 예시
- capability 요구 추정 기준
- executionMode 추천 기준

예:

- “오늘 서울 날씨를 검색해서 알려줘.”
- “이 내용을 JSON으로 정리해줘.”
- “세 가지 관점에서 비교하고 표로 정리해줘.”

### 6.2 scenario

사용 필드:

- scenarioId
- name
- workerProfileIds
- defaultExecutionMode
- orchestratorProfileId
- synthesisProfileId
- enabled
- userEditable
- isDefaultTemplate
- userModified

### 6.3 workerProfiles

사용 필드:

- workerProfileId
- displayName
- roleLabel
- systemInstruction
- providerId
- modelId
- capabilityOverrides
- inputPolicy
- outputPolicy
- executionOrder
- canRunInParallel
- dependsOnWorkerIds
- enabled

주의:

- WorkerProfile 자체를 수정하지 않는다.
- Scenario도 수정하지 않는다.
- preview 생성 전용 입력으로만 사용한다.

---

## 7. 처리 흐름

후속 구현 후보 흐름은 다음과 같다.

1. `userInput`으로 `CapabilityRequirement`를 추정한다.
2. `scenario.enabled`를 확인한다.
3. `scenario.workerProfileIds` 기준으로 WorkerProfile을 매핑한다.
4. `workerProfileIds`에 있지만 찾을 수 없는 missing Worker ID를 확인한다.
5. disabled Worker를 확인한다.
6. Provider/Model 미지정 Worker를 확인한다.
7. Worker별 `capabilityOverrides`를 참고해 capability 후보를 계산한다.
8. `inputPolicy` / `outputPolicy`를 참고해 WorkerPlan 입력·출력 후보를 계산한다.
9. `scenario.defaultExecutionMode`를 기본 힌트로 사용한다.
10. `userInput` 기반 추천 ExecutionMode와 병합한다.
11. `orchestratorProfileId` / `synthesisProfileId`를 핵심 WorkerPlan으로 표시한다.
12. WorkerProfile 목록을 WorkerPlan preview로 변환한다.
13. limitationReason 후보를 생성한다.
14. OrchestratorPlan preview를 생성한다.

금지:

- 실제 Worker 실행
- Provider 호출
- RAG 검색 실행
- Web Search 실행
- Tool 실행
- ConversationEngine 연결

---

## 8. WorkerProfile → WorkerPlan preview 변환 정책

### 8.1 기본 매핑

| WorkerProfile 필드 | WorkerPlan preview 반영 후보 |
|---|---|
| workerProfileId | WorkerPlan 보강 후보: workerProfileId |
| displayName | WorkerPlan 보강 후보: profileDisplayName |
| roleLabel | WorkerPlan 보강 후보: roleLabel |
| providerId | assignedProviderId |
| modelId | assignedModelId |
| capabilityOverrides | requiredCapabilities / limitationReason 판단 참고 |
| inputPolicy.selectedWorkerOutputs | dependsOnWorkerIds 참고 |
| dependsOnWorkerIds | dependsOnWorkerIds |
| outputPolicy.expectedOutputType | expectedOutputType |
| executionOrder 또는 Scenario 내부 순서 | executionOrder |
| canRunInParallel | canRunInParallel |
| enabled=false | plannedStatus = LIMITED / SKIPPED / BLOCKED 후보 |

### 8.2 WorkerRole enum 임시 매핑

현재 WorkerPlan의 `workerRole`이 enum 기반이면 사용자 정의 `roleLabel`을 직접 표현하기 어렵다.

임시 매핑 후보:

- roleLabel이 “orchestrator” 계열이면 `WorkerRole.ORCHESTRATOR`
- roleLabel이 “reviewer” 계열이면 `WorkerRole.REVIEWER`
- roleLabel이 “synthesis” 계열이면 `WorkerRole.SYNTHESIS`
- roleLabel이 “rag” 계열이면 `WorkerRole.RAG`
- roleLabel이 “tool” 계열이면 `WorkerRole.TOOL`
- 그 외는 `WorkerRole.MAIN`

이 임시 매핑은 preview용이며, 사용자 정의 역할 보존을 위해 후속 모델 보강이 필요하다.

---

## 9. CapabilityRequirement / CapabilityResolution 결합 정책

### 9.1 userInput 기반 요구 capability

`userInput`에서 요구 capability를 먼저 추정한다.

예:

- “검색”, “최신”, “날씨” → Web Search 관련 capability
- “JSON” → JSON_OUTPUT / STRUCTURED_OUTPUT
- “표” → TABLE_OUTPUT / STRUCTURED_OUTPUT
- “코드 검증” → CODE_CHECK
- “기억”, “전에 말한” → MEMORY_RECALL / CONVERSATION_HISTORY_SEARCH

### 9.2 WorkerProfile capabilityOverrides 반영

WorkerProfile의 `capabilityOverrides`는 Worker별 “사용 의도”로 반영한다.

정책:

- override가 `USER_ENABLED`여도 실제 실행 가능을 의미하지 않는다.
- override가 `AVAILABLE`이어도 Provider/Model/MAHA 구현 상태 확인이 필요하다.
- override가 `NOT_AVAILABLE`이면 해당 Worker는 해당 capability 후보에서 낮은 우선순위로 본다.
- override가 `UNKNOWN`이면 모델/Provider/MAHA 구현 상태 판단으로 넘어간다.

### 9.3 Resolution preview 상태

Preview 단계에서는 다음 상태 중심으로 표시한다.

- AVAILABLE
- LIMITED
- NOT_IMPLEMENTED
- NEED_API_KEY
- NEED_USER_PERMISSION
- UNKNOWN

실제 실행 성공/실패는 아직 판단하지 않는다.

---

## 10. ExecutionMode 결정 정책

입력 요소:

- `scenario.defaultExecutionMode`
- `userInput` 기반 추천
- WorkerProfile의 `dependsOnWorkerIds`
- WorkerProfile의 `canRunInParallel`
- Worker 수
- `outputPolicy.passToNextWorker`

정책:

1. `scenario.defaultExecutionMode`는 강제값이 아니라 기본 힌트다.
2. `userInput`이 비교/여러 관점/병렬 요청이면 `PARALLEL` 또는 `MIXED` 후보를 추천한다.
3. Worker dependency가 있으면 `SEQUENTIAL` 또는 `MIXED` 후보를 추천한다.
4. 독립 Worker가 여러 개 있고 `canRunInParallel=true`가 있으면 `PARALLEL` 후보를 추천한다.
5. `passToNextWorker=true`가 있으면 `SEQUENTIAL` 후보를 추천한다.
6. 단일 Worker 또는 단순 질문이면 `SINGLE` 후보를 추천한다.
7. UI에서는 “Scenario 기본 실행 방식”과 “추천 실행 방식”을 분리 표시한다.

주의:

- `PARALLEL` / `MIXED`를 표시해도 실제 병렬 실행을 구현하지 않는다.

---

## 11. disabled / missing / orphan 처리 정책

### 11.1 disabled Worker

상황:

- Scenario에 포함되어 있으나 WorkerProfile.enabled=false

정책:

- preview에 표시
- plannedStatus = LIMITED / SKIPPED / BLOCKED 후보
- 자동 제거 금지

### 11.2 missing Worker

상황:

- scenario.workerProfileIds에 ID는 있으나 WorkerProfile 목록에 없음

정책:

- “참조 Worker를 찾을 수 없습니다” 표시
- plannedStatus = BLOCKED 후보
- 자동 제거 금지

### 11.3 Provider 미지정

상황:

- WorkerProfile.providerId=null

정책:

- assignedProviderId=null
- limitationReason: NEED_PROVIDER 후보
- WorkerProfile은 보존

### 11.4 Model 미지정

상황:

- WorkerProfile.modelId=null

정책:

- assignedModelId=null
- limitationReason: NEED_MODEL 후보
- WorkerProfile은 보존

### 11.5 orphan Provider/Model

상황:

- providerId/modelId 값은 있으나 실제 Provider/Model 목록에 없음

정책:

- limitationReason: PROVIDER_NOT_FOUND / MODEL_NOT_FOUND 후보
- 자동 삭제 금지
- 사용자가 Worker 편집 UI에서 재지정하도록 안내

### 11.6 Scenario Worker 0개

상황:

- scenario.workerProfileIds가 비어 있음

정책:

- userGoalStatus = BLOCKED 후보
- “Scenario에 포함된 Worker가 없습니다” 표시
- 실제 실행 금지

---

## 12. WorkerPlan 모델 보강 필요성

현재 WorkerPlan 한계:

- `workerRole`이 enum 기반이다.
- 가변 Worker의 사용자 정의 `roleLabel`을 보존하기 어렵다.
- 원본 WorkerProfile 참조가 없다.
- sourceScenarioId가 없다.
- Worker가 기본 템플릿인지 사용자 수정본인지 표시하기 어렵다.
- Provider/Model missing 상태를 직접 표현하기 어렵다.

후속 보강 후보 필드:

```kotlin
val workerProfileId: String?
val sourceScenarioId: String?
val roleLabel: String
val profileDisplayName: String
val isScenarioWorker: Boolean
val profileEnabled: Boolean
val providerMissing: Boolean
val modelMissing: Boolean
```

이번 단계에서는 모델 변경을 하지 않는다. 문서상 후속 보강 필요성만 정의한다.

---

## 13. buildPlanForScenario preview 출력 구조 후보

Preview UI에서 표시할 항목 후보:

- 선택 Scenario
- Scenario enabled 상태
- Scenario 기본 실행 방식
- 추천 실행 방식
- userInput 기반 요구 capability
- WorkerPlan preview 목록
- Orchestrator 지정 Worker
- Synthesis 지정 Worker
- Provider/Model 미지정 경고
- disabled Worker 경고
- missing Worker 경고
- 실제 실행 아님 안내

표시 문구 후보:

> 이 화면은 실행계획 preview입니다. 실제 Provider 호출, RAG, Web Search, Tool 실행, Worker 실행은 수행하지 않습니다.

---

## 14. preview UI 배치 방향

후속 구현 후보는 다음과 같다.

### A. Capability Resolver 진단 확장

구성:

- Scenario 선택 dropdown
- userInput 입력
- Plan Preview 표시

장점:

- 진단 목적과 가장 잘 맞다.
- 실제 대화 실행과 분리하기 쉽다.

### B. Worker Profile 관리 화면 내 Preview 탭

구성:

- Worker / Scenario / Preview 탭
- Scenario 선택
- userInput 예시 입력
- WorkerPlan preview

장점:

- Scenario/WorkerSet 상태 확인에 좋다.

### C. 대화모드 설정 메인 별도 카드

구성:

- “Worker 실행계획 Preview” 카드
- 별도 preview 화면

장점:

- 실제 대화 실행과 분리하기 쉽다.

권장:

- 첫 구현은 Capability Resolver 진단 화면 확장
- 실제 대화 화면/실행정보 UI에는 아직 연결하지 않는다.

---

## 15. 실제 구현 단계

후속 구현 순서 후보:

1. 문서 기준으로 CapabilityResolver 확장 skeleton 구현
2. `buildPlanForScenario(...)` 함수 skeleton 추가
3. WorkerProfile/Scenario 입력을 받아 preview용 plan 생성
4. Capability Resolver 진단 UI에 Scenario 선택 추가
5. Plan Preview 표시
6. disabled/missing/Provider 미지정/Model 미지정 경고 표시
7. WorkerPlan 모델 보강 여부 별도 단계에서 검토
8. 실제 ConversationEngine 연결은 별도 후속 단계로 분리

---

## 16. 기존 기능 영향

이번 단계는 설계문서 작성만 수행한다.

영향 없음:

- Worker Profile 관리 UI
- Worker 편집 UI
- Scenario 편집 UI
- WorkerProfileStore 저장/로드
- Capability Resolver 진단 UI
- 대화 전송
- Provider 호출
- Google/Gemini 대화
- OpenAI-compatible 대화
- OpenAI Responses 대화
- Gemini native Web Search
- RAG
- 실행정보 UI
- Provider 관리
- Model 관리
- SAF 백업/복원
- 작업모드 전체
