# MAHA_CONVERSATION_ROOM_UX_UI_REVIEW_v1

## 1. 목적

이 문서는 MAHA Android 대화모드의 대화세션 / 대화방 UX·UI 현황을 정리하고, 후속 개선 방향을 설계하기 위한 문서다.

최근 대화모드 설정 UX 안정화 작업으로 카드, 버튼, chip, Dialog, Switch, 중첩카드, back stack, Drawer 폭 정책이 정리되었다. 다음 단계에서는 실제 사용 시간이 가장 긴 대화방 화면을 대상으로 가독성, 메시지 흐름, 입력 경험, 실행정보 표시, RAG / Web Search 표시, structured output 표시를 점검해야 한다.

이번 문서는 설계 기록이며, Kotlin UI 코드, 저장 로직, ConversationEngine, Provider 호출, RAG / Web Search 실행 로직은 변경하지 않는다.

## 2. 설계 전제

- 대화방은 사용자가 가장 오래 머무는 화면이다.
- 대화방은 설정 화면보다 본문 가독성과 흐름 유지가 더 중요하다.
- 답변 본문과 실행정보는 시각적으로 분리되어야 한다.
- 실행정보, RAG, Web Search, trace, provider summary는 기본적으로 compact 또는 접힘 구조가 적합하다.
- 사용자가 질문과 답변 흐름을 방해받지 않도록 한다.
- 오류와 경고는 숨기지 않되 본문보다 과하게 튀지 않게 표시한다.
- phone에서는 세로 공간 절약이 중요하다.
- tablet / landscape에서는 좌우 폭을 활용할 수 있다.
- 그래픽 설정 도입 시 대화방은 설정 화면과 별도 token layer를 가질 수 있다.

## 3. 현재 대화방 화면 구성

### 3.1 상단 / Drawer / Navigation

대화방 상단 영역은 다음 역할을 가진다.

- 현재 대화세션 title 표시
- 세션 목록 또는 대화목록 진입
- 대화 설정 Drawer 진입
- 시스템 뒤로가기 처리
- 현재 대화방 상태와 설정 접근의 진입점 제공

검토 포인트:

- 상단 영역이 메시지 영역을 과도하게 밀어내지 않는가
- title이 길 때 줄바꿈 또는 말줄임 정책이 안정적인가
- 설정 Drawer 진입과 세션 목록 진입이 혼동되지 않는가
- 시스템 뒤로가기와 화면 내부 navigation 정책이 충돌하지 않는가

### 3.2 메시지 영역

메시지 영역은 다음 요소를 포함한다.

- 사용자 메시지
- assistant 응답
- system / worker / trace 성격의 block 후보
- markdown block
- code block
- table block
- json block
- error / warning / system notice
- provider / model summary 후보
- timestamp / role label 후보

검토 포인트:

- 사용자 질문과 assistant 답변이 명확히 구분되는가
- 긴 답변의 줄간격과 paragraph spacing이 읽기 좋은가
- code / json / table block이 본문과 잘 분리되는가
- error / warning block이 과하게 튀지 않으면서도 인지 가능한가
- provider summary 또는 trace가 대화 본문을 오염시키지 않는가

### 3.3 입력 영역

입력 영역은 다음 요소를 포함한다.

- 텍스트 입력창
- send button
- multiline 입력 높이
- quick setting 후보
- attachment / file action 후보
- mic / voice action 후보
- RAG / Web Search quick toggle 후보

검토 포인트:

- 입력창이 너무 작거나 크지 않은가
- multiline 입력 시 메시지 영역을 과도하게 가리지 않는가
- send button이 명확하지만 과도하게 튀지 않는가
- quick setting이 본문 흐름을 방해하지 않는가
- file / voice action 후보를 추가할 공간 정책이 있는가

### 3.4 실행정보 영역

실행정보 영역은 다음 정보를 포함할 수 있다.

- run summary
- provider / model summary
- latency / time
- token / cost 후보
- worker summary 후보
- RAG 사용 여부
- Web Search 사용 여부
- fallback 여부
- error / retry count 후보

검토 포인트:

- 실행정보가 답변 본문과 분리되어 있는가
- 기본값은 접힘 또는 1줄 summary가 적절한가
- 상세 정보가 필요한 경우만 펼칠 수 있는가
- provider / model / latency / source 정보가 복사 가능해야 하는가

### 3.5 RAG / Web Search 표시

RAG / Web Search 표시 영역은 다음 역할을 가진다.

- RAG context 결과 표시
- 검색 결과 요약
- citation / source 표시
- source title / url / snippet 표시
- 최신성 / 출처 확인 상태 후보
- fallback 또는 미사용 사유 표시

검토 포인트:

- RAG context가 assistant 본문과 섞이지 않는가
- citation이 본문 가독성을 방해하지 않는가
- source가 너무 길게 노출되지 않는가
- Web Search 사용 여부와 grounding 성공 여부가 구분되는가
- 최신성 / 확인 가능성 상태가 충분히 드러나는가

### 3.6 Dialog / Popup

대화방 관련 Dialog / Popup 후보는 다음과 같다.

- message edit dialog
- conversation settings dialog
- storage settings dialog
- quick setting popup
- confirm dialog
- code / json / table preview dialog 후보

검토 포인트:

- Dialog surface opacity / scrim이 설정 화면과 일관되는가
- 저장 / 취소 / 삭제 버튼 역할이 명확한가
- Dialog 우선 닫기 동작이 유지되는가
- message edit dialog가 긴 메시지를 안정적으로 다루는가

## 4. 대화방 UX 목표

1. 답변 본문 가독성을 최우선으로 한다.
2. 사용자 질문과 assistant 답변의 시각적 구분을 명확하게 한다.
3. 실행정보 / RAG / Web Search / trace는 본문에서 분리하고 기본 compact 표시를 지향한다.
4. 오류와 경고는 숨기지 않되, 과도한 색 면으로 본문을 방해하지 않는다.
5. phone에서는 세로 공간을 절약한다.
6. tablet / landscape에서는 폭을 활용해 메시지 max width와 보조 패널 배치를 개선할 수 있다.
7. 그래픽 설정 도입 시 density, font scale, contrast, radius, citation 표시 밀도를 조정할 수 있게 한다.
8. 대화방 token은 설정 token을 그대로 복사하지 않고, 대화방 가독성 중심의 별도 layer로 설계한다.

## 5. 메시지 카드 / 말풍선 정책

### 5.1 사용자 메시지

후보 정책:

- 사용자 메시지는 말풍선형 또는 우측 정렬 compact card 후보를 유지한다.
- 배경은 assistant보다 약간 다른 tone을 쓰되 큰 색 면은 피한다.
- 긴 사용자 입력은 적절한 max width와 줄간격을 가진다.
- 복사 / 편집 action은 기본 노출보다 long press 또는 overflow 후보가 적합하다.

### 5.2 Assistant 메시지

후보 정책:

- assistant 메시지는 본문 카드형 또는 full-width readable block 후보를 검토한다.
- markdown spacing을 안정화한다.
- 실행정보, provider summary, trace는 assistant 본문과 분리한다.
- 긴 답변은 paragraph spacing, list spacing, code block spacing을 우선 조정한다.

### 5.3 Error / Warning / System Notice

후보 정책:

- 오류 / 경고는 InlineNotice 또는 compact block으로 표시한다.
- danger / warning 색은 border / icon / label 중심으로 제한한다.
- 원문 error raw text는 기본 접힘 후보로 둔다.

## 6. 입력창 UX

입력창 개선 후보는 다음과 같다.

- compact / normal / comfortable height 옵션
- multiline max height 옵션
- send button 고정 위치 안정화
- 입력 중 키보드와 메시지 리스트 interaction 확인
- file / mic / tool action 후보를 overflow 또는 secondary action으로 배치
- RAG / Web Search quick toggle은 본문 입력보다 덜 튀게 표시
- 빈 입력 send 방지 조건은 유지

권장 기본값:

- 입력창 기본 density는 normal
- multiline max height는 phone에서 보수적으로 제한
- send button은 명확한 action color 사용
- 입력창 border는 settings button보다 약간 명확하게

