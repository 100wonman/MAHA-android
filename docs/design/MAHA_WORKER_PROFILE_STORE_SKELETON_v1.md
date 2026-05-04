# MAHA_WORKER_PROFILE_STORE_SKELETON_v1

## 1. 목적

이 문서는 MAHA 대화모드의 가변 Worker Profile과 Conversation Scenario를 후속 구현할 때 사용할 저장소 skeleton 기준을 정의한다.

이번 문서는 구현 코드가 아니다. 실제 `WorkerProfileStore.kt` 생성, JSON 파일 생성, 저장/로드 구현, SAF 백업/복원 코드 변경, ConversationEngine 연결은 수행하지 않는다.

## 2. 설계 전제

MAHA_CORE_INTENT_v1.2 기준을 따른다.

- Worker는 고정 개체가 아니다.
- Worker는 사용자가 추가, 삭제, 수정 가능한 Profile이다.
- System Instruction은 Worker별 사용자 편집 가능 설정값이다.
- Provider/Model은 Worker가 사용하는 실행 엔진이며 Worker별로 선택 가능해야 한다.
- 기본 Orchestrator/Main/Synthesis/RAG/Tool/Memory/Code Check 등은 고정 개체가 아니라 기본 역할 템플릿이다.
- WorkerProfileStore는 ProviderSettingsStore와 역할을 섞지 않는다.
- WorkerProfileStore는 Provider/Model 상세값을 복사하지 않고 `providerId`, `modelId` 참조만 관리한다.

## 3. WorkerProfileStore 역할

WorkerProfileStore는 후속 구현에서 다음 역할을 담당하는 저장소 계층 후보다.

- `worker_profiles.json` 로드
- `worker_profiles.json` 저장
- `conversation_scenarios.json` 로드
- `conversation_scenarios.json` 저장
- 기본 Worker 템플릿 초기화
- 기본 Conversation Scenario 초기화
- WorkerProfile 추가/수정/삭제
- ConversationScenario 추가/수정/삭제
- Provider/Model orphan 참조 검증
- schemaVersion 확인
- 백업/복원 대상 파일 식별

WorkerProfileStore는 API Key, Provider baseUrl, Model rawModelName, Provider 연결 테스트, Model 목록 조회, 실제 Worker 실행, ConversationEngine 호출을 담당하지 않는다.

## 4. 저장 파일

### 4-1. worker_profiles.json

역할:

- 사용자가 구성한 ConversationWorkerProfile 목록 저장
- Worker별 System Instruction 저장
- Worker별 Provider/Model 참조 저장
- Worker별 capability override 저장
- Worker별 입력/출력 정책 저장
- Worker별 실행 순서/병렬 가능 여부/의존성 저장

최상위 구조 후보:

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "workerProfiles": []
}
```

### 4-2. conversation_scenarios.json

역할:

- WorkerProfile 묶음 저장
- 대화 목적별 Worker Set 저장
- 기본 실행 방식 저장
- Orchestrator/Synthesis Worker 지정 저장

최상위 구조 후보:

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "scenarios": []
}
```

## 5. StoreEnvelope 처리

### 5-1. WorkerProfileStoreEnvelope

필드 후보:

- `schemaVersion: Int`
- `updatedAt: Long`
- `workerProfiles: List<ConversationWorkerProfile>`

정책:

- 기본 `schemaVersion = 1`
- `updatedAt`은 저장 시점 갱신
- 파일 없음이면 기본 envelope 또는 기본 템플릿 초기화 후보 생성
- 알 수 없는 필드는 후속 JSON parser 정책에 따라 무시 또는 보존 검토

### 5-2. ConversationScenarioStoreEnvelope

필드 후보:

- `schemaVersion: Int`
- `updatedAt: Long`
- `scenarios: List<ConversationScenarioProfile>`

정책:

- 기본 `schemaVersion = 1`
- `updatedAt`은 저장 시점 갱신
- 파일 없음이면 기본 Scenario envelope 후보 생성
- Scenario는 WorkerProfile을 중복 저장하지 않고 ID로만 참조한다.

## 6. schemaVersion 정책

- `worker_profiles.json.schemaVersion = 1`
- `conversation_scenarios.json.schemaVersion = 1`
- schemaVersion이 1이면 정상 로드
- schemaVersion이 누락되면 v1로 간주하거나 안전 실패 처리
- schemaVersion이 현재 앱보다 높으면 안전 실패 또는 읽기 전용 안내
- 알 수 없는 필드는 무시 가능
- 필수 필드 누락 시 기본값 보정 가능 여부를 판단

migration 원칙:

- 자동 삭제보다 보존 우선
- 사용자 수정본 보호 우선
- orphan 참조는 삭제하지 않고 제한 상태로 표시
- migration 실패 시 기존 파일 보존 우선
- 복원 가능한 오류는 사용자에게 안내

## 7. WorkerProfile 로드/저장 함수 후보

