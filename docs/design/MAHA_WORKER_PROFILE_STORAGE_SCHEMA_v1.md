# MAHA_WORKER_PROFILE_STORAGE_SCHEMA_v1

## 1. 목적

이 문서는 MAHA 대화모드의 가변 Worker Profile과 Conversation Scenario / Worker Set을 저장하기 위한 후속 구현 기준 문서다.

MAHA v1.2 기준에서 Worker는 고정 개체가 아니다. Orchestrator, Main Worker, Synthesis Worker, RAG Worker, Tool Worker, Memory Worker, Web Search Worker, Code Check Worker 같은 이름은 기본 역할 템플릿이며, 실제 Worker 구성은 사용자가 추가, 삭제, 수정, 비활성화할 수 있어야 한다.

이번 문서는 다음을 정의한다.

- `worker_profiles.json`의 역할과 schema
- `conversation_scenarios.json`의 역할과 schema
- 기본 Worker 템플릿과 사용자 수정본의 관계
- WorkerProfile과 ProviderProfile / ModelProfile 참조 관계
- Worker별 System Instruction 저장 정책
- Worker별 capability override 저장 정책
- Scenario / WorkerSet 저장 정책
- SAF 백업/복원 포함 정책
- schemaVersion 및 migration 초안

이번 단계는 설계 문서 작성만 수행한다. 실제 Kotlin 코드, 저장/로드 코드, JsonStore, ProviderSettingsStore, ConversationEngine, ConversationViewModel, Worker 관리 UI, System Instruction 편집 UI는 변경하지 않는다.

---

## 2. 설계 전제

### 2.1 Worker는 고정 개체가 아니다

MAHA는 고정된 3-worker 채팅 구조가 아니다.

Worker는 사용자가 구성 가능한 Profile이다. 앱은 기본 Worker 템플릿을 제공할 수 있지만, 실제 실행 기준은 사용자 설정값이어야 한다.

### 2.2 System Instruction은 사용자 편집 가능해야 한다

System Instruction은 앱 내부에 고정된 문구로 박으면 안 된다. 기본 템플릿은 시작점일 뿐이며, 사용자가 수정한 instruction은 앱 업데이트로 덮어쓰지 않는다.

### 2.3 Provider / Model은 Worker의 실행 엔진이다

WorkerProfile은 Provider/Model 정보를 복사해서 저장하지 않는다. `providerId`, `modelId`만 참조한다. Provider/Model 상세 정보는 기존 `provider_profiles.json`, `model_profiles.json`에 남긴다.

### 2.4 WorkerProfile과 WorkerPlan은 다르다

- WorkerProfile: 사용자가 저장한 Worker 설정
- WorkerPlan: 특정 요청에 대해 Orchestrator가 생성한 실행 계획
- WorkerRunResult: 실제 실행 결과

이 문서는 WorkerProfile과 Scenario 저장 schema를 설계한다. WorkerPlan과 WorkerRunResult의 실제 저장/실행 구조는 후속 단계에서 다룬다.

---

## 3. 저장 파일 목록

후속 구현 시 앱 전용 저장소의 설정 영역에 다음 파일을 추가하는 것을 권장한다.

```text
MAHA/settings/
├─ provider_profiles.json            기존 Provider 설정
├─ provider_api_keys.json            기존 API Key 저장소, 별도 보안 정책
├─ model_profiles.json               기존 Model 설정
├─ worker_profiles.json              신규 후보
└─ conversation_scenarios.json       신규 후보
```

### 3.1 worker_profiles.json

역할:

- 사용자가 구성한 ConversationWorkerProfile 목록 저장
- Worker별 System Instruction 저장
- Worker별 Provider/Model 참조 저장
- Worker별 capability override 저장
- Worker별 입력/출력 정책 저장
- Worker별 실행 순서, 병렬 가능 여부, 의존성 저장

### 3.2 conversation_scenarios.json

역할:

- WorkerProfile 묶음 저장
- 대화 목적별 Worker Set 저장
- 기본 실행 방식 저장
- Orchestrator / Synthesis Worker 지정 저장
- 사용자 편집 가능한 Scenario 템플릿 저장

---

## 4. worker_profiles.json schema

### 4.1 최상위 구조 후보

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "workerProfiles": []
}
```

필드:

| 필드 | 타입 | 설명 |
|---|---:|---|
| schemaVersion | Int | worker_profiles.json schema version |
| updatedAt | Long | 파일 최종 갱신 시각 epoch millis |
| workerProfiles | Array | ConversationWorkerProfile 목록 |

### 4.2 ConversationWorkerProfile 후보

```json
{
  "workerProfileId": "worker_orchestrator_default",
  "displayName": "Orchestrator",
  "roleLabel": "ORCHESTRATOR",
  "roleDescription": "사용자 요청을 분석하고 실행 계획을 세우는 Worker",
  "systemInstruction": "사용자 요청을 capability와 worker plan으로 분석한다.",
  "providerId": null,
  "modelId": null,
  "capabilityOverrides": {},
  "inputPolicy": {},
  "outputPolicy": {},
  "executionOrder": 0,
  "canRunInParallel": false,
  "dependsOnWorkerIds": [],
  "enabled": true,
  "isDefaultTemplate": true,
  "userModified": false,
  "defaultSystemInstructionVersion": 1,
  "createdAt": 0,
  "updatedAt": 0
}
```

### 4.3 WorkerProfile 필드 정의

| 필드 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| workerProfileId | String | 예 | WorkerProfile 고유 ID. 이름 변경과 무관하게 유지 |
| displayName | String | 예 | 사용자가 보는 Worker 이름 |
| roleLabel | String | 예 | 역할 라벨. enum 고정이 아니라 사용자 정의 문자열 가능성을 열어둠 |
| roleDescription | String | 아니오 | Worker 역할 설명 |
| systemInstruction | String | 예 | 사용자 편집 가능한 System Instruction |
| providerId | String? | 아니오 | 참조 Provider ID. 복사본 저장 금지 |
| modelId | String? | 아니오 | 참조 Model ID. 복사본 저장 금지 |
| capabilityOverrides | Object | 예 | Worker 단위 capability override |
| inputPolicy | Object | 예 | Worker 입력 정책 |
| outputPolicy | Object | 예 | Worker 출력 정책 |
| executionOrder | Int | 예 | 순차 실행 후보 순서 |
| canRunInParallel | Boolean | 예 | 병렬 실행 후보 여부 |
| dependsOnWorkerIds | Array<String> | 예 | 선행 Worker 의존성 |
| enabled | Boolean | 예 | 비활성 Worker는 실행 후보에서 제외 가능 |
| isDefaultTemplate | Boolean | 예 | 앱이 제공한 기본 템플릿 여부 |
| userModified | Boolean | 예 | 사용자가 수정했는지 여부 |
| defaultSystemInstructionVersion | Int? | 아니오 | 기본 instruction template version |
| createdAt | Long | 예 | 생성 시각 |
| updatedAt | Long | 예 | 수정 시각 |

---

## 5. System Instruction 저장 정책

### 5.1 저장 위치

`systemInstruction`은 WorkerProfile에 저장한다.

### 5.2 기본 템플릿과 사용자 수정본

기본 템플릿은 앱이 제공할 수 있다.

기본 템플릿 후보:

- Orchestrator
- Main Worker
- Synthesis Worker
- Research Worker
- Reviewer Worker
- RAG Worker
- Memory Worker
- Web Search Worker
- Tool Worker
- Code Check Worker
- Comparison Worker

정책:

- 기본 템플릿은 시작점이다.
- 사용자는 기본 템플릿의 System Instruction도 수정할 수 있어야 한다.
- 사용자가 수정한 System Instruction은 앱 업데이트로 덮어쓰지 않는다.
- `userModified=true`인 WorkerProfile은 기본 템플릿 갱신 대상에서 제외한다.
- 후속 UI에서 “기본값으로 복원” 기능을 제공할 수 있다.

### 5.3 민감정보 주의

System Instruction에는 API Key, token, 비밀번호, 개인 식별 정보 같은 민감정보를 넣지 않도록 UI 안내가 필요하다. 이번 단계에서는 UI를 구현하지 않는다.

---

## 6. capabilityOverrides 저장 정책

### 6.1 구조 후보

```json
{
  "functionCalling": "UNKNOWN",
  "webSearch": "UNKNOWN",
  "codeExecution": "UNKNOWN",
  "structuredOutput": "UNKNOWN",
  "thinkingSummary": "UNKNOWN",
  "ragSearch": "UNKNOWN",
  "memoryRecall": "UNKNOWN",
  "fileRead": "UNKNOWN",
  "fileWrite": "UNKNOWN",
  "codeCheck": "UNKNOWN",
  "parallelExecution": "UNKNOWN"
}
```

### 6.2 정책

- Worker override는 ModelProfile capability V2와 별도다.
- Worker override는 “이 Worker에서 이 capability를 쓰고 싶다”는 사용자 의도다.
- override가 `USER_ENABLED`여도 실제 실행 가능을 의미하지 않는다.
- 실제 실행 가능 여부는 Capability Layer가 다음을 종합해 판단한다.
  - ProviderType 기본 정책
  - ModelProfile capabilitiesV2
  - WorkerProfile capabilityOverrides
  - 사용자 runtime 설정
  - MAHA-native 구현 상태
  - Tool Registry 구현 상태

### 6.3 기존 CapabilityStatus와 충돌 방지

프로젝트에 이미 Model capability용 `CapabilityStatus`가 존재한다. WorkerProfile 저장 schema에서는 문자열 enum값을 저장하되, Kotlin 모델 구현 시 이름 충돌을 피하기 위해 `WorkerCapabilityOverrideStatus` 또는 기존 `CapabilityLayerStatus` 같은 별도 이름을 검토한다.

---

## 7. InputPolicy / OutputPolicy schema

### 7.1 inputPolicy 후보

```json
{
  "userInputOnly": true,
  "previousWorkerOutput": false,
  "selectedWorkerOutputIds": [],
  "ragContextAllowed": false,
  "memoryContextAllowed": false,
  "webSearchContextAllowed": false,
  "maxInputChars": null,
  "includeRunHistory": false
}
```

필드:

| 필드 | 타입 | 설명 |
|---|---:|---|
| userInputOnly | Boolean | 사용자 원문 입력만 받을지 여부 |
| previousWorkerOutput | Boolean | 직전 Worker 결과를 입력으로 받을지 여부 |
| selectedWorkerOutputIds | Array<String> | 특정 Worker 결과만 받을 경우 참조 ID 후보 |
| ragContextAllowed | Boolean | RAG context 포함 허용 |
| memoryContextAllowed | Boolean | Memory context 포함 허용 |
| webSearchContextAllowed | Boolean | Web Search 결과 포함 허용 |
| maxInputChars | Int? | Worker 입력 최대 길이 |
| includeRunHistory | Boolean | 실행 history 포함 여부 |

### 7.2 outputPolicy 후보

```json
{
  "expectedOutputType": "TEXT_GENERATION",
  "requireJson": false,
  "requireMarkdownTable": false,
  "requireCodeBlock": false,
  "allowPlainText": true,
  "maxOutputChars": null,
  "passToNextWorker": true,
  "exposeToUser": true,
  "saveAsMemoryCandidate": false
}
```

필드:

| 필드 | 타입 | 설명 |
|---|---:|---|
| expectedOutputType | String | 예상 출력 capability type |
| requireJson | Boolean | JSON 출력 강제 후보 |
| requireMarkdownTable | Boolean | Markdown table 출력 강제 후보 |
| requireCodeBlock | Boolean | code block 출력 강제 후보 |
| allowPlainText | Boolean | plain text 허용 여부 |
| maxOutputChars | Int? | 출력 최대 길이 |
| passToNextWorker | Boolean | 다음 Worker로 전달 여부 |
| exposeToUser | Boolean | 사용자에게 표시 여부 |
| saveAsMemoryCandidate | Boolean | Memory 후보 저장 여부 |

---

## 8. ProviderProfile / ModelProfile 참조 정책

### 8.1 참조 방식

WorkerProfile은 `providerId`, `modelId`만 저장한다.

금지:

- API Key 저장
- baseUrl 복사 저장
- rawModelName 복사 저장
- Provider/Model 전체 JSON 중복 저장

### 8.2 orphan Provider 처리

Provider가 삭제되었지만 WorkerProfile이 해당 providerId를 참조하는 경우:

- WorkerProfile은 자동 삭제하지 않는다.
- WorkerProfile은 유지한다.
- 실행 후보에서는 제한 상태로 표시한다.
- 사용자에게 Provider 재지정을 안내한다.

사용자 문구 후보:

```text
이 Worker가 참조하는 Provider를 찾을 수 없습니다. Provider를 다시 지정하세요.
```

### 8.3 orphan Model 처리

Model이 삭제되었지만 WorkerProfile이 해당 modelId를 참조하는 경우:

- WorkerProfile은 자동 삭제하지 않는다.
- WorkerProfile은 유지한다.
- 실행 후보에서는 제한 상태로 표시한다.
- 사용자에게 Model 재지정을 안내한다.

사용자 문구 후보:

```text
이 Worker가 참조하는 Model을 찾을 수 없습니다. Model을 다시 지정하세요.
```

---

## 9. conversation_scenarios.json schema

### 9.1 최상위 구조 후보

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "scenarios": []
}
```

