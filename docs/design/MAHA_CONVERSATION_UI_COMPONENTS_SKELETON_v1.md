# MAHA_CONVERSATION_UI_COMPONENTS_SKELETON_v1

## 1. 목적

이 문서는 MAHA Android 대화모드의 대화방 화면에 적용할 `ConversationUiComponents` 후보 구조를 정의한다.

기준 문서는 다음이다.

- `MAHA_CONVERSATION_ROOM_STATIC_SCAN_v1.md`
- `MAHA_CONVERSATION_STYLE_TOKENS_v1.md`

이번 문서는 구현 지시가 아니라 skeleton 설계 기준 문서다. Kotlin 코드, `ConversationUiComponents.kt`, `ConversationStyleTokens.kt`, `ConversationRoomScreen.kt`, `ConversationBlocks.kt`, `ConversationRunPanel.kt`, `ConversationDialogs.kt`, `ConversationWebSearchQuickSetting.kt`, `ConversationEngine`, `ConversationViewModel`, Provider 호출, RAG/Web Search 실행 로직, 저장 schema, 작업모드 코드는 변경하지 않는다.

목표는 다음과 같다.

- 대화방 전용 UI component 후보를 역할별로 고정한다.
- component별 props, 연결 token, 적용 대상 파일, 위험도를 정리한다.
- 후속 구현 시 어떤 component부터 안전하게 skeleton화할지 순서를 제안한다.
- parser, trace marker, send flow, 저장 schema와 결합된 영역을 즉시 수정 금지 영역으로 분리한다.

## 2. 설계 전제

대화방은 이미 실사용 가능한 기능을 가진다. 대화 세션 생성, 대화 진행, assistant 응답 표시, output block 렌더링, 실행정보 표시, RAG/Web Search 표시, 메시지 편집 및 복사, 세션 저장 구조가 동작하는 상태를 전제로 한다.

이번 설계는 기능 추가가 아니라 visual wrapper 후보 정리다.

설계 전제:

1. 기존 대화방 기능은 유지한다.
2. `ConversationEngine`과 `ConversationViewModel` 전송 흐름은 변경하지 않는다.
3. `Provider Adapter` request/response 형식은 변경하지 않는다.
4. RAG/Web Search parser, trace marker, runInfo marker는 변경하지 않는다.
5. `ConversationStyleTokens`는 visual layer 후보이며 parser나 저장 schema에 영향을 주지 않는다.
6. `ConversationUiComponents`는 visual wrapper 후보이며 기존 callback 의미를 바꾸지 않는다.
7. 1차 구현은 LOW 위험도 component부터 시작한다.
8. input panel, run info, RAG, Web Search 표시는 HIGH 위험도로 별도 단계에서 진행한다.

## 3. component skeleton 설계 원칙

`ConversationUiComponents` 후보는 다음 원칙을 따른다.

- 대화방 본문 가독성을 설정 화면 일관성보다 우선한다.
- `SettingsUiComponents`를 그대로 복사하지 않는다.
- 대화방 전용 `ConversationStyleTokens`를 참조한다.
- 큰 배경 면은 상태색으로 칠하지 않는다.
- 상태 차이는 border, text, chip, label 중심으로 표현한다.
- RAG/Web Search/source/citation은 assistant 본문과 시각적으로 분리한다.
- parser/helper 함수는 component 안으로 옮기지 않는다.
- callback 이름과 의미는 기존 기능과 동일하게 유지한다.
- wrapper 도입은 기존 props를 감싸는 방식으로 시작하고 state ownership을 변경하지 않는다.

## 4. ConversationStyleTokens 연결 기준

후속 Kotlin 구현 후보 구조는 다음이다.

```text
ConversationStyleTokens
→ ConversationUiComponents
→ ConversationRoomScreen / ConversationBlocks / ConversationRunPanel / ConversationDialogs
```

연결 기준:

