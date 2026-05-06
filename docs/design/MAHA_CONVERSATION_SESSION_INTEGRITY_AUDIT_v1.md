# MAHA_CONVERSATION_SESSION_INTEGRITY_AUDIT_v1

## 1. 목적

이 문서는 MAHA Android 대화세션 관련 Kotlin 파일의 현재 역할과 연결 흐름을 재확인하고, parser / renderer / action / state / storage / RAG / Web Search 표시 경계를 분리해 기록한다.

최근 `ConversationStyleTokens.kt`, `ConversationUiComponents.kt` skeleton 생성과 `ConversationBlocks.kt` visual shell 1차 적용, TABLE_BLOCK 표시 보정이 진행되었다. 이 과정에서 UI 표시 계층과 parser / 저장 / 실행 계층의 경계가 섞일 위험이 커졌으므로, 다음 보정 전 전체 대화세션 무결성을 진단한다.

이번 문서는 진단 문서다. Kotlin 코드, 실행 로직, 저장 schema, Provider 호출, RAG/Web Search 실행, 작업모드는 변경하지 않는다.

## 2. 검증 대상 파일

| 파일 | 분류 | 이번 문서 기준 역할 |
|---|---|---|
| `ui/conversation/ConversationRoomScreen.kt` | UI / state bridge / parser 일부 | 대화방 화면, 입력창, 메시지 리스트, 실행정보 표시, RAG/Web Search trace parser, structured parser가 집중된 고위험 파일 |
| `ui/conversation/ConversationBlocks.kt` | renderer / action dialog | ConversationOutputBlock visual shell, block header, block copy, long-click action dialog, TABLE_BLOCK 표시 보정 위치 |
| `ui/conversation/ConversationRunPanel.kt` | renderer | 간단 실행정보 panel 표시 계층 |
| `ui/conversation/ConversationDialogs.kt` | dialog / settings UI | Conversation settings dialog, global settings screen, message edit dialog |
| `ui/conversation/ConversationSessionListScreen.kt` | session list UI | 세션 목록, 검색, 즐겨찾기, rename/delete dialog |
| `ConversationWebSearchQuickSetting.kt` | quick setting UI | Web Search quick setting row / switch 표시 |
| `ConversationStorageSettingsDialog.kt` | dialog / storage option | Conversation storage 설정 dialog |
| `ConversationViewModel.kt` | state / orchestration | session 선택/생성/삭제, input state, sendMessage, file store append, engine 호출 |
| `ConversationRequestResponse.kt` | DTO | ViewModel ↔ Engine 요청/응답 DTO |
| `conversation/ConversationModels.kt` | schema | session/message/block/run/worker result schema |
| `ConversationFileStore.kt` | storage | session.json / messages.jsonl 저장·로드·삭제·백업·복원 |
| `ConversationPromptBuilder.kt` | prompt | SYSTEM / RECENT_MESSAGES / RAG_CONTEXT / USER_INPUT prompt 조립 |
| `ConversationEngine.kt` | execution / provider route | Provider routing, Google native grounding, fallback, response/runInfo/trace 생성 |

## 3. 대화세션 전체 흐름 요약

현재 대화모드는 세션형 대화 + Provider/Profile + RAG + Web Search 기반 구조다.

```text
ConversationRoomScreen
→ ConversationViewModel.sendMessage()
→ ConversationPromptBuilder.build()
→ ConversationRequest
→ ConversationEngine.execute()
→ Provider Adapter / Gemini Native Grounding
→ ConversationResponse
→ ConversationViewModel assistant message 생성
→ ConversationFileStore.appendMessage()
→ ConversationRoomScreen / ConversationBlocks / ConversationRunPanel 표시
```

주요 분리 원칙:

- 전송 상태와 세션 상태는 `ConversationViewModel`이 가진다.
- 실행 판단과 Provider 호출은 `ConversationEngine`이 담당한다.
- 저장은 `ConversationFileStore`가 담당한다.
- schema는 `ConversationModels.kt`와 `ConversationRequestResponse.kt`가 담당한다.
- UI 표시는 `ConversationRoomScreen.kt`, `ConversationBlocks.kt`, `ConversationRunPanel.kt`, `ConversationDialogs.kt`가 담당한다.
- RAG/Web Search 실행 자체는 UI 파일에서 수행하지 않는다.
- RAG/Web Search 결과 표시는 runInfo / trace 문자열 parser에 강하게 의존한다.

