# MAHA_CONVERSATION_REFACTOR_STABILIZATION_v1

## 1. 목적

이 문서는 MAHA Android 대화세션 리팩토링 1~5단계의 완료 범위와 현재 안정 지점을 기록한다.

이번 리팩토링의 목적은 새 기능 추가가 아니라 `ConversationRoomScreen.kt`에 집중되어 있던 표시용 parser, assistant message renderer, 명시적 OutputBlock renderer, RunInfo / RAG / Web Search 표시용 parser, 입력창 UI를 단계적으로 분리해 파일 책임을 명확히 하는 것이다.

이 문서 작성 단계에서는 Kotlin 코드, 실행 로직, 저장 schema, Provider 호출, RAG/Web Search 실행, 작업모드 코드를 변경하지 않는다.

---

## 2. 기준 시점

기준 시점은 다음 1~5단계가 모두 산출된 이후다.

1. `ConversationDisplayParser.kt` 1차 분리
2. `ConversationAssistantRenderer.kt` 생성 및 assistant message rendering layer 분리
3. `ConversationBlocks.kt` renderer 책임 재정리
4. `ConversationRunInfoParser.kt` 생성 및 RunInfo / RAG / Web Search 표시용 parser 분리
5. `ConversationInputPanel.kt` 생성 및 입력창 UI 분리

이 문서는 위 작업들의 안정화 기록이며, 후속 구현을 시작하기 전 지휘 세션이 변경 범위와 남은 후보 작업을 판단하기 위한 기준 문서다.

---

## 3. 리팩토링 전 문제

리팩토링 전 `ConversationRoomScreen.kt`는 다음 책임을 과도하게 포함하고 있었다.

- 대화방 전체 화면 구성
- assistant text 표시
- heading / quote / markdown table 표시 처리
- structured text / code / json / table segment 분리
- RunInfo 표시
- RAG 표시용 trace parser
- Web Search grounding/source 표시용 trace parser
- 입력창 UI
- mode 선택 UI
- RAG / Web Search toggle UI
- fallback toggle UI

이 구조에서는 UI 보정이나 parser 보정을 진행할 때 다음 위험이 있었다.

- 표시용 parser와 Compose UI가 섞임
- renderer와 block 저장 구조의 경계가 흐려짐
- TEXT_BLOCK 내부 markdown table 표시와 명시적 TABLE_BLOCK 표시 책임이 혼재됨
- RunInfo / RAG / Web Search trace marker 의존 구간을 실수로 건드릴 위험이 큼
- 입력창 분리 없이 대화방 화면을 계속 수정하면 send flow 회귀 위험이 큼

---

## 4. 1~5단계 완료 요약

| 단계 | 작업 | 상태 | 핵심 결과 |
|---|---|---|---|
| 1단계 | `ConversationDisplayParser.kt` 분리 | 완료 | structured/text/code/json/table 표시용 순수 parser/helper 분리 |
| 2단계 | `ConversationAssistantRenderer.kt` 생성 | 완료 | assistant text 표시 계층 분리 |
| 3단계 | `ConversationBlocks.kt` 책임 재정리 | 완료 | 명시적 OutputBlock renderer 책임으로 정리 |
| 4단계 | `ConversationRunInfoParser.kt` 생성 | 완료 | RunInfo / RAG / Web Search 표시용 parser/helper 분리 |
| 5단계 | `ConversationInputPanel.kt` 생성 | 완료 | 입력창 UI 분리 |

전체 단계에서 유지한 원칙은 다음과 같다.

- `ConversationEngine` 변경 없음
- `ConversationViewModel.sendMessage` 흐름 변경 없음
- `ConversationPromptBuilder` 변경 없음
- `ConversationFileStore` 저장 schema 변경 없음
- `ConversationModels` / `ConversationRequestResponse` schema 변경 없음
- Provider 호출 변경 없음
- RAG / Web Search 실행 로직 변경 없음
- 작업모드 영향 없음
- `block.content` 저장값 변경 없음
- block type mapping 변경 없음