- component는 색상, radius, spacing, typography를 직접 정의하지 않고 token을 참조한다.
- 화면 파일은 token을 직접 많이 참조하기보다 component props를 통해 시각 구조를 전달한다.
- visual token은 `GraphicsSettings`와 연결될 수 있지만, 초기 skeleton 단계에서는 정적 기본값을 사용한다.
- `SettingsStyleTokens`와 공유 가능한 값은 `GraphicsThemeResolver` 후보를 통해 간접 공유한다.
- `ConversationUiComponents`는 parser marker, trace marker, request/response schema, 저장 schema를 알지 않는다.

## 5. component 후보 개요

필수 후보 component는 다음이다.

| component | 영역 | 1차 후보 | 위험도 |
|---|---|---:|---|
| `ConversationScreenSurface` | 전체 screen surface | 아니오 | MEDIUM |
| `ConversationMessageCard` | 공통 message shell | 예 | LOW |
| `ConversationUserBubble` | user message | 예 | MEDIUM |
| `ConversationAssistantBlockCard` | assistant block shell | 예 | LOW |
| `ConversationMessageText` | text body | 예 | LOW |
| `ConversationIconActionButton` | copy/edit/expand action | 예 | MEDIUM |
| `ConversationInputSurface` | input panel shell | 아니오 | HIGH |
| `ConversationTextInputBox` | text input | 아니오 | HIGH |
| `ConversationToggleRow` | RAG/Web Search/mode toggle | 아니오 | HIGH |
| `ConversationSendButton` | send action | 아니오 | HIGH |
| `ConversationRunInfoCard` | run info shell | 아니오 | HIGH |
| `ConversationRunInfoSection` | run info section | 아니오 | HIGH |
| `ConversationRagContextPanel` | RAG context | 아니오 | HIGH |
| `ConversationWebSearchSourceCard` | Web Search source | 아니오 | HIGH |
| `ConversationCitationChip` | citation chip | 예 | MEDIUM |
| `ConversationStructuredBlockCard` | structured block shell | 예 | MEDIUM |
| `ConversationCodeBlockSurface` | code block shell | 예 | MEDIUM |
| `ConversationJsonBlockSurface` | json block shell | 예 | MEDIUM |
| `ConversationTableSurface` | table block shell | 예 | MEDIUM |
| `ConversationDialogSurface` | dialog shell | 예 | MEDIUM |
| `ConversationNoticeBlock` | error/warning/info notice | 예 | LOW |

## 6. Message component 후보

### 6.1 ConversationMessageCard

- 역할: user / assistant / system message의 공통 shell 후보.
- 대체 대상: `UserMessageBlock`, assistant plain text block, system/error notice 주변 shell.
- 필요 props:
  - `role`
  - `modifier`
  - `selected`
  - `content`
- 연결 token:
  - `ConversationMessageTokens.messageUserBackground`
  - `ConversationMessageTokens.messageAssistantBackground`
  - `ConversationMessageTokens.messageSystemBackground`
  - `ConversationMessageTokens.messageBorderColor`
  - `ConversationMessageTokens.messageRadius`
  - `ConversationMessageTokens.messagePadding`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationBlocks.kt`
- 위험도: LOW
- 1차 구현 여부: 예
- 금지 조건:
  - role 분기 의미 변경 금지
  - message order 변경 금지
  - edit/copy callback 제거 금지

### 6.2 ConversationUserBubble

- 역할: 사용자 메시지 표시 전용 bubble/card 후보.
- 대체 대상: `UserMessageBlock` 내부 card shell.
- 필요 props:
  - `text`
  - `createdAt`
  - `onCopy`
  - `onEdit`
  - `onUnsupportedEditRequest`
  - `isEditable`
- 연결 token:
  - `ConversationMessageTokens.userBubbleBackground`
  - `ConversationMessageTokens.userBubbleBorder`
  - `ConversationMessageTokens.messageMaxWidthPhone`
  - `ConversationMessageTokens.messageMaxWidthTablet`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 조건부
- 금지 조건:
  - `onEditRequest` 의미 변경 금지
  - clipboard action 제거 금지
  - unsupported edit 안내 흐름 변경 금지

### 6.3 ConversationAssistantBlockCard

- 역할: assistant text / structured block의 외부 visual shell 후보.
- 대체 대상: `AssistantPlainTextBlock`, `StructuredOutputBlock`, output block card shell.
- 필요 props:
  - `blockType`
  - `title`
  - `showHeader`
  - `content`
- 연결 token:
  - `ConversationMessageTokens.assistantBlockBackground`
  - `ConversationMessageTokens.assistantBlockBorder`
  - `ConversationStructuredBlockTokens.structuredBlockRadius`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationBlocks.kt`