## 4. 파일별 현재 역할

### 4.1 ConversationRoomScreen.kt

역할:

- 대화방 최상위 Compose 화면.
- 상단 navigation, 메시지 리스트, 입력 panel, quick settings, assistant block rendering, 실행정보 readable panel을 포함한다.
- `expandAssistantStructuredBlocks`, `parseStructuredAnswerSegments`, `splitMarkdownTableSegments`, `splitJsonSegments` 등 structured parsing helper를 포함한다.
- `parseRagRunInfo`, `parseWebSearchGroundingInfo`, `parseWebSearchSourcesInfo` 등 trace parser를 포함한다.
- `ConversationInputPanel`에서 mode radio, RAG switch, Web Search switch, fallback switch, BasicTextField, send button을 직접 렌더링한다.

진단:

- UI renderer와 parser/helper가 한 파일에 집중되어 있다.
- structured parser, RAG/Web Search parser, run info 표시가 모두 섞여 있어 후속 UI 치환 시 HIGH 위험도다.
- 입력창은 send flow / toggle callback과 직접 연결되어 있으므로 visual wrapper 적용도 별도 단계가 필요하다.

### 4.2 ConversationBlocks.kt

역할:

- `ConversationOutputBlockCard` 중심의 OutputBlock visual shell.
- `StructuredBlockHeader`, `ConversationBlockContent`, `ConversationBlockActionDialog`를 포함한다.
- block copy action은 `LocalClipboardManager`로 원본 `block.content`를 복사한다.
- long-click action dialog에서 copy / text select / edit / share 항목을 제공한다.
- 최근 visual wrapper 1차 적용으로 `ConversationMessageCard`, `ConversationIconActionButton`, `ConversationDialogSurface`, `ConversationTextActionButton`을 사용하기 시작했다.
- 최근 TABLE_BLOCK 표시 보정으로 `parseConversationTableRows`, `ConversationTableBlock`, `ConversationTableRowView`가 추가되었다.

진단:

- renderer 파일이지만 TABLE_BLOCK 표시용 parser가 일부 들어와 있다.
- 표시용 정리(`normalizeConversationDisplayText`)와 원문 보존(copy 원본 유지)이 공존한다.
- TABLE_BLOCK 표시 보정은 UI 계층 안에서만 수행해야 하며, block content 자체를 변경하면 안 된다.
- code/json block 표시 의미는 여기서 건드리면 안 된다.

### 4.3 ConversationRunPanel.kt

역할:

- 단순 실행정보 panel 표시.
- `ConversationRunSummaryPanel`에서 status, latency, worker count, provider/model 정보를 간단히 보여준다.

진단:

- LOW~MEDIUM visual wrapper 후보지만, run 상태 의미와 연결되어 있으므로 시각 치환만 허용한다.
- 복잡한 RAG/Web Search readable panel은 주로 `ConversationRoomScreen.kt` 내부에 있다.

### 4.4 ConversationDialogs.kt

역할:

- `ConversationSettingsDialog`.
- `ConversationGlobalSettingsScreen` / `ConversationGlobalSettingsCard`.
- `ConversationMessageEditDialog`.

진단:

- Dialog surface / scrim / button 스타일 보정 대상.
- message edit dialog는 user message edit callback과 연결되므로 MEDIUM~HIGH 위험도다.
- edit text 저장 의미, dismiss 의미, enabled 조건은 수정 금지다.

### 4.5 ConversationSessionListScreen.kt

역할:

- 대화세션 목록 표시.
- 검색 input, favorite, rename, delete dialog.
- 세션 선택 / 새 대화 / Drawer navigation과 연결.

진단:

- 세션 삭제/rename action이 storage와 연결되므로 UI만 봐도 MEDIUM 위험도다.
- 직접 `AlertDialog`, `Button`, `TextButton`, `OutlinedTextField`, `Card` 사용처가 남아 있어 후속 다크테마 보정 후보지만, 현재 TABLE_BLOCK 흐름과는 별도다.

### 4.6 ConversationWebSearchQuickSetting.kt

역할:

