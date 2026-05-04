# MAHA_VARIABLE_WORKER_PROFILE_v1

## 1. 목적

이 문서는 MAHA 대화모드가 고정된 Orchestrator/Main/Synthesis 구조로 굳지 않도록 하기 위한 설계 기준 문서다.

MAHA의 Worker는 앱 내부에 고정된 개체가 아니라, 사용자가 구성하고 편집할 수 있는 Worker Profile로 확장되어야 한다. Orchestrator, Main Worker, Synthesis Worker, RAG Worker, Tool Worker, Memory Worker, Code Check Worker 같은 이름은 기본 역할 템플릿이며, 실제 실행 구성은 사용자의 Scenario 또는 Worker Set 설정을 따른다.

이번 문서는 구현 코드가 아니라 후속 구현을 위한 설계 기준이다.

## 2. 설계 전제

- MAHA는 단순 채팅 앱이 아니라 가변 멀티 에이전트 실행 하네스다.
- Provider와 Model은 목적이 아니라 Worker의 실행 엔진이다.
- Worker의 System Instruction은 사용자 편집 가능해야 한다.
- Worker 개수와 역할은 고정하지 않는다.
- 기본 템플릿은 제공할 수 있지만 실제 실행 기준은 사용자 설정이다.
- 이번 단계에서는 Kotlin 코드, 저장 schema, UI, Provider 호출 로직을 변경하지 않는다.

## 3. 고정 Worker 구조 금지 원칙

금지되는 방향:

- Orchestrator 1개, Main 1개, Synthesis 1개로 영구 고정
- Worker 역할을 enum만으로 강제 제한
- System Instruction을 앱 내부 하드코딩 문구로 고정
- Worker별 Provider/Model 선택을 막는 구조
- Worker별 capability override를 막는 구조
- 병렬 실행 가능성을 구조적으로 닫는 설계

허용되는 방향:

- 기본 역할 템플릿 제공
- 사용자 정의 Worker 추가/삭제/수정
- 사용자 정의 역할 라벨 허용
- Worker별 System Instruction 편집
- Worker별 Provider/Model 지정
- Worker별 입력/출력 정책 지정
- Worker별 실행 순서와 의존성 지정

## 4. 기본 Worker 템플릿 정의

기본 템플릿은 초보 사용자를 위한 시작점이다. 템플릿 자체가 고정 실행 구조가 되어서는 안 된다.

후보 템플릿:

- Orchestrator
- Main Worker
- Synthesis Worker
- Research Worker
- RAG Worker
- Memory Worker
- Web Search Worker
- Tool Worker
- Code Check Worker
- Comparison Worker
- Reviewer Worker
- Format Worker

정책:

- 템플릿은 초기 생성용 기본값이다.
- 사용자는 템플릿을 수정하거나 삭제할 수 있다.
- 사용자는 새 Worker를 만들 수 있다.
- 후속 구현에서는 “기본 템플릿으로 복원” 기능을 둘 수 있다.

## 5. ConversationWorkerProfile 모델 후보

`ConversationWorkerProfile`은 사용자가 저장하는 Worker 구성값이다.

후보 필드:

```kotlin
ConversationWorkerProfile
- workerProfileId: String
- displayName: String
- roleLabel: String
- roleDescription: String
- systemInstruction: String
- providerId: String?
- modelId: String?
- capabilityOverrides: WorkerCapabilityOverrides
- inputPolicy: WorkerInputPolicy
- outputPolicy: WorkerOutputPolicy
- executionOrder: Int
- canRunInParallel: Boolean
- dependsOnWorkerIds: List<String>
- enabled: Boolean
- createdAt: Long
- updatedAt: Long
```

### 5-1. workerProfileId

- 안정적인 고유 ID다.
- Worker 이름이 변경되어도 유지된다.
- Scenario와 WorkerPlan은 이 ID를 참조할 수 있다.

### 5-2. displayName / roleLabel

- `displayName`: 사용자가 화면에서 보는 이름이다.
- `roleLabel`: Orchestrator, Researcher, Reviewer 같은 역할 라벨이다.
- `roleLabel`은 enum 고정값만 허용하지 말고 사용자 정의 문자열 가능성을 열어둔다.

예:

- displayName: “빠른 요청 분석가”
- roleLabel: “Orchestrator”
- displayName: “코드 리뷰어”
- roleLabel: “Code Reviewer”

### 5-3. roleDescription

- Worker가 맡는 역할을 설명한다.
- Orchestrator가 Worker 후보를 고를 때 참고할 수 있다.
- 사용자가 이해할 수 있는 문장이어야 한다.

### 5-4. systemInstruction

- Worker의 행동 기준이다.
- 반드시 사용자 편집 가능해야 한다.
- 실제 실행 시 Worker에 주입될 수 있는 핵심 설정이다.

### 5-5. providerId / modelId

- Worker별 Provider/Model 선택을 가능하게 한다.
- null이면 Scenario 기본값 또는 현재 대화 기본 모델을 fallback 후보로 사용할 수 있다.
- 후속 구현에서는 Provider/Model 삭제 시 연결 끊김 경고가 필요하다.

### 5-6. capabilityOverrides

- 모델 기본 capability와 별도로 Worker 단위 사용자 의도를 표현한다.
- override는 실제 실행 가능을 의미하지 않는다.
- 실제 실행 가능 여부는 Capability Layer가 판단한다.

### 5-7. inputPolicy / outputPolicy

- Worker가 어떤 입력을 받을지, 어떤 출력 형식을 요구할지 정의한다.
- Worker 간 결과 전달과 Synthesis 흐름의 기준이 된다.

### 5-8. executionOrder / canRunInParallel / dependsOnWorkerIds

- 순차/병렬/혼합 실행 계획의 기초 필드다.
- 실제 병렬 실행은 후속 구현이다.

### 5-9. enabled

- false이면 Scenario에 포함되어 있어도 실행 후보에서 제외할 수 있다.

## 6. System Instruction 정책

### 6-1. 사용자 편집 가능

System Instruction은 사용자가 수정할 수 있어야 한다. 앱 내부 고정 문구로 박으면 안 된다.

정책:

- 기본 instruction template은 제공 가능
- 사용자 수정본은 WorkerProfile의 실제 설정값
- 실행 시 기본 템플릿보다 사용자 설정을 우선
- 후속 구현에서 “기본값으로 리셋” 제공 가능

### 6-2. 기본 템플릿 후보

- Orchestrator 기본 instruction
- Main Worker 기본 instruction
- Synthesis Worker 기본 instruction
- Research Worker 기본 instruction
- Reviewer Worker 기본 instruction
- RAG Worker 기본 instruction
- Web Search Worker 기본 instruction
- Tool Worker 기본 instruction
- Code Check Worker 기본 instruction
- Comparison Worker 기본 instruction

### 6-3. 사용자 설정 우선 원칙

기본 템플릿은 시작점이다. 실제 실행 기준은 사용자가 저장한 WorkerProfile이다.

예:

- 앱 제공 템플릿: “응답을 검토하고 문제를 지적하라.”
- 사용자 수정본: “초보자가 이해하도록 위험도와 수정 우선순위를 함께 설명하라.”

실행은 사용자 수정본을 따라야 한다.

### 6-4. 리셋 가능성

후속 UI에서 다음 기능을 제공할 수 있다.

- 현재 instruction을 기본 템플릿으로 되돌리기
- 수정 전/후 비교
- Worker별 instruction 백업/복원

## 7. Worker별 Provider/Model 선택 정책

정책:

- 각 Worker는 독립적인 providerId/modelId를 가질 수 있다.
- Worker별로 서로 다른 Provider/Model을 사용할 수 있어야 한다.
- Worker의 역할에 따라 적합한 모델을 선택할 수 있어야 한다.

예:

- Orchestrator: 빠른 경량 모델
- Research Worker: 검색/RAG 적합 모델
- Code Check Worker: 코드 특화 모델
- Synthesis Worker: 긴 문맥과 정리 능력이 좋은 모델
- Reviewer Worker: 추론 성능이 좋은 모델

주의:

- Provider/Model은 Worker 실행 엔진이다.
- Provider/Model 관리는 WorkerProfile과 연결될 수 있어야 한다.
- Provider/Model 삭제 시 WorkerProfile 연결 상태 검증이 필요하다.

## 8. Capability override 정책

`WorkerCapabilityOverrides` 후보:

```kotlin
WorkerCapabilityOverrides
- functionCalling: CapabilityLayerStatus?
- webSearch: CapabilityLayerStatus?
- codeExecution: CapabilityLayerStatus?
- structuredOutput: CapabilityLayerStatus?
- thinkingSummary: CapabilityLayerStatus?
- ragSearch: CapabilityLayerStatus?
- memoryRecall: CapabilityLayerStatus?
- fileRead: CapabilityLayerStatus?
- fileWrite: CapabilityLayerStatus?
- codeCheck: CapabilityLayerStatus?
- parallelExecution: CapabilityLayerStatus?
```

정책:

- WorkerProfile override는 “이 Worker에서 이 capability를 쓰고 싶다”는 사용자 의도다.
- override가 켜져도 실제 실행 가능을 의미하지 않는다.
- Capability Layer는 Model capability, Provider policy, Worker override, 런타임 상태를 종합해 판단한다.
- 기존 ModelProfile capability와 WorkerProfile override를 혼동하지 않는다.

예:

- ModelProfile: webSearch UNKNOWN
- WorkerProfile override: webSearch USER_ENABLED
- Provider policy: OPENAI_COMPATIBLE Web Search 실행 미지원
- 최종 CapabilityResolution: LIMITED 또는 NOT_IMPLEMENTED

## 9. InputPolicy / OutputPolicy 후보

### 9-1. WorkerInputPolicy 후보

```kotlin
WorkerInputPolicy
- userInputOnly: Boolean
- previousWorkerOutput: Boolean
- selectedWorkerOutputIds: List<String>
- ragContextAllowed: Boolean
- memoryContextAllowed: Boolean
- webSearchContextAllowed: Boolean
- includeRunHistory: Boolean
- maxInputChars: Int?
```

의미:

- `userInputOnly`: 사용자 원문만 입력으로 받음
- `previousWorkerOutput`: 직전 Worker 결과를 입력으로 받을 수 있음
- `selectedWorkerOutputIds`: 특정 Worker 결과만 입력으로 받음
- `ragContextAllowed`: RAG context 사용 허용
- `memoryContextAllowed`: 장기 기억 context 사용 허용
- `webSearchContextAllowed`: 검색 결과 context 사용 허용
- `includeRunHistory`: 실행 로그/이전 단계 요약 포함 허용
- `maxInputChars`: 입력 길이 제한

### 9-2. WorkerOutputPolicy 후보

```kotlin
WorkerOutputPolicy
- expectedOutputType: CapabilityType
- requireJson: Boolean
- requireMarkdownTable: Boolean
- requireCodeBlock: Boolean
- allowPlainText: Boolean
- maxOutputChars: Int?
- passToNextWorker: Boolean
- exposeToUser: Boolean
- saveAsMemoryCandidate: Boolean
```

의미:

- `expectedOutputType`: 기대 출력 형식
- `requireJson`: JSON 출력 강제 후보
- `requireMarkdownTable`: Markdown table 출력 강제 후보
- `requireCodeBlock`: 코드블록 출력 강제 후보
- `allowPlainText`: 일반 텍스트 허용
- `passToNextWorker`: 다음 Worker 입력으로 전달
- `exposeToUser`: 사용자에게 중간 결과로 표시
- `saveAsMemoryCandidate`: 장기 기억 후보로 저장 가능

## 10. 실행 순서 / 병렬 / 의존성 설계

필드:

- executionOrder
- canRunInParallel
- dependsOnWorkerIds

정책:

- `executionOrder`는 순차 실행 후보 순서를 의미한다.
- `canRunInParallel=true`인 Worker는 의존성이 없을 때 병렬 실행 후보가 될 수 있다.
- `dependsOnWorkerIds`가 있으면 해당 Worker 결과 이후 실행되어야 한다.
- 의존성이 있는 Worker는 병렬 실행 후보에서 제외된다.

예:

```text
Research Worker 1: canRunInParallel=true, dependsOn=[]
Research Worker 2: canRunInParallel=true, dependsOn=[]
Synthesis Worker: canRunInParallel=false, dependsOn=[Research Worker 1, Research Worker 2]
```

주의:

- 이번 단계는 설계만 한다.
- 실제 병렬 실행 구현은 후속 단계다.