- 위험도: LOW to MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - structured parser 결과 변경 금지
  - block order 변경 금지

### 6.4 ConversationMessageText

- 역할: markdown/plain text body typography wrapper 후보.
- 대체 대상: assistant/user text body `Text` 사용처.
- 필요 props:
  - `text`
  - `styleRole`
  - `maxLines`
  - `overflow`
- 연결 token:
  - `ConversationTypography.messageBody`
  - `ConversationTypography.messageLineHeight`
  - `ConversationTypography.mutedText`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationBlocks.kt`
- 위험도: LOW
- 1차 구현 여부: 예
- 금지 조건:
  - markdown parser 변경 금지
  - text content 변형 금지

## 7. Input component 후보

### 7.1 ConversationInputSurface

- 역할: 입력창 전체 panel shell 후보.
- 대체 대상: `ConversationInputPanel`의 bottom input area container.
- 필요 props:
  - `enabled`
  - `isSending`
  - `content`
- 연결 token:
  - `ConversationInputTokens.inputPanelBackground`
  - `ConversationInputTokens.inputPanelBorder`
  - `ConversationInputTokens.inputPanelPadding`
  - `ConversationInputTokens.inputPanelMinHeight`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - keyboard interaction 변경 금지
  - send enable 조건 변경 금지
  - input state ownership 변경 금지

### 7.2 ConversationTextInputBox

- 역할: 대화 입력 text field visual wrapper 후보.
- 대체 대상: `ConversationInputPanel`의 `BasicTextField` / input decoration.
- 필요 props:
  - `value`
  - `onValueChange`
  - `placeholder`
  - `enabled`
  - `maxHeight`
  - `singleLine`
- 연결 token:
  - `ConversationInputTokens.inputFieldBackground`
  - `ConversationInputTokens.inputFieldBorder`
  - `ConversationInputTokens.inputFieldRadius`
  - `ConversationTypography.inputText`
  - `ConversationTypography.inputPlaceholder`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - `onValueChange` 의미 변경 금지
  - IME/send action 변경 금지
  - empty input send 방지 조건 변경 금지

### 7.3 ConversationToggleRow

- 역할: RAG / Web Search / mode radio / quick setting toggle row 후보.
- 대체 대상: input panel 주변 switch/radio/quick setting row.
- 필요 props:
  - `label`
  - `checked`
  - `enabled`
  - `onCheckedChange`
  - `description`
- 연결 token:
  - `ConversationInputTokens.toggleTrackChecked`
  - `ConversationInputTokens.toggleTrackUnchecked`
  - `ConversationInputTokens.toggleTextColor`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationWebSearchQuickSetting.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - RAG/Web Search toggle state 변경 금지
  - mode selection semantics 변경 금지

### 7.4 ConversationSendButton

- 역할: send action button visual wrapper 후보.
- 대체 대상: input panel send button.
- 필요 props:
  - `enabled`
  - `isSending`
  - `onSend`
  - `icon`
  - `label`
- 연결 token:
  - `ConversationInputTokens.sendButtonBackground`
  - `ConversationInputTokens.sendButtonBorder`
  - `ConversationInputTokens.sendButtonRadius`
  - `ConversationInputTokens.sendButtonContentColor`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - `onSend` 의미 변경 금지
  - loading/disabled condition 변경 금지
  - duplicate send 방지 로직 변경 금지

## 8. Run info component 후보

### 8.1 ConversationRunInfoCard

- 역할: 실행정보 전체 shell 후보.
- 대체 대상: `ConversationRunSummaryPanelReadable`, `ConversationRunPanel` shell.
- 필요 props:
  - `status`
  - `providerName`
  - `modelName`
  - `latencySec`
  - `collapsed`
  - `onToggleCollapsed`
  - `content`
- 연결 token:
  - `ConversationRunInfoTokens.runSummaryBackground`
  - `ConversationRunInfoTokens.runSummaryBorder`
  - `ConversationRunInfoTokens.runSummaryRadius`
  - `ConversationRunInfoTokens.runSummaryCompactHeight`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationRunPanel.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - runInfo parser 변경 금지
  - trace marker 변경 금지
  - RAG/Web Search section visibility 조건 변경 금지

### 8.2 ConversationRunInfoSection

- 역할: 실행정보 내부 section wrapper 후보.
- 대체 대상: provider summary, worker result, RAG run info, Web Search grounding section.
- 필요 props:
  - `title`
  - `statusTone`
  - `collapsed`
  - `onToggle`
  - `copyText`
  - `content`
- 연결 token:
  - `ConversationRunInfoTokens.sectionBackground`
  - `ConversationRunInfoTokens.sectionBorder`
  - `ConversationRunInfoTokens.sectionHeaderText`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationRunPanel.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - copy text builder 변경 금지
  - parser key 변경 금지

## 9. RAG component 후보

### 9.1 ConversationRagContextPanel

- 역할: RAG context summary / detail panel 후보.
- 대체 대상: RAG run info section, RAG context preview area.
- 필요 props:
  - `enabled`
  - `resultCount`
  - `fallbackReason`
  - `totalTokenEstimate`
  - `sources`
  - `onCopy`
  - `content`
- 연결 token:
  - `ConversationRagTokens.ragContextBackground`
  - `ConversationRagTokens.ragContextBorder`
  - `ConversationRagTokens.ragSourceChipBackground`
  - `ConversationRagTokens.ragFallbackTextColor`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationRunPanel.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - RAG search result structure 변경 금지
  - fallbackReason key 변경 금지
  - RAG context prompt insertion 변경 금지

## 10. Web Search / citation / source component 후보

### 10.1 ConversationWebSearchSourceCard

- 역할: Web Search source/citation card 후보.
- 대체 대상: grounding sources display, source row/card.
- 필요 props:
  - `title`
  - `url`
  - `snippet`
  - `index`
  - `onOpen`
  - `onCopy`
- 연결 token:
  - `ConversationWebSearchTokens.sourceCardBackground`
  - `ConversationWebSearchTokens.sourceCardBorder`
  - `ConversationWebSearchTokens.sourceTitleText`
  - `ConversationWebSearchTokens.sourceUrlText`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationRunPanel.kt`