---

## 5. 1단계 ConversationDisplayParser 분리 결과

### 5.1 생성 파일

- `app/src/main/java/com/maha/app/ui/conversation/ConversationDisplayParser.kt`

### 5.2 분리된 책임

`ConversationDisplayParser.kt`는 UI와 무관한 표시용 순수 parser/helper를 담당한다.

분리 대상은 다음 성격이다.

- assistant text-like block 확장
- structured answer segment 분리
- plain structured segment 분리
- markdown table segment 분리
- json segment 분리
- adjacent text segment 병합
- markdown table 시작 여부 판단
- json text 유효성 판단
- json bracket balance 판단

### 5.3 유지 사항

- Compose UI 함수는 이동하지 않았다.
- RAG parser, Web Search parser, RunInfo parser는 1단계에서 분리하지 않았다.
- send flow, storage flow, Provider 호출은 변경하지 않았다.
- block type mapping은 변경하지 않았다.

---

## 6. 2단계 Assistant message rendering layer 분리 결과

### 6.1 생성 파일

- `app/src/main/java/com/maha/app/ui/conversation/ConversationAssistantRenderer.kt`

### 6.2 분리된 책임

`ConversationAssistantRenderer.kt`는 assistant text 표시 계층을 담당한다.

현재 책임은 다음과 같다.

- 일반 assistant text 표시
- heading 표시
- quote 표시
- TEXT_BLOCK / MARKDOWN_BLOCK 내부 markdown table 표시
- bold marker와 `<br>`를 화면 표시에서만 정리

### 6.3 핵심 정책

- 원본 `block.content`는 변경하지 않는다.
- TEXT_BLOCK을 TABLE_BLOCK으로 저장하거나 변환하지 않는다.
- copy / share / action dialog는 원본 content 기준을 유지한다.
- markdown table이 포함된 경우에도 renderer 단계에서만 text / heading / quote / table 형태로 분리 표시한다.

### 6.4 표시 상태

- 일반 assistant text 표시 유지
- heading marker가 화면에서 제목 스타일로 표시되는 경로 확보
- quote marker가 화면에서 인용 스타일로 표시되는 경로 확보
- markdown table 포함 TEXT_BLOCK이 table visual로 표시되는 경로 확보

---

## 7. 3단계 ConversationBlocks renderer 책임 재정리 결과

### 7.1 수정 파일

- `app/src/main/java/com/maha/app/ui/conversation/ConversationBlocks.kt`

### 7.2 현재 책임

`ConversationBlocks.kt`는 명시적 `ConversationOutputBlock` renderer 책임을 담당한다.

현재 책임은 다음과 같다.

- 명시적 OutputBlock card shell
- 명시적 CODE_BLOCK 표시
- 명시적 JSON_BLOCK 표시
- 명시적 TABLE_BLOCK 표시
- block copy action
- block action dialog
- preview / expand
- edit / share / long-click callback 연결 유지

### 7.3 책임에서 제외된 영역

- TEXT_BLOCK / MARKDOWN_BLOCK 내부 markdown table 표시 책임은 `ConversationAssistantRenderer.kt`가 담당한다.
- assistant 본문 내부 segment 표시는 `ConversationBlocks.kt` 책임이 아니다.
- RunInfo / RAG / Web Search parser는 `ConversationBlocks.kt` 책임이 아니다.

### 7.4 유지 사항

- block type mapping 변경 없음
- block content 저장값 변경 없음
- callback 의미 변경 없음
- CODE_BLOCK / JSON_BLOCK 표시 의미 유지
- 명시적 TABLE_BLOCK 표시 유지

---

## 8. 4단계 RunInfo / RAG / Web Search parser 분리 결과

### 8.1 생성 파일

- `app/src/main/java/com/maha/app/ui/conversation/ConversationRunInfoParser.kt`

### 8.2 분리된 책임

`ConversationRunInfoParser.kt`는 UI와 무관한 실행정보 표시용 parser/helper를 담당한다.

