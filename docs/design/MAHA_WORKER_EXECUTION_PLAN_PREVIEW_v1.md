# MAHA Worker Execution Plan Preview v1

## 1. 목적

이 문서는 MAHA 대화모드에서 사용자가 구성한 `ConversationScenarioProfile`과 `ConversationWorkerProfile`을 기준으로 **실행계획 preview**를 생성하는 구조를 정의한다.

Preview는 실제 대화 실행이 아니라, 사용자가 선택한 Scenario와 입력 예시를 바탕으로 다음 내용을 진단하는 단계다.

- 어떤 capability가 필요해 보이는지
- Scenario에 포함된 Worker들이 어떤 실행 후보가 되는지
- 어떤 Worker가 disabled / missing / orphan 상태인지
- Scenario 기본 실행 방식과 요청 기반 추천 실행 방식이 어떻게 다른지
- 실제 실행 전에 어떤 제한 사유가 예상되는지

이번 문서는 후속 구현 기준 문서이며, 실제 Kotlin 코드 구현이나 ConversationEngine 연결을 포함하지 않는다.

---

## 2. 설계 전제

- MAHA_CORE_INTENT_v1.2 기준을 유지한다.
- Worker는 고정 개체가 아니라 사용자가 추가/삭제/수정 가능한 `ConversationWorkerProfile`이다.
- Scenario는 WorkerProfile 목록을 참조하는 `ConversationScenarioProfile` / WorkerSet이다.
- Orchestrator / Synthesis는 고정 개체가 아니라 Scenario에서 지정 가능한 WorkerProfile 참조다.
- `OrchestratorPlan` / `WorkerPlan`은 실제 실행 결과가 아니라 요청별 실행 계획이다.
- 이번 단계에서는 실제 대화 실행 연결을 하지 않는다.
- Provider 호출, RAG, Web Search, Tool 실행, Worker 실행, 병렬 실행은 하지 않는다.

---

## 3. Preview와 실제 실행의 차이

### Preview

Preview는 사용자가 구성한 Scenario와 WorkerProfile을 바탕으로 예상 실행계획을 보여준다.

Preview 단계에서는 다음을 하지 않는다.

- 실제 Provider 호출
- 실제 Worker 실행
- 실제 RAG 검색
- 실제 Web Search 실행
- 실제 Tool 실행
- 실제 Worker 간 입력/출력 전달
- 실제 병렬 실행

Preview의 capability 판단은 추정/진단 목적이다. 따라서 Preview 결과를 “실제 성공 보장”으로 표시하면 안 된다.

### Actual Execution

Actual Execution은 후속 단계에서 `ConversationEngine`, Orchestrator, Worker 실행이 연결된 이후의 실제 실행이다.

Actual Execution 단계에서는 다음 결과가 실제 상태를 만든다.

- Provider 호출 성공/실패
- RAG 검색 성공/실패
- Web Search 실행 여부
- Tool 실행 여부
- Worker별 응답 생성 결과
- 병렬/순차 실행 결과
- 사용자 목표 수행 상태

Preview의 목적은 Actual Execution 전에 사용자가 Scenario 구성을 이해하고 점검할 수 있게 하는 것이다.

---

## 4. Scenario 기반 plan preview 흐름

후속 구현 시 후보 흐름은 다음과 같다.

1. 사용자가 Scenario를 선택한다.
2. 사용자가 userInput 예시를 입력한다.
3. `CapabilityResolver.inferRequirements(userInput)` 또는 동등한 요구 capability 추정 함수를 실행한다.
4. `scenario.workerProfileIds`를 기준으로 WorkerProfile 목록을 조회한다.
5. disabled Worker, missing Worker, orphan Provider/Model 상태를 확인한다.
6. `scenario.defaultExecutionMode`를 기본 실행 힌트로 읽는다.
7. 각 WorkerProfile의 `capabilityOverrides`, `inputPolicy`, `outputPolicy`, `providerId`, `modelId`를 읽는다.
8. WorkerProfile을 WorkerPlan preview 후보로 변환한다.
9. `scenario.orchestratorProfileId`와 `scenario.synthesisProfileId` 지정 여부를 반영한다.
10. OrchestratorPlan preview를 생성한다.
11. UI에 요약과 상세를 분리해 표시한다.