- Web Search quick setting row.
- `SettingsSwitch`를 통해 Web Search 상태를 표시/변경한다.

진단:

- UI는 작지만 toggle 의미가 `ConversationViewModel.webSearchEnabled`와 연결되므로 onCheckedChange 의미 변경 금지.

### 4.7 ConversationStorageSettingsDialog.kt

역할:

- Conversation storage 관련 설정 dialog.
- app-specific / SAF storage 상태, fallback storage, migration/backup 관련 action 표시.

진단:

- Dialog surface와 Switch/setting row는 visual 후보.
- storage mode / SAF permission / migration callback 의미는 수정 금지.

### 4.8 ConversationViewModel.kt

역할:

- 세션 state, selected session, input state, quick setting state, sendMessage 실행 흐름을 관리한다.
- `sendMessage()`에서 user message를 append하고, `ConversationEngine.execute()` 결과를 assistant message로 append한다.
- Provider/Model 기본값, API key, RAG/Web Search toggle state를 request에 반영한다.
- message edit는 user message update 중심으로 처리한다.

진단:

- DO_NOT_TOUCH 영역.
- UI 보정 중 callback 의미가 이 파일의 state update 의미를 바꾸면 안 된다.

### 4.9 ConversationRequestResponse.kt

역할:

- `ConversationRequest`, `ConversationResponse` DTO.
- userInput, selectedMode, searchEnabled, ragContext, recentMessages, selectedProvider, selectedModel, webSearchEnabled, webSearchFallbackEnabled, response blocks, runInfo 등을 전달한다.

진단:

- schema에 가까운 DTO이므로 DO_NOT_TOUCH.
- graphics / UI 보정과 무관하다.

### 4.10 ConversationModels.kt

역할:

- `ConversationRole`, `ConversationOutputBlockType`, `ConversationRunStatus` enum.
- `ConversationSession`, `ConversationMessage`, `ConversationOutputBlock`, `ConversationRun`, `ConversationWorkerResult` schema.

진단:

- block type mapping과 저장 schema에 영향을 주는 핵심 파일이다.
- TABLE_BLOCK 문제 해결을 위해 type을 바꾸거나 field를 추가하는 것은 금지한다.

### 4.11 ConversationFileStore.kt

역할:

- app-specific / SAF 세션 저장·로드·삭제·백업·복원.
- `session.json`, `messages.jsonl` 처리.
- `appendMessage`, `loadSessions`, `backupSessionToSaf`, `restoreSafBackupSession` 등 저장 핵심.

진단:

- DO_NOT_TOUCH.
- message edit와 append history 정합성은 후속 검토 후보지만 이번 보정 범위가 아니다.

### 4.12 ConversationPromptBuilder.kt

역할:

- prompt section 조립.
- 순서: `[SYSTEM]` → `[RECENT_MESSAGES]` → `[RAG_CONTEXT]` → `[USER_INPUT]`.

진단:

- recent messages에 모든 message block content가 join될 수 있다.
- TRACE/ERROR/provider summary 등이 recent prompt에 섞일 가능성은 후속 품질 개선 후보다.
- UI 보정에서 건드리면 안 된다.

### 4.13 ConversationEngine.kt

역할:

- Provider routing.
- Google / OpenAI / OpenAI-compatible / Local / Custom 계열 실행 분기.
- Gemini native Web Search grounding 실행 및 fallback 처리.
- Provider success/failure response, runInfo, trace 생성.
- RAG trace, Web Search grounding trace/source trace 생성.

진단:

- DO_NOT_TOUCH.
- trace marker는 `ConversationRoomScreen` parser와 강하게 결합되어 있다.
- UI 보정에서 trace key / marker / source block 구조를 바꾸면 안 된다.

## 5. sendMessage 흐름

```text
ConversationInputPanel send button
→ onSend callback
→ ConversationViewModel.sendMessage()
→ inputText blank / isRunning guard
→ session resolve 또는 create
→ user ConversationMessage 생성
→ ConversationFileStore.appendMessage(user)
→ ConversationPromptBuilder.build(request)
→ ConversationEngine.execute(request)
→ ConversationResponse
→ assistant ConversationMessage 생성
→ ConversationFileStore.appendMessage(assistant)
→ UI state 갱신
```

수정 금지:

- send button enable 조건.
- `onSend` 의미.
- isRunning guard.
- request field 의미.
- user/assistant message append 순서.
- error response handling.

## 6. message 저장 흐름

```text
ConversationViewModel
→ ConversationFileStore.appendMessage(sessionId, message)
→ app-specific 또는 SAF 저장 branch
→ messages.jsonl append
→ loadSessions() 시 session.json + messages.jsonl load
```

진단:

- 저장은 append 중심이다.
- UI renderer에서 표시용 text normalization을 하더라도 `block.content` 원문은 copy/storage 기준으로 유지해야 한다.
- TABLE_BLOCK 표시용 정리는 화면 표시에 한정해야 한다.

## 7. prompt build 흐름

```text
ConversationPromptBuilder.build(request)
→ [SYSTEM]
→ [RECENT_MESSAGES]
→ [RAG_CONTEXT]
→ [USER_INPUT]
```

진단:

- prompt build는 UI 보정 대상이 아니다.
- recent messages는 block content를 join하는 구조이므로, block content를 UI 편의를 위해 변경하면 prompt 품질에도 영향을 줄 수 있다.
- TRACE/ERROR/provider summary allowlist는 후속 RAG/prompt 품질 개선 단계로 분리한다.

## 8. assistant response 표시 흐름

```text
ConversationResponse.blocks
→ assistant ConversationMessage.blocks
→ ConversationRoomScreen message list
→ ConversationOutputBlockRenderer 또는 ConversationOutputBlockCard
→ ConversationBlocks.kt visual shell / content renderer
```

경계:

- block type과 content는 response/schema 영역.
- 표시 방식은 renderer 영역.
- copy action은 원본 content 기준 유지.
- preview/expand state는 UI state.

## 9. structured block 표시 흐름

현재 structured block은 두 경로가 공존한다.

1. `ConversationRoomScreen.kt` 내부 structured parser/display 경로.
2. `ConversationBlocks.kt`의 `ConversationOutputBlockCard` / TABLE_BLOCK 표시 보정 경로.

혼재 위험:

- 같은 TABLE/CODE/JSON 성격이 어떤 화면 경로에서 표시되는지 명확히 고정되지 않으면 보정이 중복될 수 있다.
- structured parser와 renderer가 분리되어 있지 않아, 표시 보정 중 content parsing까지 건드릴 위험이 있다.

## 10. TABLE_BLOCK / CODE_BLOCK / JSON_BLOCK 표시 흐름

### 10.1 TABLE_BLOCK

현재 흐름 후보:

```text
Engine / response block 또는 Room structured parser
→ ConversationOutputBlock(type = TABLE_BLOCK, content = raw table text)
→ ConversationBlocks.kt ConversationBlockContent
→ parseConversationTableRows(content)
→ ConversationTableBlock / Row / Cell 표시
```

진단:

- 최근 보정은 `ConversationBlocks.kt` 안에서만 TABLE_BLOCK 표시용 row/cell parser와 renderer를 추가했다.
- `**bold**`, `<br>` 제거는 표시용 `normalizeConversationDisplayText`로 처리한다.
- copy action은 원본 `block.content`를 복사한다.
- 구분열/상세열 정렬 문제는 renderer 책임으로 보는 것이 합리적이지만, content 자체가 markdown/plain text에 가까운 경우 완전 해결은 parser/normalization 정책 문서화 후 진행해야 한다.

즉시 보정 후보:

- TABLE_BLOCK renderer compile hygiene 확인.
- 표시용 table parser와 기존 Room structured parser 중복 책임 정리 문서화.
- phone에서 horizontal scroll 또는 compact row 전환 여부 별도 설계.

수정 금지:

- `ConversationOutputBlockType.TABLE_BLOCK` schema 변경.
- block content 저장값 변경.
- Engine trace / parser marker 변경.

### 10.2 CODE_BLOCK

역할:

- code text 표시.
- syntax highlight 후보는 `ConversationRoomScreen.kt` 내부 helper와 직접 Color 사용처에 있다.

위험:

- code parser / syntax highlight는 MEDIUM.
- code content 자체 변경 금지.

### 10.3 JSON_BLOCK

역할:

- JSON text 표시.
- `prettyJsonText`, `highlightJsonText` 등 helper가 `ConversationRoomScreen.kt`에 있다.

