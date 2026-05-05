# MAHA_CORE_INTENT_v1.3

## 1. 목적

MAHA는 Android 기기에서 동작하는 개인용 AI 하네스 앱이다. 핵심 목적은 사용자의 장기 맥락, 로컬 기억, RAG, Provider/Model 선택, Worker/Scenario 기반 실행 구조를 조합해 사용자가 직접 제어 가능한 AI 작업 환경을 제공하는 것이다.

v1.3의 핵심 추가 목적은 다음과 같다.

- 대화모드와 작업모드의 성격을 명확히 분리한다.
- 대화모드는 작업모드보다 가볍게 시작한다.
- 강력한 내부 하네스 구조를 유지하되, 일반 대화모드 설정 화면은 단순하게 유지한다.
- Worker / Scenario / Capability / Policy 구조는 고급 하네스 설정으로 격리한다.
- System Instruction은 Model이 아니라 WorkerProfile의 소유 설정임을 명확히 한다.
- Provider/Model은 WorkerProfile이 사용하는 실행 엔진으로 정의한다.

## 2. 앱 개발 계기

LLM은 세션이 바뀌거나 컨텍스트가 넘치면 사용자의 장기 맥락을 잃는다. MAHA는 로컬 기기 기반 기억 저장과 RAG를 통해 세션을 넘어선 대화 연속성을 목표로 한다.

LLM은 할루시네이션과 그럴듯한 거짓 정보를 만들 수 있다. MAHA는 모르면 모른다고 말하고, 확인할 수 없으면 확인 불가라고 말하며, 필요한 경우 Web Search / RAG / Tool 검증을 사용하거나 사용 불가 사유를 표시해야 한다.

상용 유료 서비스는 모델, 플랜, 툴, 파일 제어, 음성 자동화, 로컬 저장, 비용 제어 측면에서 사용자의 모든 니즈를 충족하지 못할 수 있다. 여러 유료 서비스와 API를 조합하면 비용 리스크가 커진다.

MAHA는 무료 API, 저비용 API, 소형 로컬 LLM, 선택적 고성능 모델 사용을 조합해 비용을 최소화하는 방향을 따른다.

## 3. 대화모드 핵심 목적

대화모드는 사용자의 일상 대화, 기억 기반 응답, 빠른 질의응답, 저비용 응답을 담당한다.

대화모드는 기본적으로 가볍게 시작해야 한다. 모든 요청을 항상 멀티에이전트 구조로 무겁게 처리하지 않는다. 필요한 경우에만 Memory / RAG / Web Search / Tool / WorkerSet 기능을 확장한다.

대화모드의 기본 우선순위는 다음과 같다.

1. 빠른 시작
2. 낮은 비용
3. 사용자의 장기 맥락 보존
4. 정직한 답변
5. 필요한 경우에만 검증 기능 확장
6. 복잡한 하네스 설정은 고급 영역으로 격리

명시 원칙:

> 대화모드는 작업모드보다 가볍게 시작해야 하며, 필요한 경우에만 하네스 기능을 확장한다.

## 4. 작업모드 핵심 목적

작업모드는 무거운 다단계 작업을 담당한다.

작업모드는 다음 용도에 적합하다.

- 코드 수정
- 파일 처리
- 긴 분석
- 장기 작업
- 대형 자동화
- 다중 단계 실행
- 여러 Worker 또는 Agent를 사용하는 복합 작업

작업모드는 비용과 시간이 더 들어갈 수 있음을 전제한다. 따라서 작업모드는 대화모드의 기본 흐름과 분리해야 한다.

## 5. 대화모드와 작업모드 분리 원칙

대화모드와 작업모드는 같은 앱 안에 있지만 실행 목적과 UX가 다르다.

대화모드:

- 가볍게 시작한다.
- 일반 대화와 빠른 응답을 우선한다.
- Memory / RAG / Web Search / WorkerSet은 필요할 때만 사용한다.
- 설정 화면은 단순해야 한다.
- 고급 하네스 구조는 기본 화면에 전부 노출하지 않는다.

작업모드:

- 복잡한 작업과 장기 실행을 담당한다.
- 다중 Agent / Worker / Step 기반 구조를 허용한다.
- 더 많은 비용과 시간을 사용할 수 있다.
- 작업 결과, 로그, 재시도, 병렬 실행, 파일 처리를 더 적극적으로 사용한다.

## 6. 대화모드 경량 구동 원칙

대화모드는 다음 원칙을 따른다.

- 기본 응답은 단일 Provider/Model 또는 기본 Worker 중심으로 시작한다.
- 사용자가 명시적으로 요구하거나 요청 성격상 필요한 경우에만 RAG, Web Search, Tool, WorkerSet을 확장한다.
- Worker/Scenario가 설정되어 있어도 모든 대화에서 무조건 실행하지 않는다.
- preview/진단 결과와 실제 실행 결과를 구분한다.
- 비용이 큰 Provider, 다중 Worker, 병렬 실행은 기본값이 아니라 선택적 확장이다.

