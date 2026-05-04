# MAHA_WORKER_PROFILE_EDIT_UI_v1

## 1. 목적

이 문서는 MAHA 대화모드의 Worker Profile 편집 UI를 후속 구현하기 위한 설계 기준이다.

MAHA_CORE_INTENT_v1.2 기준에 따라 Worker는 고정 개체가 아니라 사용자가 추가, 삭제, 수정 가능한 Profile이다. System Instruction은 Worker별 사용자 편집 가능 설정값이며, Provider와 Model은 Worker가 사용하는 실행 엔진으로 Worker별 선택 가능해야 한다.

이번 문서는 실제 Compose UI 구현이나 WorkerProfileStore 저장 호출 연결을 포함하지 않는다.

## 2. 설계 전제

- Worker Profile 관리 UI skeleton은 유지한다.
- WorkerProfileStore 실제 저장/로드 구현은 별도 계층으로 존재한다.
- 이번 설계는 편집 UI의 구조와 정책만 정의한다.
- Scenario 편집 UI는 후속 단계로 분리한다.
- ConversationEngine, ConversationViewModel, Provider 호출, RAG, Web Search, 작업모드는 변경하지 않는다.

## 3. Worker 편집 진입 흐름

### 3-1. 진입 후보

- Worker Profile 관리 화면의 Worker 카드에서 `상세/편집` 선택
- Worker 카드 상세 영역의 `편집` 버튼
- Worker 목록 상단의 `새 Worker 추가` 버튼
- Worker 카드의 `복제` 액션

### 3-2. 화면 방식

모바일 환경에서는 긴 System Instruction 편집이 필요하므로 Dialog보다 전체 화면 편집을 우선한다.

후속 구현 우선순위:

1. 전체 화면 편집
2. Modal bottom sheet
3. Dialog

초기 구현에서는 Worker 목록 화면의 read-only preview를 유지하고, 편집은 별도 전체 화면으로 분리한다.

## 4. Worker 추가 정책

새 Worker 생성 시 기본값 후보는 다음과 같다.

| 필드 | 기본값 |
|---|---|
| workerProfileId | 새 고유 ID |
| displayName | 새 Worker |
| roleLabel | Custom |
| roleDescription | 빈 문자열 |
| systemInstruction | 빈 문자열 또는 기본 placeholder |
| providerId | null |
| modelId | null |
| capabilityOverrides | UNKNOWN 중심 |
| inputPolicy | 안전 기본값 |
| outputPolicy | plain text 허용 |
| executionOrder | 마지막 순서 |
| canRunInParallel | false |
| dependsOnWorkerIds | emptyList |
| enabled | true |
| isDefaultTemplate | false |
| userModified | true |

정책:

- 새 Worker는 기본 템플릿이 아니다.
- 사용자 생성 Worker는 `userModified=true`로 저장한다.
- providerId/modelId가 없어도 저장 가능해야 한다.
- 실행 시 Provider/Model 미지정은 제한 상태로 표시한다.

## 5. Worker 복제 정책

복제 대상:

- 기본 템플릿 Worker
- 사용자 수정 Worker

복제 시 정책:

- 새 workerProfileId 생성
- displayName에는 `복사본` 접미사 또는 사용자가 수정 가능한 이름 부여
- systemInstruction 포함 복사
- providerId/modelId 참조 복사 가능
- capabilityOverrides 복사
- inputPolicy/outputPolicy 복사
- isDefaultTemplate=false
- userModified=true
- createdAt/updatedAt 새로 갱신

기본 템플릿 원본은 보존한다. 복제본은 사용자 Worker로 취급한다.

## 6. Worker 비활성화/활성화 정책

비활성화는 삭제가 아니다.

- enabled=false로 변경
- WorkerProfile은 보존
- Scenario에 포함되어 있어도 실행 후보에서 제외 가능
- UI에서 비활성 배지 표시

활성화는 enabled=true로 되돌리는 작업이다.

## 7. Worker 삭제 정책

삭제는 사용자 확인 Dialog가 필요하다.

정책:

- 초기 구현에서는 삭제보다 비활성화를 우선한다.
- 사용자 생성 Worker 삭제는 허용 가능하다.
- 기본 템플릿 삭제는 제한하거나 강한 확인을 요구한다.
- Scenario에서 참조 중인 Worker 삭제 시 경고한다.
- Scenario 참조를 자동 제거할지 orphan으로 남길지는 후속 정책에서 확정한다.