## 7. 실행정보 패널 UX

실행정보 패널은 기본 compact 구조가 적합하다.

권장 구조:

1. 1줄 summary
   - provider / model
   - latency
   - RAG / Web Search 사용 여부
   - warning count 후보
2. 접힘 상세
   - runInfo
   - provider message
   - fallback info
   - token / cost 후보
   - raw trace는 개발자 모드 또는 복사 전용 후보

주의:

- 실행정보를 assistant 본문 상단에 크게 노출하지 않는다.
- trace / provider summary가 RAG index와 recent prompt에 섞이지 않도록 후속 정책과 연결한다.
- 실패 정보는 본문과 분리하되 사용자가 원인을 확인할 수 있어야 한다.

## 8. RAG / Web Search 표시 UX

### 8.1 RAG

후보 정책:

- RAG context는 본문과 분리한다.
- 기본은 compact summary로 표시한다.
- 상세 context는 펼침 또는 preview dialog 후보로 둔다.
- source title, chunk preview, score, fallbackReason을 분리한다.
- 과도한 원문 노출은 피한다.

### 8.2 Web Search

후보 정책:

- citations는 본문에 직접 섞지 않고 실행정보 또는 source panel에서 관리한다.
- source는 compact card 또는 inline chip 후보로 표시한다.
- title / url / snippet 표시 밀도를 graphics setting 후보와 연결한다.
- grounding 성공, fallback 성공, 미지원 사유를 구분한다.

## 9. Structured block / Code block 표시 UX

structured block 후보:

- markdown block
- code block
- json block
- table block
- error block
- trace block
- memory block

개선 후보:

- code block 전용 배경과 border token
- code font scale 옵션
- copy button 위치 통일
- json/table preview와 raw view 전환 후보
- table은 phone에서 horizontal scroll 또는 compact row 전환 후보
- trace block은 기본 노출 최소화

## 10. Dialog / Message Edit UX

개선 후보:

- message edit dialog는 충분히 불투명한 dark surface 유지
- 긴 메시지 편집 시 textarea max height 안정화
- 저장 / 취소 / 삭제 버튼 역할 명확화
- Dialog 우선 닫기 유지
- storage settings dialog와 동일한 scrim / opacity 정책 유지
- 편집 후 messages.jsonl append 구조와 정합성은 후속 검토 후보

## 11. Phone / Tablet / Landscape 고려사항

### Phone

- 세로 공간 절약 우선
- 실행정보 기본 접힘
- RAG / Web Search source compact 표시
- 입력창 max height 제한
- long answer에서 scroll 안정성 확인

### Tablet

- message max width 조정
- source / run info 보조 패널 후보
- Drawer 폭과 대화방 폭 균형 검토
- message list 좌우 여백 확대 가능

### Landscape

- keyboard가 열렸을 때 입력창과 메시지 영역 충돌 확인
- 실행정보 panel을 inline이 아닌 side / bottom compact 후보로 검토
- message bubble max width 재조정 필요

## 12. ConversationStyleTokens 후보

후보 파일:

- ConversationStyleTokens.kt
- ConversationUiComponents.kt

후보 token:

### Message

- messageUserBackground
- messageAssistantBackground
- messageSystemBackground
- messageBorderColor
- messageRadius
- messagePadding
- messageSpacing
- messageMaxWidthPhone
- messageMaxWidthTablet

### Input

- inputPanelBackground
- inputPanelBorder
- inputFieldBackground
- inputFieldBorder
- inputFieldRadius
- inputPanelPadding
- sendButtonRadius

### Structured / Code

- structuredBlockBackground
- structuredBlockBorder
- structuredBlockRadius
- codeBlockBackground
- codeBlockBorder
- codeBlockFontScale
- tableBlockBorder
- jsonBlockBackground

### Citation / RAG

- citationChipColor
- citationChipBorder
- ragContextBorder
- ragContextBackground
- webSearchCitationBorder
- sourceCardBorder

### Run Summary