위험:

- JSON parsing / formatting은 표시 영역이지만 실패 시 content 의미가 바뀔 수 있으므로 HIGH에 가깝다.
- 후속 치환은 shell부터 진행하고 parser는 유지한다.

## 11. RAG context 흐름

```text
ConversationViewModel.searchEnabled
→ ConversationRequest.searchEnabled
→ RagContextBuilder / RagKeywordSearchEngine result
→ ConversationRequest.ragContext
→ ConversationPromptBuilder [RAG_CONTEXT]
→ ConversationEngine buildRagTraceText
→ ConversationRun.runInfo / trace
→ ConversationRoomScreen parseRagRunInfo
→ RunRagSection / copy text
```

진단:

- RAG 실행은 UI가 아니라 Engine / RAG 계층이다.
- RAG 표시 parser는 `ConversationRoomScreen.kt`에 있으며 trace text marker에 의존한다.
- marker 변경은 금지한다.
- RAG 표시 compact화는 후속 wrapper 작업이지만 parser 고정 후 진행해야 한다.

## 12. Web Search grounding/source 표시 흐름

```text
ConversationViewModel.webSearchEnabled
→ ConversationRequest.webSearchEnabled
→ ConversationEngine
→ WebSearchUsageResolver
→ GeminiNativeProviderAdapter.execute
→ groundingMetadata / citations / searchQueries
→ ConversationEngine trace/source text 생성
→ ConversationRoomScreen parseWebSearchGroundingInfo / parseWebSearchSourcesInfo
→ RunWebSearchGroundingSection / source 표시 / copy text
```

진단:

- Web Search execution은 `ConversationEngine` / provider adapter 영역이다.
- UI는 runInfo trace를 parser로 읽어 표시한다.
- citations/source는 assistant 본문에 직접 삽입하지 않는 정책이다.
- fallback success 시 citation이 없을 수 있다.
- `tool_calls/function_call`은 감지/summary만 있고 실제 tool execution은 없다.

수정 금지:

- trace key.
- marker string.
- fallback policy.
- source/citation schema.

## 13. run info / trace 표시 흐름

```text
ConversationEngine creates ConversationRun
→ runInfo / workerResults / trace block
→ ConversationRoomScreen displayRunForConversation
→ ConversationRunSummaryPanelReadable
→ RunSummarySection / RunTraceSection / RunWorkerResultSection / RunRagSection / RunWebSearchGroundingSection
```

진단:

- run info는 본문과 분리되어야 한다.
- 현재 readable panel과 parser가 `ConversationRoomScreen.kt`에 집중되어 있다.
- 후속 compact화는 HIGH 위험도이며 별도 단계가 필요하다.

## 14. dialog / edit / copy action 흐름

### 14.1 block copy / action dialog

```text
ConversationOutputBlockCard
→ copy icon 또는 long-click
→ ConversationBlockActionDialog
→ copy/text select/edit/share action
```

진단:

- copy는 원본 `block.content` 기준이다.
- 표시용 정리와 copy 원문이 달라도 의도된 정책으로 기록한다.
- edit callback 의미는 변경 금지다.

### 14.2 message edit dialog

```text
ConversationMessageEditDialog
→ edited text input
→ onSave
→ ConversationViewModel.updateUserMessage
→ file store/session state 갱신
```

진단:

- user message edit만 지원하는 방향으로 보인다.
- append history와 edit 정합성은 후속 확인 후보.

### 14.3 session list dialogs

- rename dialog.
- delete dialog.
- restore/delete confirmation 후보.

진단:

- storage action과 연결되므로 visual only 치환도 callback 의미 주의 필요.

## 15. session list / session file 흐름

```text
ConversationFileStore.loadSessions()
→ ConversationViewModel.conversationSessions
→ ConversationSessionListScreen
→ select / rename / favorite / delete action
→ ConversationViewModel
→ ConversationFileStore update/delete
```

Storage/RAG 관리 화면은 별도 UI에서 session.json / messages.jsonl 보기, backup, restore, indexing, deletion을 담당한다.

수정 금지:

- session id.
- directory naming.
- messages.jsonl structure.
- SAF/app-specific selection policy.

## 16. parser / renderer / action / state / storage 분리 상태

