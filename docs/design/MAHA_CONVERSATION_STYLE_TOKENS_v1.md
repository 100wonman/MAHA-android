# MAHA_CONVERSATION_STYLE_TOKENS_v1

## 1. 목적

이 문서는 MAHA Android 대화모드의 대화방 화면에 적용할 `ConversationStyleTokens` 후보 구조를 정의한다.

기준 문서는 다음이다.

- `MAHA_CONVERSATION_ROOM_STATIC_SCAN_v1.md`
- `MAHA_CONVERSATION_ROOM_UX_UI_REVIEW_v1.md`
- `MAHA_GRAPHICS_SETTINGS_OPTIONS_v1.md`

이번 문서는 구현 지시가 아니라 설계 기준 문서다. Kotlin 코드, `ConversationRoomScreen`, `ConversationBlocks`, `ConversationRunPanel`, `ConversationDialogs`, `ConversationWebSearchQuickSetting`, `ConversationEngine`, `ConversationViewModel`, Provider 호출, RAG/Web Search 실행 로직, 저장 schema, 작업모드 코드는 변경하지 않는다.

목표는 다음과 같다.

- 대화방 전용 visual token 구조를 정리한다.
- `SettingsStyleTokens`와 공유 가능한 값과 분리해야 할 값을 명확히 한다.
- 메시지, 입력창, 실행정보, RAG, Web Search, structured block, code/json/table, Dialog, notice 영역별 token 후보를 고정한다.
- 후속 `ConversationStyleTokens.kt`와 `ConversationUiComponents.kt` 구현 전에 위험도를 분리한다.

## 2. 설계 전제

대화방은 설정 화면보다 사용 시간이 길고, 본문 가독성과 메시지 흐름 유지가 우선이다.

설계 전제는 다음과 같다.

1. 대화방은 설정 화면보다 본문 가독성이 우선이다.
2. `SettingsStyleTokens`를 그대로 복사하지 않는다.
3. 큰 카드 배경을 상태색으로 칠하지 않는다.
4. 상태 표현은 `border`, `text`, `chip`, `label` 중심으로 설계한다.
5. 실행정보, RAG, Web Search, trace는 본문과 분리되는 compact 표시를 전제로 한다.
6. parser, marker, execution flow에 영향을 주는 token은 만들지 않는다.
7. token 설계는 visual layer에 한정한다.
8. 대화방 기능은 이미 동작하는 상태로 전제하며, 이번 문서는 기능 구현 문서가 아니다.

## 3. SettingsStyleTokens와 ConversationStyleTokens 분리 원칙

`SettingsStyleTokens`는 설정 화면의 카드, 버튼, chip, Dialog, Switch, 중첩카드 정책을 안정화한 token이다. 대화방은 다음 이유로 별도 `ConversationStyleTokens`가 필요하다.

- 대화방은 긴 본문 읽기와 입력 경험이 중심이다.
- 설정 화면보다 paragraph spacing, lineHeight, message max width, code block 가독성의 비중이 크다.
- 실행정보, RAG, Web Search source는 설정 card가 아니라 conversation assistant response의 보조 정보다.
- message bubble, input panel, run summary, citation chip은 설정 화면 card/button과 역할이 다르다.

분리 원칙:

| 구분 | SettingsStyleTokens | ConversationStyleTokens |
|---|---|---|
| 주요 목적 | 설정 화면 일관성 | 대화방 가독성 / 메시지 흐름 |
| 큰 surface | 설정 section card | message / input / structured block / run info |
| density | 설정 정보 밀도 | 메시지 간격 / 입력창 높이 / 실행정보 compact 정도 |
| action | 설정 버튼 / navigation | send / copy / edit / expand / citation action |
| warning/error | 설정 notice / danger action | assistant response notice / provider error / trace 요약 |
| 적용 범위 | 대화모드 설정 계열 | ConversationRoom 계열 |

공유 가능한 값은 `GraphicsSettings`나 `GraphicsThemeResolver`를 통해 간접 공유한다. 화면 파일이 직접 `SettingsStyleTokens`와 `ConversationStyleTokens`를 섞어 참조하는 구조는 피한다.

## 4. GraphicsSettings와 공유 가능한 값

후속 `GraphicsSettings`에서 Settings와 Conversation 양쪽이 공유할 수 있는 값은 다음이다.