- runSummaryBackground
- runSummaryBorder
- runSummaryRadius
- runSummaryCompactHeight
- traceBlockBackground
- traceBlockBorder

### Error / Warning

- errorNoticeBorder
- warningNoticeBorder
- infoNoticeBorder
- noticeBackground

## 13. Graphics 설정과 연결할 옵션

대화방에 연결 가능한 graphics option 후보:

- conversationDensity
- messageSpacing
- messageFontScale
- codeBlockFontScale
- messageBubbleRadius
- inputPanelHeight
- runSummaryDefaultCollapsed
- ragContextDisplayDensity
- citationDisplayDensity
- timestampVisibility
- roleLabelVisibility
- reducedMotion
- largeTouchTargets
- highContrast
- reducedTransparency
- structuredBlockDensity

연결 방침:

- SettingsStyleTokens를 그대로 복사하지 않는다.
- GraphicsSettings의 accent / density / fontScale / contrast 값은 공유 가능하다.
- ConversationStyleTokens는 대화방 가독성을 우선한다.
- Settings token과 Conversation token은 GraphicsThemeResolver에서 함께 생성할 수 있다.

## 14. 구현 우선순위

권장 순서:

1. 대화방 UX/UI 현황 문서화
2. ConversationRoomScreen 직접 Material 사용처 정적 스캔
3. 대화방 메시지 카드 / 입력창 / 실행정보 패널 역할 목록 고정
4. ConversationStyleTokens 설계
5. ConversationUiComponents skeleton 설계
6. 메시지 카드 스타일 1차 치환
7. 입력창 / quick setting UI 보정
8. 실행정보 패널 compact화
9. RAG / Web Search 표시 compact화
10. phone / tablet / landscape 검증
11. Graphics 설정과 동적 연결

## 15. 유지해야 할 금지사항

후속 구현 전까지 다음은 금지한다.

- ConversationEngine 연결 변경
- ConversationViewModel 전송 흐름 변경
- Provider 호출 변경
- RAG / Web Search 실행 로직 변경
- 저장 schema 변경
- Worker / Scenario 실제 실행 연결
- GraphicsSettingsStore 즉시 구현
- ConversationStyleTokens 실제 구현
- 작업모드 수정
- trace / provider summary를 본문에 직접 섞는 구조

## 16. 후속 구현 단계

### 단계 1: 정적 스캔

- ConversationRoomScreen.kt 직접 Color / Card / Button / Dialog / TextField 사용처 확인
- ConversationBlocks.kt block style 사용처 확인
- ConversationDialogs.kt dialog / edit style 사용처 확인

### 단계 2: 역할 목록 고정

- UserMessageBlock 역할 목록
- AssistantMessageBlock 역할 목록
- StructuredOutputBlock 역할 목록
- ConversationInputPanel 역할 목록
- RunSummaryPanel 역할 목록
- RAG / Web Search 표시 역할 목록

### 단계 3: token 설계

- ConversationStyleTokens.kt 후보 작성
- SettingsStyleTokens와 공유할 값 / 분리할 값 구분
- GraphicsSettings 연결 후보 정리

### 단계 4: UI skeleton 설계

- ConversationUiComponents.kt 후보 작성
- MessageCard, RunInfoPanel, CitationChip, RagContextPanel, ConversationInputBox 후보 정의

### 단계 5: 단계별 치환

- 메시지 카드부터 1차 치환
- 입력창 / quick setting 별도 치환
- 실행정보 패널 별도 compact화
- RAG / Web Search 별도 compact화
- phone / tablet / landscape 검증

## 17. 마이그레이션 전 안정 지점

현재 안정 지점:

- 대화모드 설정 UX 안정화 문서화 완료
- 그래픽 설정 옵션 설계 문서화 완료
- 대화방 UX/UI 현황 점검 문서화 단계
- 아직 ConversationRoomScreen 실제 UI 코드는 변경하지 않음
- ConversationEngine / Provider 호출 / RAG / Web Search 실행 / 저장 schema는 미변경

다음 세션 또는 후속 구현 세션은 이 문서를 기준으로 대화방 UI 개선을 단계별로 진행해야 한다.