필드:

| 필드 | 타입 | 설명 |
|---|---:|---|
| schemaVersion | Int | conversation_scenarios.json schema version |
| updatedAt | Long | 파일 최종 갱신 시각 epoch millis |
| scenarios | Array | ConversationScenarioProfile 목록 |

### 9.2 ConversationScenarioProfile 후보

```json
{
  "scenarioId": "scenario_default_general",
  "name": "기본 대화 세트",
  "description": "일반 대화용 기본 Worker Set",
  "workerProfileIds": [
    "worker_orchestrator_default",
    "worker_main_default",
    "worker_synthesis_default"
  ],
  "defaultExecutionMode": "SINGLE",
  "orchestratorProfileId": "worker_orchestrator_default",
  "synthesisProfileId": "worker_synthesis_default",
  "userEditable": true,
  "isDefaultTemplate": true,
  "userModified": false,
  "enabled": true,
  "createdAt": 0,
  "updatedAt": 0
}
```

### 9.3 Scenario 필드 정의

| 필드 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| scenarioId | String | 예 | Scenario 고유 ID |
| name | String | 예 | 사용자 표시 이름 |
| description | String | 아니오 | Scenario 설명 |
| workerProfileIds | Array<String> | 예 | Scenario에 포함된 WorkerProfile ID 목록 |
| defaultExecutionMode | String | 예 | SINGLE / SEQUENTIAL / PARALLEL / MIXED |
| orchestratorProfileId | String? | 아니오 | Orchestrator 역할 WorkerProfile 참조 |
| synthesisProfileId | String? | 아니오 | Synthesis 역할 WorkerProfile 참조 |
| userEditable | Boolean | 예 | 사용자 편집 가능 여부 |
| isDefaultTemplate | Boolean | 예 | 앱 제공 기본 Scenario 여부 |
| userModified | Boolean | 예 | 사용자 수정 여부 |
| enabled | Boolean | 예 | 사용 가능 여부 |
| createdAt | Long | 예 | 생성 시각 |
| updatedAt | Long | 예 | 수정 시각 |