| 공유 후보 | 설명 | 적용 후보 |
|---|---|---|
| `themeMode` | dark / light / system / high contrast 후보 | 전체 visual resolver |
| `accentPreset` | action text, selected border 계열 | settings action, conversation send/copy/citation |
| `density` | 설정 화면 기본 density | settings 화면 중심 |
| `conversationDensity` | 대화방 전용 density | message spacing, run summary, input panel |
| `fontScale` | 전체 글자 배율 기준 | settings / conversation 공통 기준 |
| `messageFontScale` | 대화 본문 전용 후보 | message body |
| `codeBlockFontScale` | code block 전용 후보 | code/json block |
| `borderIntensity` | border 강도 | cards, message, run info, dialog |
| `surfaceContrast` | surface 대비 | card/message/input/run info background |
| `dialogOpacity` | dialog surface 불투명도 | settings dialog, conversation dialog |
| `scrimIntensity` | modal scrim 강도 | dialog/popup |
| `chipToneIntensity` | chip tone 강도 | settings chip, citation chip, status chip |
| `highContrast` | 고대비 접근성 | text/border/chip 강화 |
| `reduceTransparency` | 투명도 감소 | dialog, panel, chip alpha 강화 |
| `reduceMotion` | 애니메이션 최소화 | expand/collapse, scroll animation 후보 |
| `largeTouchTargets` | 터치 대상 확대 | send/copy/edit/toggle action |

## 5. GraphicsSettings와 분리해야 할 값

다음 값은 대화방 전용 성격이 강하므로 `ConversationStyleTokens` 또는 conversation-specific setting으로 둔다.

| 분리 후보 | 이유 | 위험도 |
|---|---|---|
| `messageMaxWidthPhone` | phone message readability에 직접 영향 | MEDIUM |
| `messageMaxWidthTablet` | tablet layout 전용 | MEDIUM |
| `inputPanelMinHeight` | keyboard와 입력 흐름에 영향 | HIGH |
| `inputPanelMaxHeight` | multiline input / list 가림에 영향 | HIGH |
| `runSummaryDefaultCollapsed` | 실행정보 노출 정책과 결합 | HIGH |
| `ragContextDisplayDensity` | RAG 표시 밀도와 결합 | HIGH |
| `citationDisplayDensity` | Web Search source 표시 밀도와 결합 | HIGH |
| `traceBlockDefaultCollapsed` | trace 노출 정책과 결합 | DO_NOT_TOUCH |
| `parserMarkerPolicy` | visual token 대상이 아님 | DO_NOT_TOUCH |
| `sendFlowState` | visual token 대상이 아님 | DO_NOT_TOUCH |
| `storageSerializationPolicy` | visual token 대상이 아님 | DO_NOT_TOUCH |

## 6. Conversation token 그룹 구조

후속 Kotlin 구조 후보는 다음이다.

```text
ConversationStyleTokens
├─ ConversationColors
├─ ConversationSurfaces
├─ ConversationBorders
├─ ConversationShapes
├─ ConversationSpacing
├─ ConversationTypography
├─ ConversationMessageTokens
├─ ConversationInputTokens
├─ ConversationRunInfoTokens
├─ ConversationRagTokens
├─ ConversationWebSearchTokens
├─ ConversationStructuredBlockTokens
├─ ConversationCodeTokens
├─ ConversationJsonTokens
├─ ConversationTableTokens
├─ ConversationDialogTokens
└─ ConversationNoticeTokens
```

초기 구현에서는 모든 그룹을 실제 Kotlin 파일로 한 번에 만들지 않는다. 먼저 문서 기준으로 그룹과 역할을 고정한 뒤, `ConversationBlocks`, `ConversationInputPanel`, `RunInfoPanel` 순서로 필요한 최소 token만 구현한다.

## 7. ConversationColors

- 역할: 대화방 전체에서 사용하는 semantic color 후보를 정의한다.
- 후보 token:
  - `accentColor`
  - `actionTextColor`
  - `selectedTextColor`
  - `mutedTextColor`
  - `bodyTextColor`
  - `titleTextColor`
  - `successTextColor`
  - `warningTextColor`
  - `dangerTextColor`
  - `infoTextColor`
  - `linkTextColor`
  - `citationTextColor`