분리 대상 성격은 다음과 같다.

- RAG run info parser
- Web Search grounding info parser
- Web Search source info parser
- source field 정규화 helper
- UI 의존 없는 copy text builder
- provider / fallback summary 표시용 parser 일부

### 8.3 유지 사항

- `ConversationRunSummaryPanelReadable` UI 함수는 이동하지 않았다.
- `RunRagSection` UI 함수는 이동하지 않았다.
- `RunWebSearchGroundingSection` UI 함수는 이동하지 않았다.
- Card / Text / Modifier / IconButton을 사용하는 Compose UI 함수는 parser 파일로 이동하지 않았다.
- trace marker string은 변경하지 않았다.
- parser key는 변경하지 않았다.
- fallback policy는 변경하지 않았다.
- RAG / Web Search 실행 로직은 변경하지 않았다.

---

## 9. 5단계 ConversationInputPanel 파일 분리 결과

### 9.1 생성 파일

- `app/src/main/java/com/maha/app/ui/conversation/ConversationInputPanel.kt`

### 9.2 분리된 책임

`ConversationInputPanel.kt`는 대화방 입력창 UI를 담당한다.

현재 책임은 다음과 같다.

- 입력창 BasicTextField 표시
- send button 표시
- mode radio 표시
- RAG switch 표시
- Web Search switch 표시
- Web Search fallback switch 표시
- quick setting 접힘 / 펼침 표시
- keyboard hide 후 onSend 호출 흐름 유지
- canSend 조건 표시 반영

### 9.3 유지 사항

- 입력 상태 소유권은 변경하지 않았다.
- `ConversationViewModel` 연결 방식은 변경하지 않았다.
- sendMessage 흐름은 변경하지 않았다.
- canSend 조건은 변경하지 않았다.
- onSend / onModeChange / onToggleSearch / onToggleWebSearch / onToggleFallback callback 의미는 변경하지 않았다.
- RAG / Web Search 실행 로직은 변경하지 않았다.

---

## 10. 신규 생성 파일 목록

리팩토링 1~5단계에서 신규 생성된 파일은 다음과 같다.

- `ConversationDisplayParser.kt`
- `ConversationAssistantRenderer.kt`
- `ConversationRunInfoParser.kt`
- `ConversationInputPanel.kt`

이미 이전 단계에서 생성되어 있던 대화방 visual skeleton 관련 파일은 다음과 같다.

- `ConversationStyleTokens.kt`
- `ConversationUiComponents.kt`

---

## 11. 수정된 기존 파일 목록

리팩토링 1~5단계에서 수정된 기존 파일은 다음과 같다.

- `ConversationRoomScreen.kt`
- `ConversationBlocks.kt`

수정 성격은 다음과 같다.

- `ConversationRoomScreen.kt`: 집중되어 있던 parser / renderer / input panel 일부를 신규 파일 호출 구조로 분리
- `ConversationBlocks.kt`: 명시적 OutputBlock renderer 책임으로 정리

---

## 12. 현재 책임 경계

### 12.1 ConversationRoomScreen.kt

현재 책임:

- 대화방 전체 화면 composition
- ViewModel state 연결
- 대화 목록 / 메시지 목록 배치
- 분리된 renderer / parser / input panel 호출
- 설정 drawer / navigation 연결

제외된 책임:

- assistant text 세부 rendering
- text/code/json/table 순수 parser
- RunInfo / RAG / Web Search 순수 parser
- 입력창 UI 세부 구현

### 12.2 ConversationDisplayParser.kt

현재 책임:

- assistant display segment 생성
- structured/text/code/json/table 표시용 순수 parser/helper
- UI와 무관한 text parsing

금지:

- Compose UI 포함 금지
- RAG/Web Search trace parser 포함 금지
- send flow 포함 금지

### 12.3 ConversationAssistantRenderer.kt

현재 책임:

- assistant text 표시
- heading 표시
- quote 표시
- TEXT_BLOCK / MARKDOWN_BLOCK 내부 markdown table 표시
- 화면 표시용 cleanup

금지:

- 원본 block.content 변경 금지
- block type 변환 금지
- copy/share 기준 변경 금지

### 12.4 ConversationBlocks.kt

현재 책임:

- 명시적 OutputBlock card rendering
- CODE_BLOCK / JSON_BLOCK / TABLE_BLOCK rendering
- block copy / action dialog / preview-expand

금지:

- assistant 본문 내부 markdown table 표시 책임 재흡수 금지
- block type mapping 변경 금지
- block content 저장값 변경 금지

### 12.5 ConversationRunInfoParser.kt

현재 책임:

- RunInfo / RAG / Web Search 표시용 순수 parser/helper
- source field normalize
- UI 의존 없는 copy text builder

금지:

- trace marker 변경 금지
- parser key 변경 금지
- RAG/Web Search 실행 로직 변경 금지

### 12.6 ConversationInputPanel.kt

현재 책임:

- 입력창 UI
- send button UI
- mode radio UI
- RAG / Web Search / fallback switch UI
- quick setting 접힘/펼침 UI

금지:

- input state ownership 변경 금지
- canSend 조건 변경 금지
- callback 의미 변경 금지
- sendMessage 흐름 변경 금지

---

## 13. 유지된 금지 영역

다음 영역은 리팩토링 1~5단계에서 변경하지 않는 것이 안정 기준이다.

### 13.1 Engine

- `ConversationEngine` 변경 없음
- Provider 호출 흐름 변경 없음
- RAG / Web Search 실행 로직 변경 없음

### 13.2 ViewModel

- `ConversationViewModel` 변경 없음
- sendMessage 흐름 변경 없음
- state ownership 변경 없음

### 13.3 Storage

- `ConversationFileStore` 변경 없음
- session.json / messages.jsonl 저장 구조 변경 없음

### 13.4 Schema

- `ConversationModels` 변경 없음
- `ConversationRequestResponse` schema 변경 없음
- block type mapping 변경 없음
- block.content 저장값 변경 없음

### 13.5 Prompt

- `ConversationPromptBuilder` 변경 없음
- prompt build 흐름 변경 없음

### 13.6 Provider

- Provider Adapter 변경 없음
- Provider request / response 형식 변경 없음
- API Key / token / secret 출력 없음

### 13.7 RAG / Web Search

- RAG 실행 로직 변경 없음
- Web Search 실행 로직 변경 없음
- trace marker 변경 없음
- parser key 변경 없음
- fallback policy 변경 없음

### 13.8 작업모드

- 작업모드 UI 변경 없음
- 작업모드 실행 흐름 변경 없음

---

## 14. 표시 계층 안정 지점

현재 표시 계층 안정 지점은 다음과 같다.

```text
ConversationRoomScreen.kt
├─ 전체 화면 composition
├─ ConversationAssistantRenderer.kt 호출
├─ ConversationBlocks.kt 호출
├─ ConversationRunInfoParser.kt 호출 결과를 기존 RunInfo UI에서 사용
└─ ConversationInputPanel.kt 호출
```

```text
ConversationDisplayParser.kt
└─ UI와 무관한 assistant display segment parser
```

```text
ConversationAssistantRenderer.kt
└─ assistant text / heading / quote / markdown table display
```

```text
ConversationBlocks.kt
└─ explicit OutputBlock card / code / json / table / action dialog
```

```text
ConversationRunInfoParser.kt
└─ RunInfo / RAG / Web Search trace display parser
```

```text
ConversationInputPanel.kt
└─ input field / send button / mode / RAG / Web Search toggles
```

---

## 15. 남은 위험 구간

남은 위험 구간은 다음과 같다.

### 15.1 ConversationRoomScreen.kt 잔여 대형 composition

파일 크기는 줄었지만 여전히 전체 화면 composition과 state 연결을 담당하므로 수정 시 주의가 필요하다.

