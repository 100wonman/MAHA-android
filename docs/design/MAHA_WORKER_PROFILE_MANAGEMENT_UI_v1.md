# MAHA_WORKER_PROFILE_MANAGEMENT_UI_v1

## 1. 목적

이 문서는 MAHA 대화모드에서 Worker Profile과 Conversation Scenario / Worker Set을 관리하기 위한 UI 설계 기준이다.

MAHA_CORE_INTENT_v1.2 기준에 따라 Worker는 고정 개체가 아니라 사용자가 추가, 삭제, 수정할 수 있는 Profile이다. Orchestrator, Main Worker, Synthesis Worker, RAG Worker, Tool Worker 같은 이름은 고정된 앱 내부 개체가 아니라 기본 역할 템플릿이다.

이 문서는 실제 Compose 구현 지시서가 아니라 후속 구현을 위한 UI 기준 문서다.

## 2. 설계 전제

- Worker Profile은 사용자 편집 가능한 작업자 정의다.
- System Instruction은 Worker별 사용자 편집 가능 설정값이다.
- Provider와 Model은 Worker가 사용하는 실행 엔진이다.
- Worker별 Provider/Model 선택이 가능해야 한다.
- capability override는 사용자의 의도 표시이며 실제 실행 가능 여부는 Capability Layer가 판단한다.
- Conversation Scenario / Worker Set은 여러 Worker Profile을 묶는 사용자 편집 가능한 실행 구성이다.
- 이번 단계에서는 실제 UI 구현, 저장/로드 연결, ConversationEngine 연결을 하지 않는다.

## 3. 화면 목록

후속 UI 후보는 다음과 같다.

1. Worker Profile 관리 메인
2. Worker Profile 목록
3. Worker Profile 상세/편집
4. System Instruction 편집
5. Worker capability override 편집
6. Worker input/output policy 편집
7. Conversation Scenario 목록
8. Conversation Scenario 상세/편집

권장 배치는 대화모드 전역 설정의 별도 진입 카드다.

- 대화모드 전역 설정 → Worker Profile 관리
- Worker Profile 관리 내부에서 Worker / Scenario 탭 분리
- 1차 구현은 단일 화면 + 상세 Dialog 방식도 허용

## 4. Worker Profile 목록 화면

### 표시 항목

각 Worker 카드에는 다음을 표시한다.

- Worker 이름
- roleLabel
- roleDescription 요약
- enabled 상태
- Provider/Model 요약
- 병렬 가능 여부
- executionOrder
- 기본 템플릿 여부
- 사용자 수정 여부
- orphan Provider/Model 경고 여부

### 액션 후보

- 새 Worker 추가
- 기본 템플릿에서 복사
- 상세 보기
- 복제
- 활성/비활성 전환
- 삭제
- 기본값 복원

### 정책

- 기본 템플릿 삭제는 제한하거나 확인 Dialog를 요구한다.
- userModified=true 항목은 앱 업데이트로 덮어쓰지 않는다는 안내가 필요하다.
- Provider/Model orphan 상태는 목록 카드에서 즉시 알아볼 수 있어야 한다.
- Worker 개수는 고정하지 않는다.

## 5. Worker Profile 상세/편집 화면

### 편집 항목

- displayName
- roleLabel
- roleDescription
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

### 섹션 구성

#### A. 기본 정보

- 이름
- 역할 라벨
- 역할 설명
- 활성/비활성
- 기본 템플릿 여부
- 사용자 수정 여부

#### B. 실행 엔진

- Provider 선택
- Model 선택
- ProviderType 표시
- API Key 필요/미설정 경고
- Provider/Model orphan 경고
- Model 관리로 이동 후보

#### C. System Instruction

- 현재 instruction 요약 미리보기
- 전체 편집 진입
- 변경사항 있음 표시
- 기본값 복원 후보

#### D. Capability Override

- functionCalling
- webSearch
- codeExecution
- structuredOutput
- thinkingSummary
- ragSearch
- memoryRecall
- fileRead
- fileWrite
- codeCheck
- parallelExecution

#### E. 입력/출력 정책