- 기본값 방향:
  - 현재 안정화된 다크테마와 같은 계열을 사용하되, 대화 본문은 설정 화면보다 높은 본문 대비를 우선한다.
  - 링크와 citation은 action 색과 유사할 수 있지만 별도 token으로 둔다.
- SettingsStyleTokens와 공유 가능 여부:
  - accent / action / warning / danger base 계열은 공유 가능.
  - message body / citation / link 색은 Conversation 전용 권장.
- GraphicsSettings 연결 후보:
  - `accentPreset`, `fontScale`, `highContrast`, `chipToneIntensity`.
- 후속 구현 위험도: LOW.

## 8. ConversationSurfaces

- 역할: 대화방 주요 표면 배경을 정의한다.
- 후보 token:
  - `screenBackground`
  - `messageUserBackground`
  - `messageAssistantBackground`
  - `messageSystemBackground`
  - `inputPanelBackground`
  - `inputFieldBackground`
  - `runSummaryBackground`
  - `structuredBlockBackground`
  - `codeBlockBackground`
  - `ragContextBackground`
  - `webSearchSourceBackground`
  - `noticeBackground`
  - `dialogBackground`
  - `popupBackground`
- 기본값 방향:
  - 큰 면 배경은 단일 dark surface 계열에서 작은 차이만 둔다.
  - 사용자 메시지와 assistant 메시지는 지나치게 다른 색 면을 쓰지 않고, alignment / border / radius / spacing으로 구분한다.
  - error/warning/success/info 상태가 큰 surface background를 직접 바꾸지 않는다.
- SettingsStyleTokens와 공유 가능 여부:
  - screen/card/dialog surface의 기본 계열은 공유 가능.
  - message/input/run/structured surface는 Conversation 전용 권장.
- GraphicsSettings 연결 후보:
  - `surfaceContrast`, `conversationDensity`, `highContrast`, `reduceTransparency`.
- 후속 구현 위험도: MEDIUM.

## 9. ConversationBorders

- 역할: 대화방 surface 간 경계와 상태 표시 border를 정의한다.
- 후보 token:
  - `messageBorderColor`
  - `messageUserBorderColor`
  - `messageAssistantBorderColor`
  - `inputPanelBorderColor`
  - `inputFieldBorderColor`
  - `runSummaryBorderColor`
  - `structuredBlockBorderColor`
  - `codeBlockBorderColor`
  - `ragContextBorderColor`
  - `webSearchCitationBorderColor`
  - `sourceCardBorderColor`
  - `errorNoticeBorderColor`
  - `warningNoticeBorderColor`
  - `infoNoticeBorderColor`
  - `selectedBorderColor`
  - `focusedBorderColor`
- 기본값 방향:
  - 일반 border는 낮은 대비.
  - focused input / selected / action border는 약간 강화.
  - error/warning은 border와 label 중심.
- SettingsStyleTokens와 공유 가능 여부:
  - default border / selected border / danger-warning border 강도는 공유 가능.
  - message / code / RAG / source border는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `borderIntensity`, `highContrast`, `reducedTransparency`.
- 후속 구현 위험도: LOW ~ MEDIUM.

## 10. ConversationShapes

- 역할: 대화방 message, input, block, dialog의 radius 정책을 정의한다.
- 후보 token:
  - `messageRadius`
  - `messageUserRadius`
  - `messageAssistantRadius`
  - `inputPanelRadius`
  - `inputFieldRadius`
  - `sendButtonRadius`
  - `runSummaryRadius`
  - `structuredBlockRadius`
  - `codeBlockRadius`
  - `citationChipRadius`
  - `sourceCardRadius`
  - `dialogRadius`
  - `noticeRadius`
- 기본값 방향:
  - message는 설정 card보다 약간 더 말풍선형 가능.
  - code/structured block은 읽기 편한 rectangular card 후보.
  - input field는 터치 안정성을 위해 충분한 radius 유지.
- SettingsStyleTokens와 공유 가능 여부:
  - Dialog / chip / general radius level은 공유 가능.
  - message bubble / input field radius는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `messageBubbleRadius`, `cardRadiusLevel`, `buttonRadiusLevel`.
- 후속 구현 위험도: LOW.

## 11. ConversationSpacing