이 흐름은 진단 전용이며 실제 Worker 실행을 하지 않는다.

---

## 5. ConversationScenarioProfile과 ConversationWorkerProfile 관계

`ConversationScenarioProfile`은 실행할 WorkerSet을 직접 복사하지 않고 `workerProfileIds`로 참조한다.

정책:

- Scenario는 WorkerProfile ID 목록만 저장한다.
- WorkerProfile 자체를 Scenario에 중복 저장하지 않는다.
- Scenario 내부 Worker 순서는 `workerProfileIds` 순서를 우선 사용한다.
- WorkerProfile의 `executionOrder`는 Worker의 기본 순서 정보로 유지한다.
- Scenario 내부 순서와 WorkerProfile `executionOrder`가 충돌하면 preview에서는 Scenario 내부 순서를 우선 표시한다.
- Orchestrator/Synthesis는 Scenario의 `orchestratorProfileId`, `synthesisProfileId`로 지정한다.
- 지정된 Orchestrator/Synthesis가 Scenario 내부 WorkerSet에 없으면 경고를 표시한다.

---

## 6. WorkerProfile → WorkerPlan 변환 정책

후속 preview 생성 시 WorkerProfile은 WorkerPlan preview 후보로 변환된다.

매핑 후보:

| WorkerProfile | WorkerPlan 후보 |
|---|---|
| workerProfileId | workerProfileId 참조 후보 필드 |
| displayName | profileDisplayName 또는 WorkerPlan 표시명 |
| roleLabel | roleLabel 또는 workerRole 확장 후보 |
| providerId | assignedProviderId |
| modelId | assignedModelId |
| capabilityOverrides | requiredCapabilities / resolvedCapabilities 참고 자료 |
| inputPolicy | inputSource / dependsOnWorkerIds 참고 자료 |
| outputPolicy.expectedOutputType | expectedOutputType |
| executionOrder | executionOrder 후보 |
| canRunInParallel | canRunInParallel |
| dependsOnWorkerIds | dependsOnWorkerIds |
| enabled=false | plannedStatus LIMITED / SKIPPED / BLOCKED 후보 |

### WorkerPlan 모델 보강 후보

현재 WorkerPlan은 enum 기반 `workerRole` 구조를 가진다. 그러나 가변 Worker 구조에서는 `roleLabel`이 사용자 정의 문자열일 수 있다.

후속 모델 보강 후보:

```kotlin
workerProfileId: String?
roleLabel: String
profileDisplayName: String
sourceScenarioId: String?
```

이번 단계에서는 모델을 변경하지 않는다. 문서에서 보강 필요성만 명시한다.

---

## 7. CapabilityResolver와의 결합 방식

현재 `CapabilityResolver`는 userInput 기반으로 요구 capability와 WorkerPlan skeleton을 생성하는 구조다.

후속 방향은 userInput만 보지 않고 Scenario/WorkerProfile context를 함께 받는 것이다.

후보 함수:

```kotlin
buildPlan(userInput: String)
```

후속 확장 후보:

```kotlin
buildPlanForScenario(
    userInput: String,
    scenario: ConversationScenarioProfile,
    workerProfiles: List<ConversationWorkerProfile>
)
```

역할:

- `userInput`: 요구 capability 추정에 사용
- `scenario`: defaultExecutionMode, WorkerSet, Orchestrator/Synthesis 지정 제공
- `workerProfiles`: 실제 Worker 후보, capability override, provider/model 참조, input/output policy 제공

주의:

- 이번 단계에서는 함수 구현 금지
- 기존 `CapabilityResolver.buildPlan(...)` 변경 금지
- preview와 실제 실행 계획은 후속 단계에서 분리 연결

---

## 8. OrchestratorPlan preview 구조

Preview용 OrchestratorPlan은 다음 정보를 포함하는 것이 적절하다.

