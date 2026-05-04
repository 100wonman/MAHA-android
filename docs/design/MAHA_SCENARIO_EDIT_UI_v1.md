# MAHA_SCENARIO_EDIT_UI_v1

## 1. 목적

이 문서는 MAHA 대화모드에서 Conversation Scenario / WorkerSet을 사용자가 추가, 삭제, 수정할 수 있도록 하기 위한 후속 UI 구현 기준 문서다.

Scenario는 고정 프리셋이 아니라 사용자가 구성하는 WorkerProfile 묶음이다. Scenario는 WorkerProfile 자체를 중복 저장하지 않고 `workerProfileIds`로 참조한다. Orchestrator / Synthesis profile도 고정 개체가 아니라 Scenario에서 지정 가능한 WorkerProfile 참조다.

이번 문서는 설계 기준이며 실제 Compose UI 구현, 저장 연결, ConversationEngine 연결은 포함하지 않는다.

## 2. 설계 전제

- MAHA_CORE_INTENT_v1.2 기준을 유지한다.
- Worker는 사용자가 추가, 삭제, 수정할 수 있는 Profile이다.
- Scenario는 WorkerProfile 목록을 참조하는 WorkerSet이다.
- Scenario는 기본 템플릿일 수 있지만 사용자 수정본을 우선한다.
- Scenario 편집은 실제 실행 연결과 분리한다.
- WorkerProfile 편집 UI는 별도 흐름으로 유지한다.

## 3. Scenario 편집 진입 흐름

진입 후보는 다음과 같다.

- Worker Profile 관리 화면의 Scenario 탭
- Scenario 카드의 `상세/편집` 버튼
- Scenario 목록 상단의 `새 Scenario 추가` 버튼
- Scenario 카드의 `복제` 액션

권장 화면 방식은 별도 전체 화면이다. Scenario 편집은 Worker 추가, 제거, 순서 조정, 실행 방식 선택, 핵심 Worker 지정 등을 포함하므로 Dialog보다 전체 화면이 모바일에서 안정적이다.

## 4. Scenario 추가 정책

새 Scenario 기본값 후보는 다음과 같다.

- `scenarioId`: 새 ID 생성
- `name`: `새 Scenario`
- `description`: 빈 문자열
- `workerProfileIds`: 빈 목록 또는 기본 Main Worker 1개 후보
- `defaultExecutionMode`: `SINGLE`
- `orchestratorProfileId`: null
- `synthesisProfileId`: null
- `userEditable`: true
- `isDefaultTemplate`: false
- `userModified`: true
- `enabled`: true
- `createdAt`: 현재 시각
- `updatedAt`: 현재 시각

정책:

- 새 Scenario는 기본 템플릿이 아니다.
- Worker가 없어도 저장 가능하되 실행 후보에서는 제한 상태로 표시하는 방향이 안전하다.
- 실제 실행 연결은 후속 단계에서 수행한다.

## 5. Scenario 복제 정책

복제 대상은 기본 Scenario와 사용자 수정 Scenario 모두 가능하다.

복제 시 정책:

- 새 `scenarioId` 생성
- `name`에 `복사본` 또는 사용자가 수정 가능한 이름 부여
- `description` 복사
- `workerProfileIds` 참조 복사
- `defaultExecutionMode` 복사
- `orchestratorProfileId` / `synthesisProfileId` 복사
- `isDefaultTemplate=false`
- `userModified=true`
- `userEditable=true`
- `createdAt` / `updatedAt` 새로 갱신

주의:

- WorkerProfile 자체는 복제하지 않는다.
- 복제본은 기존 WorkerProfile을 참조한다.
- WorkerProfile까지 함께 복제하는 기능은 후속 고급 기능으로 분리한다.

## 6. Scenario 비활성화 / 활성화 정책

비활성화는 `enabled=false`로 처리한다.

- Scenario는 보존한다.
- 실행 후보에서는 제외할 수 있다.
- UI에는 비활성 배지를 표시한다.
- 다시 활성화하면 `enabled=true`로 복구한다.

초기 구현에서는 삭제보다 비활성화를 우선한다.

## 7. Scenario 삭제 정책

삭제는 사용자 확인 Dialog가 필요하다.

정책:

- 기본 Scenario 삭제는 제한하거나 강한 확인을 요구한다.
- 삭제해도 WorkerProfile은 삭제하지 않는다.
- 현재 활성 대화 또는 기본 설정에서 사용 중인 Scenario라면 경고한다.
- 초기 구현에서는 사용자 생성 Scenario 삭제부터 허용한다.

## 8. Scenario 상세 편집 화면 구조

### A. 기본 정보

- `name`
- `description`
- `enabled`
- `userEditable`
- `isDefaultTemplate` 표시
- `userModified` 표시

### B. WorkerSet

- 포함 Worker 목록
- Worker 추가
- Worker 제거
- Worker 순서 조정
- Worker 상세로 이동 후보

### C. 실행 방식

- `defaultExecutionMode` 선택
  - `SINGLE`
  - `SEQUENTIAL`
  - `PARALLEL`
  - `MIXED`

### D. 핵심 Worker 지정

- `orchestratorProfileId` 선택
- `synthesisProfileId` 선택
- 미지정 허용 여부 안내

### E. 참조 경고