권장 초기 정책:

- 기본 템플릿: 삭제 제한, 비활성화만 허용
- 사용자 Worker: 삭제 가능, 확인 Dialog 필수
- Scenario 참조 중인 Worker: 삭제 전 경고 필수

## 8. Worker 상세 편집 화면 구조

편집 화면은 다음 섹션으로 구성한다.

### A. 기본 정보

필드:

- displayName
- roleLabel
- roleDescription
- enabled

UI:

- 짧은 TextField 중심
- roleDescription은 multiline 가능
- enabled는 Switch

### B. 실행 엔진

필드:

- Provider 선택
- Model 선택
- Provider/Model 상태 요약
- API Key 필요/미설정 경고
- orphan Provider/Model 재지정

UI:

- Provider dropdown
- Model dropdown
- ProviderType badge
- Model capability 요약
- Provider 관리 / Model 관리로 이동 링크

### C. System Instruction

필드:

- systemInstruction

UI:

- 큰 multiline text field
- 글자 수 표시
- 기본 템플릿 보기
- 기본값 복원
- 민감정보 입력 금지 안내

### D. Capability Override

항목:

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

UI:

- 각 capability별 dropdown 또는 segmented control
- 상태 후보: UNKNOWN, AVAILABLE, LIMITED, NOT_AVAILABLE, NOT_IMPLEMENTED, USER_ENABLED

안내 문구:

> 이 설정은 Worker가 사용하길 원하는 capability입니다. 실제 실행 가능 여부는 Provider/Model 상태와 MAHA 구현 상태를 함께 판단합니다.

### E. InputPolicy

필드:

- 사용자 입력 사용
- 이전 Worker 출력 사용
- 특정 Worker 출력 사용
- RAG context 허용
- Memory context 허용
- Web Search context 허용
- 실행 이력 포함
- 최대 입력 길이

UI:

- 고급 접힘 섹션
- 기본값은 안전하고 단순하게 유지

### F. OutputPolicy

필드:

- expectedOutputType
- JSON 요구
- Markdown table 요구
- Code block 요구
- Plain text 허용
- 최대 출력 길이
- 다음 Worker 전달
- 사용자에게 노출
- Memory 후보 저장

UI:

- 고급 접힘 섹션
- 예상 출력 형식은 dropdown
- 전달/노출/저장은 Switch

### G. 실행 계획

필드:

- executionOrder
- canRunInParallel
- dependsOnWorkerIds

UI:

- executionOrder 숫자 입력 또는 순서 선택
- canRunInParallel Switch
- dependsOnWorkerIds multi-select
- 의존성 충돌 경고

## 9. System Instruction 편집 정책

원칙:

- System Instruction은 Worker 행동 기준이다.
- 반드시 사용자 편집 가능해야 한다.
- 앱 업데이트로 사용자 수정본을 덮어쓰지 않는다.
- 기본값 복원은 명시적 사용자 액션으로만 수행한다.

UI 요구:

- multiline text field
- 긴 텍스트 입력 가능
- 글자 수 표시
- 저장 전 변경사항 감지
- 민감정보 입력 금지 안내
- 기본 템플릿 보기
- 기본값 복원 확인 Dialog

민감정보 안내 문구 후보:

> System Instruction에는 API Key, 비밀번호, 개인 민감정보를 입력하지 마세요.

저장 방식:

- 자동 저장보다 명시적 저장을 우선한다.
- 저장 시 userModified=true, updatedAt 갱신.

## 10. Provider/Model 선택 정책

정책:

- Worker별 Provider/Model 선택 가능
- providerId/modelId만 WorkerProfile에 저장
- API Key/baseUrl/rawModelName 복사 저장 금지
- Provider/Model 삭제 시 orphan 상태 표시
- 사용자는 재지정 가능해야 한다

UI 후보:

- Provider dropdown
- Model dropdown
- ProviderType 표시
- Model capability 요약
- API Key 미설정 경고
- Provider 관리로 이동
- Model 관리로 이동

주의:

Provider/Model 선택은 Worker 실행 엔진 지정이다. Provider/Model 선택이 capability 실행 가능을 보장하지 않는다.

## 11. Capability override 편집 정책

override는 “이 Worker에서 사용자가 원하는 capability”이다.

정책:

- ModelProfile capability와 Worker override를 혼동하지 않는다.
- USER_ENABLED여도 실제 실행 가능을 의미하지 않는다.
- Capability Layer가 Provider/Model 상태와 MAHA 구현 상태를 함께 판단한다.