- 입력 소스
- RAG context 허용 여부
- Memory context 허용 여부
- Web Search context 허용 여부
- 예상 출력 형식
- 다음 Worker 전달 여부
- 사용자 노출 여부
- Memory 후보 저장 여부

#### F. 실행 계획

- 실행 순서
- 병렬 가능 여부
- 의존 Worker 선택

## 6. System Instruction 편집 UI

### 원칙

- System Instruction은 앱 내부 고정 문구가 되면 안 된다.
- 앱은 기본 instruction template을 제공할 수 있다.
- 실제 실행 기준은 사용자 수정본이다.
- 민감정보 입력 금지 안내가 필요하다.

### UI 후보

- 큰 multiline text field
- 현재 글자 수 표시
- 기본 템플릿 보기
- 기본값으로 복원
- 저장
- 취소
- 변경사항 있음 표시

### 안내 문구 후보

> 이 지시문은 해당 Worker의 행동 기준입니다. API Key, 비밀번호, 개인식별정보 같은 민감정보를 넣지 마세요.

### 기본값 복원 정책

- 기본값 복원은 명시적 확인 Dialog 후 수행한다.
- 복원 전 현재 사용자 수정본을 덮어쓴다는 안내를 표시한다.

## 7. Provider/Model 선택 UI

### 정책

- Worker별 Provider/Model 선택이 가능해야 한다.
- WorkerProfile에는 providerId/modelId 참조만 저장한다.
- API Key, baseUrl, rawModelName 전체 복사본은 WorkerProfile에 저장하지 않는다.

### UI 후보

- Provider dropdown
- Model dropdown
- ProviderType 표시
- Model capability 요약 표시
- API Key 필요/미설정 경고
- Provider 관리로 이동 후보
- Model 관리로 이동 후보

### orphan 처리

Provider 또는 Model이 삭제된 경우:

- 카드 상단에 경고 배지 표시
- 상세 화면에 경고 문구 표시
- “다시 선택” 버튼 제공
- 자동 삭제 금지

## 8. capability override 편집 UI

### 표시 항목

- functionCalling
- webSearch
- codeExecution
- structuredOutput
- thinkingSummary
- ragSearch
- memoryRecall
- fileRead
- fileWrite
- codeCheck
- parallelExecution

### 상태 후보

- UNKNOWN
- AVAILABLE
- LIMITED
- NOT_AVAILABLE
- NOT_IMPLEMENTED
- USER_ENABLED

### 안내 문구

> 이 설정은 Worker가 사용하길 원하는 capability입니다. 실제 실행 가능 여부는 Provider/Model 상태와 MAHA 구현 상태를 함께 판단합니다.

### 정책

- override가 USER_ENABLED여도 실제 실행 가능을 의미하지 않는다.
- 실제 실행 가능 여부는 Capability Layer가 판단한다.
- ModelProfile capability와 WorkerProfile override는 역할이 다르다.

## 9. InputPolicy / OutputPolicy 편집 UI

### InputPolicy UI 후보

- 사용자 입력만 사용
- 이전 Worker 출력 사용
- 특정 Worker 출력 선택
- RAG context 허용
- Memory context 허용
- Web Search context 허용
- 실행 이력 포함
- 최대 입력 길이

### OutputPolicy UI 후보

- 예상 출력 형식
- JSON 요구
- Markdown table 요구
- Code block 요구
- Plain text 허용
- 최대 출력 길이
- 다음 Worker로 전달
- 사용자에게 노출
- Memory 후보로 저장

### 정책

- 1차 구현에서는 고급 설정을 접힘 섹션으로 둔다.
- 기본값은 안전하고 단순해야 한다.
- 입력/출력 정책은 실제 실행 연결 전까지 preview/설정값으로만 유지한다.

## 10. 실행 순서 / 병렬 / 의존성 UI

### 표시/편집 항목

- executionOrder
- canRunInParallel
- dependsOnWorkerIds

### 정책

- executionOrder는 순차 실행 후보 순서다.
- canRunInParallel=true인 Worker는 의존성이 없을 때 병렬 실행 후보가 될 수 있다.
- dependsOnWorkerIds가 있으면 해당 Worker 결과 이후 실행되어야 한다.
- 이번 UI 설계는 실제 병렬 실행 구현을 의미하지 않는다.