### 9.4 Scenario 정책

- Scenario는 WorkerProfile을 참조한다.
- WorkerProfile 자체를 중복 저장하지 않는다.
- 기본 Scenario도 사용자가 복사/수정 가능해야 한다.
- `userModified=true`인 Scenario는 앱 업데이트로 덮어쓰지 않는다.
- Scenario에 포함된 WorkerProfile이 삭제되면 orphan Worker 참조로 표시하고 자동 삭제하지 않는다.

---

## 10. 기본 Worker 템플릿 초기화 정책

### 10.1 초기화 시점

후속 구현 시 다음 조건에서 기본 템플릿을 생성할 수 있다.

- `worker_profiles.json`이 없음
- `workerProfiles`가 비어 있음
- 사용자가 “기본 Worker 템플릿 다시 생성”을 명시적으로 선택

### 10.2 기본 템플릿 후보

- Orchestrator
- Main Worker
- Synthesis Worker
- Research Worker
- Reviewer Worker
- RAG Worker
- Memory Worker
- Web Search Worker
- Tool Worker
- Code Check Worker
- Comparison Worker

### 10.3 덮어쓰기 방지

정책:

- 기본 템플릿 생성은 최초 실행 또는 명시적 복원에서만 수행한다.
- 기존 `workerProfileId`가 존재하면 자동 덮어쓰지 않는다.
- `userModified=true`인 WorkerProfile은 앱 업데이트로 덮어쓰지 않는다.
- 새로운 기본 템플릿이 추가될 경우 기존 사용자 수정본은 유지하고 새 템플릿만 추가한다.

