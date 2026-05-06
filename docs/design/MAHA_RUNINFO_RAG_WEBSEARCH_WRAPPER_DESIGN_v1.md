# MAHA_RUNINFO_RAG_WEBSEARCH_WRAPPER_DESIGN_v1

## 1. 목적

이 문서는 대화세션 리팩토링 1~5단계 완료 이후, 대화방의 실행정보(RunInfo), RAG, Web Search grounding/source 표시 영역을 후속 구현에서 안전하게 wrapper화하기 위한 설계 기록이다.

이번 단계는 구현이 아니라 설계다. Kotlin 코드, 실행 로직, parser marker, trace key, fallback policy, source/citation schema, 저장 schema, 작업모드는 변경하지 않는다.

목표는 다음과 같다.

- `ConversationRunSummaryPanelReadable`의 현재 역할을 정리한다.
- RunInfo / RAG / Web Search 표시 영역을 assistant 본문과 분리된 compact UI 구조로 설계한다.
- parser가 `ConversationRunInfoParser.kt`로 분리된 현재 상태를 전제로 wrapper 후보를 정의한다.
- copy action과 collapsed / expanded 상태 기준을 유지한다.
- phone 화면에서 과도한 세로 길이와 raw trace 노출을 줄이는 표시 정책을 정리한다.

---

## 2. 설계 전제

현재 대화세션 리팩토링 상태는 다음과 같다.

1. `ConversationDisplayParser.kt` 분리 완료
2. `ConversationAssistantRenderer.kt` 분리 완료
3. `ConversationBlocks.kt` renderer 책임 재정리 완료
4. `ConversationRunInfoParser.kt` 분리 완료
5. `ConversationInputPanel.kt` 분리 완료

이 문서는 4단계 이후의 후속 UI wrapper 설계다.

전제는 다음과 같다.

- RunInfo / RAG / Web Search parser는 이미 UI 파일에서 분리되어 있다.
- parser marker, trace key, fallback key는 변경하지 않는다.
- RAG / Web Search 실행 로직은 변경하지 않는다.
- source / citation schema는 변경하지 않는다.
- 실행정보는 assistant 답변 본문과 시각적으로 분리되어야 한다.
- 기본 노출은 compact / collapsed 중심이 적합하다.
- 상세 trace / raw text는 사용자가 펼쳤을 때만 확인하는 구조가 적합하다.

---

## 3. 현재 ConversationRunSummaryPanelReadable 역할

`ConversationRunSummaryPanelReadable`은 현재 대화 실행정보 표시의 중심 UI 역할을 가진다.

현재 역할은 다음과 같이 볼 수 있다.

- Provider 호출 정보 표시
- model / provider / latency / status 표시
- RAG 사용 여부 표시
- RAG query / result count / used chunk count / fallback reason 표시
- Web Search grounding 사용 여부 표시
- search query count / citation count / source 목록 표시
- provider response summary 표시
- fallback summary 표시
- trace raw text 또는 flat section 표시
- 일부 copy action 제공
- 접힘 / 펼침 형태의 상세 정보 표시

문제 후보는 다음과 같다.

- 한 composable이 summary, section, trace, RAG, Web Search, provider summary를 함께 담당한다.
- phone 화면에서 상세 정보가 길어질 수 있다.
- raw trace와 사용자에게 필요한 요약 정보의 위계가 섞일 수 있다.
- RAG / Web Search source가 과도하게 펼쳐지면 assistant 본문보다 눈에 띌 수 있다.

---

## 4. 설계 범위

### 4.1 RunInfo

RunInfo는 한 실행(run)의 결과 요약을 보여준다.

표시 후보:

- 실행 상태
- Provider 이름
- Model 이름
- latency
- error type
- actualApiCall 여부
- RAG ON/OFF
- Web Search requested / executed / used 여부
- warning / fallback 상태

### 4.2 RAG

RAG 표시는 검색 또는 인덱싱된 대화/문서 context 사용 여부를 보여준다.

표시 후보:

- RAG ON/OFF
- query
- resultCount
- usedChunkCount
- totalTokenEstimate
- maxResults
- fallback 여부
- fallbackReason
- context preview 후보

### 4.3 Web Search grounding

Web Search grounding 표시는 Provider native grounding 또는 web search 시도 상태를 보여준다.

표시 후보:

- requested
- providerType
- modelWebSearchStatus
- nativeGroundingAvailable
- canAttemptGrounding
- groundingExecuted
- groundingUsed
- citationCount
- searchQueryCount
- errorType
- reason

### 4.4 Web Search source / citation

source / citation 표시는 grounding 또는 source 목록을 compact하게 보여준다.

표시 후보:

- source index
- title
- url
- snippet
- citation count
- search query

### 4.5 provider / fallback summary

provider / fallback summary는 Provider 응답 상태와 fallback 판단 결과를 보여준다.

표시 후보:

- providerType
- adapter
- endpoint
- finishReason
- success
- errorType
- responseLength
- groundingUsed
- fallbackAllowed
- fallbackAttempted
- fallbackSucceeded
- fallbackReason

### 4.6 trace / raw text

trace / raw text는 디버깅 정보다.

표시 정책:

- 기본 노출 금지
- 접힘 상태 유지
- 개발자 확인용 상세 영역에서만 표시
- copy action 유지
- parser marker 변경 금지

---

## 5. wrapper 후보

### 5.1 ConversationRunInfoCard

- 역할: 실행정보 전체를 감싸는 최상위 카드
- 대체 대상: `ConversationRunSummaryPanelReadable`의 외부 카드 shell
- 필요 props: `status`, `providerName`, `modelName`, `latencySec`, `isCollapsed`, `onToggleCollapsed`, `content`
- 표시 정책: 기본 compact, 필요 시 expanded detail
- 위험도: MEDIUM
- 1차 구현 여부: 가능
- 금지 조건: trace parser / run state 의미 변경 금지

### 5.2 ConversationRunInfoSummaryRow

- 역할: 실행정보의 1줄 요약 표시
- 대체 대상: Provider / model / status / latency 요약 row
- 필요 props: `statusLabel`, `providerLabel`, `modelLabel`, `latencyLabel`, `ragLabel`, `webSearchLabel`, `warningLabel`
- 표시 정책: phone에서도 1~2줄 안에 들어가는 compact row
- 위험도: LOW~MEDIUM
- 1차 구현 여부: 가능
- 금지 조건: 상태 계산 변경 금지

### 5.3 ConversationRunInfoSection

- 역할: RunInfo 상세 section wrapper
- 대체 대상: provider call, provider response summary, fallback summary section
- 필요 props: `title`, `summary`, `collapsed`, `onToggle`, `onCopy`, `content`
- 표시 정책: 기본 collapsed 가능
- 위험도: MEDIUM
- 1차 구현 여부: 가능
- 금지 조건: copy text 의미 변경 금지

### 5.4 ConversationRagSummaryPanel

- 역할: RAG 상태 요약 표시
- 대체 대상: RunRagSection summary 영역
- 필요 props: `enabled`, `query`, `resultCount`, `usedChunkCount`, `fallback`, `fallbackReason`
- 표시 정책: ON/OFF와 결과 수 중심 compact 표시
- 위험도: HIGH
- 1차 구현 여부: 후순위
- 금지 조건: RAG 실행 로직 / fallback policy 변경 금지

### 5.5 ConversationRagContextPanel

- 역할: RAG context 상세 표시
- 대체 대상: RAG context/raw preview 영역
- 필요 props: `query`, `items`, `totalTokenEstimate`, `onCopy`
- 표시 정책: context 원문 과다 노출 방지, 기본 collapsed
- 위험도: HIGH
- 1차 구현 여부: 후순위
- 금지 조건: chunk selection / indexing / scoring 변경 금지

### 5.6 ConversationWebSearchGroundingPanel

- 역할: Web Search grounding 상태 요약 표시
- 대체 대상: RunWebSearchGroundingSection summary 영역
- 필요 props: `requested`, `groundingExecuted`, `groundingUsed`, `citationCount`, `searchQueryCount`, `errorType`, `reason`
- 표시 정책: requested / executed / used를 한눈에 구분
- 위험도: HIGH
- 1차 구현 여부: 후순위
- 금지 조건: grounding marker / parser key 변경 금지

### 5.7 ConversationWebSearchSourceCard

- 역할: Web Search source 1개 표시
- 대체 대상: source title / url / snippet 표시 블록
- 필요 props: `index`, `title`, `url`, `snippet`, `onCopyUrl`
- 표시 정책: title 우선, url은 muted, snippet은 1~3줄 compact
- 위험도: HIGH
- 1차 구현 여부: 후순위
- 금지 조건: source schema / citation schema 변경 금지

### 5.8 ConversationCitationChip

- 역할: citation/source count 또는 source index 표시
- 대체 대상: 작은 source/citation label
- 필요 props: `label`, `selected`, `tone`, `onClick`
- 표시 정책: 작은 chip 중심, 큰 면 색상 금지
- 위험도: MEDIUM
- 1차 구현 여부: 가능하나 source wrapper 이후 권장
- 금지 조건: citation mapping 변경 금지

### 5.9 ConversationProviderSummaryPanel

- 역할: Provider response summary compact 표시
- 대체 대상: provider response summary section
- 필요 props: `providerType`, `adapter`, `endpoint`, `finishReason`, `success`, `errorType`, `responseLength`
- 표시 정책: 성공/실패/응답 길이 중심 compact 표시
- 위험도: MEDIUM
- 1차 구현 여부: 가능
- 금지 조건: Provider 호출 결과 해석 변경 금지

### 5.10 ConversationTraceTextPanel

