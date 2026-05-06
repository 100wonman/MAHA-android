# MAHA_CONVERSATION_SEND_GUARD_POLICY_v1

## 1. 목적

이 문서는 대화세션에서 서버 응답 대기 중 추가 전송을 어떻게 처리할지 정책을 정리한다.

이번 단계는 정책 확인과 문서화만 수행한다. Kotlin 코드, send flow, Provider 호출, RAG/Web Search 실행 로직, 저장 schema, 작업모드는 변경하지 않는다.

목표는 다음과 같다.

- 현재 입력창과 전송 버튼의 실제 동작을 기록한다.
- 서버 응답 대기 중 중복 전송 위험을 분리한다.
- 권장 정책을 1개 제안한다.
- 후속 구현 시 수정 대상과 금지 영역을 고정한다.

---

## 2. 검토 대상

검토 대상은 다음이다.

- `app/src/main/java/com/maha/app/ui/conversation/ConversationInputPanel.kt`
- `app/src/main/java/com/maha/app/ui/conversation/ConversationRoomScreen.kt`
- `app/src/main/java/com/maha/app/ConversationViewModel.kt`

현재 세션에서 직접 확인 가능한 범위는 `ConversationInputPanel.kt`, `ConversationRoomScreen.kt`, AppRoot 연결부, 사용자 실사용 관찰 결과다.

`ConversationViewModel.kt` 원문은 이번 세션에 직접 첨부된 최신 파일로 확인되지 않았다. 따라서 ViewModel guard는 코드맵과 실사용 관찰 기준으로 위험 후보로 기록한다.

---

## 3. 현재 동작 요약

### 3.1 사용자가 관찰한 실제 동작

사용자 관찰 기준 현재 동작은 다음과 같다.

- 메시지를 전송한 뒤 서버 응답을 기다리는 동안 글씨 입력이 가능하다.
- 메시지를 전송한 뒤 서버 응답을 기다리는 동안 전송 버튼도 활성 상태로 보인다.
- 즉, 서버 응답 대기 중 추가 전송이 UI에서 막히지 않는 상태다.

### 3.2 ConversationInputPanel.kt 기준

`ConversationInputPanel.kt`는 다음 성격의 조건을 사용한다.

- 입력값이 비어 있으면 전송하지 않는다.
- `isRunning`이 true이면 전송 버튼을 비활성화하는 구조다.
- 실제 전송 클릭도 `canSend`가 true일 때만 `onSend()`를 호출하는 구조다.

즉, 입력 패널 자체는 “입력 허용 + 실행 중 send button 비활성” 정책을 지원할 수 있는 형태다.

### 3.3 ConversationRoomScreen.kt 기준

`ConversationRoomScreen.kt`는 `isRunning` 값을 `ConversationInputPanel`로 전달한다.

따라서 UI 차원에서 중복 전송을 막으려면 `ConversationRoomScreen`으로 전달되는 `isRunning` 값이 서버 요청 중 true가 되어야 한다.

### 3.4 AppRoot 연결 관찰

현재 확인 가능한 AppRoot 연결부에서는 `conversationIsRunning`이 `ConversationRoomScreen`의 `isRunning`으로 전달된다.

그러나 확인 가능한 코드 조각 기준으로는 `conversationIsRunning`이 서버 요청 시작/종료에 맞춰 갱신되는 흐름이 명확하지 않다. 이 경우 `ConversationInputPanel` 내부 조건이 있어도 실제 화면에서는 `isRunning == false`로 남아 전송 버튼이 활성처럼 보일 수 있다.

### 3.5 ConversationViewModel.sendMessage guard

`ConversationViewModel.sendMessage()` 내부에 “요청 중이면 즉시 return”하는 guard가 존재하는지는 이번 세션에서 최신 원문 기준으로 확정하지 못했다.

따라서 현재 위험은 다음 두 층으로 분리한다.

1. UI guard 불확실
   - 서버 요청 중에도 send button이 활성으로 보일 수 있다.

2. ViewModel guard 불확실
   - 서버 요청 중 `sendMessage()`가 다시 호출될 때 중복 user message 저장 또는 중복 Provider 호출을 막는지 확인이 필요하다.