### UI 후보

- 숫자 입력 또는 순서 조정 버튼
- 병렬 가능 스위치
- 의존 Worker multi-select
- 의존성 충돌 경고

## 11. Conversation Scenario / WorkerSet UI

### Scenario 목록 표시

- Scenario 이름
- 설명
- 포함 Worker 수
- 기본 실행 방식
- Orchestrator profile
- Synthesis profile
- 활성 상태
- 기본 템플릿 여부
- 사용자 수정 여부

### Scenario 상세/편집 항목

- name
- description
- workerProfileIds
- defaultExecutionMode
- orchestratorProfileId
- synthesisProfileId
- enabled
- userEditable

### 액션 후보

- 새 Scenario 추가
- 기본 Scenario 복사
- Worker 추가/제거
- Worker 순서 변경
- 기본 실행 방식 선택
- 활성/비활성 전환
- 삭제

### 정책

- Scenario는 WorkerProfile을 참조한다.
- WorkerProfile 자체를 중복 저장하지 않는다.
- 기본 Scenario도 사용자 편집 가능 구조를 지향한다.

## 12. 기본 템플릿 / 사용자 수정본 UI 정책

### 기본 Worker 템플릿 후보

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

### UI 정책

- 기본 템플릿은 “템플릿” 배지를 표시한다.
- 사용자 수정본은 “수정됨” 배지를 표시한다.
- 기본 템플릿에서 복사해 새 Worker를 만들 수 있다.
- userModified=true 항목은 앱 업데이트로 덮어쓰지 않는다.
- 기본값 복원은 명시적 확인 후 수행한다.

## 13. orphan Provider/Model/Worker 표시 정책

### orphan Provider

문구:

> 이 Worker가 참조하는 Provider를 찾을 수 없습니다.

액션:

- Provider 다시 선택
- Provider 관리로 이동
- 일단 유지

### orphan Model

문구:

> 이 Worker가 참조하는 Model을 찾을 수 없습니다.

액션:

- Model 다시 선택
- Model 관리로 이동
- 일단 유지

### orphan Worker

문구:

> 이 Scenario에 포함된 일부 Worker를 찾을 수 없습니다.

액션:

- 누락 Worker 제거
- Worker 다시 선택
- 일단 유지

### 정책

- 자동 삭제 금지
- 사용자 복구 우선
- 실행 후보에서는 제한 상태로 표시

## 14. 저장/로드 연결 시점

이번 문서 단계에서는 저장/로드를 연결하지 않는다.

후속 구현 순서 후보:

1. WorkerProfileStore 실제 파일 저장/로드 구현
2. 기본 Worker 템플릿 초기화 구현
3. Worker Profile 목록 UI read-only 구현
4. Worker Profile 상세 read-only 구현
5. Worker Profile 생성/수정/삭제 구현
6. System Instruction 편집 구현
7. Scenario 목록/상세 구현
8. Capability Resolver 진단 UI와 WorkerProfile read-only 연결
9. ConversationEngine 연결 설계
10. 실제 실행 연결

## 15. 기존 기능 유지

이 설계는 다음 기능을 변경하지 않는다.

- 대화 전송
- Provider 호출
- Google/Gemini 대화
- OpenAI-compatible 대화
- OpenAI Responses 대화
- Gemini native Web Search
- RAG
- Capability Resolver 진단 UI
- 실행정보 UI
- 코드/JSON/테이블 렌더링
- Provider 관리
- Model 관리
- SAF 백업/복원
- 작업모드 전체

## 16. 완료 기준

- Worker Profile 목록/상세/편집 UI 설계 완료
- System Instruction 편집 UI 설계 완료
- Provider/Model 선택 UI 설계 완료
- capability override 편집 UI 설계 완료
- InputPolicy / OutputPolicy UI 설계 완료
- Scenario / WorkerSet UI 설계 완료
- orphan Provider/Model/Worker 표시 정책 정의 완료
- 실제 코드 변경 없음
- 빌드 영향 없음