### 15.2 RunInfo UI compact화

parser는 분리되었지만 RunInfo UI 자체는 아직 compact화하지 않았다. UI 보정은 별도 단계로 분리해야 한다.

### 15.3 RAG / Web Search 표시 wrapper

RAG / Web Search parser는 분리되었지만 표시 wrapper는 아직 별도 component로 정리하지 않았다. marker와 parser key를 건드리면 안 된다.

### 15.4 ConversationInputPanel 스타일 보정

입력창은 파일 분리만 완료되었고 스타일 보정은 하지 않았다. send flow와 canSend 조건을 유지한 상태에서 별도 단계로만 진행해야 한다.

### 15.5 TEXT_BLOCK 내부 markdown table 장기 정책

현재는 assistant text 내부에서 table visual로 표시한다. 후속에서 별도 block card로 분리할지는 지휘 세션 판단이 필요하다.

---

## 16. 후속 후보

### 후보 1. 1~5단계 빌드 / 실사용 통합 검증

- 일반 메시지 전송
- 표 포함 답변 표시
- code/json 표시
- RunInfo 표시
- RAG / Web Search 표시
- 입력창 / toggle / send 동작

### 후보 2. RunInfo UI compact화 설계

- parser는 이미 분리됨
- UI compact화는 별도 지시문으로 진행
- trace raw text 과다 노출 여부 검토

### 후보 3. RAG / Web Search 표시 wrapper 설계

- parser marker 변경 없이 표시 wrapper만 정리
- source/citation compact card 후보 검토

### 후보 4. ConversationInputPanel 스타일 보정

- 현재는 파일 분리만 완료
- 입력창, quick setting, toggle, send button 스타일 보정은 별도 단계

### 후보 5. ConversationRoomScreen 잔여 composition 정리

- navigation / drawer / message list / system back handling 등 더 큰 구조는 별도 설계 후 진행

---

## 17. 다음 지휘 판단 후보

권장 순서는 다음과 같다.

1. 1~5단계 리팩토링 통합 빌드 / 실사용 검증
2. RunInfo / RAG / Web Search 표시 wrapper 설계
3. ConversationInputPanel 스타일 보정
4. ConversationRoomScreen 잔여 composition 정리

바로 UI 보정보다 먼저 1~5단계 리팩토링 결과의 통합 안정성 확인이 필요하다.

---

## 18. 검증 체크리스트

### 18.1 문서 단계 검증

- 이 문서만 추가되었는가
- Kotlin 파일 변경이 없는가
- 실행 로직 변경이 없는가
- 저장 schema 변경이 없는가
- 작업모드 영향이 없는가

### 18.2 후속 실사용 검증

- 일반 입력 가능
- 빈 입력 전송 방지 유지
- 실행 중 전송 버튼 비활성 유지
- send button 동작 유지
- mode radio 선택 유지
- RAG switch 동작 유지
- Web Search switch 동작 유지
- fallback switch 동작 유지
- quick setting 접힘/펼침 유지
- keyboard hide 후 send 흐름 유지
- 일반 assistant text 표시 유지
- markdown table 포함 assistant text 표시 유지
- code/json block 표시 유지
- RunInfo 표시 유지
- RAG 표시 유지
- Web Search 표시 유지

---

## 19. 결론

대화세션 리팩토링 1~5단계는 기능 추가가 아니라 `ConversationRoomScreen.kt`의 책임 집중을 줄이는 구조 정리 작업이었다.

현재 안정 지점은 다음과 같다.

- display parser 분리 완료
- assistant message renderer 분리 완료
- explicit OutputBlock renderer 책임 정리 완료
- RunInfo / RAG / Web Search 표시용 parser 분리 완료
- input panel 파일 분리 완료
- Engine / ViewModel / Storage / Schema / Provider / RAG-Web Search 실행 / 작업모드는 미변경

후속 작업은 바로 추가 리팩토링으로 확장하지 말고, 먼저 1~5단계 통합 검증 후 진행해야 한다.