초기 구현에서는 capability override 저장은 가능하되, 대화 실행에는 연결하지 않는 단계를 별도로 둘 수 있다.

## 12. InputPolicy / OutputPolicy 편집 정책

초기 편집 UI에서는 고급 설정으로 접어서 제공한다.

InputPolicy 기본값 후보:

- userInputOnly=true
- previousWorkerOutput=false
- selectedWorkerOutputs=emptyList
- ragContextAllowed=false
- memoryContextAllowed=false
- webSearchContextAllowed=false
- maxInputChars=8000
- includeRunHistory=false

OutputPolicy 기본값 후보:

- expectedOutputType=TEXT_GENERATION
- requireJson=false
- requireMarkdownTable=false
- requireCodeBlock=false
- allowPlainText=true
- maxOutputChars=8000
- passToNextWorker=true
- exposeToUser=true
- saveAsMemoryCandidate=false

정책:

- 초보자가 처음부터 모든 설정을 조정하지 않아도 되도록 기본값은 단순하게 둔다.
- 고급 정책은 접힘 섹션으로 분리한다.

## 13. 변경사항 감지 / 저장 / 취소 정책

### 변경사항 감지

- 편집 화면 진입 시 원본 profile snapshot 유지
- 필드 변경 시 dirty=true
- 뒤로가기 시 변경사항 폐기 확인

### 저장

- 저장 버튼 명시
- 저장 성공 시 WorkerProfileStore.upsertWorkerProfile 호출 후보
- updatedAt 갱신
- userModified=true 설정

### 취소

- 원본 유지
- 변경사항이 있으면 폐기 확인
- 변경사항이 없으면 즉시 뒤로가기

주의:

이번 설계 단계에서는 실제 저장 구현을 하지 않는다. 후속 구현에서 WorkerProfileStore 저장 호출을 연결한다.

## 14. 기본 템플릿 / 사용자 수정본 정책

정책:

- 기본 템플릿은 시작점이다.
- 사용자가 수정하면 userModified=true가 된다.
- 앱 업데이트는 사용자 수정본을 덮어쓰면 안 된다.
- 기본값 복원은 명시적 사용자 액션으로만 수행한다.
- 기본 템플릿 원본 보존을 위해 “복제 후 수정” 흐름을 우선 제공할 수 있다.

UI 표시:

- 기본 템플릿 badge
- 사용자 수정본 badge
- 기본값 복원 버튼
- 복제 버튼

## 15. orphan Provider/Model 재지정 정책

orphan 상황:

- providerId가 존재하지 않음
- modelId가 존재하지 않음

UI 표시:

- Worker 카드 상단 경고 badge
- 편집 화면의 실행 엔진 섹션 경고
- Provider 다시 선택
- Model 다시 선택
- 일단 유지

문구 후보:

- 이 Worker가 참조하는 Provider를 찾을 수 없습니다.
- 이 Worker가 참조하는 Model을 찾을 수 없습니다.

정책:

- 자동 삭제 금지
- 사용자 복구 우선
- 실행 후보에서는 LIMITED 또는 BLOCKED로 표시 가능

## 16. Scenario 편집 범위

Scenario 편집은 이번 문서에서 세부 구현하지 않는다.

후속 분리 대상:

- Scenario 추가/복제/삭제
- WorkerSet 구성 변경
- Worker 순서 변경
- defaultExecutionMode 선택
- orchestratorProfileId / synthesisProfileId 지정

이번 문서는 Worker Profile 편집에 집중한다.

## 17. 후속 구현 단계

1. Worker 편집 화면 skeleton 추가
2. Worker 추가/복제/비활성화 placeholder 추가
3. System Instruction 편집 UI 추가
4. Provider/Model 선택 UI 추가
5. capability override 편집 UI 추가
6. InputPolicy / OutputPolicy 편집 UI 추가
7. WorkerProfileStore.upsertWorkerProfile 저장 연결
8. orphan Provider/Model 재지정 UI 연결
9. Scenario 편집 UI 설계 및 구현
10. ConversationEngine 연결 설계

## 18. 기존 기능 영향

이번 문서는 설계 기준 문서이므로 코드 변경이 없다.

영향 없음:

- Worker Profile 관리 UI skeleton
- WorkerProfileStore 실제 저장/로드
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
- 작업모드