```kotlin
loadWorkerProfiles(): WorkerProfileStoreEnvelope
saveWorkerProfiles(envelope: WorkerProfileStoreEnvelope)
```

### loadWorkerProfiles

- `worker_profiles.json` 읽기
- 파일이 없으면 기본 envelope 생성
- 필요 시 기본 Worker 템플릿 초기화 후보 호출
- schemaVersion 확인
- JSON parse 실패 시 안전 실패 처리

정책:

- parse 실패 시 빈 목록으로 덮어쓰기 금지
- 기존 파일 보존 우선
- 사용자에게 복구 가능 상태 제공

### saveWorkerProfiles

- WorkerProfileStoreEnvelope 저장
- `updatedAt` 갱신
- 기존 파일 손상 방지
- API Key/baseUrl/model raw data 저장 금지

## 8. ConversationScenario 로드/저장 함수 후보

```kotlin
loadConversationScenarios(): ConversationScenarioStoreEnvelope
saveConversationScenarios(envelope: ConversationScenarioStoreEnvelope)
```

### loadConversationScenarios

- `conversation_scenarios.json` 읽기
- 파일이 없으면 기본 Scenario envelope 생성
- schemaVersion 확인
- Scenario가 참조하는 WorkerProfile 존재 여부 검증은 별도 함수에서 수행

### saveConversationScenarios

- ConversationScenarioStoreEnvelope 저장
- `updatedAt` 갱신
- Scenario는 WorkerProfile ID 목록만 저장
- WorkerProfile 본문 중복 저장 금지

## 9. WorkerProfile CRUD 함수 후보

```kotlin
getWorkerProfile(workerProfileId: String): ConversationWorkerProfile?
upsertWorkerProfile(profile: ConversationWorkerProfile): ConversationWorkerProfile
deleteWorkerProfile(workerProfileId: String): Boolean
listEnabledWorkerProfiles(): List<ConversationWorkerProfile>
listDefaultWorkerTemplates(): List<ConversationWorkerProfile>
```

정책:

- `getWorkerProfile`: workerProfileId 기준 단일 조회
- `upsertWorkerProfile`: 같은 ID가 있으면 업데이트, 없으면 추가
- `deleteWorkerProfile`: 삭제 전 Scenario 참조 여부 확인 필요
- `listEnabledWorkerProfiles`: `enabled = true`인 Worker 반환
- `listDefaultWorkerTemplates`: `isDefaultTemplate = true`인 Worker 반환

삭제 정책 1차 권장:

- 삭제 전 확인 Dialog
- 참조 중인 Scenario 안내
- 자동 cascade 삭제 금지

## 10. ConversationScenario CRUD 함수 후보

```kotlin
getScenario(scenarioId: String): ConversationScenarioProfile?
upsertScenario(scenario: ConversationScenarioProfile): ConversationScenarioProfile
deleteScenario(scenarioId: String): Boolean
listEnabledScenarios(): List<ConversationScenarioProfile>
getDefaultScenario(): ConversationScenarioProfile?
```

정책:

- Scenario 삭제는 WorkerProfile 자체를 삭제하지 않는다.
- WorkerProfile은 독립 설정으로 유지한다.
- 기본 Scenario 결정은 `isDefaultTemplate` 또는 후속 `defaultScenarioId` 정책으로 분리 검토한다.

## 11. 기본 Worker 템플릿 초기화 함수 후보

```kotlin
ensureDefaultWorkerTemplates()
createDefaultWorkerTemplates(): List<ConversationWorkerProfile>
```

기본 Worker 템플릿 후보:

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

- `worker_profiles.json`이 없거나 비어 있을 때 기본 템플릿 생성 가능
- 앱 업데이트 시 새 기본 템플릿 추가 가능
- 기존 사용자 수정본 덮어쓰기 금지
- `userModified = true`인 WorkerProfile은 절대 자동 덮어쓰기 금지
- `isDefaultTemplate = true`라도 사용자가 수정했으면 사용자 설정 우선

기본값 복원:

- 후속 UI에서 명시적 사용자 동작으로만 수행
- 전체 복원과 개별 Worker 복원 모두 후속 후보
- 복원 시 확인 Dialog 필요

## 12. 기본 Conversation Scenario 초기화 함수 후보

```kotlin
ensureDefaultScenarios()
createDefaultConversationScenarios(): List<ConversationScenarioProfile>
```

기본 Scenario 후보:

- 기본 단일 대화 Scenario
- 조사/요약 Scenario
- 코드 검토 Scenario
- 글쓰기/리뷰 Scenario
- RAG 중심 Scenario
- 비교/병렬 분석 Scenario

정책:

- `conversation_scenarios.json`이 없거나 비어 있을 때 기본 Scenario 생성 가능
- 기본 Scenario는 기본 Worker 템플릿 ID를 참조할 수 있음
- WorkerProfile이 없으면 기본 Worker 템플릿 초기화가 먼저 필요
- 사용자 수정본 덮어쓰기 금지

## 13. 사용자 수정본 보존 정책