- 위험도: HIGH
- 1차 구현 여부: 아니오
- 금지 조건:
  - `parseWebSearchSourcesInfo` marker 변경 금지
  - source URL sanitization 정책 변경 금지
  - citations 본문 삽입 금지

### 10.2 ConversationCitationChip

- 역할: compact citation/source status chip 후보.
- 대체 대상: citation count, source count, grounding status chip.
- 필요 props:
  - `label`
  - `tone`
  - `selected`
  - `onClick`
- 연결 token:
  - `ConversationWebSearchTokens.citationChipBackground`
  - `ConversationWebSearchTokens.citationChipBorder`
  - `ConversationWebSearchTokens.citationChipText`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationRunPanel.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예, 단 source parser와 분리할 때만
- 금지 조건:
  - source/citation count 계산 변경 금지

## 11. Structured / code / json / table component 후보

### 11.1 ConversationStructuredBlockCard

- 역할: output block shell 후보.
- 대체 대상: `StructuredOutputBlock`, `ConversationOutputBlockCard` shell.
- 필요 props:
  - `blockType`
  - `title`
  - `content`
  - `actions`
- 연결 token:
  - `ConversationStructuredBlockTokens.structuredBlockBackground`
  - `ConversationStructuredBlockTokens.structuredBlockBorder`
  - `ConversationStructuredBlockTokens.structuredBlockRadius`