### 10.4 기본값 복원 후보

후속 UI에서 다음 기능을 둘 수 있다.

- 현재 Worker를 기본 instruction으로 복원
- 기본 Worker 템플릿 전체 다시 생성
- 기본 Scenario 템플릿 전체 다시 생성

주의:

- 기본값 복원은 사용자 확인 Dialog가 필요하다.
- 복원 전에 현재 사용자 설정 백업을 권장한다.

---

## 11. 백업/복원 정책

### 11.1 SAF 백업 포함 대상

후속 설정 백업/복원에 다음 파일을 포함하는 것을 권장한다.

- `worker_profiles.json`
- `conversation_scenarios.json`

### 11.2 API Key와의 관계

WorkerProfile은 API Key를 저장하지 않는다. API Key는 기존 `provider_api_keys.json` 보안 정책을 따른다.

정책:

- WorkerProfile / Scenario는 SAF 백업 대상에 포함 가능
- API Key는 기본 백업 제외 유지
- 복원 후 Provider API Key는 사용자가 다시 입력해야 할 수 있음

### 11.3 복원 방식

권장 1차 복원 방식:

- 병합 복원
- 같은 `workerProfileId`가 있으면 skip
- 같은 `scenarioId`가 있으면 skip
- restored/skipped 개수 표시

후속 옵션:

- 덮어쓰기 복원
- 이름 충돌 시 새 ID로 복사 복원
- orphan Provider/Model 자동 점검 리포트

### 11.4 복원 후 검증

복원 후 확인 항목:

- WorkerProfile 목록 reload
- Scenario 목록 reload
- Provider orphan 여부
- Model orphan 여부
- Scenario가 참조하는 WorkerProfile orphan 여부
- enabled Worker 수
- default Scenario 존재 여부

---

## 12. migration 정책 초안

### 12.1 schemaVersion

- `worker_profiles.json schemaVersion = 1`
- `conversation_scenarios.json schemaVersion = 1`

### 12.2 schemaVersion 누락

정책 후보:

- schemaVersion이 없으면 v1로 간주하거나 안전 실패 처리
- 사용자 데이터 보존을 우선한다
- 자동 삭제 금지

### 12.3 알 수 없는 필드

- 알 수 없는 필드는 무시한다.
- 이후 버전 호환성을 위해 unknown field를 삭제하지 않는 방식도 검토 가능하다.

### 12.4 필수 필드 누락

필수 필드 누락 시 기본값 후보:

| 필드 | 기본값 |
|---|---|
| workerProfileId | 새 ID 생성 또는 복원 실패 |
| displayName | "이름 없는 Worker" |
| roleLabel | "CUSTOM" |
| systemInstruction | 빈 문자열 또는 기본 템플릿 |
| providerId | null |
| modelId | null |
| capabilityOverrides | 모든 항목 UNKNOWN |
| inputPolicy | 기본 input policy |
| outputPolicy | 기본 output policy |
| enabled | true |
| userModified | true |

### 12.5 orphan 처리

- providerId orphan: 삭제하지 않고 제한 표시
- modelId orphan: 삭제하지 않고 제한 표시
- workerProfileIds orphan: Scenario는 유지하고 누락 Worker를 경고 표시
- orchestratorProfileId orphan: Scenario 실행 제한
- synthesisProfileId orphan: Synthesis optional이면 제한, 필수이면 실행 제한

### 12.6 보존 우선 원칙

Migration은 자동 삭제보다 보존을 우선한다. 사용자가 직접 복구할 수 있도록 상태와 제한 사유를 표시한다.

---

## 13. 기존 파일과 역할 분리

### 13.1 provider_profiles.json

역할:

- ProviderType
- displayName
- baseUrl
- modelListEndpoint
- 활성 상태
- Provider 설정

WorkerProfile은 providerId만 참조한다.

### 13.2 model_profiles.json

역할:

- rawModelName
- displayName
- Model capability V2
- contextWindow
- input/output modalities
- providerId 연결

WorkerProfile은 modelId만 참조한다.

### 13.3 worker_profiles.json

역할:

- Worker 역할
- System Instruction
- Worker capability override
- Worker input/output policy
- Worker execution policy

### 13.4 conversation_scenarios.json

역할:

- WorkerProfile 조합
- 실행 방식 기본값
- Orchestrator/Synthesis 지정

---

## 14. 후속 구현 단계

권장 구현 순서:

1. WorkerProfile / Scenario 저장 모델 Kotlin skeleton 설계
2. WorkerProfileStore 설계
3. 기본 Worker 템플릿 초기화 로직 설계
4. Worker 관리 UI 설계
5. System Instruction 편집 UI 설계
6. Scenario / Worker Set 관리 UI 설계
7. Capability Resolver와 WorkerProfile 연결 설계
8. OrchestratorPlan에 workerProfileId 참조 추가 검토
9. SAF 설정 백업/복원에 worker/scenario 포함
10. 실제 ConversationEngine 연결은 마지막 단계에서 별도 지시문으로 진행

---

## 15. 이번 단계의 비적용 범위

이번 단계에서는 다음을 하지 않는다.

- 실제 Kotlin 코드 구현
- 저장/로드 코드 구현
- JsonStore 구현 변경
- ProviderSettingsStore 변경
- model_profiles.json 변경
- provider_profiles.json 변경
- ConversationEngine 연결
- ConversationViewModel 연결
- Worker 관리 UI 구현
- System Instruction 편집 UI 구현
- 저장 schema 실제 적용
- 병렬 실행 구현
- Tool 실행 구현
- RAG/Web Search 구조 변경
- 작업모드 수정

---

## 16. 결론

Worker Profile 저장 schema는 MAHA v1.2의 핵심 방향인 “가변 멀티 에이전트 하네스”를 저장 구조로 뒷받침하기 위한 기반이다.

`worker_profiles.json`은 사용자 편집 가능한 Worker 자체를 저장한다.

`conversation_scenarios.json`은 WorkerProfile 조합과 실행 방식 기본값을 저장한다.

Provider/Model은 Worker의 실행 엔진이며, WorkerProfile은 Provider/Model 전체를 복사하지 않고 ID로 참조한다.

System Instruction은 WorkerProfile의 핵심 사용자 설정값이며 앱 업데이트로 덮어쓰면 안 된다.

백업/복원은 WorkerProfile과 Scenario를 포함해야 하지만 API Key는 기존 보안 정책대로 기본 제외한다.

후속 구현은 저장 모델 skeleton → Store → 기본 템플릿 → 관리 UI → Capability Resolver 연결 → Orchestrator 연결 순서로 진행하는 것이 안전하다.
