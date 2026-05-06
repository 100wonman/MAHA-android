# MAHA_CONVERSATION_ROOM_STATIC_SCAN_v1

## 1. 목적

이 문서는 MAHA Android 대화모드의 대화방 UI 계열 파일을 정적 스캔하고, 후속 `ConversationStyleTokens` / `ConversationUiComponents` 설계 전에 고정해야 할 역할 목록과 위험 구역을 기록한다.

이번 문서는 구현 지시가 아니라 지도 작성 문서다. Kotlin UI 코드, 저장 로직, `ConversationEngine`, `ConversationViewModel`, Provider 호출, RAG / Web Search 실행 로직, Worker / Scenario 실제 실행 연결, 작업모드 코드는 변경하지 않는다.

## 2. 스캔 대상 파일

정적 스캔 대상은 다음 파일이다.

| 파일 | 스캔 목적 | 상태 |
|---|---|---|
| `app/src/main/java/com/maha/app/ui/conversation/ConversationRoomScreen.kt` | 대화방 화면 본체, 메시지 영역, 입력창, 실행정보, RAG/Web Search parser와 표시 영역 파악 | 읽기 전용 |
| `app/src/main/java/com/maha/app/ui/conversation/ConversationBlocks.kt` | OutputBlock 카드, 구조화 block, block action dialog 파악 | 읽기 전용 |
| `app/src/main/java/com/maha/app/ui/conversation/ConversationRunPanel.kt` | 별도 실행정보 panel 구성 파악 | 읽기 전용 |
| `app/src/main/java/com/maha/app/ui/conversation/ConversationDialogs.kt` | 대화 설정 dialog, 전역 설정 화면, 메시지 편집 dialog 파악 | 읽기 전용 |
| `app/src/main/java/com/maha/app/ConversationWebSearchQuickSetting.kt` | Web Search quick setting row와 switch 파악 | 읽기 전용 |

참고 기준 문서는 `MAHA_CONVERSATION_ROOM_UX_UI_REVIEW_v1.md`다. `MAHA_DIRECT_UI_USAGE_SCAN_v3.md`는 이번 세션에 제공되지 않았으므로, 본 문서는 위 Kotlin 파일의 직접 정적 스캔 결과를 기준으로 작성한다.

## 3. ConversationRoomScreen 전체 역할 요약

`ConversationRoomScreen.kt`는 대화방 UI의 중심 파일이다. 주요 책임은 다음과 같다.

- 대화방 상단 title / global settings 진입 표시
- 메시지 목록 렌더링
- user / assistant message block 분기
- assistant 응답의 structured block 자동 분해
- assistant trace block 제외 및 실행정보 panel 표시
- run summary, worker result, provider response summary, RAG, Web Search grounding/source 표시
- code/json/table block 렌더링 및 syntax highlight
- 입력창, quick settings, mode radio, RAG/Web Search switch, send action
- RAG/Web Search trace parser와 copy text helper

위 책임이 한 파일에 집중되어 있으므로, 후속 치환은 반드시 역할 단위로 분리해야 한다. 특히 parser / trace / RAG / Web Search / execution info는 UI token 치환과 함께 수정하면 회귀 위험이 크다.

## 4. 메시지 영역 역할

### 4.1 메시지 목록

`ConversationRoomScreen`의 `LazyColumn`은 `session.messages`를 표시한다. assistant 메시지인 경우 다음 처리를 수행한다.

- 직전 user message를 `triggerText`로 계산
- highlight test block 후보 생성
- structured answer segment로 확장
- `TRACE_BLOCK`을 실행정보로 분리
- `ConversationRunSummaryPanelReadable`을 assistant block 위에 표시
- trace block은 본문 출력에서 제외

### 4.2 User message

`UserMessageBlock`은 user message용 card와 action icon을 담당한다.

역할:

- 사용자 메시지를 card 형태로 표시
- 메시지 편집 action 제공
- 복사 action 제공
- user message는 편집 가능, assistant message는 편집 미지원 안내로 연결

위험도: `MEDIUM`

이유:

- `onEditRequest`, `onUnsupportedEditRequest`, clipboard action과 결합되어 있다.
- 단순 스타일 치환 중 action icon이 사라지거나 click target이 줄어들면 회귀가 발생한다.

### 4.3 Assistant plain text