- 적용 대상 파일:
  - `ConversationBlocks.kt`
  - `ConversationRoomScreen.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - block type mapping 변경 금지
  - block content transform 변경 금지

### 11.2 ConversationCodeBlockSurface

- 역할: code block visual shell 후보.
- 대체 대상: code block renderer, syntax highlight shell.
- 필요 props:
  - `language`
  - `code`
  - `onCopy`
  - `wrapLines`
- 연결 token:
  - `ConversationCodeTokens.codeBlockBackground`
  - `ConversationCodeTokens.codeBlockBorder`
  - `ConversationCodeTokens.codeBlockFontScale`
  - `ConversationCodeTokens.syntaxKeywordColor`
  - `ConversationCodeTokens.syntaxStringColor`
- 적용 대상 파일:
  - `ConversationBlocks.kt`
  - `ConversationRoomScreen.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - syntax parser 변경 금지
  - code text mutation 금지

### 11.3 ConversationJsonBlockSurface

- 역할: JSON block visual shell 후보.
- 대체 대상: json block renderer.
- 필요 props:
  - `jsonText`
  - `pretty`
  - `onCopy`
  - `onToggleRaw`
- 연결 token:
  - `ConversationJsonTokens.jsonBlockBackground`
  - `ConversationJsonTokens.jsonBlockBorder`
  - `ConversationJsonTokens.jsonKeyTextColor`
- 적용 대상 파일:
  - `ConversationBlocks.kt`
  - `ConversationRoomScreen.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - JSON validation/parse semantics 변경 금지

### 11.4 ConversationTableSurface

- 역할: table block visual shell 후보.
- 대체 대상: table renderer.
- 필요 props:
  - `headers`
  - `rows`
  - `compact`
  - `horizontalScrollEnabled`
- 연결 token:
  - `ConversationTableTokens.tableBackground`
  - `ConversationTableTokens.tableBorder`
  - `ConversationTableTokens.tableHeaderBackground`
  - `ConversationTableTokens.tableCellPadding`
- 적용 대상 파일:
  - `ConversationBlocks.kt`
  - `ConversationRoomScreen.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 조건부
- 금지 조건:
  - markdown table parser 변경 금지
  - row/column ordering 변경 금지

## 12. Dialog / popup component 후보

### 12.1 ConversationDialogSurface

- 역할: conversation dialog / popup modal visual shell 후보.
- 대체 대상: `ConversationBlockActionDialog`, `ConversationSettingsDialog`, `ConversationMessageEditDialog` container.
- 필요 props:
  - `title`
  - `onDismissRequest`
  - `confirmAction`
  - `dismissAction`
  - `content`
- 연결 token:
  - `ConversationDialogTokens.dialogBackground`
  - `ConversationDialogTokens.dialogBorder`
  - `ConversationDialogTokens.dialogScrim`
  - `ConversationDialogTokens.dialogRadius`
- 적용 대상 파일:
  - `ConversationDialogs.kt`
  - `ConversationBlocks.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - dismiss condition 변경 금지
  - confirm/delete callback 의미 변경 금지
  - message edit save flow 변경 금지

## 13. Notice / error / warning component 후보

### 13.1 ConversationNoticeBlock

- 역할: error / warning / info / trace compact notice visual wrapper 후보.
- 대체 대상: error block, provider warning, fallback notice, unsupported edit notice.
- 필요 props:
  - `tone`
  - `title`
  - `message`
  - `actions`
  - `collapsible`
- 연결 token:
  - `ConversationNoticeTokens.errorNoticeBorder`
  - `ConversationNoticeTokens.warningNoticeBorder`
  - `ConversationNoticeTokens.infoNoticeBorder`
  - `ConversationNoticeTokens.noticeBackground`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationBlocks.kt`
  - `ConversationDialogs.kt`
