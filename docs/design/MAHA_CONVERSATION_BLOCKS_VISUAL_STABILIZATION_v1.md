# MAHA_CONVERSATION_BLOCKS_VISUAL_STABILIZATION_v1

## 1. 목적

이 문서는 `ConversationStyleTokens.kt` / `ConversationUiComponents.kt` skeleton 생성 이후 `ConversationBlocks.kt`에 1차 적용된 visual wrapper 범위와 현재 안정 지점을 기록한다.

이번 문서는 구현 지시문이 아니라 안정화 기록이다. 이 문서 작성 단계에서는 Kotlin UI 코드, parser, marker, block type mapping, callback, 저장 schema, 실행 로직, Provider 호출, RAG/Web Search 실행, 작업모드 코드를 변경하지 않는다.

목표는 다음과 같다.

- `ConversationBlocks.kt` visual wrapper 적용 범위를 명확히 고정한다.
- 적용된 영역과 미적용 영역을 분리한다.
- `TABLE_BLOCK`의 현재 표시 상태와 남은 문제를 기록한다.
- 원본 content 보존 정책과 화면 표시 정리 정책을 분리한다.
- 후속 보정 후보를 지휘 세션이 판단할 수 있게 정리한다.

---

## 2. 기준 시점

기준 시점은 다음 작업들이 완료된 이후다.

1. `ConversationStyleTokens.kt` / `ConversationUiComponents.kt` skeleton 생성
2. `ConversationBlocks.kt` 단순 visual shell 1차 적용
3. `conversationUnifiedCardShape()` / `conversationUnifiedCardColor()` helper 복구
4. `ConversationStyleTokens.kt`의 `cardBackground` / `cardRadius` alias 보정
5. `TABLE_BLOCK` 전용 표시 renderer 1차 보정
6. `TABLE_BLOCK` column divider 보정
7. `TABLE_BLOCK` 고정 구분열 + 가변 상세열 보정
8. 직전 검증에서 추가 Kotlin 변경 없이 상태 고정

직전 검증 기준 빌드는 사용자 확인 필요 상태로 기록되었다.

---

## 3. 관련 산출물

관련 문서는 다음과 같다.

- `MAHA_CONVERSATION_ROOM_UX_UI_REVIEW_v1.md`
- `MAHA_CONVERSATION_ROOM_STATIC_SCAN_v1.md`
- `MAHA_CONVERSATION_STYLE_TOKENS_v1.md`
- `MAHA_CONVERSATION_UI_COMPONENTS_SKELETON_v1.md`
- `MAHA_CONVERSATION_SESSION_INTEGRITY_AUDIT_v1.md`

관련 Kotlin 산출물은 다음과 같다.

- `app/src/main/java/com/maha/app/ui/conversation/ConversationStyleTokens.kt`
- `app/src/main/java/com/maha/app/ui/conversation/ConversationUiComponents.kt`
- `app/src/main/java/com/maha/app/ui/conversation/ConversationBlocks.kt`

---

## 4. 변경 이력 요약

### 4.1 Skeleton 생성

`ConversationStyleTokens.kt`와 `ConversationUiComponents.kt`가 신규 생성되었다.

이 단계에서는 기존 대화방 화면에 적용하지 않았고, LOW~MEDIUM 위험도 visual wrapper 후보만 skeleton으로 작성했다.

후순위로 남긴 영역은 다음과 같다.

- `ConversationInputSurface`
- `ConversationTextInputBox`
- `ConversationToggleRow`
- `ConversationSendButton`
- `ConversationRunInfoCard`
- `ConversationRunInfoSection`
- `ConversationRagContextPanel`
- `ConversationWebSearchSourceCard`

### 4.2 ConversationBlocks visual shell 1차 적용

`ConversationBlocks.kt`의 LOW~MEDIUM visual shell에만 wrapper를 적용했다.

주요 적용 범위는 다음과 같다.

- `ConversationOutputBlockCard` 외부 visual shell
- `StructuredBlockHeader` action wrapper
- `ConversationBlockActionDialog` dialog surface
- 일부 단순 Text body wrapper 후보

### 4.3 Compile hygiene 보정

`ConversationBlocks.kt` 적용 이후 발생한 helper / token reference 문제를 보정했다.

- `conversationUnifiedCardShape()` helper 복구
- `conversationUnifiedCardColor()` helper 복구
- `ConversationSurfaces.cardBackground` alias 추가
- `ConversationShapes.cardRadius` alias 추가

### 4.4 TABLE_BLOCK 표시 보정