- 역할: 대화방의 vertical rhythm, message gap, padding을 정의한다.
- 후보 token:
  - `screenPaddingPhone`
  - `screenPaddingTablet`
  - `messagePadding`
  - `messageSpacing`
  - `messageGroupSpacing`
  - `assistantBlockSpacing`
  - `structuredBlockPadding`
  - `codeBlockPadding`
  - `inputPanelPadding`
  - `inputFieldHorizontalPadding`
  - `runSummaryPadding`
  - `ragPanelPadding`
  - `sourceCardPadding`
  - `dialogContentPadding`
- 기본값 방향:
  - phone은 compact spacing 우선.
  - tablet은 horizontal padding과 max width를 늘릴 수 있다.
  - 실행정보, RAG, Web Search는 compact 표시를 기본값으로 둔다.
- SettingsStyleTokens와 공유 가능 여부:
  - base spacing scale은 공유 가능.
  - message/run/input-specific spacing은 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `conversationDensity`, `messageSpacing`, `largeTouchTargets`.
- 후속 구현 위험도: MEDIUM.

## 12. ConversationTypography

- 역할: 대화방 본문, code, metadata, notice, input text typography를 정의한다.
- 후보 token:
  - `messageBodyTextStyle`
  - `messageBodyLineHeight`
  - `messageMetaTextStyle`
  - `messageTimestampTextStyle`
  - `inputTextStyle`
  - `placeholderTextStyle`
  - `runSummaryTextStyle`
  - `runMetaTextStyle`
  - `sourceTitleTextStyle`
  - `sourceUrlTextStyle`
  - `codeTextStyle`
  - `jsonTextStyle`
  - `tableTextStyle`
  - `noticeTextStyle`
- 기본값 방향:
  - assistant body는 설정 text보다 읽기 중심 lineHeight를 확보한다.
  - code/json은 monospace 유지 후보.
  - metadata는 muted text로 작게 유지.
- SettingsStyleTokens와 공유 가능 여부:
  - global font scale / title/body/muted role 개념은 공유 가능.
  - message/code/source/run typography는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `fontScale`, `messageFontScale`, `codeBlockFontScale`, `highContrast`.
- 후속 구현 위험도: MEDIUM.

## 13. ConversationMessageTokens

- 역할: user / assistant / system message shell과 action 영역을 정의한다.
- 후보 token:
  - `userMessageBackground`
  - `assistantMessageBackground`
  - `systemMessageBackground`
  - `userMessageBorderColor`
  - `assistantMessageBorderColor`
  - `messageRadius`
  - `messagePadding`
  - `messageSpacing`
  - `messageMaxWidthPhone`
  - `messageMaxWidthTablet`
  - `messageActionIconColor`
  - `messageActionTouchSize`
  - `messageRoleLabelColor`
  - `messageTimestampColor`
- 기본값 방향:
  - user와 assistant는 alignment, border, radius, meta label로 구분한다.
  - 본문 큰 면을 강한 accent 색으로 칠하지 않는다.
  - action icon은 기본적으로 낮은 대비, hover/press 후보는 후속.
- SettingsStyleTokens와 공유 가능 여부:
  - action color / muted text / border base는 공유 가능.
  - message-specific shell은 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `messageSpacing`, `messageFontScale`, `messageBubbleRadius`, `timestampVisibility`, `roleLabelVisibility`, `largeTouchTargets`.
- 후속 구현 위험도: MEDIUM.

## 14. ConversationInputTokens

- 역할: input panel, quick setting, send action, toggle/radio 영역을 정의한다.
- 후보 token:
  - `inputPanelBackground`
  - `inputPanelBorderColor`
  - `inputPanelPadding`
  - `inputPanelMinHeight`
  - `inputPanelMaxHeightPhone`
  - `inputPanelMaxHeightTablet`
  - `inputFieldBackground`
  - `inputFieldBorderColor`
  - `inputFieldFocusedBorderColor`
  - `inputFieldRadius`
  - `inputPlaceholderColor`
  - `sendButtonBackground`
  - `sendButtonBorderColor`
  - `sendButtonIconColor`
  - `sendButtonDisabledColor`
  - `quickSettingRowBackground`
  - `quickSettingBorderColor`
  - `inputToggleCheckedColor`
  - `inputToggleUncheckedColor`
- 기본값 방향:
  - 입력창은 읽기/쓰기 대비를 설정 card보다 명확하게 한다.
  - send button은 action임을 명확히 하되 큰 면 색상 과잉을 피한다.
  - RAG/Web Search quick toggle은 본문 입력보다 덜 튀게 한다.
