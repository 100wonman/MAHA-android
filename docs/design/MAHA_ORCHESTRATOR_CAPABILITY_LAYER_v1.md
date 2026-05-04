# MAHA_ORCHESTRATOR_CAPABILITY_LAYER_v1

## 0. 문서 성격

이 문서는 구현 지시서가 아니라 **향후 구현 기준 설계 문서**다.

현재 코드의 Provider 호출, RAG, Web Search, 저장 schema, 작업모드 구조를 변경하지 않는다.  
이 문서는 MAHA가 단순 채팅앱이나 Provider 관리앱으로 흐르지 않고, Orchestrator 중심의 MAHA-native multi-agent harness로 확장되기 위한 판단 기준을 정의한다.

---

## 1. 목적

MAHA 대화모드는 단순히 사용자의 입력을 Provider에 전달하고 답변을 받는 채팅 흐름이 아니다.

대화모드의 목표는 다음이다.

1. 사용자 요청을 해석한다.
2. 필요한 capability를 판단한다.
3. 현재 Provider/Model이 native로 처리 가능한지 확인한다.
4. MAHA 내부 기능으로 보완 가능한지 판단한다.
5. 단일 / 순차 / 병렬 / 혼합 실행 방식을 선택한다.
6. Worker 실행 계획을 만든다.
7. 실행 결과와 제한 사유를 사용자가 이해할 수 있게 표시한다.

핵심 기준은 다음이다.

> Provider-native 기능은 활용할 수 있지만 MAHA의 본체는 아니다.  
> MAHA의 본체는 Orchestrator / Worker / RAG / Memory / Tool / File / Code Check / Execution Planner로 구성되는 MAHA-native harness다.

---

## 2. Orchestrator 정의

### 2.1 역할

Orchestrator는 대화 요청을 곧바로 Provider에 넘기는 라우터가 아니다.

Orchestrator는 사용자의 요청을 실행 가능한 작업 계획으로 변환하는 판단 계층이다.

담당 역할:

- 사용자 요청 의도 분석
- 필요한 capability 판단
- 작업 의존성 판단
- 단일 / 순차 / 병렬 / 혼합 실행 방식 선택
- 사용 가능한 Provider-native 기능 확인
- MAHA-native 기능으로 보완 가능한지 판단
- 불가능하거나 제한되는 경우 제한 사유 생성
- Worker 실행 계획 생성
- 최종 실행정보에 판단 결과 기록

### 2.2 Orchestrator가 던져야 할 질문

Orchestrator는 최소한 다음 질문에 답해야 한다.

- 이 요청은 단일 모델 호출로 충분한가?
- 여러 Worker로 나누어야 하는가?
- 하위 작업들이 서로 독립적인가?
- 어떤 Worker 결과가 다른 Worker 입력으로 필요한가?
- RAG가 필요한가?
- Web Search가 필요한가?
- Memory가 필요한가?
- 로컬 파일 접근이 필요한가?
- 코드 검증이 필요한가?
- 구조화 출력이 필요한가?
- Provider-native 기능으로 처리할 수 있는가?
- MAHA-native 기능으로 대체할 수 있는가?
- 불가능하다면 어떤 제한 사유를 사용자에게 보여줘야 하는가?

---

## 3. Capability Layer 정의

Capability Layer는 Orchestrator가 실행 계획을 세우기 전에 요청과 실행 환경을 판단하는 중간 계층이다.

### 3.1 판단 대상

Capability Layer는 다음을 판단한다.

- 사용자가 요구한 능력
- 현재 Provider/Model이 native로 제공하는 능력
- MAHA 내부에서 제공 가능한 능력
- 현재 미구현 또는 제한된 능력
- 실행 가능한 방식
- 제한 사유

### 3.2 판단 계층

Capability 판단은 한 단계 값으로 끝내지 않는다.

판단 순서:

1. 사용자 요청에서 요구 capability 추출
2. ProviderType 기본 정책 확인
3. ModelProfile capabilitiesV2 확인
4. 사용자 override 확인
5. 런타임 응답 감지 결과 확인
6. MAHA-native 기능으로 대체 가능 여부 확인
7. 실제 실행 가능 여부 확인
8. 제한 사유 생성