`TABLE_BLOCK` 표시 문제에 대해 `ConversationBlocks.kt` 안에서만 단건 보정을 여러 차례 진행했다.

현재 상태는 다음과 같다.

- 구분열 / 상세열 2열 구조
- 고정 구분열 + 가변 상세열 구조
- 열 사이 column divider 추가
- `**bold marker**` 화면 표시 정리 경로 유지
- `<br>` 화면 표시 정리 경로 유지
- 긴 상세 텍스트는 상세열 안에서 줄바꿈되는 구조

단, `TABLE_BLOCK` 품질은 완전 확정이 아니라 임시 고정 상태다.

---

## 5. ConversationStyleTokens.kt 생성 상태

`ConversationStyleTokens.kt`는 대화방 전용 정적 token skeleton이다.

현재 성격은 다음과 같다.

- 실제 `GraphicsSettings`와 연결하지 않음
- runtime token provider 구조 아님
- 기존 설정 화면의 `SettingsStyleTokens`를 그대로 복사하지 않음
- 대화방 가독성 중심 token layer 후보
- 기존 화면에 전면 적용되지 않음

현재 포함된 성격의 token 그룹은 다음과 같다.

- `ConversationColors`
- `ConversationSurfaces`
- `ConversationBorders`
- `ConversationShapes`
- `ConversationSpacing`
- `ConversationTypography`
- `ConversationMessageTokens`
- `ConversationStructuredBlockTokens`
- `ConversationDialogTokens`
- `ConversationNoticeTokens`

현재 안정 지점에서 이 파일은 visual skeleton을 위한 기준값 제공 역할만 한다.

---

## 6. ConversationUiComponents.kt 생성 상태

`ConversationUiComponents.kt`는 대화방 전용 visual wrapper skeleton이다.

현재 성격은 다음과 같다.

- Compose component skeleton만 포함
- 기존 화면에 전면 적용하지 않음
- parser/helper/callback 의미를 포함하지 않음
- send flow, toggle flow, edit/copy callback 의미를 변경하지 않음
- LOW~MEDIUM 위험도 wrapper 중심

현재 포함된 주요 wrapper 후보는 다음과 같다.

- `ConversationMessageCard`
- `ConversationAssistantBlockCard`
- `ConversationMessageText`
- `ConversationIconActionButton`
- `ConversationStructuredBlockCard`
- `ConversationDialogSurface`
- `ConversationNoticeBlock`
- `ConversationTextActionButton`

후순위로 남아야 할 wrapper는 다음과 같다.

- 입력창 wrapper
- send button wrapper
- run info wrapper
- RAG panel wrapper
- Web Search source wrapper

---

## 7. ConversationBlocks.kt visual wrapper 적용 범위

`ConversationBlocks.kt`에는 LOW~MEDIUM visual shell 중심으로만 wrapper가 적용되었다.

적용된 영역은 다음과 같다.

1. Output block 외부 shell
   - 기존 block content와 type mapping은 유지
   - 외부 visual card shell만 wrapper 적용

2. Header action
   - copy action의 시각 wrapper 적용
   - copy callback 의미는 유지

3. Block action dialog
   - dialog surface wrapper 적용
   - 복사 / 텍스트 선택 / 메시지 편집 / 공유 callback 의미는 유지

4. TABLE_BLOCK visual renderer
   - 표시 UI 안에서 table row/cell 구조 보정
   - 원본 block content 저장값은 변경하지 않음

미적용 영역은 다음과 같다.

- `ConversationRoomScreen.kt`
- `ConversationInputPanel`
- `ConversationRunSummaryPanelReadable`
- RAG 표시 영역
- Web Search 표시 영역
- structured parser
- code/json/table parser upstream
- Provider summary parser
- trace marker parser

---

## 8. 적용된 wrapper 목록

| wrapper | 적용 대상 | 현재 역할 | 위험도 | 비고 |
|---|---|---|---|---|
| `ConversationMessageCard` | `ConversationOutputBlockCard` 외부 shell | block 카드 시각 shell | LOW~MEDIUM | block content 의미 변경 없음 |
| `ConversationIconActionButton` | `StructuredBlockHeader` copy action | icon action 시각 wrapper | MEDIUM | copy callback 의미 유지 |
| `ConversationDialogSurface` | `ConversationBlockActionDialog` | dialog surface 시각 wrapper | MEDIUM | dialog 표시/닫기 조건 유지 |
| `ConversationTextActionButton` | block action dialog button | text action button 시각 wrapper | MEDIUM | onClick 의미 유지 |
| `ConversationMessageText` | 단순 text body 후보 | 본문 text 표시 후보 | LOW | 전면 적용 아님 |
| `ConversationStructuredBlockCard` | structured block shell 후보 | structured visual shell 후보 | MEDIUM | parser 변경 없음 |