| 영역 | 현재 주 담당 | 상태 | 진단 |
|---|---|---|---|
| Send state | ConversationViewModel | 비교적 분리 | UI callback 의미 변경 금지 |
| Provider execution | ConversationEngine / Adapter | 분리 | UI에서 수정 금지 |
| Storage | ConversationFileStore | 분리 | UI에서 schema 변경 금지 |
| Prompt build | ConversationPromptBuilder | 분리 | UI 표시용 content 변경이 prompt에 영향 가능 |
| Structured parser | ConversationRoomScreen | 혼재 | renderer와 분리 필요 |
| RAG parser | ConversationRoomScreen | 혼재 | trace marker 의존. HIGH |
| Web Search parser | ConversationRoomScreen | 혼재 | trace/source marker 의존. HIGH |
| OutputBlock renderer | ConversationBlocks | 부분 분리 | TABLE_BLOCK 표시 parser가 추가되어 책임이 일부 혼재 |
| Action dialog | ConversationBlocks / ConversationDialogs | 부분 분리 | callback 의미 보존 필요 |
| Session list UI | ConversationSessionListScreen | UI/action 혼재 | rename/delete storage action 연결 |

## 17. 중복 또는 혼재된 책임

1. `ConversationRoomScreen.kt`에 화면 조립, structured parser, runInfo parser, RAG/Web Search parser, 입력 panel이 모두 집중되어 있다.
2. `ConversationBlocks.kt`에 renderer와 TABLE_BLOCK 표시용 parser가 함께 있다.
3. TABLE_BLOCK 표시가 `ConversationRoomScreen.kt`의 structured parser와 `ConversationBlocks.kt`의 table renderer에 걸쳐 있다.
4. code/json/table parser와 visual shell 치환이 같은 파일/흐름에 섞일 위험이 있다.
5. Dialog surface 보정과 action callback이 같은 컴포넌트에 섞여 있다.
6. copy 표시용 정리와 원본 copy 정책이 서로 다른 기준을 갖는다.

## 18. 최근 token/component skeleton 적용 상태

적용됨:

- `ConversationStyleTokens.kt` 신규 skeleton.
- `ConversationUiComponents.kt` 신규 skeleton.
- `ConversationBlocks.kt` visual shell 1차 적용.
- `ConversationMessageCard` wrapper 적용.
- `ConversationIconActionButton` wrapper 적용.
- `ConversationDialogSurface` wrapper 적용.
- `ConversationTextActionButton` 추가.
- `conversationUnifiedCardColor()` / `conversationUnifiedCardShape()` helper 복구.
- `ConversationSurfaces.cardBackground` / `ConversationShapes.cardRadius` alias 보정.
- TABLE_BLOCK 표시 UI 보정.

미적용:

- `ConversationRoomScreen.kt`.
- `ConversationInputPanel`.
- `ConversationRunSummaryPanelReadable`.
- RAG / Web Search 표시 panel.
- `ConversationRunPanel.kt`.
- `ConversationDialogs.kt` 일부 Dialog.
- `ConversationSessionListScreen.kt`.

주의:

- visual wrapper 적용은 아직 낮은 위험도 영역 중심이다.
- input/run/RAG/Web Search는 HIGH 영역으로 계속 분리해야 한다.

## 19. 위험 구간

### HIGH

- `ConversationRoomScreen.kt` 전체.
- `ConversationInputPanel`: send button, BasicTextField, RAG/Web Search toggle, mode radio와 state callback 결합.
- `ConversationRunSummaryPanelReadable`: runInfo, worker result, trace, RAG, Web Search 표시 결합.
- `parseRagRunInfo` / `parseWebSearchGroundingInfo` / `parseWebSearchSourcesInfo`.
- `StructuredOutputBlock`, `CodeContent`, `TableContent`, `prettyJsonText`, syntax highlight helper.
- `ConversationMessageEditDialog`: 저장 callback과 연결.
- `ConversationSessionListScreen` rename/delete dialogs.
- `ConversationFileStore`: session 저장/백업/복원.
- `ConversationPromptBuilder`: prompt 품질에 직접 영향.
- `ConversationEngine`: Provider/RAG/Web Search 실행과 trace 생성.

### DO_NOT_TOUCH

