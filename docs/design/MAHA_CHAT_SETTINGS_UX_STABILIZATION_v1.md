# MAHA_CHAT_SETTINGS_UX_STABILIZATION_v1

## 1. 목적

이 문서는 MAHA Android 대화모드 설정 UX 안정화 작업의 완료 범위와 후속 작업 경계를 기록한다.

대상은 최근 진행한 대화모드 설정 계열 UI 보정 작업이다. 카드, 버튼, chip, Dialog, Popup, Switch, Toggle, 중첩카드, back stack, Drawer 폭, 설정 페이지 구조 정리 내용을 현재 안정 지점으로 남긴다.

이 문서는 구현 지시문이 아니라 안정화 마감 기록이다. 이 문서 작성 단계에서는 Kotlin UI 코드, 저장 로직, Provider 호출, ConversationEngine, Worker/Scenario 실행, RAG/Web Search/Tool 실행, 작업모드 코드를 변경하지 않는다.

## 2. 적용 범위

이 안정화 기록은 다음 대화모드 설정 계열 화면을 대상으로 한다.

- 대화모드 설정 메인
- 프로바이더 / 모델 / API 설정 1페이지
- 프로바이더 / 모델 상세 설정 2페이지
- Provider 관리
- Model 관리
- 메모리 / RAG / 저장소 설정
- 저장소 관리
- 고급 하네스 설정
- Worker Profile 관리
- Worker Profile 편집
- Scenario 관리
- Scenario 편집
- Capability Resolver 진단
- Scenario Preview / Plan Preview
- 앱 시작 메인 화면의 작업모드 / 대화모드 선택 카드
- 대화모드 설정 계열 Dialog / Popup / Confirm Dialog / Switch / Toggle

## 3. 완료된 설정 UX 보정 항목

### 3.1 대화모드 설정 구조 정리

- 대화모드 설정 메인 카드맵을 기본 설정 중심으로 정리했다.
- 더미 카드, 미연결 placeholder 카드, 중복 진입 카드를 제거하거나 통합했다.
- 고급 하네스 설정을 기본 설정 화면과 분리했다.
- 프로바이더 / 모델 / API 설정을 1페이지 요약 구조와 2페이지 상세 설정 구조로 정리했다.
- Provider / Model 관리를 하단 펼침 영역이 아니라 상세 설정 페이지 구조로 분리했다.
- 2페이지의 2중 스크롤 문제를 완화했다.
- 설정 백업 / 복원 카드의 위치를 하단 중심으로 정리했다.

### 3.2 앱 시작 메인 화면 정리

- 앱 실행 직후 작업모드 / 대화모드 선택 카드에 대화모드 설정과 동일한 다크테마 스타일 기준을 적용했다.
- 작업모드 / 대화모드 카드 상단의 영문 중복 chip을 제거했다.
- 한글 제목과 설명문 중심으로 메인 카드 구조를 정리했다.
- 작업모드 / 대화모드 진입 onClick 로직은 유지했다.

## 4. Provider / Model / API 설정 정리 내용

### 4.1 Provider / Model 설정 구조

- 프로바이더 / 모델 / API 설정 1페이지는 요약 중심으로 유지한다.
- 상세 설정은 프로바이더 / 모델 상세 설정 2페이지에서 담당한다.
- Provider / Model 탭 전환 구조는 유지한다.
- 현재 설정 상태 카드의 요약 정보는 compact summary 성격으로 유지한다.
- Provider / Model 상세 관리 영역은 전체 단일 스크롤 흐름 안에서 표시한다.

### 4.2 ProviderManagementScreen 보정 내용

- Provider 관리 상단 카드, Provider 목록 정리 / 필터 카드, Provider 개별 카드 스타일을 공통 설정 스타일 기준으로 보정했다.
- Provider 추가 / 수정 / 삭제 / 활성 Switch를 유지했다.
- Provider별 모델 목록 불러오기 버튼을 복구하고 유지했다.
- GOOGLE, OPENAI, OPENAI_COMPATIBLE, LOCAL, CUSTOM, NVIDIA ProviderType별 표시 정책을 유지했다.
- 모델 목록 Dialog 검색창을 복구했다.
- 검색어 입력 시 모델 목록 필터링을 유지했다.
- 조회 결과에서 Model Profile 추가와 중복 모델 추가 방지 로직을 유지했다.
- API Key 원문은 표시하지 않는다.

### 4.3 ModelManagementScreen 보정 내용

- Model 관리 상단 카드, 기본 모델 경고 카드, Model 목록 필터 카드, Model 개별 카드 스타일을 공통 설정 스타일 기준으로 보정했다.
- Model 추가 / 수정 / 삭제 / 활성 Switch를 유지했다.
- 기본 모델 지정 기능을 유지했다.
- 즐겨찾기 추가 / 해제 기능을 유지했다.
- ProviderType 필터, 즐겨찾기만 필터, 활성 모델만 필터를 유지했다.
- Model 추가 / 수정 Dialog의 canSave 조건을 유지했다.
- Provider missing / orphan 관련 표시 정책을 유지했다.