---

## 9. TABLE_BLOCK 현재 표시 상태

현재 `TABLE_BLOCK`은 `ConversationBlocks.kt` 내부에서 visual renderer를 통해 표시된다.

현재 표시 정책은 다음과 같다.

- 2열 row/cell 구조를 사용한다.
- 왼쪽은 구분열이다.
- 오른쪽은 상세열이다.
- 구분열은 고정 폭 기준으로 표시한다.
- 상세열은 남은 폭을 사용한다.
- 열 사이에는 약한 vertical divider가 있다.
- 긴 상세 텍스트는 상세열 안에서 줄바꿈된다.
- 화면 표시용 정리 함수가 `**bold marker**`와 `<br>` 노출을 줄인다.

현재 상태는 임시 고정이다.

---

## 10. TABLE_BLOCK 남은 문제

`TABLE_BLOCK`은 아직 완전 확정 상태가 아니다.

남은 문제 후보는 다음과 같다.

1. 원본 content 구조 불확실성
   - `TABLE_BLOCK`으로 들어온 content가 실제 row/cell 구조인지 확인이 필요하다.
   - markdown table인지, plain text인지, markdown/plain text 혼합인지 확인이 필요하다.

2. renderer 내부 표시 정리와 원본 보존 정책 경계
   - 화면 표시에서는 `**bold marker**`와 `<br>`를 정리한다.
   - copy action은 원본 `block.content`를 유지해야 한다.
   - 이 경계를 계속 유지해야 한다.

3. phone 폭 대응
   - 2열 고정 구조는 phone에서 상세열 폭이 부족할 수 있다.
   - 필요 시 horizontal scroll 또는 key/value stacked layout 후보를 검토한다.

4. upstream parser와 renderer 책임 중복
   - `ConversationRoomScreen` 쪽 table parser와 `ConversationBlocks.kt`의 table 표시 정리 책임이 중복될 가능성이 있다.
   - 이 문제는 바로 수정하지 않고 진단 후 별도 단계로 분리한다.

---

## 11. 원본 content 보존 / 화면 표시 정리 정책

현재 정책은 다음과 같다.

- 원본 `block.content` 저장값은 변경하지 않는다.
- copy / share action은 원본 content 기준을 유지한다.
- 화면 표시에서는 읽기 편한 형태로 marker를 정리할 수 있다.
- `**bold marker**` 제거 또는 표시 정리는 visual layer에 한정한다.
- `<br>`는 화면 표시에서 줄바꿈처럼 보이게 할 수 있다.
- parser marker / trace marker / block type mapping은 변경하지 않는다.

즉, `TABLE_BLOCK` 표시 보정은 renderer 내부 visual cleanup일 뿐이다.

---

## 12. copy / edit / share / long-click callback 유지 상태

현재 안정 지점에서 다음 callback 의미는 유지되어야 한다.

- block copy action
- block long-click action dialog
- block edit action
- block share action
- preview / expand action

visual wrapper는 button/card/dialog surface를 감쌀 수 있지만, callback 의미를 바꾸면 안 된다.

현재까지의 적용은 callback 의미를 변경하지 않는 범위에서 진행되었다.

---

## 13. code/json/table 영향 범위

현재 영향 범위는 다음과 같다.

| block type | 현재 상태 | 영향 |
|---|---|---|
| `CODE_BLOCK` | 기존 표시 의미 유지 | visual wrapper shell 영향만 가능 |
| `JSON_BLOCK` | 기존 표시 의미 유지 | visual wrapper shell 영향만 가능 |
| `TABLE_BLOCK` | table visual renderer 보정됨 | 표시 UI 영향 있음 |
| `TEXT_BLOCK` | 기존 표시 의미 유지 | visual wrapper shell 영향만 가능 |
| `MARKDOWN_BLOCK` | 기존 표시 의미 유지 | visual wrapper shell 영향만 가능 |

`TABLE_BLOCK` 외의 code/json 표시 의미는 변경하지 않는 것이 안정 지점이다.

---

## 14. 금지 영역 영향 없음 확인

이번 안정 지점까지 다음 영역은 수정하지 않는 것이 기준이다.

