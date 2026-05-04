# MAHA_WORKER_PROFILE_STORE_IMPLEMENTATION_DESIGN_v1

## 1. 목적

이 문서는 MAHA 대화모드의 가변 Worker Profile과 Conversation Scenario를 실제 JSON 파일로 저장·로드하기 위한 후속 구현 기준을 정의한다.

이번 문서는 설계 기준 문서이며 실제 Kotlin 코드 구현, JsonStore 변경, SAF 백업/복원 코드 변경, ConversationEngine 연결, Worker 관리 UI 저장 연결은 포함하지 않는다.

## 2. 설계 전제

MAHA_CORE_INTENT_v1.2 기준을 따른다.

Worker는 고정 개체가 아니라 사용자가 추가, 삭제, 수정 가능한 Profile이다.

System Instruction은 Worker별 사용자 편집 가능 설정값이다.

Provider와 Model은 Worker가 사용하는 실행 엔진이며 WorkerProfile에는 providerId와 modelId 참조만 저장한다.

WorkerProfileStore는 ProviderSettingsStore와 역할을 분리한다. ProviderSettingsStore는 Provider/Model 설정을 관리하고, WorkerProfileStore는 WorkerProfile/Scenario 설정을 관리한다.

## 3. 저장 파일 경로/역할

후속 구현의 저장 파일 후보는 앱 전용 저장소의 설정 영역이다.

```text
MAHA/settings/
├─ worker_profiles.json
└─ conversation_scenarios.json
```

### worker_profiles.json

역할:

- ConversationWorkerProfile 목록 저장
- Worker별 System Instruction 저장
- Worker별 providerId/modelId 참조 저장
- Worker별 capabilityOverrides 저장
- Worker별 inputPolicy/outputPolicy 저장
- Worker별 실행 순서, 병렬 가능 여부, 의존성 저장