### 4.4 ProviderType.OPENAI 보정 기록

- ProviderType.OPENAI 추가 이후 누락되었던 when 분기를 보정한 상태를 안정 지점으로 기록한다.
- OPENAI는 공식 OpenAI Responses API 계열 Provider로 분리한다.
- OPENAI_COMPATIBLE / LOCAL / CUSTOM은 OpenAI-compatible chat/completions 계열로 유지한다.

## 5. Memory / RAG / Storage 설정 정리 내용

- 메모리 / RAG / 저장소 설정 페이지의 placeholder 성격 카드를 제거했다.
- 저장소 관리 화면의 카드 / 버튼 / RAG 검색 테스트 / 세션 카드 스타일을 공통 설정 스타일 기준으로 보정했다.
- 저장소 관리 화면의 좌측 최상단 시각적 back arrow를 제거했다.
- 메모리 / RAG / 저장소 설정 페이지의 좌측 최상단 시각적 back arrow도 제거했다.
- RAG 검색 테스트, session.json 보기, messages.jsonl 보기, 세션 백업 / 복원 / 인덱싱 / 삭제 기능은 유지했다.
- Storage / RAG 저장 schema는 변경하지 않았다.

## 6. 고급 하네스 설정 정리 내용

- 고급 하네스 설정 메인 화면을 기본 설정과 분리된 고급 영역으로 유지했다.
- Worker Profile / Scenario 관리와 Capability Resolver / Scenario Preview 진입점을 고급 하네스 설정 내부로 정리했다.
- 고급 하네스 설정과 하위 화면의 카드 / 버튼 / chip / 접힘 UI를 SettingsStyleTokens / SettingsUiComponents 기준으로 1차 통일했다.
- 고급 하네스 설정 하위 화면의 좌측 최상단 시각적 back arrow를 제거했다.
- 시스템 뒤로가기와 settings back stack은 유지했다.
- 고급 하네스는 preview / diagnostic / settings 계층이며 실제 Worker/Scenario 실행 연결은 하지 않는다.

## 7. Worker Profile / Scenario / Capability Resolver UI 정리 내용

### 7.1 Worker Profile 관리

- Worker Profile 관리 카드 스타일을 다크테마 token 기준으로 보정했다.
- Worker 상세보기 영역의 카드 속 카드 회색 중첩감을 줄였다.
- role, provider, model, capability, policy 관련 보조 정보는 InfoRow / ChipRow 중심으로 정리했다.
- 상세 보기 / 상세 닫기 / 편집 / 복제 / 활성화 / 비활성화 / 삭제 버튼은 유지했다.
- 삭제 확인 Dialog는 유지했다.

### 7.2 Worker Profile 편집

- Capability Override의 “이전 / 다음” 순차 변경 방식을 제거했다.
- Capability Override 상태를 직접 선택 가능한 버튼형 selector로 변경했다.
- OutputPolicy expectedOutputType의 “이전 / 다음” 순차 변경 방식도 제거했다.
- expectedOutputType을 직접 선택 가능한 버튼형 selector로 변경했다.
- Provider / Model 선택 카드에서 작은 “선택됨 / 선택 가능” 배지를 제거했다.
- Provider / Model 선택 상태는 하단 긴 버튼 하나로 표현한다.
- providerId / modelId / capabilityOverrides / inputPolicy / outputPolicy 저장 로직은 유지했다.
- provider 변경 시 modelId를 null로 보정하는 기존 로직을 유지했다.
- userModified, updatedAt 갱신 정책을 유지했다.

### 7.3 Scenario 관리 / 편집

- Scenario 관리와 Scenario 편집 화면의 카드 / 버튼 / chip 스타일을 공통 설정 스타일 기준으로 보정했다.
- Scenario WorkerSet 추가 / 제거 / 순서 변경 버튼을 유지했다.
- 실행 방식, Orchestrator / Synthesis 선택 섹션을 유지했다.
- Scenario 저장 / 취소 기능을 유지했다.
- Scenario 저장 schema는 변경하지 않았다.

### 7.4 Capability Resolver / Scenario Preview

- Capability Resolver 진단 화면의 Preview only 안내, Scenario 선택, Plan summary, WorkerPlan, limitation 표시 영역의 중첩카드 회색감을 줄였다.
- Scenario 선택 영역의 보조 정보는 카드 중첩보다 InfoRow / InlineNotice 중심으로 정리했다.
- Capability Resolver의 판단 로직은 변경하지 않았다.
- Scenario Preview / Plan Preview는 실제 실행이 아닌 preview / diagnostic 계층으로 유지한다.