## 7. 대화모드 UX 단순화 원칙

내부 엔진 구조는 강력해도 된다. 그러나 일반 대화모드 설정 화면은 단순해야 한다.

원칙:

- Worker / Scenario / Capability / Policy 설정은 기본 설정 화면에 전부 펼쳐두지 않는다.
- 복잡한 설정은 고급 하네스 설정으로 격리한다.
- 더미 카드, 미연결 placeholder 카드, 중복 진입 카드는 제거한다.
- 긴 설정 카드는 요약 + 접힘 상세 구조로 표시한다.
- Provider/Model 설정은 기본 화면에서는 요약만 보이고, 상세 선택 UI는 접힘 영역에 둔다.
- 사용자가 자주 쓰는 설정과 개발/진단용 설정을 분리한다.

## 8. 설정 3층 구조

### 8.1 기본 설정

일반 사용자가 가장 먼저 보는 설정이다.

포함 후보:

- 기본 Provider/Model 요약
- Memory 사용 여부
- RAG 사용 여부
- Web Search 사용 여부
- 비용 절약 / 정확성 우선 / 빠른 응답 우선
- 현재 기본 대화 실행 상태 요약

### 8.2 고급 설정

대화 품질과 비용, 검색 동작을 조정하는 설정이다.

포함 후보:

- 기본 Scenario 선택
- 기억 검색 강도
- Web Search fallback
- 응답 형식 선호
- 비용 제한
- Provider/Model 상세 선택
- Web Search 가능 모델/Provider 안내

### 8.3 하네스 설정

개발자 또는 고급 사용자가 사용하는 내부 하네스 설정이다.

포함 후보:

- Worker Profile
- Scenario / WorkerSet
- Capability Resolver
- Scenario Plan Preview
- Capability Override
- InputPolicy / OutputPolicy
- Worker 실행계획 preview

하네스 설정은 기본 대화 UX를 방해하지 않도록 별도 고급 영역으로 격리한다.

## 9. Worker / Scenario / Capability / Policy의 위치

Worker / Scenario / Capability / Policy는 MAHA 내부 하네스의 핵심 구조다.

그러나 일반 대화모드 설정 화면에서는 이 구조를 모두 전면 노출하지 않는다. 기본 화면은 요약 중심으로 유지하고, 세부 편집은 하네스 설정 안에서 제공한다.

정책:

- Worker Profile 편집은 하네스 설정으로 둔다.
- Scenario / WorkerSet 편집은 하네스 설정으로 둔다.
- Capability Resolver와 Scenario Preview는 진단/preview 영역으로 둔다.
- Capability Override, InputPolicy, OutputPolicy는 고급 하네스 설정으로 둔다.
- 실제 대화 실행 연결 전까지 preview는 실제 실행처럼 표시하지 않는다.

## 10. System Instruction 소유권

System Instruction은 모델별 설정이 아니다.

System Instruction은 WorkerProfile별 설정이다.

원칙:

- WorkerProfile은 자신만의 systemInstruction을 가진다.
- Scenario는 WorkerProfile 묶음이다.
- Provider/Model은 WorkerProfile이 사용하는 실행 엔진이다.
- 실제 호출 시에는 해당 WorkerProfile의 systemInstruction이 해당 Worker가 선택한 Provider/Model 호출에 적용된다.
- ProviderAdapter는 Worker systemInstruction을 각 API 형식에 맞게 변환한다.

구조:

```text
Scenario
→ WorkerProfile
→ systemInstruction
→ providerId / modelId
→ ProviderAdapter
→ 실제 API 호출 형식
```

주의:

- Scenario 자체 systemInstruction은 후속 후보로 둘 수 있다.
- 현재 핵심 원칙은 WorkerProfile systemInstruction 중심이다.
- Model 자체에 systemInstruction을 붙이는 구조로 오해하지 않도록 한다.

## 11. Scenario / WorkerProfile / Provider / Model 관계

Scenario는 WorkerProfile의 묶음이다.

WorkerProfile은 다음을 가진다.

- displayName
- roleLabel
- systemInstruction
- providerId
- modelId
- capabilityOverrides
- inputPolicy
- outputPolicy
- executionOrder
- dependsOnWorkerIds
- enabled

Provider/Model은 WorkerProfile의 실행 엔진이다.

Provider/Model 관계 원칙:

- WorkerProfile은 providerId / modelId만 참조한다.
- API Key, baseUrl, rawModelName을 WorkerProfile에 복사 저장하지 않는다.
- Provider/Model이 삭제되면 WorkerProfile은 orphan 상태가 될 수 있다.
- orphan은 자동 삭제하지 않고 제한 상태와 재지정 안내로 처리한다.
- Provider/Model 선택이 capability 실행 가능을 보장하지 않는다.

## 12. ProviderAdapter 변환 책임

ProviderAdapter는 WorkerProfile의 systemInstruction과 사용자 입력, 컨텍스트, 정책을 실제 Provider API 형식으로 변환하는 책임을 가진다.

예:

- Gemini 계열은 system instruction 또는 contents 구조에 맞게 변환한다.
- OpenAI-compatible 계열은 messages system role 등으로 변환한다.
- OpenAI Responses 계열은 instructions/input 구조에 맞게 변환한다.
- Local/Custom Provider는 해당 OpenAI-compatible 규격에 맞게 변환한다.

주의:

- ProviderAdapter는 capability를 실제 실행하는 기능과 systemInstruction을 변환하는 기능을 분리해야 한다.
- tool/webSearch/codeExecution 실제 실행은 별도 실행 계층에서 제어한다.
- Worker systemInstruction을 적용한다고 해서 Tool 실행이 자동으로 가능해지는 것은 아니다.

## 13. 비용 최소화 원칙

대화모드는 비용 최소화를 기본값으로 한다.

원칙:

- 가벼운 요청은 가벼운 모델 또는 로컬/저비용 모델로 처리한다.
- 고성능/유료 API는 필요할 때만 사용한다.
- Worker/Scenario는 비용을 증가시킬 수 있으므로 preview/진단/제한 표시가 필요하다.
- API Key 오남용이나 비용 폭탄을 방지하는 구조가 필요하다.
- fallback은 사용자가 이해할 수 있게 표시해야 한다.
- 다중 Worker 실행은 기본값이 아니라 명시적 확장이다.

## 14. 정직성 / 사실성 / 검증 원칙

MAHA는 그럴듯한 거짓 정보를 만들지 않는 것을 우선한다.

원칙:

- 모르면 모른다고 말한다.
- 확인할 수 없으면 확인 불가라고 표시한다.
- 능력 밖이면 능력 밖이라고 말한다.
- 최신 정보나 사실 검증이 필요한 경우 Web Search / RAG / Tool을 사용하거나 사용 불가 사유를 표시한다.
- 출처 없는 내용을 확정적으로 말하지 않는다.
- 대안을 제시하되, 확인 여부를 분리한다.
- Provider 호출 성공과 사용자 목표 달성 성공을 분리한다.
- preview 결과와 실제 실행 결과를 분리한다.

## 15. 기억 / RAG / 로컬 저장 원칙

MAHA는 로컬 기기 기반 기억과 RAG를 핵심 방향으로 유지한다.

원칙:

- 앱 전용 저장소가 실시간 저장소다.
- SAF는 백업/복원용이다.
- RAG 저장소는 Web Search와 분리한다.
- Memory/RAG 검색은 대화모드의 장기 맥락 보완 수단이다.
- RAG 결과는 출처와 컨텍스트를 분리 표시한다.
- 사용자 데이터와 API Key는 안전하게 다룬다.
- API Key는 기본적으로 외부 백업에 평문 포함하지 않는다.

## 16. 후속 구현 지침

후속 구현은 다음 순서를 따른다.

1. 대화모드 전역 설정 카드맵 정리
2. 더미 카드 / 미연결 카드 / 중복 카드 제거
3. Provider/Model 설정창 요약 + 접힘 상세 적용
4. Worker / Scenario / Capability Resolver / Preview를 고급 하네스 설정으로 통합
5. 기본 설정 화면을 요약 중심으로 축소
6. Scenario preview는 실제 실행과 분리 유지
7. WorkerProfile systemInstruction → ProviderAdapter 변환 경로 설계
8. ConversationEngine 연결은 별도 대형 단계로 분리
9. 실제 Worker 실행, 병렬 실행, Tool 실행은 별도 단계로 분리
10. 작업모드 Provider 구조와 대화모드 Provider/Worker 구조의 경계를 유지

## 17. v1.3 핵심 요약

- 대화모드는 작업모드보다 가볍게 시작한다.
- 복잡한 하네스 기능은 필요한 경우에만 확장한다.
- 일반 대화모드 설정 화면은 단순해야 한다.
- Worker / Scenario / Capability / Policy는 고급 하네스 설정으로 격리한다.
- System Instruction은 WorkerProfile 소유다.
- Provider/Model은 WorkerProfile의 실행 엔진이다.
- ProviderAdapter는 Worker systemInstruction을 API별 호출 형식으로 변환한다.
- preview는 실제 실행이 아니다.
- 정직성, 검증 가능성, 비용 최소화, 로컬 기억 연속성을 유지한다.