최상위 구조 후보:

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "workerProfiles": []
}
```

### conversation_scenarios.json

역할:

- ConversationScenarioProfile 목록 저장
- Scenario별 WorkerProfile 묶음 저장
- 기본 실행 방식 저장
- Orchestrator/Synthesis profile 참조 저장

최상위 구조 후보:

```json
{
  "schemaVersion": 1,
  "updatedAt": 0,
  "scenarios": []
}
```

## 4. WorkerProfileStore 실제 저장/로드 흐름

### 최초 접근 흐름

1. WorkerProfileStore 최초 접근
2. worker_profiles.json 로드 시도
3. 파일 없음이면 기본 Worker envelope 생성
4. conversation_scenarios.json 로드 시도
5. 파일 없음이면 기본 Scenario envelope 생성
6. in-memory cache에 envelope 보관
7. UI 또는 후속 Planner가 cache를 읽음

### 로드 함수 후보

```kotlin
loadWorkerProfiles(): WorkerProfileStoreEnvelope
loadConversationScenarios(): ConversationScenarioStoreEnvelope
```

후속 구현에서 반환 타입은 단순 envelope 대신 result wrapper를 검토할 수 있다.

후보:

```kotlin
WorkerProfileStoreResult<T>
```

포함 후보:

- success
- data
- errorType
- errorMessage
- recovered
- usedDefaultEnvelope

### 저장 함수 후보

```kotlin
saveWorkerProfiles(envelope: WorkerProfileStoreEnvelope)
saveConversationScenarios(envelope: ConversationScenarioStoreEnvelope)
```

저장 정책:

- upsert/delete 이후 updatedAt 갱신
- atomic write 후보 적용
- 저장 실패 시 기존 파일 보존
- 저장 실패는 앱 crash가 아니라 오류 상태로 반환

## 5. in-memory cache 정책

WorkerProfileStore는 파일 읽기를 매번 반복하지 않고 in-memory cache를 유지한다.

정책:

- Store 최초 접근 시 파일 로드
- load 함수는 cache가 있으면 cache 우선 반환 가능
- forceReload 옵션은 후속 구현 후보
- upsert/delete/save 이후 cache와 파일을 함께 갱신
- 저장 실패 시 cache rollback 또는 사용자 경고 정책 필요

함수 후보:

```kotlin
loadWorkerProfiles(forceReload: Boolean = false)
loadConversationScenarios(forceReload: Boolean = false)
clearCache()
```

주의:

- recomposition마다 파일 읽기 금지
- UI는 Store 상태를 직접 과도하게 poll하지 않음
- 후속 ViewModel 도입 시 상태 흐름을 분리

## 6. worker_profiles.json 생성/로드/저장 정책

### 파일 없음

- 기본 Worker 템플릿 포함 envelope 생성
- schemaVersion=1
- updatedAt=현재 시각
- 기본 템플릿은 isDefaultTemplate=true, userModified=false

### 빈 파일

정책 후보:

- 안전 fallback: 기본 envelope 생성
- 원본 빈 파일은 백업 또는 복구 후보로 남김

### parse 실패

정책:

- 원본 파일 보존
- 기본 envelope로 임시 동작 가능
- 사용자에게 복구 안내 가능
- parse 실패 상태를 Store result에 포함

### 저장

정책:

- atomic write 후보 사용
- 임시 파일에 먼저 기록 후 교체
- 저장 실패 시 기존 파일 유지
- API Key, baseUrl, rawModelName 복사 금지

## 7. conversation_scenarios.json 생성/로드/저장 정책

### 파일 없음

- 기본 Scenario envelope 생성
- 기본 Worker 템플릿 ID를 참조하는 Scenario 생성 가능
- worker_profiles.json이 먼저 초기화되어야 함

### Scenario 생성 시 주의

- Scenario는 WorkerProfile 자체를 중복 저장하지 않음
- workerProfileIds만 참조
- orchestratorProfileId/synthesisProfileId도 WorkerProfile ID 참조

### parse 실패

정책:

- 원본 파일 보존
- 기본 Scenario envelope 임시 사용 가능
- Scenario가 깨져도 WorkerProfile 전체가 삭제되면 안 됨

## 8. 기본 템플릿 최초 생성 정책

### 기본 Worker 템플릿

후보:

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

- 최초 파일 생성 시 기본 템플릿 포함
- providerId/modelId는 null 가능
- systemInstruction은 짧은 기본 placeholder로 시작
- enabled=true
- isDefaultTemplate=true
- userModified=false
- 앱 업데이트 시 새 템플릿 추가 가능
- 기존 사용자 수정본 덮어쓰기 금지

### 기본 Scenario

후보:

- 기본 단일 대화
- 조사/요약
- 코드 검토
- 글쓰기/리뷰
- RAG 중심
- 비교/병렬 분석

정책:

- 기본 Scenario도 사용자 편집 가능 구조 지향
- userEditable=true
- isDefaultTemplate=true
- userModified=false
- WorkerProfile ID 참조만 저장

## 9. 사용자 수정본 보존 정책

핵심 원칙:

- userModified=true인 WorkerProfile은 자동 덮어쓰기 금지
- userModified=true인 Scenario는 자동 덮어쓰기 금지
- 앱 업데이트로 기본 템플릿 문구가 바뀌어도 사용자 수정본 유지
- 기본값 복원은 후속 UI에서 명시적 사용자 동작으로만 수행

후속 구현 후보:

```kotlin
resetWorkerProfileToDefaultTemplate(workerProfileId: String)
resetScenarioToDefaultTemplate(scenarioId: String)
```

주의:

- reset은 확인 Dialog 필요
- System Instruction 사용자 수정본을 조용히 덮어쓰면 안 됨

## 10. schemaVersion / migration 정책

### schemaVersion

- worker_profiles.json schemaVersion=1
- conversation_scenarios.json schemaVersion=1

### 누락

정책 후보:

- schemaVersion 누락 시 v1로 간주 가능한지 필드 검사
- 필수 필드가 지나치게 부족하면 안전 실패

### 불일치

정책:

- migration 함수 후보 실행
- migration 실패 시 원본 파일 보존
- 앱 crash 금지

함수 후보:

```kotlin
migrateWorkerProfilesEnvelope(raw: String): WorkerProfileStoreEnvelope
migrateConversationScenariosEnvelope(raw: String): ConversationScenarioStoreEnvelope
repairWorkerProfileDefaults(profile: ConversationWorkerProfile): ConversationWorkerProfile
repairScenarioDefaults(scenario: ConversationScenarioProfile): ConversationScenarioProfile
```

### 필수 필드 누락

정책:

- workerProfileId 누락: 복구 어려움, 해당 항목 제한/skip 후보
- displayName 누락: “이름 없음” 기본값 후보
- systemInstruction 누락: 빈 문자열 또는 기본 템플릿 instruction 후보
- providerId/modelId 누락: null 허용
- capabilityOverrides 누락: UNKNOWN 중심 기본값
- inputPolicy/outputPolicy 누락: 안전 기본값

## 11. 오류 처리 정책

### 파일 없음

- 기본 envelope 생성
- 사용자 오류로 보지 않음

### 빈 파일

- 기본 envelope 생성 또는 안전 실패
- 원본 보존 우선

### JSON parse 실패

- 원본 파일 보존
- 사용자 복구 가능 상태 표시
- 임시 기본 envelope로 화면 접근 가능하게 할지 후속 결정

### schemaVersion 불일치

- migration 시도
- 실패 시 원본 보존

### 필수 필드 누락

- 기본값 보정 가능하면 보정
- 복구 불가능하면 해당 항목만 제한 상태

### 중복 ID

- 자동 삭제 금지
- 충돌 표시
- 후속 UI에서 사용자 선택 복구

### 저장 실패

- 기존 파일 보존
- cache rollback 여부 검토
- 사용자에게 저장 실패 안내

## 12. orphan Provider/Model 참조 처리

대상:

- WorkerProfile.providerId가 존재하지 않음
- WorkerProfile.modelId가 존재하지 않음
- Scenario.workerProfileIds 일부가 존재하지 않음
- Scenario.orchestratorProfileId가 존재하지 않음
- Scenario.synthesisProfileId가 존재하지 않음

정책:

- 자동 삭제 금지
- WorkerProfile / Scenario 보존
- 실행 후보에서는 LIMITED 또는 BLOCKED 후보
- UI에서는 재지정 안내
- Store는 검증 결과만 반환

함수 후보:

```kotlin
validateWorkerProfileReferences(...): WorkerProfileReferenceValidation
validateScenarioReferences(...): ScenarioReferenceValidation
findOrphanProviderReferences(...): List<String>
findOrphanModelReferences(...): List<String>
findOrphanWorkerReferences(...): List<String>
```

사용자 메시지 후보:

- “이 Worker가 참조하는 Provider를 찾을 수 없습니다.”
- “이 Worker가 참조하는 Model을 찾을 수 없습니다.”
- “이 Scenario에 포함된 일부 Worker를 찾을 수 없습니다.”

## 13. SAF 백업/복원 연결 정책

### 포함 대상

- worker_profiles.json
- conversation_scenarios.json

### 연결 시점

후속 설정 백업/복원 구현에서 Provider/Model 설정과 함께 포함한다.

### 복원 후 검증

복원 후 다음 검증 필요:

- schemaVersion 확인
- parse 가능 여부 확인
- WorkerProfile 중복 ID 확인
- Scenario 중복 ID 확인
- providerId/modelId orphan 확인
- Scenario workerProfileIds orphan 확인

### fallback

- 복원 실패 시 기존 파일 보존
- 일부 파일만 복원 성공한 경우 제한 상태 표시
- orphan이 있어도 자동 삭제 금지

## 14. 후속 구현 단계

1. WorkerProfileStore 실제 파일 IO 구현
2. worker_profiles.json / conversation_scenarios.json 최초 생성 구현
3. schemaVersion / migration / repair 함수 구현
4. WorkerProfile 관리 UI 저장 연결
5. System Instruction 편집 저장 연결
6. Provider/Model 선택 저장 연결
7. Scenario 관리 저장 연결
8. SAF 설정 백업/복원 포함
9. CapabilityResolver가 WorkerProfile/Scenario를 참조하도록 확장
10. ConversationEngine 연결은 별도 후속 단계에서 검토

## 15. 금지 유지 사항

이번 설계 단계에서는 다음을 하지 않는다.

- 실제 Kotlin 코드 구현
- WorkerProfileStore.kt 파일 IO 구현
- JsonStore 변경
- SAF 백업/복원 코드 변경
- Worker 관리 UI 저장 연결
- ConversationEngine 연결
- Provider 호출 로직 변경
- RAG/Web Search 구조 변경
- 병렬 실행 구현
- Tool 실행 구현
- 작업모드 수정