- SettingsStyleTokens와 공유 가능 여부:
  - Switch/toggle checked/unchecked 정책은 공유 가능.
  - input max height, send action, quick setting density는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `inputPanelHeight`, `conversationDensity`, `largeTouchTargets`, `reducedMotion`.
- 후속 구현 위험도: HIGH.
- 주의:
  - `canSend`, `onSend`, keyboard hide, mode change, RAG/Web Search toggle callback에는 영향 주면 안 된다.

## 15. ConversationRunInfoTokens

- 역할: 실행정보 summary, 접힘 상세, provider/model/RAG/Web Search 상태 표시를 정의한다.
- 후보 token:
  - `runSummaryBackground`
  - `runSummaryBorderColor`
  - `runSummaryRadius`
  - `runSummaryPadding`
  - `runSummaryCompactHeight`
  - `runSummaryHeaderTextColor`
  - `runSummaryMetaTextColor`
  - `runInfoSectionBorderColor`
  - `runInfoSectionDividerColor`
  - `runInfoCopyActionColor`
  - `runInfoCollapsedIconColor`
  - `runInfoExpandedIconColor`
  - `traceBlockBackground`
  - `traceBlockBorderColor`
- 기본값 방향:
  - 기본 compact 표시.
  - 상세는 접힘 영역.
  - trace/raw text는 개발자성 정보로 낮은 prominence.
- SettingsStyleTokens와 공유 가능 여부:
  - panel/card surface, divider, action color는 공유 가능.
  - run summary hierarchy, compact height, trace prominence는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `runSummaryDefaultCollapsed`, `conversationDensity`, `highContrast`, `reducedTransparency`.
- 후속 구현 위험도: HIGH.
- 주의:
  - trace marker, parser, provider summary key는 visual token 대상이 아니다.

## 16. ConversationRagTokens

- 역할: RAG run info, context preview, source/chunk summary 표시를 정의한다.
- 후보 token:
  - `ragContextBackground`
  - `ragContextBorderColor`
  - `ragContextRadius`
  - `ragContextPadding`
  - `ragSummaryChipBackground`
  - `ragSummaryChipBorderColor`
  - `ragSourceTitleColor`
  - `ragSourcePreviewColor`
  - `ragScoreTextColor`
  - `ragFallbackNoticeBorderColor`
  - `ragCopyActionColor`
- 기본값 방향:
  - 본문과 분리.
  - compact summary 우선.
  - 상세 context는 접힘 또는 preview 후보.
  - score/fallbackReason은 meta 정보로 낮은 prominence.
- SettingsStyleTokens와 공유 가능 여부:
  - info/success/warning tone 일부 공유 가능.
  - RAG source hierarchy는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `ragContextDisplayDensity`, `conversationDensity`, `chipToneIntensity`.
- 후속 구현 위험도: HIGH.
- 주의:
  - RAG parser key, context assembly, search result logic은 visual token 대상이 아니다.

## 17. ConversationWebSearchTokens

- 역할: Web Search grounding, source, citation 표시를 정의한다.
- 후보 token:
  - `webSearchSummaryBackground`
  - `webSearchSummaryBorderColor`
  - `citationChipBackground`
  - `citationChipBorderColor`
  - `citationChipTextColor`
  - `sourceCardBackground`
  - `sourceCardBorderColor`
  - `sourceTitleColor`
  - `sourceUrlColor`
  - `sourceSnippetColor`
  - `groundingSuccessBorderColor`
  - `groundingFallbackBorderColor`
  - `groundingErrorBorderColor`
  - `webSearchCopyActionColor`
- 기본값 방향:
  - citations는 본문 직접 삽입보다 실행정보 / source panel 표시를 전제로 한다.
  - source는 compact card 또는 chip 후보.
  - title/url/snippet hierarchy를 분리한다.
- SettingsStyleTokens와 공유 가능 여부:
  - info/success/warning/danger tone 일부 공유 가능.
  - citation/source hierarchy는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `citationDisplayDensity`, `chipToneIntensity`, `highContrast`, `reducedTransparency`.
- 후속 구현 위험도: HIGH.
- 주의:
  - grounding marker, sources parser, fallback marker, URL normalization은 token 대상이 아니다.