- `ConversationEngine.kt`.
- `ConversationViewModel.kt` sendMessage 흐름.
- `ConversationRequestResponse.kt`.
- `conversation/ConversationModels.kt` schema.
- `ConversationFileStore.kt` 저장 schema / SAF / messages.jsonl.
- `ConversationPromptBuilder.kt` prompt section 조립.
- Provider Adapter 계열.
- RAG/Web Search 실행 파일.
- trace marker / parser marker / block type mapping.
- block content 저장값.
- 작업모드.

## 20. 수정 금지 구간

이번 감사 이후에도 다음은 별도 지시 없이는 수정하지 않는다.

- parser marker / trace marker.
- block type enum / mapping.
- `ConversationOutputBlock.content` 저장 의미.
- `ConversationRequest` / `ConversationResponse` field.
- `sendMessage()` 흐름.
- `ConversationFileStore` 저장 구조.
- `ConversationPromptBuilder` recent messages / RAG_CONTEXT 조립.
- Web Search fallback policy.
- RAG search / indexing logic.
- Provider call path.
- Worker/Scenario actual execution.
- 작업모드.

## 21. 즉시 보정 후보

후보는 문서화만 한다. 이번 단계에서는 보정하지 않는다.

1. `ConversationBlocks.kt` TABLE_BLOCK renderer compile/quality hygiene 점검.
   - 중복 named argument, 중복 return 등 단순 위생 문제가 남아 있는지 확인 후보.
   - 단, code/json 표시나 parser marker는 건드리지 않는다.
2. TABLE_BLOCK renderer 책임 경계 문서화.
   - 표시용 `normalizeConversationDisplayText`와 원본 copy 정책을 명확히 유지.
3. `ConversationRoomScreen.kt`의 `TableContent`와 `ConversationBlocks.kt` TABLE_BLOCK renderer 중복 책임 조사.
4. `ConversationSessionListScreen.kt` Dialog 다크테마 잔여 여부 확인.
5. `ConversationRunPanel.kt`과 `ConversationRoomScreen.kt` run panel 중복성 확인.

## 22. 후속 작업 우선순위

1. `ConversationBlocks.kt` 현재 빌드 위생 확인.
2. TABLE_BLOCK renderer 표시 품질을 phone 기준으로 재확인.
3. `ConversationBlocks.kt` visual wrapper 적용 결과 통합 보고.
4. `ConversationSessionListScreen.kt` Dialog/카드 다크테마 보정 여부 검토.
5. `ConversationDialogs.kt` message edit dialog UI 보정 여부 검토.
6. `ConversationRoomScreen.kt` 정적 parser 추출 설계.
7. `ConversationInputPanel` wrapper 설계 후 별도 단계 적용.
8. run info / RAG / Web Search panel wrapper 설계 후 별도 단계 적용.

## 23. 검증 결론

현재 대화세션 기능은 이미 실사용 가능한 상태다.

- 대화 세션 생성 / 선택 / 삭제 / 저장.
- 대화방 진입과 메시지 전송.
- Provider 호출과 assistant 응답 표시.
- OutputBlock rendering.
- 실행정보 표시.
- RAG / Web Search trace 표시.
- storage / SAF / backup 관련 흐름.

최근 작업은 기능 구현이 아니라 대화방 visual shell과 TABLE_BLOCK 표시 품질 보정이다.

무결성 관점에서 핵심 결론은 다음과 같다.

1. `ConversationEngine`, `ConversationViewModel`, `ConversationFileStore`, `ConversationModels`, `ConversationPromptBuilder`는 계속 DO_NOT_TOUCH로 유지한다.
2. `ConversationRoomScreen.kt`는 parser / renderer / input / run info가 집중된 HIGH 위험도 파일이므로 즉시 UI 치환 대상으로 삼지 않는다.
3. `ConversationBlocks.kt`는 현재 visual shell 1차 적용 대상이지만, TABLE_BLOCK 표시용 parser가 추가되었으므로 추가 보정은 단건으로만 진행한다.
4. RAG/Web Search 표시 compact화는 trace marker와 parser 안정성 확인 후 별도 단계로 분리한다.
5. 작업모드와 고급 하네스 실제 실행 연결은 이번 대화방 UI 보정 흐름과 분리한다.