`AssistantPlainTextBlock`은 assistant plain text를 단순 text로 표시한다.

위험도: `LOW`

후속 후보:

- `ConversationMessageText` 또는 `AssistantMessageText` wrapper 후보
- paragraph spacing / lineHeight token 후보

## 5. 입력 영역 역할

`ConversationInputPanel`은 bottom bar 전체를 담당한다.

포함 요소:

- quick setting 접힘/펼침 header
- mode radio group: 자동 / 일반 / 코드 / 검증
- RAG 검색 switch
- Web Search switch
- Web Search fallback switch
- `BasicTextField` 입력창
- send `IconButton`

기능 보존 조건:

- `canSend = inputText.trim().isNotEmpty() && !isRunning`
- send 클릭 시 keyboard hide 후 `onSend()` 호출
- RAG/Web Search/fallback toggle은 기존 callback 유지
- mode radio는 `onModeChange(option)` 유지

위험도: `HIGH`

이유:

- 전송 흐름과 직접 연결된다.
- `Switch`, `RadioButton`, `BasicTextField`, `IconButton`이 모두 UI state와 action에 결합되어 있다.
- 후속 스타일 치환 시 기능 변경 없이 wrapper 교체만 해야 한다.

## 6. 실행정보 패널 역할

### 6.1 ConversationRunSummaryPanelReadable

`ConversationRunSummaryPanelReadable`은 assistant 응답의 trace block과 `ConversationRun`을 결합해 실행정보 card를 구성한다.

포함 정보:

- 전체 run summary
- worker result summary
- RAG 표시
- Web Search grounding 표시
- Web Search sources 표시
- provider response summary / fallback provider response summary
- cleaned execution trace
- copy text 생성
- section별 접힘 상태

위험도: `HIGH`

이유:

- trace text parser와 강하게 결합되어 있다.
- RAG / Web Search / provider summary marker가 바뀌면 표시가 깨질 수 있다.
- UI 치환 시 parser/helper를 함께 수정하면 안 된다.

### 6.2 RunFlatSection / RunCollapsibleFlatTextSection

역할:

- 실행정보 하위 section 공통 wrapper
- copy action
- 접힘/펼침 action
- divider 표시
- raw trace / source / context text scroll 표시

위험도: `MEDIUM ~ HIGH`

후속 후보:

- `ConversationRunInfoSection`
- `ConversationRunInfoHeader`
- `ConversationCopyIconButton`
- `ConversationDivider`

### 6.3 ConversationRunPanel.kt

`ConversationRunSummaryPanel`은 별도 실행정보 panel 파일이다. 내부적으로 card와 worker detail card를 사용한다.

위험도: `MEDIUM`

주의:

- 실제 현재 화면에서 `ConversationRunSummaryPanelReadable`과 중복 역할일 수 있으므로, 후속 단계에서 사용 경로를 먼저 확인해야 한다.

## 7. RAG 표시 역할

RAG 관련 역할은 `ConversationRunSummaryPanelReadable`, `parseRagRunInfo`, `RunRagSection`, `buildRagRunCopyText`에 분산되어 있다.

주요 trace key:

- enabled
- query
- resultCount
- usedChunkCount
- maxContextChars
- fallback
- fallbackReason
- contextText

표시 정책:

- RAG 실행 여부와 결과 수를 실행정보 안에서 표시
- 참조 context는 별도 접힘 section에서 표시
- copy text 제공

위험도: `HIGH`

이유:

- trace marker와 parser 의존성이 있다.
- RAG context가 assistant 본문 또는 recent prompt/RAG index에 섞이지 않도록 후속 allowlist 설계와 연결해야 한다.

## 8. Web Search / grounding / source 표시 역할

Web Search 관련 역할은 다음 함수에 분산되어 있다.

- `parseWebSearchGroundingInfo`
- `parseWebSearchSourcesInfo`
- `RunWebSearchGroundingSection`
- `buildWebSearchGroundingCopyText`
- `buildWebSearchSourcesCopyText`
- `normalizeSourceField`

표시 정보:

- requested
- groundingExecuted
- groundingUsed
- citationCount
- searchQueryCount
- fallbackAllowed
- fallbackAttempted
- fallbackSucceeded
- groundingErrorType
- fallbackErrorType
- finalAnswerSource
- source title / url / snippet