## 11. ConversationScenarioProfile / WorkerSet 설계

`ConversationScenarioProfile`은 WorkerProfile 묶음이다.

후보 필드:

```kotlin
ConversationScenarioProfile
- scenarioId: String
- name: String
- description: String
- workerProfileIds: List<String>
- defaultExecutionMode: ExecutionMode
- orchestratorProfileId: String?
- synthesisProfileId: String?
- userEditable: Boolean
- createdAt: Long
- updatedAt: Long
```

목적:

- 사용자가 자주 쓰는 Worker 구성을 저장한다.
- 대화 목적별 Worker 조합을 선택할 수 있게 한다.

예:

- 코딩 검토 세트
- 조사/요약 세트
- 글쓰기 세트
- 개인비서 세트
- RAG 중심 세트
- 비교/리뷰 세트

정책:

- Scenario는 WorkerProfile ID 목록을 참조한다.
- 기본 Scenario 템플릿은 제공 가능하다.
- 사용자 설정 Scenario를 우선한다.
- Scenario에 포함된 Worker 중 enabled=false인 Worker는 실행 후보에서 제외할 수 있다.

## 12. OrchestratorPlan / WorkerPlan과의 관계

구분:

- WorkerProfile: 저장된 사용자 설정
- WorkerPlan: 특정 요청에 대해 Orchestrator가 생성한 실행 계획
- WorkerRunResult: 실제 실행 결과

관계:

- OrchestratorPlan은 Scenario와 WorkerProfile 목록을 참고해 생성된다.
- WorkerPlan은 WorkerProfile을 참조할 수 있다.
- WorkerPlan은 WorkerProfile의 systemInstruction, providerId, modelId, capabilityOverrides, inputPolicy, outputPolicy를 기반으로 생성된다.
- WorkerRunResult는 WorkerPlan 실행 후 생성되는 결과다.

후속 검토:

- 현재 CapabilityLayerModels.kt의 WorkerPlan에 `workerProfileId` 필드 추가 여부
- WorkerRunResult 별도 모델 설계
- 실행 결과와 저장된 WorkerProfile의 분리

## 13. 저장 schema 변경 여부

이번 단계:

- 저장 schema 변경 없음
- Kotlin 코드 구현 없음
- JSON 저장/복원 구현 없음

후속 구현 시 후보 파일:

```text
MAHA/settings/worker_profiles.json
MAHA/settings/conversation_scenarios.json
```

후속 구현 시 고려:

- schemaVersion
- 기본 템플릿 초기화
- 사용자 수정본 우선 정책
- Provider/Model 삭제 시 orphan WorkerProfile 경고
- settings backup/restore 포함 여부
- migration 정책

## 14. 후속 구현 단계

권장 순서:

1. WorkerProfile/Scenario 저장 schema 상세 설계
2. WorkerProfile Kotlin model skeleton 추가
3. WorkerProfileStore skeleton 추가
4. 기본 Worker template 생성 정책 설계
5. Worker 관리 UI 설계
6. System Instruction 편집 UI 설계
7. Scenario / Worker Set 관리 UI 설계
8. CapabilityResolver가 WorkerProfile을 참고하는 구조 설계
9. OrchestratorPlan에 workerProfileId 연결
10. 실제 ConversationEngine 연결은 마지막 단계에서 별도 검토

## 15. 기존 기능 영향

이번 문서는 설계 기준 문서다.

- 코드 변경 없음
- 빌드 영향 없음
- Provider 호출 변경 없음
- RAG 변경 없음
- Web Search 변경 없음
- Capability Resolver 진단 UI 변경 없음
- 작업모드 변경 없음

## 16. 핵심 원칙 요약

- Worker는 고정 개체가 아니라 사용자 편집 가능한 Profile이다.
- Orchestrator/Main/Synthesis는 기본 템플릿이지 고정 실행 구조가 아니다.
- System Instruction은 사용자 편집 가능해야 한다.
- Worker별 Provider/Model 선택이 가능해야 한다.
- Worker별 capability override는 실제 실행 가능과 분리된다.
- Scenario는 WorkerProfile 묶음이다.
- WorkerProfile, WorkerPlan, WorkerRunResult는 서로 다른 개념이다.