- 위험도: LOW to MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - errorType 변경 금지
  - raw error content 삭제 금지
  - trace marker 변경 금지

## 14. Action button / copy / edit / expand component 후보

### 14.1 ConversationIconActionButton

- 역할: copy, edit, expand/collapse, preview, raw view 등 작은 icon/text action wrapper 후보.
- 대체 대상: `IconButton`, `TextButton`, small action row.
- 필요 props:
  - `icon`
  - `label`
  - `enabled`
  - `tone`
  - `onClick`
- 연결 token:
  - `ConversationColors.actionTextColor`
  - `ConversationBorders.actionBorderColor`
  - `ConversationShapes.actionButtonRadius`
  - `ConversationSpacing.actionButtonPadding`
- 적용 대상 파일:
  - `ConversationRoomScreen.kt`
  - `ConversationBlocks.kt`
  - `ConversationRunPanel.kt`
  - `ConversationDialogs.kt`
- 위험도: MEDIUM
- 1차 구현 여부: 예
- 금지 조건:
  - copy text builder 변경 금지
  - edit callback 의미 변경 금지
  - expand/collapse state ownership 변경 금지

## 15. component별 공통 props 기준

### 15.1 공통 props

후속 skeleton에서 공통으로 고려할 props는 다음이다.

| prop | 역할 | 주의 |
|---|---|---|
| `modifier` | layout 확장 | 기존 layout weight/scroll 구조 변경 금지 |
| `enabled` | action 가능 여부 | 기존 enabled 조건 그대로 전달 |
| `selected` | 선택 상태 | 선택 의미를 새로 만들지 않음 |
| `tone` | neutral/info/success/warning/danger/action | 큰 background 채움 금지 |
| `compact` | 밀도 조정 | 기본값은 기존 UI와 동일 |
| `onClick` | action callback | 의미 변경 금지 |
| `content` | slot content | parser/helper를 slot 안으로 이동 금지 |

### 15.2 message props

- `role`
- `messageId`
- `text`
- `blocks`
- `createdAt`
- `isEditable`
- `onCopy`
- `onEdit`
- `onUnsupportedEditRequest`

### 15.3 input props

- `inputText`
- `onInputChange`
- `canSend`
- `isSending`
- `onSend`
- `searchEnabled`
- `webSearchEnabled`
- `webSearchFallbackEnabled`
- `onToggleSearch`
- `onToggleWebSearch`
- `onToggleFallback`

### 15.4 run info props

- `runStatus`
- `providerName`
- `modelName`
- `latencySec`
- `ragInfo`
- `webSearchInfo`
- `errorType`
- `collapsed`
- `onToggleCollapsed`
- `onCopyRunInfo`

### 15.5 source/RAG props

- `sourceTitle`
- `sourceUrl`
- `snippet`
- `score`
- `fallbackReason`
- `citationCount`
- `searchQueryCount`
- `onCopySource`
- `onOpenSource`

### 15.6 dialog/action props

- `title`
- `message`
- `confirmLabel`
- `dismissLabel`
- `danger`
- `onConfirm`
- `onDismissRequest`
- `onCancel`

## 16. component별 적용 대상 파일