- 역할: raw trace text 표시
- 대체 대상: raw trace / flat text section
- 필요 props: `title`, `text`, `collapsed`, `onToggle`, `onCopy`
- 표시 정책: 기본 collapsed, mono text, copy 유지
- 위험도: MEDIUM~HIGH
- 1차 구현 여부: 후순위
- 금지 조건: trace marker / raw text 생성 변경 금지

---

## 6. 표시 정책

### 6.1 compact 정책

기본 화면에서는 다음 정보만 우선 표시한다.

- status
- provider / model
- latency
- RAG ON/OFF
- Web Search used 여부
- error / fallback 여부

세부 raw text는 기본 노출하지 않는다.

### 6.2 collapsed 정책

기본 collapsed 대상:

- raw trace
- provider response summary 상세
- RAG context 원문
- Web Search source snippet 전체
- fallback raw reason 상세

기본 expanded 가능 대상:

- 짧은 status row
- error summary
- RAG/Web Search 결과 개수

### 6.3 detail 정책

expanded detail에서는 다음을 볼 수 있어야 한다.

- provider call detail
- provider response detail
- RAG query/result detail
- Web Search search queries
- source list
- fallback reason
- raw trace

단, source snippet과 raw trace는 과도한 원문 노출을 피한다.

### 6.4 copy action 정책

copy action은 유지한다.

- summary copy
- raw trace copy
- source URL copy
- provider summary copy
- RAG context copy 후보

금지:

- copy text 의미 변경
- parser key 변경
- trace marker 변경
- source/citation schema 변경

### 6.5 phone 밀도 정책

phone 화면에서는 다음을 우선한다.

- 1줄 summary 우선
- 상세 section 기본 접힘
- source snippet 1~2줄 제한 후보
- raw trace 기본 접힘
- chip은 작게 유지
- RAG / Web Search source를 assistant 본문보다 더 크게 보이지 않게 함

---

## 7. 위험도 분류

### LOW

- 단순 label / value row wrapper
- status chip wrapper
- section title wrapper
- spacing / divider wrapper

### MEDIUM

- RunInfo card shell
- RunInfo summary row
- provider summary panel
- trace text panel visual shell
- citation chip
- copy button wrapper

### HIGH

- RAG summary panel
- RAG context panel
- Web Search grounding panel
- Web Search source card
- source/citation mapping 표시
- fallback 표시 위계

### DO_NOT_TOUCH

- `ConversationEngine`
- `ConversationViewModel`
- RAG 실행 로직
- Web Search 실행 로직
- trace marker
- parser key
- fallback policy
- source/citation schema
- 저장 schema
- Provider Adapter
- 작업모드

---

## 8. 후속 구현 우선순위

권장 순서:

1. `ConversationRunInfoCard` 외부 shell만 적용
2. `ConversationRunInfoSummaryRow` 적용
3. `ConversationProviderSummaryPanel` 적용
4. `ConversationTraceTextPanel` 시각 wrapper 적용
5. `ConversationRagSummaryPanel` 설계 재확인 후 적용
6. `ConversationWebSearchGroundingPanel` 설계 재확인 후 적용
7. `ConversationWebSearchSourceCard` / `ConversationCitationChip` 적용
8. phone 화면 실기기 검증

주의:

- RAG / Web Search source 표시는 HIGH 위험도이므로 첫 구현에서 대규모 치환하지 않는다.
- source/citation mapping과 parser key는 변경하지 않는다.
- raw trace compact화는 표시 정책만 바꾸고 원문 생성 로직은 변경하지 않는다.

---

## 9. 검증 체크리스트

후속 구현 시 확인할 항목:

- 실행정보가 기존처럼 표시되는가
- Provider / model / latency가 유지되는가
- RAG ON/OFF 표시가 유지되는가
- RAG result count / used chunk count가 유지되는가
- Web Search requested / executed / used 표시가 유지되는가
- citation count / search query count가 유지되는가
- source title / url / snippet 표시가 유지되는가
- fallback reason 표시가 유지되는가
- copy action이 기존처럼 동작하는가
- raw trace가 필요 시 확인 가능한가
- parser marker가 변경되지 않았는가
- RAG / Web Search 실행 로직이 변경되지 않았는가
- 저장 schema가 변경되지 않았는가

---

## 10. 다음 지휘 판단 후보

다음 후보는 다음과 같다.

1. RunInfo 외부 shell과 summary row만 1차 적용
2. provider/fallback summary panel만 1차 적용
3. RAG/Web Search는 설계만 유지하고 아직 치환하지 않음
4. ConversationInputPanel 스타일 보정으로 우선 이동
5. 대화세션 리팩토링 1~5단계 전체 검증 보고서 작성

권장 후보는 1번이다.

이유는 RunInfo 외부 shell과 summary row는 LOW~MEDIUM 위험도이며, RAG/Web Search parser key나 실행 로직에 닿지 않고 표시 위계만 정리할 수 있기 때문이다.