- planId
- sourceScenarioId
- scenarioName
- userInputPreview
- scenarioDefaultExecutionMode
- recommendedExecutionMode
- userGoalStatusPreview
- requestedCapabilities
- resolvedCapabilities
- limitationReasons
- workerPlanPreviews
- disabledWorkerWarnings
- missingWorkerWarnings
- orphanProviderWarnings
- orphanModelWarnings
- providerModelMissingWarnings

현재 모델이 모든 필드를 지원하지 않으면 상세 trace 또는 preview DTO를 별도로 두는 방향을 검토한다.

정책:

- 기존 OrchestratorPlan 모델에 무리하게 모든 UI 필드를 넣지 않는다.
- 실제 실행용 Plan과 preview UI용 DTO를 분리하는 방안을 우선 검토한다.
- preview UI에서는 “예상 계획”임을 명확히 표시한다.

---

## 9. ExecutionMode preview 기준

Preview에서 실행 방식을 추천할 때의 우선순위 후보는 다음과 같다.

1. 사용자 입력에서 병렬/비교/여러 관점 요청이 명확하면 `PARALLEL` 또는 `MIXED` 후보
2. Worker dependency가 존재하면 `SEQUENTIAL` 또는 `MIXED` 후보
3. Scenario의 `defaultExecutionMode`를 기본 힌트로 사용
4. 단순 출력/일반 질문이면 `SINGLE` 후보

정책:

- `scenario.defaultExecutionMode`는 강제값이 아니라 기본 힌트다.
- Preview에서는 “Scenario 기본 실행 방식”과 “요청 기반 추천 실행 방식”을 분리 표시한다.
- 실제 실행에서는 Orchestrator가 요청 내용, Worker 의존성, Provider/Model 상태를 다시 판단한다.
- `PARALLEL` 또는 `MIXED`가 preview에 표시되어도 실제 병렬 실행이 구현된 것은 아니다.

---

## 10. disabled / orphan / missing Worker 처리

### disabled Worker

상황:

- Scenario에 포함된 WorkerProfile이 `enabled=false`이다.

처리:

- 자동 제거 금지
- preview WorkerPlan에서는 `SKIPPED`, `LIMITED`, 또는 `BLOCKED` 후보로 표시
- UI에는 “비활성 Worker가 Scenario에 포함되어 있습니다” 경고 표시

### missing Worker

상황:

- `scenario.workerProfileIds`에 ID가 있으나 WorkerProfile 목록에서 찾을 수 없다.

처리:

- 자동 제거 금지
- preview에서는 `BLOCKED` 후보
- UI 문구: “참조 Worker를 찾을 수 없습니다.”
- 사용자가 Scenario 편집에서 제거/재지정할 수 있게 한다.

### orphan Provider

상황:

- WorkerProfile은 존재하지만 `providerId`가 Provider 목록에서 찾을 수 없다.

처리:

- WorkerProfile 자동 삭제 금지
- preview에서는 `LIMITED` 또는 `BLOCKED` 후보
- UI 문구: “이 Worker가 참조하는 Provider를 찾을 수 없습니다.”

### orphan Model

상황:

- WorkerProfile은 존재하지만 `modelId`가 Model 목록에서 찾을 수 없다.

처리:

- WorkerProfile 자동 삭제 금지
- preview에서는 `LIMITED` 또는 `BLOCKED` 후보
- UI 문구: “이 Worker가 참조하는 Model을 찾을 수 없습니다.”

### Provider/Model 미지정

상황:

- `providerId=null` 또는 `modelId=null`이다.

처리:

- WorkerProfile 유지
- preview에서는 `NEED_PROVIDER` 또는 `NEED_MODEL` 유사 제한 사유 후보 표시
- 후속 CapabilityLayer `LimitationReason`으로 연결

---

## 11. 사용자 목표 상태 preview 기준

Preview에서는 실제 실행 결과가 없으므로 `UserGoalStatus`를 확정 성공/실패로 단정하지 않는다.

후보 표시:

- `READY_PREVIEW`: 실행에 필요한 기본 구성이 있어 보임
- `LIMITED_PREVIEW`: 일부 Worker/Provider/Model/capability 제한이 예상됨
- `BLOCKED_PREVIEW`: WorkerSet 없음, 모든 Worker disabled, Provider/Model 미지정 등으로 실행 불가 예상
- `UNKNOWN_PREVIEW`: Preview 판단 정보가 부족함

현재 `UserGoalStatus` enum에 preview 전용 값이 없다면 기존 상태를 직접 오염시키지 않고 preview UI 문구로 표현하는 것이 안전하다.

예:

- 사용자 목표 상태 preview: 제한 가능
- 사유: Web Search capability가 요청되었지만 해당 Scenario의 Worker에 실행 가능한 Provider/Model이 지정되지 않았습니다.

---

## 12. preview UI 배치 후보

### A. Capability Resolver 진단 화면 확장

구성:

- userInput 입력
- Scenario 선택
- Plan Preview 생성
- 요구 capability
- Scenario 기본 실행 방식
- 추천 실행 방식
- WorkerPlan preview
- 제한 사유 표시

장점:

- 현재 진단 목적과 잘 맞음
- 실제 대화 화면과 분리 가능
- 초기 구현 부담이 낮음

권장:

- 초반 구현 후보로 가장 안전하다.

### B. Worker Profile 관리 화면에 “실행계획 preview” 탭 추가

구성:

- Worker 탭
- Scenario 탭
- 실행계획 Preview 탭

장점:

- Scenario 관리 흐름과 가깝다.
- 사용자가 Scenario를 수정한 뒤 바로 preview를 확인할 수 있다.

주의:

- Worker Profile 관리 화면이 과도하게 복잡해질 수 있다.

### C. 대화모드 설정 메인 별도 카드

구성:

- “Worker 실행계획 preview” 카드
- 별도 진단 화면

장점:

- 실제 대화 실행과 분리하기 좋다.
- 진단 기능임을 명확히 할 수 있다.

주의:

- 설정 메인 카드가 많아질 수 있다.

### 실행정보 UI 연결 전 분리 원칙

- 실제 대화 실행정보 UI에는 아직 연결하지 않는다.
- Preview 결과는 실제 실행 trace가 아니다.
- 실행정보 UI에 연결하는 시점은 ConversationEngine/Orchestrator 실제 실행 연결 이후로 분리한다.

---

## 13. 후속 구현 단계

권장 후속 단계:

1. Preview 전용 DTO 설계
   - ScenarioPlanPreview
   - WorkerPlanPreview
   - PlanPreviewWarning

2. CapabilityResolver 확장 skeleton
   - buildPlanForScenario(...) 후보 추가
   - userInput + Scenario + WorkerProfiles 기반 preview 생성

3. Capability Resolver 진단 UI 확장
   - Scenario 선택
   - preview 생성 버튼
   - WorkerPlan preview 표시

4. WorkerProfile/Scenario validation 함수 보강
   - missing Worker
   - disabled Worker
   - orphan Provider
   - orphan Model
   - Provider/Model 미지정

5. 실제 ConversationEngine 연결 설계
   - preview와 actual execution 분리 유지
   - 실제 실행정보 UI 연결 시점 정의

---

## 14. 금지 유지 사항

이번 설계 단계에서는 다음을 하지 않는다.

- Kotlin 코드 구현
- WorkerPlan 모델 변경
- CapabilityResolver 실제 연결 변경
- ConversationEngine 연결
- ConversationViewModel 대화 흐름 연결
- Provider 호출 변경
- Worker 실행 구현
- 병렬 실행 구현
- Tool 실행 구현
- RAG 구조 변경
- Web Search 구조 변경
- SAF 백업/복원 코드 변경
- 작업모드 수정

---

## 15. 완료 기준

- Scenario 기반 실행계획 preview 흐름 정의 완료
- WorkerProfile → WorkerPlan 변환 정책 정의 완료
- CapabilityResolver 결합 방식 정의 완료
- ExecutionMode preview 기준 정의 완료
- disabled/orphan/missing Worker 처리 정책 정의 완료
- preview UI 배치 후보 정의 완료
- 실제 코드 변경 없음
- 빌드 영향 없음