| component | 1차 적용 대상 | 후순위 적용 대상 |
|---|---|---|
| `ConversationMessageCard` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationAssistantBlockCard` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationMessageText` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationIconActionButton` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt`, `ConversationRunPanel.kt` |
| `ConversationDialogSurface` | `ConversationBlocks.kt` dialog | `ConversationDialogs.kt` |
| `ConversationStructuredBlockCard` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationCodeBlockSurface` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationJsonBlockSurface` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationTableSurface` | `ConversationBlocks.kt` | `ConversationRoomScreen.kt` |
| `ConversationInputSurface` | 후순위 | `ConversationRoomScreen.kt` |
| `ConversationRunInfoCard` | 후순위 | `ConversationRoomScreen.kt`, `ConversationRunPanel.kt` |
| `ConversationRagContextPanel` | 후순위 | `ConversationRoomScreen.kt`, `ConversationRunPanel.kt` |
| `ConversationWebSearchSourceCard` | 후순위 | `ConversationRoomScreen.kt`, `ConversationRunPanel.kt` |

## 17. component별 위험도 요약

### LOW

- `ConversationMessageCard`
- `ConversationMessageText`
- `ConversationAssistantBlockCard`의 단순 shell
- `ConversationNoticeBlock`의 info/warning/error visual shell

### MEDIUM

- `ConversationUserBubble`
- `ConversationIconActionButton`
- `ConversationDialogSurface`
- `ConversationStructuredBlockCard`
- `ConversationCodeBlockSurface`
- `ConversationJsonBlockSurface`
- `ConversationTableSurface`
- `ConversationCitationChip`

### HIGH

- `ConversationInputSurface`
- `ConversationTextInputBox`
- `ConversationToggleRow`
- `ConversationSendButton`
- `ConversationRunInfoCard`
- `ConversationRunInfoSection`
- `ConversationRagContextPanel`
- `ConversationWebSearchSourceCard`

### DO_NOT_TOUCH

- parser marker
- trace marker
- send flow
- request/response schema
- storage schema
- RAG/Web Search execution logic
- Provider Adapter call path

## 18. 1차 구현 우선순위

권장 순서:

1. `ConversationUiComponents.kt` skeleton 작성
2. `ConversationStyleTokens.kt` skeleton 작성 또는 선행 token skeleton 확인
3. `ConversationBlocks.kt`의 단순 block shell 후보부터 치환
4. `ConversationDialogSurface`를 block action dialog부터 적용
5. `ConversationMessageText` / `ConversationAssistantBlockCard`를 plain text 영역에 적용
6. `ConversationIconActionButton`을 copy/edit/expand action에 적용
7. `ConversationStructuredBlockCard` / code/json/table visual wrapper를 적용
8. `ConversationInputPanel`은 별도 지시문으로 분리
9. `RunInfo / RAG / Web Search` wrapper는 parser 고정 확인 후 별도 지시문으로 분리
10. phone / tablet / landscape 검증

## 19. 즉시 구현 금지 영역

다음 영역은 skeleton 설계 이후에도 바로 치환하지 않는다.

- `ConversationInputPanel`의 send enable 조건
- `ConversationInputPanel`의 keyboard / multiline height 정책
- `ConversationRunSummaryPanelReadable`의 parser 연결부
- RAG trace parser
- Web Search grounding/source parser
- copy text builder
- `TRACE_BLOCK` 분리 정책
- provider summary marker
- message edit 저장 정합성
- `messages.jsonl` 저장 구조
- `ConversationEngine.execute`
- `ConversationViewModel.sendMessage`

## 20. 검증 체크리스트

후속 구현 전 체크리스트:

- component가 기존 callback 의미를 바꾸지 않는가
- enabled/visible 조건이 기존 화면에서 그대로 전달되는가
- parser/helper 함수가 wrapper 내부로 이동하지 않았는가
- source/citation 표시가 assistant 본문에 직접 섞이지 않는가
- run info와 assistant 본문이 계속 분리되어 있는가
- copy/edit/expand action이 사라지지 않았는가
- `ConversationEngine`과 `ConversationViewModel`이 변경되지 않았는가
- RAG/Web Search 실행 로직이 변경되지 않았는가
- 저장 schema가 변경되지 않았는가
- 작업모드 파일이 변경되지 않았는가

## 21. 마이그레이션 전 안정 지점

현재 안정 지점은 다음이다.

- 대화방 기능은 이미 실사용 가능한 상태다.
- 이번 문서는 visual wrapper 후보만 정리한다.
- Kotlin 코드는 변경하지 않는다.
- 다음 구현 세션은 LOW 위험도 component skeleton부터 시작한다.
- HIGH 위험도 영역인 input panel, run info, RAG, Web Search는 별도 설계와 검증 후 진행한다.