## 8. 카드 / 버튼 / chip 스타일 token 정리 내용

### 8.1 SettingsStyleTokens 정책

- 대화모드 설정 내부의 주요 색상 정책을 SettingsStyleTokens 중심으로 정리했다.
- 카드 큰 면 배경은 단일 dark surface 계열로 유지한다.
- 상태 차이는 큰 배경색이 아니라 border / chip / text 중심으로 표현한다.
- 액션 색, 선택 색, 상태 색의 역할을 분리했다.
- 선택 상태는 border / text 중심으로 표현한다.
- danger / warning / success / info는 큰 카드 배경에 직접 사용하지 않는다.
- chip은 작은 상태 표시이므로 약한 tone background를 허용한다.

### 8.2 SettingsUiComponents 정책

- SettingsSectionCard, SettingsNavCard, SettingsExpandableCard, SettingsInfoPanel, SettingsInfoRow, SettingsInlineNotice, SettingsDivider, SettingsPrimaryButton, SettingsSecondaryButton, SettingsDangerButton, SettingsTextButton, SettingsStatusChip, SettingsChipRow 계열을 중심으로 스타일을 통일했다.
- 직접 Card / Button / TextButton / OutlinedButton 사용을 줄이고 공통 컴포넌트 사용을 늘렸다.
- Switch / Radio / 선택 컨트롤도 공통 token 기준을 적용하도록 보정했다.

## 9. Dialog / Popup / Switch 다크테마 정리 내용

### 9.1 Dialog / Popup

- 대화모드 설정 계열 Dialog / Popup / Confirm Dialog에 다크테마 surface를 적용했다.
- Dialog 버튼은 역할에 따라 primary / secondary / text / danger 스타일을 적용한다.
- Dialog 뒤 설정 카드가 비치는 문제를 완화하기 위해 dialog surface opacity와 scrim 정책을 보정했다.
- dialogBackground, dialogInnerBackground, dialogBorderColor, dialogScrimColor 정책을 명시했다.
- Provider 추가 / 수정 Dialog, Provider 삭제 확인 Dialog, Provider 모델 목록 Dialog, Model 추가 / 수정 Dialog, Model 삭제 확인 Dialog, Storage / RAG 관련 Dialog, Worker / Scenario 삭제 Dialog, Conversation 설정 Dialog 계열을 보정 대상으로 다뤘다.

### 9.2 Switch / Toggle / Radio

- Provider 활성 / 비활성 Switch, Model 활성 / 비활성 Switch, Worker / Scenario Boolean toggle, Storage / RAG 관련 toggle의 밝은 Material 기본색 노출을 줄였다.
- checked / unchecked / disabled 상태를 SettingsStyleTokens 기준으로 보정했다.
- Dialog 표시 조건, 닫기 조건, onClick, onCheckedChange, visible / enabled 조건은 유지했다.

## 10. 중첩카드 정책 정리 내용

대화모드 설정 내부의 중첩카드 정책은 다음과 같이 정리한다.

1. 화면의 주요 구획만 SettingsSectionCard를 사용한다.
2. 카드 내부의 보조 정보는 중첩 Card가 아니라 SettingsInfoRow / SettingsDivider / SettingsChipRow / SettingsInlineNotice 중심으로 표현한다.
3. 꼭 필요한 내부 panel은 같은 dark background 계열과 약한 border만 사용한다.
4. 카드 중첩은 최대 2단계로 제한한다.
5. 상태 차이는 배경색이 아니라 border / chip / text / spacing으로 표현한다.
6. 경고 / 위험 / 성공 / 선택 상태도 큰 카드 배경색을 바꾸지 않는다.
7. chip은 작은 상태 표시이므로 약한 tone 표현을 허용하되, 카드 / 버튼의 큰 면 배경에는 tone background를 쓰지 않는다.

금지할 패턴은 다음과 같다.

- 카드 안에 또 큰 회색 카드 여러 개 배치
- 상태별로 카드 내부 배경을 밝게 바꾸는 구조
- 단순 정보를 각각 별도 카드로 표시하는 구조
- 버튼 또는 선택 상태를 큰 배경색 채움으로 표현하는 구조

권장 패턴은 다음과 같다.

- label / value 형태의 SettingsInfoRow
- 짧은 안내용 SettingsInlineNotice
- 상태 요약용 SettingsChipRow
- 섹션 구분용 SettingsDivider
- border 중심의 selector / segmented button / button row

## 11. back stack / 시스템 뒤로가기 정책

- 좌측 최상단 시각적 back arrow는 대화모드 설정 계열 화면에서 제거하는 방향으로 정리했다.
- Android 시스템 뒤로가기는 직전 설정 화면으로 pop되어야 한다.
- Dialog가 열려 있으면 Dialog 닫기가 우선이다.
- 설정 root에서 시스템 뒤로가기를 누르면 Drawer가 닫혀야 한다.
- 시각적 back icon 제거는 navigation logic 제거가 아니다.
- settings back stack은 AppRoot의 기존 구조를 유지한다.