## 18. ConversationStructuredBlockTokens

- 역할: markdown-like structured output block의 shell과 spacing을 정의한다.
- 후보 token:
  - `structuredBlockBackground`
  - `structuredBlockBorderColor`
  - `structuredBlockRadius`
  - `structuredBlockPadding`
  - `structuredBlockSpacing`
  - `structuredBlockTitleColor`
  - `structuredBlockMetaColor`
  - `structuredBlockActionColor`
  - `structuredBlockDividerColor`
- 기본값 방향:
  - assistant 본문과 구분되지만 과한 카드 중첩은 피한다.
  - copy/preview action은 낮은 prominence.
- SettingsStyleTokens와 공유 가능 여부:
  - surface/border/action base 공유 가능.
  - structured body spacing은 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `structuredBlockDensity`, `conversationDensity`, `borderIntensity`.
- 후속 구현 위험도: MEDIUM.

## 19. ConversationCodeTokens

- 역할: code block shell, syntax highlight, copy action, font scale 후보를 정의한다.
- 후보 token:
  - `codeBlockBackground`
  - `codeBlockBorderColor`
  - `codeBlockRadius`
  - `codeBlockPadding`
  - `codeBlockTextColor`
  - `codeBlockFontScale`
  - `codeBlockLineHeight`
  - `codeLanguageLabelColor`
  - `codeCopyActionColor`
  - `syntaxKeywordColor`
  - `syntaxStringColor`
  - `syntaxNumberColor`
  - `syntaxCommentColor`
  - `syntaxDefaultColor`
- 기본값 방향:
  - code block은 본문보다 약간 더 명확한 dark surface.
  - syntax highlight는 가독성 우선, 과한 원색 피함.
  - monospace 유지 후보.
- SettingsStyleTokens와 공유 가능 여부:
  - border/action base 일부 공유 가능.
  - syntax color와 code font는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `codeBlockFontScale`, `highContrast`, `reducedTransparency`.
- 후속 구현 위험도: MEDIUM.
- 주의:
  - syntax parser는 visual token과 분리한다.

## 20. ConversationJsonTokens

- 역할: JSON block shell, validation/error 표시, raw/pretty 후보를 정의한다.
- 후보 token:
  - `jsonBlockBackground`
  - `jsonBlockBorderColor`
  - `jsonBlockRadius`
  - `jsonBlockPadding`
  - `jsonKeyColor`
  - `jsonStringColor`
  - `jsonNumberColor`
  - `jsonBooleanColor`
  - `jsonNullColor`
  - `jsonErrorBorderColor`
  - `jsonCopyActionColor`
- 기본값 방향:
  - code block과 유사하되 JSON semantic 색상은 더 절제.
  - invalid JSON 표시가 필요한 경우 danger border/text 중심.
- SettingsStyleTokens와 공유 가능 여부:
  - code/structured surface 일부 공유 가능.
  - JSON semantic color는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `codeBlockFontScale`, `highContrast`, `structuredBlockDensity`.
- 후속 구현 위험도: MEDIUM.
- 주의:
  - JSON validation/splitting parser는 token 대상이 아니다.

## 21. ConversationTableTokens

- 역할: markdown table 표시, row border, horizontal scroll 후보를 정의한다.
- 후보 token:
  - `tableBlockBackground`
  - `tableBlockBorderColor`
  - `tableHeaderBackground`
  - `tableHeaderTextColor`
  - `tableCellTextColor`
  - `tableRowDividerColor`
  - `tableCellPadding`
  - `tableHorizontalScrollIndicatorColor`
  - `tableCopyActionColor`
- 기본값 방향:
  - phone에서는 horizontal scroll 후보 유지.
  - header만 약한 contrast, 전체 table 면 색상 과잉 금지.
- SettingsStyleTokens와 공유 가능 여부:
  - divider/border/action base 공유 가능.
  - table cell spacing과 header treatment는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `structuredBlockDensity`, `conversationDensity`, `fontScale`.
- 후속 구현 위험도: MEDIUM.
- 주의:
  - table segment parser와 markdown split logic은 token 대상이 아니다.

## 22. ConversationDialogTokens