---

## 4. 현재 동작 상세

| 항목 | 현재 판단 |
|---|---|
| 응답 대기 중 입력 | 가능 |
| 응답 대기 중 send button | 사용자 관찰 기준 활성 |
| 응답 대기 중 send 클릭 | 실제 중복 호출 가능성 확인 필요 |
| `ConversationInputPanel` canSend | 입력값 존재 + `!isRunning` 구조로 추정 |
| `isRunning` 전달 | `ConversationRoomScreen` → `ConversationInputPanel` 전달 |
| `isRunning` 실제 갱신 | AppRoot/ViewModel 연결 확인 필요 |
| `sendMessage` guard | 최신 원문 기준 확인 필요 |
| 중복 user message 저장 가능성 | guard 없으면 가능 |
| 중복 Provider 호출 가능성 | guard 없으면 가능 |
| 실패/재시도/취소 정책 | 별도 정책 확인 필요 |
| queue 방식 | 현재 기본 정책으로 보기 어려움 |

---

## 5. 정책 후보 비교

### 후보 1. 입력 허용 + send button 비활성

내용:

- 서버 응답 대기 중에도 사용자는 다음 메시지를 입력할 수 있다.
- 서버 응답 대기 중에는 전송 버튼만 비활성화한다.
- `sendMessage()`도 요청 중이면 무시하도록 guard를 둔다.

장점:

- 사용자가 다음 메시지를 미리 작성할 수 있다.
- 중복 Provider 호출을 막을 수 있다.
- 개인용 대화 앱 기본 UX로 적합하다.
- 입력 state ownership을 크게 바꾸지 않아도 된다.

단점:

- 전송 중임을 명확히 보여줘야 한다.
- ViewModel과 UI의 running state 연결이 필요하다.

### 후보 2. 입력 허용 + send 클릭 시 무시

내용:

- 서버 응답 대기 중에도 전송 버튼은 활성처럼 보일 수 있다.
- 클릭하면 ViewModel guard가 무시한다.

장점:

- UI 변경이 적다.
- 중복 Provider 호출을 ViewModel에서 막을 수 있다.

단점:

- 사용자는 버튼이 눌렸는데 아무 일도 안 일어난다고 느낄 수 있다.
- UX가 불명확하다.

### 후보 3. 입력 차단 + send button 비활성

내용:

- 서버 응답 중 입력창도 막고 전송 버튼도 막는다.

장점:

- 중복 전송 가능성이 낮다.

단점:

- 사용자가 다음 메시지를 미리 작성할 수 없다.
- 대화 앱 UX로는 답답할 수 있다.

### 후보 4. 중복 전송 허용

내용:

- 서버 응답 중에도 추가 전송을 허용한다.

장점:

- 빠른 연속 입력이 가능하다.

단점:

- 대화 순서가 꼬일 수 있다.
- 중복 Provider 호출이 발생할 수 있다.
- RAG/Web Search trace와 저장 순서가 복잡해진다.
- 현재 앱의 안정성 우선 단계에는 부적합하다.

### 후보 5. Queue 방식 도입

내용:

- 서버 응답 중 입력된 메시지를 큐에 넣고 이전 응답이 끝난 뒤 순차 전송한다.

장점:

- 고급 UX로 확장 가능하다.

단점:

- send flow, 저장 순서, 오류 처리, 취소 정책이 복잡해진다.
- 지금 단계의 범위를 초과한다.

---

## 6. 권장 정책

권장 정책은 다음이다.

> 입력 허용 + send button 비활성 + ViewModel guard 유지/추가

정책 의미:

- 서버 응답 대기 중에도 입력창은 계속 사용할 수 있다.
- 서버 응답 대기 중에는 전송 버튼은 비활성으로 보인다.
- 사용자가 어떤 방식으로든 `sendMessage()`를 다시 호출하더라도 ViewModel에서 중복 요청을 막는다.
- queue 방식은 후순위로 둔다.

선정 이유:

- 개인용 대화 앱 기본값으로 안전하다.
- 사용자는 다음 메시지를 미리 작성할 수 있다.
- 중복 user message 저장과 중복 Provider 호출을 막을 수 있다.
- RAG/Web Search 실행 중복과 trace 혼선을 줄일 수 있다.
- 현재 리팩토링 안정화 흐름과 잘 맞는다.