위험도: `HIGH`

이유:

- Gemini native grounding trace marker와 UI parser가 결합되어 있다.
- `sources` parsing은 title/url/snippet normalization에 의존한다.
- citation/source 표시 UX 개선은 가능하지만 parser marker 변경은 금지한다.

## 9. structured output / code / json / table block 역할

`ConversationRoomScreen.kt` 내부 structured 처리와 `ConversationBlocks.kt` output block 처리가 함께 존재한다.

### 9.1 structured parser

관련 함수:

- `expandAssistantStructuredBlocks`
- `splitAssistantTextLikeBlock`
- `parseStructuredAnswerSegments`
- `parsePlainStructuredSegments`
- `splitMarkdownTableSegments`
- `splitJsonSegments`
- `mergeAdjacentTextSegments`
- `isMarkdownTableStart`
- `isValidJsonText`
- `isJsonBracketBalanced`

위험도: `HIGH`

이유:

- assistant text를 block으로 나누는 구조다.
- 스타일 치환 단계에서 건드리면 응답 표시 자체가 바뀔 수 있다.

### 9.2 structured renderer

관련 함수:

- `StructuredOutputBlock`
- `WrappedPlainContent`
- `CodeContent`
- `TableContent`
- `highlightCodeText`
- `highlightJsonText`
- `prettyJsonText`
- `parseTableRows`

직접 색상:

- code syntax highlight에 `Color(0x...)` 직접 지정 존재
- table cell padding/width 직접 지정 존재

위험도:

- syntax highlight color: `MEDIUM`
- parser/pretty/parse table: `HIGH`

후속 후보:

- syntax highlight color를 `ConversationCodeColors` 후보로 분리
- table density / horizontal scroll 정책 분리
- parser 변경 없이 renderer token만 치환

## 10. Dialog / Popup 역할

### 10.1 ConversationBlocks.kt dialog

`ConversationBlockActionDialog`는 block long press action menu 역할을 한다.

Action:

- 복사
- 텍스트 선택
- 메시지 편집 또는 추후 지원 표시
- 공유

위험도: `MEDIUM`

주의:

- `Dialog`, `Card`, `TextButton` 직접 사용 존재
- action 자체는 유지해야 한다.

### 10.2 ConversationDialogs.kt

포함 dialog/screen:

- `ConversationSettingsDialog`
- `ConversationGlobalSettingsScreen`
- `ConversationGlobalSettingsCard`
- `ConversationMessageEditDialog`

위험도:

- settings dialog: `MEDIUM`
- global settings screen: `MEDIUM`
- message edit dialog: `HIGH`

이유:

- message edit는 message content 변경 callback과 결합되어 있다.
- dialog surface / scrim / text field style 치환은 가능하지만 onSave/onDismiss 조건 변경은 금지한다.

## 11. parser / helper function 역할

| 함수군 | 역할 | 위험도 | 후속 방침 |
|---|---|---|---|
| structured parser | assistant text를 text/code/json/table segment로 분리 | HIGH | UI 치환 단계에서 변경 금지 |
| JSON/table helper | JSON 유효성, bracket balance, table row parsing | HIGH | renderer token화와 분리 |
| RAG parser | trace text에서 RAG run info 추출 | HIGH | trace marker 변경 금지 |
| Web Search parser | grounding/source info 추출 | HIGH | source UX만 후속 분리 가능 |
| copy text builder | run/RAG/Web Search/source/provider summary 복사용 text 생성 | MEDIUM | copy action 유지 |
| status formatter | run/worker/error label 표시 | MEDIUM | text token화 가능, 의미 변경 금지 |
| syntax highlighter | code/json highlight 색상 지정 | MEDIUM | 색 token화 후보 |
| quick settings summary | input panel summary text 생성 | LOW | token 치환 가능 |

## 12. 직접 Material UI 사용처 표