### userModified

`userModified = true`는 다음을 의미한다.

- 사용자가 Worker/Scenario를 수정함
- 앱 업데이트나 기본 템플릿 갱신으로 덮어쓰면 안 됨
- 기본값 복원은 명시적 사용자 동작이 있어야 함

### isDefaultTemplate

`isDefaultTemplate = true`는 다음을 의미한다.

- 앱이 제공한 기본 템플릿에서 시작됨
- 사용자가 수정할 수 있음
- 기본 템플릿이라는 출처 표시 가능

### 앱 업데이트 정책

- 새 기본 템플릿 추가 가능
- 기존 기본 템플릿의 사용자 수정본 덮어쓰기 금지
- 기본 템플릿 문구가 업데이트되어도 `userModified=true` 항목은 보존
- update conflict는 후속 UI에서 “새 기본 템플릿으로 갱신” 선택 제공 가능

## 14. Provider/Model orphan 참조 처리

orphan 상황:

- WorkerProfile.providerId가 존재하지 않음
- WorkerProfile.modelId가 존재하지 않음
- Scenario.workerProfileIds 중 일부가 존재하지 않음
- Scenario.orchestratorProfileId가 존재하지 않음
- Scenario.synthesisProfileId가 존재하지 않음

검증 함수 후보:

```kotlin
validateWorkerProfileReferences(
    workerProfiles: List<ConversationWorkerProfile>,
    providerIds: Set<String>,
    modelIds: Set<String>
): List<WorkerProfileReferenceIssue>

validateScenarioReferences(
    scenarios: List<ConversationScenarioProfile>,
    workerProfileIds: Set<String>
): List<ScenarioReferenceIssue>

findOrphanProviderReferences(...): List<String>
findOrphanModelReferences(...): List<String>
```

처리 정책:

- 자동 삭제 금지
- WorkerProfile/Scenario는 보존
- 실행 후보에서는 제한 상태 표시
- 사용자가 Provider/Model/Worker를 재지정할 수 있어야 함

사용자 메시지 후보:

- “이 Worker가 참조하는 Provider를 찾을 수 없습니다.”
- “이 Worker가 참조하는 Model을 찾을 수 없습니다.”
- “이 Scenario에 포함된 일부 Worker를 찾을 수 없습니다.”
- “Orchestrator Worker를 찾을 수 없습니다.”
- “Synthesis Worker를 찾을 수 없습니다.”

## 15. SAF 백업/복원 연결 시점

후속 SAF 포함 대상:

- `worker_profiles.json`
- `conversation_scenarios.json`

권장 후속 순서:

1. WorkerProfileStore 실제 구현
2. WorkerProfile 관리 UI 구현
3. 저장/로드 안정화
4. 설정 백업/복원에 Worker/Scenario 파일 포함
5. 복원 후 orphan 검증 UI 추가

복원 정책:

- Provider/Model 설정과 함께 복원되는 것이 바람직함
- WorkerProfile만 복원될 수 있으므로 orphan 검증 필요
- Scenario만 복원될 수 있으므로 missing Worker 검증 필요
- 복원 실패 시 전체 앱이 깨지지 않아야 함
- schemaVersion 불일치 시 migration 또는 안전 fallback 필요

## 16. 오류 처리 정책

오류 후보:

- 파일 없음
- JSON parse 실패
- schemaVersion 불일치
- 필수 필드 누락
- 중복 ID
- orphan Provider/Model 참조
- Scenario의 missing Worker 참조
- 저장 실패

처리 정책:

- 파일 없음: 기본 envelope 또는 기본 템플릿 후보 생성
- JSON parse 실패: 안전 실패 + 기존 파일 보존
- schemaVersion 불일치: migration 가능 여부 판단, 불가 시 안전 실패
- 필수 필드 누락: 기본값 보정 가능 여부 검토
- 중복 ID: 충돌 보고 후 보존 우선
- orphan 참조: 삭제하지 않고 제한 상태 표시
- 저장 실패: 기존 파일 보존 우선

## 17. 후속 구현 단계

권장 순서:

1. `WorkerProfileStore.kt` skeleton 추가
2. `worker_profiles.json` / `conversation_scenarios.json` 실제 로드/저장 구현
3. 기본 Worker 템플릿 생성
4. 기본 Conversation Scenario 생성
5. Provider/Model orphan 검증 함수 구현
6. WorkerProfile 관리 UI 설계
7. System Instruction 편집 UI 설계
8. Scenario / WorkerSet 관리 UI 설계
9. Capability Resolver와 WorkerProfile 연결
10. ConversationEngine 연결 설계
11. SAF 백업/복원 포함 구현

## 18. 기존 기능 영향

이번 문서는 설계 기준만 정의한다.

- 코드 변경 없음
- 빌드 영향 없음
- Provider 호출 변경 없음
- RAG 변경 없음
- Web Search 변경 없음
- Capability Resolver 진단 UI 변경 없음
- 작업모드 변경 없음