---

## 7. 구현 후보

이번 문서 단계에서는 구현하지 않는다.

후속 구현 시 수정 후보는 다음이다.

### 7.1 ConversationViewModel.kt

필수 확인/보정 후보:

- 요청 진행 중 상태값 존재 여부 확인
- `sendMessage()` 진입부 guard 확인
- 요청 시작 시 running state true
- 요청 종료, 실패, timeout 시 running state false 보장
- 중복 user message 저장 방지
- 중복 Provider 호출 방지

권장 형태:

- ViewModel이 단일 source of truth로 running state를 소유한다.
- UI는 이 state를 읽기만 한다.

### 7.2 AppRoot.kt 또는 state 연결부

필수 확인/보정 후보:

- ViewModel running state가 `ConversationRoomScreen.isRunning`으로 전달되는지 확인
- 로컬 `conversationIsRunning`이 실제 요청 상태와 분리되어 있다면 ViewModel state로 연결 검토

### 7.3 ConversationRoomScreen.kt

필수 확인/보정 후보:

- `isRunning` 값을 `ConversationInputPanel`로 그대로 전달한다.
- send flow 자체는 변경하지 않는다.

### 7.4 ConversationInputPanel.kt

현재 구조 유지 후보:

- 입력창은 enabled 유지
- send button은 `canSend = inputText.trim().isNotEmpty() && !isRunning`
- 클릭 시 `canSend`가 true일 때만 `onSend()` 호출

---

## 8. 유지할 동작 / 변경할 동작

### 유지할 동작

- 사용자는 서버 응답 대기 중에도 다음 메시지를 입력할 수 있다.
- 입력 상태 소유권은 기존 구조를 유지한다.
- RAG/Web Search toggle 의미는 변경하지 않는다.
- keyboard hide 후 send 흐름은 유지한다.
- Provider 호출 방식은 변경하지 않는다.
- 저장 schema는 변경하지 않는다.

### 변경할 동작 후보

- 서버 응답 대기 중 send button은 비활성으로 보이게 한다.
- 서버 응답 대기 중 send 클릭이 들어와도 ViewModel에서 무시한다.
- 요청 종료/실패/timeout 후 send button이 다시 활성화된다.

---

## 9. 금지 영역

후속 구현 시에도 다음은 금지한다.

- ConversationEngine 호출 구조 변경
- Provider Adapter request/response 형식 변경
- RAG 실행 로직 변경
- Web Search 실행 로직 변경
- 저장 schema 변경
- block type mapping 변경
- block.content 저장값 변경
- 작업모드 수정
- queue 방식 즉시 도입
- 취소/재시도 정책 동시 구현

---

## 10. 검증 체크리스트

후속 구현 시 검증 기준은 다음이다.

1. 서버 응답 대기 중 입력 가능
2. 서버 응답 대기 중 send button 비활성
3. 서버 응답 대기 중 send 클릭 시 추가 user message 저장 없음
4. 서버 응답 대기 중 추가 Provider 호출 없음
5. 응답 성공 후 send button 재활성
6. 응답 실패 후 send button 재활성
7. timeout 후 send button 재활성
8. RAG ON 상태에서 중복 호출 없음
9. Web Search ON 상태에서 중복 호출 없음
10. 기존 일반 전송 정상
11. 저장 schema 변경 없음
12. 작업모드 영향 없음

---

## 11. 결론

현재 실사용 관찰 기준으로 서버 응답 대기 중 입력과 전송 버튼이 활성 상태로 보인다.

`ConversationInputPanel`은 `isRunning` 값이 올바르게 true로 전달되면 send button을 비활성화할 수 있는 구조로 보인다. 그러나 현재 실제 동작은 서버 응답 대기 중에도 send button이 활성으로 보이므로, running state 연결 또는 ViewModel guard 확인이 필요하다.

권장 정책은 다음이다.

> 입력 허용 + send button 비활성 + ViewModel sendMessage guard

이번 단계에서는 문서화만 수행하며, Kotlin 코드는 변경하지 않는다.