| 파일 | 함수/블록 | 직접 사용처 | 현재 역할 | 위험도 | 후속 후보 |
|---|---|---|---|---|---|
| ConversationRoomScreen.kt | ConversationRoomScreen | Scaffold, LazyColumn, background, padding | 대화방 전체 layout와 bottomBar 구성 | HIGH | 화면 shell token은 마지막 단계 |
| ConversationRoomScreen.kt | EmptyConversationCard | Card | 빈 대화 안내 card | LOW | ConversationEmptyStateCard |
| ConversationRoomScreen.kt | UserMessageBlock | Card, IconButton | user message card, 복사/편집 action | MEDIUM | ConversationMessageCard, ConversationIconAction |
| ConversationRoomScreen.kt | ConversationRunSummaryPanelReadable | Card, IconButton | 실행정보 전체 card, copy/collapse action | HIGH | ConversationRunSummaryCard |
| ConversationRoomScreen.kt | RunFlatSection | IconButton, background divider | 실행정보 하위 접힘 section | HIGH | RunInfoSection wrapper |
| ConversationRoomScreen.kt | RunCollapsibleFlatTextSection | IconButton, background divider | raw text/source/context 접힘 section | HIGH | RunInfoTextSection wrapper |
| ConversationRoomScreen.kt | StructuredOutputBlock | Card, IconButton | structured/code/json/table block shell | MEDIUM | StructuredBlockCard |
| ConversationRoomScreen.kt | ConversationInputPanel | Card, RadioButton, Switch, BasicTextField, IconButton | 입력창, quick settings, send action | HIGH | ConversationInputPanel component |
| ConversationBlocks.kt | ConversationOutputBlockCard | Card, combinedClickable | OutputBlock card / preview expand | HIGH | ConversationBlockCard |
| ConversationBlocks.kt | StructuredBlockHeader | IconButton | block copy action | MEDIUM | BlockHeaderAction |
| ConversationBlocks.kt | ConversationBlockActionDialog | Dialog, Card, TextButton | block action menu | MEDIUM | ConversationActionDialog |
| ConversationRunPanel.kt | ConversationRunSummaryPanel | Card, IconButton | 별도 run summary panel | MEDIUM | RunPanel component 후보 |
| ConversationDialogs.kt | ConversationSettingsDialog | Dialog, Card, SettingsSwitch | quick settings dialog | MEDIUM | ConversationSettingsDialog wrapper |
| ConversationDialogs.kt | ConversationGlobalSettingsScreen | background, Card, SettingsTextButton | global settings screen | MEDIUM | ConversationSettingsScreen component |
| ConversationDialogs.kt | ConversationMessageEditDialog | Dialog, Card, BasicTextField | message edit dialog | HIGH | MessageEditDialog component |
| ConversationWebSearchQuickSetting.kt | ConversationWebSearchQuickSetting | SettingsSwitch | Web Search quick setting row | MEDIUM | QuickSettingToggleRow |

## 13. 직접 Color / background / border / shape 사용처 표

| 파일 | 함수/블록 | 직접 사용처 | 현재 역할 | 위험도 | 후속 후보 |
|---|---|---|---|---|---|
| ConversationRoomScreen.kt | ConversationRoomScreen | `MaterialTheme.colorScheme.background` | page background | LOW | Conversation surface token |
| ConversationRoomScreen.kt | EmptyConversationCard / UserMessageBlock / Run panel / InputPanel | `conversationUnifiedCardColor()` | unified conversation card color | MEDIUM | ConversationStyleTokens.cardBackground |
| ConversationRoomScreen.kt | RunFlatSection / RunCollapsibleFlatTextSection | `.background(MaterialTheme.colorScheme.background)`, divider alpha | flat section background/divider | HIGH | RunInfoSection surface/divider token |
| ConversationRoomScreen.kt | StructuredOutputBlock | `MaterialTheme.colorScheme.background` inside content | block inner content background | MEDIUM | StructuredBlock inner surface token |
| ConversationRoomScreen.kt | highlightCodeText | `Color(0xFFFFD166)` 등 syntax colors | code syntax highlight | MEDIUM | ConversationCodeColors |
| ConversationRoomScreen.kt | highlightJsonText | `Color(0xFFA5D6A7)` 등 syntax colors | JSON syntax highlight | MEDIUM | ConversationJsonColors |
| ConversationBlocks.kt | conversationUnifiedCardColor | `MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)` | shared block card background | HIGH | ConversationStyleTokens shared color |
| ConversationBlocks.kt | conversationUnifiedCardShape | `RoundedCornerShape(28.dp)` | shared block radius | MEDIUM | ConversationStyleTokens.messageRadius |
| ConversationBlocks.kt | ConversationBlockContent | gradient background alpha | long message preview fade | MEDIUM | ConversationPreviewGradient token |
| ConversationRunPanel.kt | worker detail card | `RoundedCornerShape(22.dp)`, primary alpha | worker detail subcard | MEDIUM | RunWorkerDetailCard token |
| ConversationDialogs.kt | ConversationMessageEditDialog | `RoundedCornerShape(20.dp)`, background alpha | edit text field surface | HIGH | ConversationEditField token |
| ConversationWebSearchQuickSetting.kt | ConversationWebSearchQuickSetting | `Color(0xFFD0D3DA)`, `Color(0xFFB8BCC6)` | title/body text colors | LOW | ConversationQuickSettingText token |