기대 흐름은 다음과 같다.

```text
대화모드 설정 메인
→ 프로바이더 / 모델 / API 설정
→ 프로바이더 / 모델 상세 설정
→ 시스템 뒤로가기
→ 프로바이더 / 모델 / API 설정
```

```text
대화모드 설정 메인
→ 메모리 / RAG / 저장소 설정
→ 저장소 관리
→ 시스템 뒤로가기
→ 메모리 / RAG / 저장소 설정
```

```text
대화모드 설정 메인
→ 고급 하네스 설정
→ Worker Profile 관리
→ Worker 편집
→ 시스템 뒤로가기
→ Worker Profile 관리
```

```text
고급 하네스 설정
→ Scenario 관리
→ Scenario 편집
→ 시스템 뒤로가기
→ Scenario 관리
```

```text
고급 하네스 설정
→ Capability Resolver
→ 시스템 뒤로가기
→ 고급 하네스 설정
```

## 12. 유지해야 할 금지사항

다음 항목은 이 안정화 범위에서 변경하지 않는다.

- ConversationEngine 연결 금지
- ConversationViewModel 대화 흐름 변경 금지
- Provider 호출 로직 변경 금지
- ProviderAdapter 변경 금지
- Worker / Scenario 실제 실행 연결 금지
- 병렬 실행 구현 금지
- Tool 실행 구현 금지
- RAG/Web Search 실행 연결 변경 금지
- WorkerProfileStore 저장 구조 변경 금지
- Scenario 저장 구조 변경 금지
- Provider / Model 저장 schema 변경 금지
- Storage / RAG 저장 schema 변경 금지
- CapabilityResolver 판단 로직 변경 금지
- 작업모드 UI 또는 실행 흐름 수정 금지
- API Key 원문 표시 금지
- API Key 백업 포함 금지

## 13. 남은 후보 작업

### 후보 1. 대화모드 설정 UX 실기기 최종 점검

- phone / tablet / landscape 확인
- 카드 밀도 확인
- 세로 스크롤 길이 확인
- Dialog 표시 확인
- Switch / Radio / Popup 잔여 색감 확인

### 후보 2. Storage / Conversation Dialog 잔여 미세 보정

- Storage 관련 Dialog 잔여 밝은 표면 확인
- Conversation 설정 Dialog와 대화 본문 UI의 스타일 경계 정리
- 대화 본문 UI까지 설정 token을 무리하게 확장하지 않도록 경계 유지

### 후보 3. RAG 품질 개선

- TRACE / ERROR / PROVIDER_SUMMARY block allowlist 설계
- ConversationPromptBuilder recent messages allowlist 검토
- ConversationChunkIndexer block type filter 검토
- SAF-only 세션 indexing 검토
- keyword scoring 개선 또는 BM25 / embedding 준비 검토

### 후보 4. 설정 백업 범위 검증

- worker_profiles.json 백업 포함 여부 확인
- conversation_scenarios.json 백업 포함 여부 확인
- provider_api_keys.json 백업 제외 원칙 유지
- 작업모드 ApiKeyManager 저장값 백업 제외 원칙 유지

### 후보 5. 고급 하네스 실제 실행 연결 설계

- ConversationRequest에 scenarioId / workerProfileIds 추가 여부 설계
- ConversationEngine에 직접 연결할지 별도 OrchestratorEngine을 둘지 결정
- WorkerProfile systemInstruction을 Provider prompt에 적용하는 경로 설계
- WorkerPlan preview와 실제 ConversationRun / ConversationWorkerResult 연결 schema 설계
- 순차 / 병렬 / 혼합 실행 coroutine policy 설계

주의: 후보 5는 별도 대형 단계로 분리한다. 현재 안정화 문서의 범위에서는 실제 실행 연결을 시작하지 않는다.

## 14. 다음 단계 진입 기준

다음 구현 단계로 넘어가기 전 기준은 다음과 같다.

- 대화모드 설정 주요 화면에서 카드 / 버튼 / chip / Dialog / Switch 스타일이 일관되어야 한다.
- Provider / Model / Worker / Scenario / Storage 기능 회귀가 없어야 한다.
- settings back stack과 Android 시스템 뒤로가기 동작이 정상이어야 한다.
- Dialog 우선 닫기 동작이 유지되어야 한다.
- ConversationEngine, Provider 호출, Worker/Scenario 실행, RAG/Web Search/Tool 실행 계층이 변경되지 않은 상태여야 한다.
- 저장 schema가 변경되지 않아야 한다.
- 작업모드가 영향받지 않아야 한다.
- 빌드가 성공해야 한다.