---

## 4. Capability 분류

초기 Capability Layer는 다음 capability를 기준으로 설계한다.

### 4.1 출력 capability

- TEXT_GENERATION
- STRUCTURED_OUTPUT
- CODE_BLOCK_OUTPUT
- JSON_OUTPUT
- TABLE_OUTPUT

### 4.2 검색 / 기억 capability

- RAG_SEARCH
- MEMORY_RECALL
- CONVERSATION_HISTORY_SEARCH
- WEB_SEARCH_PROVIDER_NATIVE
- WEB_SEARCH_MAHA_NATIVE

### 4.3 파일 / 코드 capability

- LOCAL_FILE_READ
- LOCAL_FILE_WRITE
- CODE_CHECK
- CODE_EXECUTION

### 4.4 Tool capability

- TOOL_CALL_DETECTION
- TOOL_EXECUTION
- TOOL_RESULT_REINJECTION
- TOOL_LOOP

### 4.5 실행 방식 capability

- SINGLE_EXECUTION
- SEQUENTIAL_EXECUTION
- PARALLEL_EXECUTION
- MIXED_EXECUTION
- SYNTHESIS
- REVIEW

---

## 5. Capability 상태값

Capability는 단순 true/false가 아니라 상태값으로 관리한다.

후보 상태:

- AVAILABLE: 현재 실행 가능
- LIMITED: 일부 가능하지만 제한 있음
- NOT_AVAILABLE: 현재 불가능
- NOT_IMPLEMENTED: 설계상 필요하지만 아직 구현되지 않음
- NEED_USER_PERMISSION: 사용자 권한 필요
- NEED_API_KEY: API Key 필요
- PROVIDER_NATIVE_ONLY: Provider-native로만 가능
- MAHA_NATIVE_AVAILABLE: MAHA-native 기능으로 가능
- USER_ENABLED: 사용자가 수동 활성화함
- UNKNOWN: 아직 확정할 수 없음

---

## 6. Provider-native / MAHA-native 구분

### 6.1 Provider-native

Provider-native는 외부 Provider가 자체 제공하는 기능이다.

예:

- Gemini native Web Search
- OpenAI Responses web_search
- Groq Compound built-in web/code tools
- Provider-hosted code execution
- Provider-hosted file search

정책:

- 사용할 수 있으면 활용할 수 있다.
- 그러나 Provider-native 기능을 MAHA의 본체로 취급하지 않는다.
- Provider-native 기능이 없을 때 MAHA-native로 대체 가능한지 판단해야 한다.

### 6.2 MAHA-native

MAHA-native는 앱 내부 하네스가 직접 제공해야 하는 기능이다.

예:

- MAHA RAG
- 장기 기억
- 대화 이력 검색
- 로컬 저장소 파일 제어
- 외부 검색 API 연결
- 코드 검증
- Tool Registry
- Worker 간 결과 전달
- 실행 로그
- 실패 지점 표시
- 단일 / 순차 / 병렬 / 혼합 실행 계획

정책:

- MAHA-native 기능이 중심이다.
- Provider 기능이 없어도 MAHA가 실행 흐름을 관찰하고 제한 사유를 표시할 수 있어야 한다.
- Provider/Model은 Worker가 사용하는 실행 엔진이다.

---

## 7. 실행 방식 판단

### 7.1 단일 실행

단일 실행은 하나의 Worker 또는 하나의 Provider 호출로 충분한 경우에 사용한다.

적합한 요청:

- 일반 질문
- 단순 요약
- 단순 번역/변환
- 코드블록 출력
- JSON 출력
- 테이블 출력

판단 기준:

- 외부 검색이 필요하지 않음
- 여러 관점 비교가 필요하지 않음
- 앞 단계 결과를 다음 단계에 넘길 필요가 없음
- 단일 모델 호출로 사용자 목표를 달성할 수 있음

### 7.2 순차 실행

순차 실행은 이전 Worker 결과가 다음 Worker 입력으로 필요한 경우에 사용한다.

예:

- 계획 → 조사 → 작성 → 검토
- RAG 검색 → 답변 작성
- 코드 생성 → 코드 검증 → 수정 제안
- 요구사항 분석 → 구현 계획 → 결과 생성

판단 기준:

- 앞 단계 결과가 다음 단계 입력으로 필요함
- 추론이 단계적으로 쌓임
- 실패 지점을 명확히 추적해야 함
- 안정성이 속도보다 중요함

### 7.3 병렬 실행

병렬 실행은 서로 독립적인 하위 작업을 동시에 처리할 수 있을 때 사용한다.

예:

- 여러 문서 chunk 독립 요약
- 여러 관점 분석
- 여러 Provider 응답 비교
- 코드 문법 검토와 로직 검토 분리
- 검색 소스별 독립 조사

판단 기준:

- 하위 작업들이 서로 기다릴 필요가 없음
- 속도 또는 비교가 중요함
- 독립 결과를 나중에 합치면 됨

### 7.4 혼합 실행

혼합 실행은 순차 실행과 병렬 실행을 함께 사용하는 방식이다.

예:

1. Orchestrator가 요청 분석
2. Research Worker 여러 개 병렬 실행
3. RAG Worker 병렬 실행
4. Code Check Worker 병렬 실행
5. Synthesis Worker가 결과 통합
6. Reviewer Worker가 최종 검증

판단 기준:

- 일부 작업은 독립적으로 병렬 실행 가능함
- 일부 작업은 앞 단계 결과를 기다려야 함
- 최종 통합/검증 Worker가 필요함

---

## 8. Worker 역할 분류

### 8.1 Orchestrator Worker

역할:

- 요청 분석
- capability 판단
- 실행 방식 선택
- Worker 계획 생성
- 제한 사유 생성

### 8.2 Main Worker

역할:

- 핵심 답변 생성
- RAG context 반영
- Provider 호출 수행
- 구조화 출력 준수

### 8.3 RAG Worker

역할:

- 로컬 RAG 검색
- 관련 chunk 추출
- context 구성
- 출처/근거 요약

### 8.4 Memory Worker

역할:

- 장기 기억 검색
- 사용자 선호/작업 맥락 recall
- 현재 요청에 필요한 기억만 전달

### 8.5 Tool Worker

역할:

- tool_call 감지
- Tool Registry와 매칭
- 실행 가능 여부 판단
- 실제 tool execution은 후속 구현 대상

### 8.6 Web Search Worker

역할:

- Provider-native Web Search 사용 가능 여부 판단
- MAHA-native Web Search 사용 가능 여부 판단
- 검색 결과를 Main/Synthesis Worker에 전달

### 8.7 Code Check Worker

역할:

- 코드 문법/로직 검토
- Android/Kotlin/Compose 관련 오류 위험 탐지
- 수정 제안 생성

### 8.8 Comparison Worker

역할:

- 여러 모델/Provider 응답 비교
- 상충 결과 표시
- 신뢰도/한계 요약

### 8.9 Synthesis Worker

역할:

- 여러 Worker 결과 통합
- 누락/충돌 확인
- 제한 사항 명시
- 최종 답변 생성

### 8.10 Reviewer Worker

역할:

- 최종 답변 검토
- 형식 검증
- 사용자 목표 충족 여부 확인

---

## 9. 성공 / 제한 / 실패 상태 정의

대화모드 성공은 Provider 호출 성공만으로 판단하지 않는다.

분리할 상태:

- Provider 호출 상태
- Worker 실행 상태
- 사용자 목표 수행 상태
- Capability 충족 상태
- 실행 방식 상태
- 제한 사유

### 9.1 상태값

- SUCCESS: 정상 수행
- FAILED: 실패
- SKIPPED: 조건상 건너뜀
- LIMITED: 사용자 목표 일부만 수행
- BLOCKED: 권한/정책/구현 제한으로 차단
- NOT_IMPLEMENTED: 아직 구현되지 않음
- NEED_PERMISSION: 사용자 권한 필요
- NEED_API_KEY: API Key 필요

### 9.2 예시

Groq 일반 모델에게 날씨 검색을 요청한 경우:

- Provider 호출: SUCCESS
- Worker 실행: SUCCESS
- 사용자 목표 수행: LIMITED
- Capability 충족: WEB_SEARCH_NOT_AVAILABLE
- 제한 사유: 현재 Provider/Model에서는 MAHA Web Search tool이 연결되지 않음

이렇게 표시해야 사용자가 다음을 이해할 수 있다.

> 앱 호출은 성공했지만, 내가 요청한 실제 목표는 제한되었다.

---

## 10. 실행정보 표시 항목

실행정보는 디버그 로그가 아니라 사용자가 하네스를 관찰하는 계기판이어야 한다.

### 10.1 기본 요약

기본 화면에는 다음만 표시한다.

- 사용자 목표 수행 상태
- 실행 방식
- Provider 호출 상태
- Worker 전체 상태
- 제한 사유 요약
- 사용한 주요 capability

### 10.2 Orchestrator 판단 결과

접힘 영역에 다음을 표시한다.

- 요청 유형
- 요구 capability
- 선택된 실행 방식
- Provider-native 사용 여부
- MAHA-native 사용 여부
- 미지원 capability
- 제한 사유
- Worker 계획
- Worker별 상태

### 10.3 상세 trace

상세 trace는 기본 화면에 모두 노출하지 않는다.

정책:

- 기본 화면은 요약 중심
- 상세 판단 로그는 접힘 영역
- 복사 기능에서는 상세 원문 접근 가능
- Provider raw response는 필요한 범위로 제한 표시
- API Key/민감정보는 노출 금지

---

## 11. 후속 구현 단계

### 11.1 1단계: 설계 모델 추가

- CapabilityType enum 후보 설계
- CapabilityStatus enum 후보 설계
- ExecutionMode enum 후보 설계
- UserGoalStatus enum 후보 설계
- LimitationReason 모델 후보 설계

### 11.2 2단계: Orchestrator 판단 skeleton

- 사용자 요청 분석 skeleton
- capability 요구값 추출 skeleton
- Provider/Model capability 비교 skeleton
- 실행 방식 추천 skeleton
- 제한 사유 생성 skeleton

### 11.3 3단계: 실행정보 연결

- Orchestrator 판단 결과를 실행정보에 표시
- 사용자 목표 수행 상태 표시
- 제한 사유 표시
- Worker 계획 표시

### 11.4 4단계: Execution Planner 설계/구현

- 단일 실행
- 순차 실행
- 병렬 실행 후보 계획
- 혼합 실행 후보 계획
- 실제 병렬 실행은 별도 단계에서 구현

### 11.5 5단계: MAHA-native 기능 확장

- Tool Registry
- MAHA-native Web Search
- Memory
- Local File Control
- Code Check Worker
- RAG 고도화

---

## 12. 금지 방향

다음 방향으로 흐르면 MAHA의 목적이 약해진다.

- Provider 설정 앱으로 변하는 것
- 단순 채팅 앱으로 변하는 것
- 특정 Provider 고급 기능에만 매몰되는 것
- Worker 구조가 이름만 남고 역할 분리가 사라지는 것
- 실행정보가 단순 raw debug log로만 남는 것
- 실패 지점이 사용자에게 보이지 않는 것
- RAG/Memory/File/Tool이 부가기능처럼 밀려나는 것
- 순차 실행만을 영구 구조로 오해하는 것
- 병렬 실행 가능성을 구조적으로 닫는 것

---

## 13. 기준 문장

MAHA는 모델을 많이 붙이는 앱이 아니다.  
MAHA는 모델을 작업자로 조직하는 앱이다.

MAHA는 Provider 기능을 모으는 앱이 아니다.  
MAHA는 Provider의 한계를 내부 하네스로 보완하는 앱이다.

MAHA는 단순히 답변을 받는 앱이 아니다.  
MAHA는 요청이 어떻게 처리되었는지 사용자가 관찰하고 조정하는 앱이다.

MAHA는 순차 실행만 하는 앱이 아니다.  
MAHA는 Orchestrator가 작업 의존성을 판단해 단일 / 순차 / 병렬 / 혼합 실행을 선택하는 앱이다.