## 14. 위험도 분류

### LOW

- `EmptyConversationCard`
- `AssistantPlainTextBlock`
- `buildQuickSettingsSummary`
- static label / title color token화
- `ConversationWebSearchQuickSetting` text color token화

### MEDIUM

- `UserMessageBlock`
- `StructuredOutputBlock` shell
- `ConversationBlockActionDialog`
- `ConversationRunPanel.kt` worker detail card
- syntax highlight colors
- message preview gradient
- dialog surface / button style
- copy action icon style

### HIGH

- `ConversationInputPanel`
- `ConversationRunSummaryPanelReadable`
- `RunFlatSection`
- `RunCollapsibleFlatTextSection`
- RAG parser / display sections
- Web Search grounding/source parser / display sections
- structured parser / JSON/table parser
- `ConversationMessageEditDialog`
- `ConversationOutputBlockCard` preview/click/long-click behavior

### DO_NOT_TOUCH

현재 단계 또는 1차 스타일 치환 단계에서 직접 수정 금지:

- `ConversationEngine`
- `ConversationViewModel` 전송 흐름
- Provider Adapter request/response
- RAG 실행 로직
- Web Search 실행 로직
- Tool execution
- Worker / Scenario actual execution
- 저장 schema
- trace marker string 변경
- RAG/Web Search parser marker 변경
- structured parser 의미 변경
- messages.jsonl/session 저장 정합성 관련 로직

## 15. ConversationStyleTokens 후보

후보 파일:

- `ConversationStyleTokens.kt`

후보 token:

### Message

- `messageUserBackground`
- `messageAssistantBackground`
- `messageSystemBackground`
- `messageBorderColor`
- `messageRadius`
- `messagePadding`
- `messageSpacing`
- `messageMaxWidthPhone`
- `messageMaxWidthTablet`
- `messagePreviewGradientStart`
- `messagePreviewGradientEnd`

### Input

- `inputPanelBackground`
- `inputPanelBorder`
- `inputFieldBackground`
- `inputFieldBorder`
- `inputFieldRadius`
- `inputPanelPadding`
- `sendButtonRadius`
- `sendButtonTextColor`
- `quickSettingToggleColor`

### Structured / Code

- `structuredBlockBackground`
- `structuredBlockBorder`
- `structuredBlockRadius`
- `codeBlockBackground`
- `codeBlockBorder`
- `codeBlockFontScale`
- `codeKeywordColor`
- `codeStringColor`
- `codeNumberColor`
- `jsonStringColor`
- `jsonNumberColor`
- `tableBlockBorder`

### Citation / RAG / Web Search

- `citationChipBackground`
- `citationChipBorder`
- `citationTextColor`
- `ragContextBackground`
- `ragContextBorder`
- `webSearchCitationBorder`
- `sourceCardBackground`
- `sourceCardBorder`

### Run Summary

- `runSummaryBackground`
- `runSummaryBorder`
- `runSummaryRadius`
- `runSummaryDividerColor`
- `runSummaryCompactHeight`
- `traceBlockBackground`
- `traceBlockBorder`

### Error / Warning

- `errorNoticeBorder`
- `warningNoticeBorder`
- `infoNoticeBorder`
- `noticeBackground`
- `errorTextColor`
- `warningTextColor`

## 16. ConversationUiComponents 후보

후보 파일:

- `ConversationUiComponents.kt`

후보 component:

| 후보 component | 역할 | 1차 적용 후보 |
|---|---|---|
| `ConversationMessageCard` | user/assistant/system message shell | 메시지 카드 |
| `ConversationMessageText` | markdown/plain text body style | plain text |
| `ConversationIconActionButton` | copy/edit/collapse icon action | IconButton 대체 |
| `ConversationInputSurface` | bottom input panel shell | ConversationInputPanel |
| `ConversationTextInputBox` | BasicTextField wrapper | input field |
| `ConversationToggleRow` | RAG/Web Search toggle row | quick settings |
| `ConversationRunInfoCard` | run summary shell | 실행정보 card |
| `ConversationRunInfoSection` | run info 하위 접힘 section | RunFlatSection |
| `ConversationStructuredBlockCard` | code/json/table shell | StructuredOutputBlock |
| `ConversationCodeBlock` | code block style | CodeContent |
| `ConversationTableBlock` | table style | TableContent |
| `ConversationCitationChip` | citation/source compact 표시 | Web Search source |
| `ConversationRagContextPanel` | RAG context 표시 | RunRagSection |
| `ConversationDialogSurface` | 대화방 dialog dark surface | Dialogs |

## 17. 즉시 수정 금지 영역

후속 UI 치환 전까지 아래는 즉시 수정하지 않는다.

- trace parser / marker string
- RAG parser / fallbackReason parsing
- Web Search source parser / grounding fields
- structured parser / JSON/table segment splitter
- send button enable 조건
- `onSend`, `onToggleSearch`, `onToggleWebSearch`, `onModeChange` callback 흐름
- message edit save / dismiss callback
- block long-click action semantics
- `ConversationFileStore` 저장 구조
- `ConversationChunkIndexer` indexing 기준

## 18. 후속 치환 순서

권장 순서:

1. 정적 스캔 결과 검토 및 역할 목록 확정
2. `ConversationStyleTokens.kt` 설계 문서 작성
3. `ConversationUiComponents.kt` skeleton 설계 문서 작성
4. `ConversationBlocks.kt`의 block card / dialog action부터 작은 범위로 치환
5. `ConversationRoomScreen.kt`의 `EmptyConversationCard` / `UserMessageBlock` 등 LOW~MEDIUM 영역 치환
6. `ConversationInputPanel`은 별도 단계로 분리
7. `ConversationRunSummaryPanelReadable` / RAG / Web Search 표시 영역은 parser 고정 후 별도 단계로 분리
8. structured/code/json/table renderer는 parser 변경 없이 visual token만 분리
9. phone / tablet / landscape 검증
10. Graphics 설정과 동적 연결 검토

금지:

- `ConversationRoomScreen.kt` 대형 파일을 한 번에 전면 치환하지 않는다.
- input panel과 run info panel을 같은 턴에 대규모 변경하지 않는다.
- parser/helper 변경과 visual 변경을 같은 턴에 섞지 않는다.

## 19. 검증 체크리스트

문서 작성 검증:

- [ ] 대화방 주요 UI 영역 역할이 분리되어 있는가
- [ ] 직접 Material UI 사용처 표가 있는가
- [ ] 직접 Color/background/border/shape 사용처 표가 있는가
- [ ] 위험도 분류가 있는가
- [ ] ConversationStyleTokens 후보가 있는가
- [ ] ConversationUiComponents 후보가 있는가
- [ ] 후속 치환 순서가 작은 단계로 분리되어 있는가
- [ ] 즉시 수정 금지 영역이 명확한가

코드 변경 검증:

- [ ] Kotlin 파일 변경 없음
- [ ] `ConversationRoomScreen.kt` 변경 없음
- [ ] `ConversationBlocks.kt` 변경 없음
- [ ] `ConversationRunPanel.kt` 변경 없음
- [ ] `ConversationDialogs.kt` 변경 없음
- [ ] `ConversationWebSearchQuickSetting.kt` 변경 없음
- [ ] `ConversationEngine` 변경 없음
- [ ] `ConversationViewModel` 변경 없음
- [ ] Provider Adapter 변경 없음
- [ ] RAG / Web Search 실행 파일 변경 없음
- [ ] 저장 schema 변경 없음
- [ ] 작업모드 변경 없음

보안 / 민감정보 검증:

- [ ] API Key / token / secret / signing 정보가 포함되지 않았는가
- [ ] Provider 호출 URL 원문이나 key query parameter가 포함되지 않았는가
- [ ] preview / diagnostic을 actual execution처럼 표현하지 않았는가