- `ConversationRoomScreen.kt`
- `ConversationRunPanel.kt`
- `ConversationDialogs.kt`
- `ConversationSessionListScreen.kt`
- `ConversationEngine.kt`
- `ConversationViewModel.kt`
- `ConversationPromptBuilder.kt`
- `ConversationFileStore.kt`
- `ConversationModels.kt`
- Provider Adapter 계열
- RAG 실행 로직
- Web Search 실행 로직
- 저장 schema
- 작업모드

또한 다음 항목도 변경하지 않는다.

- parser marker
- trace marker
- block type mapping
- block content 저장값
- sendMessage 흐름
- Provider 호출 경로
- copy / edit / share / long-click callback 의미

---

## 15. 현재 안정 지점

현재 안정 지점은 다음과 같이 정의한다.

### 적용 완료

- `ConversationStyleTokens.kt` skeleton 생성
- `ConversationUiComponents.kt` skeleton 생성
- `ConversationBlocks.kt` visual shell 1차 적용
- `ConversationOutputBlockCard` wrapper 적용
- `StructuredBlockHeader` action wrapper 적용
- `ConversationBlockActionDialog` surface wrapper 적용
- helper / token alias 컴파일 보정
- `TABLE_BLOCK` 임시 표시 보정

### 미적용

- `ConversationRoomScreen.kt`
- `ConversationInputPanel`
- `ConversationRunSummaryPanelReadable`
- RAG 표시 영역
- Web Search 표시 영역
- Conversation session list UI
- 대화방 전체 layout / navigation

### 임시 고정

- `TABLE_BLOCK` 고정 구분열 + 가변 상세열 표시
- `TABLE_BLOCK` bold marker / `<br>` 화면 정리
- `TABLE_BLOCK` column divider 표시

### 보류

- 원본 table content 구조 분석
- table parser / renderer 책임 분리
- phone에서 table stacked layout 여부
- RAG/Web Search 표시 wrapper
- RunInfo wrapper
- InputPanel wrapper

---

## 16. 후속 보정 후보

후속 후보는 다음 순서가 적합하다.

### 후보 1. TABLE_BLOCK 원본 content 구조 진단

- sample `block.content`를 기준으로 row/cell 구조 확인
- markdown table인지 plain text인지 판정
- renderer에서 처리 가능한 범위와 upstream parser 후보 분리

### 후보 2. TABLE_BLOCK phone layout 정책 결정

- 2열 고정 유지
- horizontal scroll 적용
- stacked key/value layout 적용
- compact table density 옵션 후보

### 후보 3. ConversationBlocks visual shell 안정화 검증

- block copy action
- action dialog
- preview/expand
- code/json 표시
- table 표시

### 후보 4. ConversationDialogs / ConversationSessionListScreen 잔여 스타일 확인

- dialog dark surface 잔여 확인
- session rename/delete dialog 확인
- message edit dialog와 block action dialog 스타일 경계 확인

### 후보 5. RunInfo / RAG / Web Search wrapper 설계 재확인

- parser marker 변경 없이 표시 wrapper만 분리
- trace/provider summary를 본문과 분리
- source/citation compact화 후보

---

## 17. 다음 지휘 판단 후보

지휘 세션에서 선택할 수 있는 다음 후보는 다음과 같다.

1. `TABLE_BLOCK` 원본 content 구조 진단을 먼저 진행한다.
2. `ConversationBlocks.kt` visual shell 검증을 마감하고 message wrapper 2차로 이동한다.
3. `ConversationDialogs.kt` / `ConversationSessionListScreen.kt` 잔여 Dialog 스타일 보정으로 이동한다.
4. `ConversationInputPanel`은 아직 건드리지 않고 별도 설계 후 진행한다.
5. `RunInfo / RAG / Web Search`는 parser marker 고정 문서 작성 후 별도 진행한다.

권장 판단은 1번이다.

이유는 `TABLE_BLOCK` 문제의 원인이 renderer layout인지 원본 content 구조인지 아직 완전히 분리되지 않았기 때문이다.

---

## 18. 검증 체크리스트

- 문서만 추가되었는가
- Kotlin 파일 변경이 없는가
- `ConversationBlocks.kt` 추가 보정이 없는가
- `ConversationRoomScreen.kt` 변경이 없는가
- `ConversationEngine.kt` / `ConversationViewModel.kt` 변경이 없는가
- Provider 호출 경로가 변경되지 않았는가
- RAG/Web Search 실행 로직이 변경되지 않았는가
- 저장 schema가 변경되지 않았는가
- 작업모드가 변경되지 않았는가
- 민감정보가 문서에 포함되지 않았는가