- 역할: message edit, block action, preview dialog 등 대화방 dialog surface를 정의한다.
- 후보 token:
  - `conversationDialogBackground`
  - `conversationDialogInnerBackground`
  - `conversationDialogBorderColor`
  - `conversationDialogScrimColor`
  - `conversationDialogRadius`
  - `conversationDialogContentPadding`
  - `conversationDialogTitleColor`
  - `conversationDialogBodyColor`
  - `conversationDialogPrimaryActionColor`
  - `conversationDialogSecondaryActionColor`
  - `conversationDialogDangerActionColor`
- 기본값 방향:
  - 설정 dialog와 같은 opacity/scrim 안정성 유지.
  - message edit dialog는 긴 텍스트 편집 안정성이 우선.
- SettingsStyleTokens와 공유 가능 여부:
  - dialog surface/scrim/radius/action role은 공유 가능.
  - message edit text area sizing은 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `dialogOpacity`, `scrimIntensity`, `reducedTransparency`, `largeTouchTargets`.
- 후속 구현 위험도: MEDIUM ~ HIGH.
- 주의:
  - dialog show/dismiss 조건과 edit save/delete callback은 token 대상이 아니다.

## 23. ConversationNoticeTokens

- 역할: error, warning, info, trace, fallback, unsupported notice 표시를 정의한다.
- 후보 token:
  - `noticeBackground`
  - `noticeBorderColor`
  - `infoNoticeBorderColor`
  - `warningNoticeBorderColor`
  - `errorNoticeBorderColor`
  - `fallbackNoticeBorderColor`
  - `unsupportedNoticeBorderColor`
  - `noticeTitleColor`
  - `noticeBodyColor`
  - `noticeMetaColor`
  - `traceNoticeBackground`
  - `traceNoticeBorderColor`
  - `noticeActionColor`
- 기본값 방향:
  - notice는 본문보다 분리하되 큰 danger/warning 면을 쓰지 않는다.
  - trace는 기본 compact / low prominence.
  - provider raw error는 접힘 후보.
- SettingsStyleTokens와 공유 가능 여부:
  - info/warning/danger tone 일부 공유 가능.
  - trace/fallback/unsupported hierarchy는 Conversation 전용.
- GraphicsSettings 연결 후보:
  - `highContrast`, `chipToneIntensity`, `reducedTransparency`.
- 후속 구현 위험도: HIGH.
- 주의:
  - errorType, trace marker, fallback condition은 token 대상이 아니다.

## 24. Phone / tablet / landscape 대응 token 후보

### 24.1 Phone

후보:

- `messageMaxWidthPhone`
- `screenPaddingPhone`
- `inputPanelMaxHeightPhone`
- `runSummaryCompactHeightPhone`
- `sourceCardMaxLinesPhone`
- `ragContextPreviewMaxLinesPhone`

기본 방향:

- 세로 공간 절약.
- 실행정보 기본 접힘.
- source/citation compact 표시.

위험도: MEDIUM ~ HIGH.

### 24.2 Tablet

후보:

- `messageMaxWidthTablet`
- `screenPaddingTablet`
- `messageListHorizontalMarginTablet`
- `sidePanelWidthTabletCandidate`
- `sourcePanelPreferredWidthTablet`

기본 방향:

- 좌우 폭 활용.
- message max width 조정.
- source/run info 보조 패널은 후속 후보로만 기록.

위험도: MEDIUM.

### 24.3 Landscape

후보:

- `landscapeInputPanelMaxHeight`
- `landscapeRunInfoPlacementCandidate`
- `landscapeMessageMaxWidth`
- `keyboardAvoidanceBottomPaddingCandidate`

기본 방향:

- keyboard와 input panel 충돌 방지.
- 실행정보 side/bottom compact 후보는 후속 단계.

위험도: HIGH.

## 25. 접근성 / high contrast / reduced transparency 후보

후보:

- `highContrastMessageBorderMultiplier`
- `highContrastTextBoost`
- `largeTouchTargetMinSize`
- `reducedTransparencySurfaceAlpha`
- `reducedTransparencyDialogAlpha`
- `reducedMotionExpandCollapse`
- `colorIndependentStatusLabels`
- `accessibilityFocusBorderColor`

기본 방향:

- 색상에만 의존하지 않는다.
- label, icon, border를 함께 사용한다.
- reduced transparency가 켜지면 dialog, panel, chip alpha를 더 불투명하게 한다.
- large touch targets는 copy/edit/send/expand/collapse에 우선 적용한다.