- orphan Worker
- disabled Worker 포함
- Worker 없음
- Orchestrator/Synthesis 참조 누락

### F. 저장 / 취소

- 저장
- 취소
- 변경사항 폐기 확인

## 9. Worker 추가 / 제거 정책

### Worker 추가

- 기존 WorkerProfile 목록에서 선택한다.
- enabled Worker를 우선 표시한다.
- 비활성 Worker는 고급 옵션으로 표시할 수 있다.
- 이미 포함된 Worker는 중복 추가하지 않는다.

### Worker 제거

- Scenario에서 참조만 제거한다.
- WorkerProfile 자체는 삭제하지 않는다.
- Orchestrator/Synthesis로 지정된 Worker를 제거할 경우 해당 참조를 null 처리하거나 경고한다.

## 10. Worker 순서 조정 정책

표시 항목:

- Worker 순서
- Worker 이름
- roleLabel
- enabled 상태
- Provider/Model 요약 후보

조정 방식:

- 초기 구현은 위로/아래로 버튼을 권장한다.
- Drag-and-drop은 후속 고급 기능으로 분리한다.
- `workerProfileIds`의 순서를 Scenario 내부 WorkerSet 순서로 해석한다.
- WorkerProfile의 `executionOrder`와 Scenario 내부 순서가 충돌하면 Scenario 내부 순서를 우선하는 방향을 검토한다.

## 11. defaultExecutionMode 선택 정책

선택 후보:

- `SINGLE`
- `SEQUENTIAL`
- `PARALLEL`
- `MIXED`

정책:

- 이 값은 Scenario의 기본 실행 방식이다.
- Orchestrator가 요청별로 다른 실행 방식을 추천할 수 있다.
- Scenario 기본값은 실행 계획의 우선 힌트로 사용한다.
- 실제 실행 연결은 후속 단계에서 수행한다.

UI 안내 문구 후보:

> 이 값은 Scenario의 기본 실행 방식입니다. 실제 실행 시 Orchestrator가 요청 내용과 Worker 의존성을 함께 판단합니다.

## 12. Orchestrator / Synthesis profile 지정 정책

### orchestratorProfileId

- Scenario에서 Orchestrator 역할을 담당할 WorkerProfile 참조다.
- 미지정을 허용할 수 있다.
- 미지정 시 후속 실행 단계에서 기본 Orchestrator 후보를 찾거나 제한 상태를 표시한다.

### synthesisProfileId

- Scenario에서 최종 통합을 담당할 WorkerProfile 참조다.
- 미지정을 허용할 수 있다.
- `MIXED` 또는 `PARALLEL` 모드에서는 미지정 경고를 표시할 수 있다.

정책:

- Orchestrator/Synthesis는 고정 개체가 아니다.
- Scenario가 지정하는 WorkerProfile 참조다.
- 선택지는 Scenario에 포함된 Worker 중에서 우선 제공한다.
- 전체 WorkerProfile에서 선택 후 Scenario에 자동 포함하는 옵션은 후속 후보로 둔다.

## 13. orphan Worker 경고 및 복구 정책

orphan 상황:

- `workerProfileIds` 중 존재하지 않는 WorkerProfile ID가 있음
- `orchestratorProfileId`가 존재하지 않음
- `synthesisProfileId`가 존재하지 않음
- 포함 Worker가 disabled 상태
- Scenario에 Worker가 0개

UI 표시:

- 카드 상단 경고 배지
- 상세 화면 경고 문구
- `참조 제거`
- `다른 Worker로 재지정`
- `일단 유지`

정책:

- 자동 삭제 금지
- 사용자 복구 우선
- 실행 후보에서는 `LIMITED` 또는 `BLOCKED` 후보
- Store는 검증 결과만 제공하고 UI가 표시한다.

## 14. 변경사항 감지 / 저장 / 취소 정책

### 변경사항 감지

- 편집 화면 진입 시 원본 scenario snapshot을 유지한다.
- 필드 변경 시 `dirty=true`로 표시한다.
- 뒤로가기 시 변경사항 폐기 확인을 표시한다.

### 저장

- 명시적 저장 버튼을 사용한다.
- 저장 성공 시 `WorkerProfileStore.upsertScenario(...)` 호출 후보를 사용한다.
- 저장 시 `updatedAt` 갱신
- 저장 시 `userModified=true`

### 취소

- 원본을 유지한다.
- 변경사항이 있으면 폐기 확인을 표시한다.

## 15. 후속 구현 단계

1. ScenarioEditScreen skeleton 추가
2. Scenario 목록에서 편집 진입 연결
3. Scenario 기본 정보 저장 연결
4. Worker 추가/제거 저장 연결
5. Worker 순서 조정 저장 연결
6. defaultExecutionMode 저장 연결
7. orchestratorProfileId / synthesisProfileId 지정 저장 연결
8. orphan Worker 검증 UI 연결
9. Scenario를 Capability Resolver / OrchestratorPlan 생성에 참고하는 단계 설계
10. ConversationEngine 실행 흐름 연결은 별도 대형 단계로 분리

## 16. 기존 기능 영향

이번 설계 단계에서는 코드 변경이 없다.

- Provider 호출 변경 없음
- RAG 변경 없음
- Web Search 변경 없음
- Worker Profile UI 변경 없음
- WorkerProfileStore 변경 없음
- 작업모드 변경 없음