위험도: MEDIUM.

## 26. 기본값 후보

기본값은 현재 안정화된 다크테마 계열을 기준으로 한다.

| 영역 | 기본값 방향 |
|---|---|
| Theme | dark |
| Conversation density | normal |
| Message font scale | 1.0 |
| Code font scale | 1.0 |
| Message background | dark surface 계열, user/assistant 약한 차이 |
| Message border | 낮은 대비 border |
| Input panel | card보다 약간 명확한 border |
| Send button | action color 중심 |
| Run summary | compact / low prominence |
| RAG context | compact summary / 상세 접힘 후보 |
| Web Search source | compact source card / citation chip 후보 |
| Code block | 약간 더 명확한 dark surface |
| Dialog | 충분히 불투명한 dark surface + scrim |
| Notice | border / label 중심 |

## 27. 후속 Kotlin 구현 시 파일 구조 후보

후속 파일 후보:

```text
app/src/main/java/com/maha/app/ui/conversation/ConversationStyleTokens.kt
app/src/main/java/com/maha/app/ui/conversation/ConversationUiComponents.kt
```

후속 구현 순서 후보:

1. `ConversationStyleTokens.kt` skeleton 작성
2. `ConversationUiComponents.kt` skeleton 작성
3. `ConversationBlocks.kt`의 단순 block shell부터 token 적용
4. `ConversationDialogs.kt`의 dialog surface/button wrapper 적용
5. `ConversationRoomScreen.kt`의 user/assistant message shell 일부 적용
6. `ConversationInputPanel`은 별도 지시문으로 분리
7. `ConversationRunSummaryPanelReadable`은 parser marker 고정 후 별도 지시문으로 분리
8. RAG / Web Search 표시 wrapper는 parser 변경 없이 별도 지시문으로 분리

## 28. 즉시 구현 금지 영역

다음은 이번 문서 작성 이후에도 즉시 구현하지 않는다.

- `ConversationStyleTokens.kt` 생성
- `ConversationUiComponents.kt` 생성
- `GraphicsSettingsStore` 구현
- `GraphicsThemeResolver` 구현
- `ConversationRoomScreen` 직접 치환
- `ConversationInputPanel` 치환
- `ConversationRunSummaryPanelReadable` 치환
- RAG parser 변경
- Web Search parser 변경
- trace marker 변경
- structured parser 변경
- send flow 변경
- message 저장 schema 변경
- Provider 호출 변경
- Worker / Scenario 실행 연결
- 작업모드 수정

## 29. 검증 체크리스트

문서 단계 검증:

- [ ] 변경 파일은 `docs/design/MAHA_CONVERSATION_STYLE_TOKENS_v1.md` 하나다.
- [ ] Kotlin 파일 변경이 없다.
- [ ] `ConversationEngine` 변경이 없다.
- [ ] `ConversationViewModel` 변경이 없다.
- [ ] Provider Adapter 변경이 없다.
- [ ] RAG / Web Search 실행 로직 변경이 없다.
- [ ] 저장 schema 변경이 없다.
- [ ] 작업모드 변경이 없다.
- [ ] 민감정보가 포함되지 않는다.

후속 구현 전 검증:

- [ ] `MAHA_CONVERSATION_ROOM_STATIC_SCAN_v1.md`와 본 문서가 함께 참조된다.
- [ ] token 적용 전 역할 목록이 고정된다.
- [ ] parser/helper 함수는 수정 대상에서 제외된다.
- [ ] input panel, run info, RAG/Web Search는 각각 별도 단계로 분리된다.

## 30. 마이그레이션 전 안정 지점

현재 안정 지점:

- 대화방 UX/UI 현황 문서화 완료
- 대화방 UI 정적 스캔 문서화 완료
- ConversationStyleTokens 후보 설계 완료
- 아직 Kotlin UI 코드는 변경하지 않음
- 아직 `ConversationStyleTokens.kt` / `ConversationUiComponents.kt`를 생성하지 않음
- ConversationEngine / ConversationViewModel / Provider 호출 / RAG / Web Search / 저장 schema는 미변경

다음 단계는 `ConversationUiComponents` skeleton 설계 또는 대화방 메시지 카드 역할 목록 고정이다. 실제 치환은 별도 구현 지시문으로만 진행한다.
