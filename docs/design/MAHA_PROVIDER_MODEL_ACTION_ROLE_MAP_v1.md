# MAHA_PROVIDER_MODEL_ACTION_ROLE_MAP_v1

기준 시점: 2026-05-05  
대상 파일:

- `ProviderManagementScreen.kt`
- `ModelManagementScreen.kt`

목적:

- Provider/Model 설정 화면의 카드와 버튼 역할을 고정한다.
- 각 액션의 표시 조건, enabled 조건, onClick 역할을 문서화한다.
- 후속 `SettingsUiComponents` 기반 스타일 치환에서 기능 버튼 손실, 분기 손상, enabled 조건 변경을 방지한다.
- 이 문서는 설계/검증 기준 문서이며 Kotlin 기능 로직을 변경하지 않는다.

---

## 1. 목적

이 문서는 대화모드의 Provider/Model 설정 화면에서 반드시 유지해야 하는 카드, 버튼, 표시 조건, enabled 조건, onClick 역할을 고정한다.

후속 UI 스타일 통일 작업에서 아래와 같은 회귀를 막는 것을 목표로 한다.

- Provider 추가/수정/삭제 버튼 손실
- Provider별 모델 목록 불러오기 버튼 손실
- ProviderType별 모델 목록 조회 분기 손상
- Model 추가/수정/삭제/기본 지정/즐겨찾기 버튼 손실
- Dialog 진입/저장/삭제 흐름 손상
- enabled 조건과 visible 조건의 임의 변경
- ProviderSettingsStore 저장 흐름 변경

---

## 2. 설계 전제

1. 작업 범위는 대화모드 설정 UI에 한정한다.
2. 작업모드 `Agent`, `ExecutionEngine`, `ModelRouter`, 작업모드 Provider는 대상이 아니다.
3. `ConversationEngine`, `ConversationViewModel`, Provider Adapter, RAG, Web Search, Tool 실행 계층은 대상이 아니다.
4. Provider/Model 저장 schema는 변경하지 않는다.
5. API Key 원문은 화면, 로그, 문서에 출력하지 않는다.
6. Provider와 Model은 `ProviderSettingsStore`를 통해 저장/로드한다.
7. 스타일 치환은 wrapper 교체 수준이어야 하며 기능 조건을 바꾸면 안 된다.

---

## 3. 직전 회귀 원인 요약

직전 스타일 통일 작업 후 Provider/Model 관리 화면에서 기능 버튼이 사라지거나 표시 조건이 깨진 것으로 의심되었다.

회귀 방지 관점의 원인은 다음과 같이 정리한다.

1. 작은 `TextButton` 기반 액션 버튼을 큰 공통 버튼으로 치환하면서 모바일 폭에서 버튼이 밀리거나 일부 버튼이 보이지 않을 수 있다.
2. 스타일 치환 중 `visible`, `enabled`, `onClick` 조건을 함께 건드리면 기능 버튼이 사라질 수 있다.
3. ProviderType별 모델 목록 조회 분기가 UI 스타일 코드와 섞이면 특정 ProviderType만 남고 나머지 타입이 누락될 수 있다.
4. Provider/Model 카드의 액션 버튼은 스타일 대상이지만 기능 조건은 고정 대상이다.

따라서 후속 스타일 치환에서는 **버튼 wrapper만 교체**하고, 버튼 존재 여부 / enabled 조건 / onClick / ProviderType 분기는 그대로 보존해야 한다.

---

## 4. ProviderManagementScreen 카드 역할 목록

| 카드/영역 | 역할 | 표시 조건 | 보존 조건 |
|---|---|---|---|
| Provider 관리 상단 카드 | Provider 설정 화면의 제목, 설명, Provider 추가 버튼 제공 | 항상 표시 | Provider 추가 버튼 유지 |
| Provider 목록 정리 카드 | 검색, ProviderType 필터, 활성/Key/Base URL 필터 제공 | 항상 표시 | 필터 state와 onChange 유지 |
| Provider 없음 카드 | 등록된 Provider가 없음을 안내 | `providers.isEmpty()` | 추가 버튼은 상단 카드에 유지 |
| Provider 필터 결과 없음 카드 | 검색/필터 조건에 맞는 Provider가 없음을 안내 | `providers`는 있으나 `filteredProviders.isEmpty()` | 필터만 해제하면 목록 복귀해야 함 |
| Provider 개별 카드 | Provider 단위 정보, 활성 Switch, 모델 목록 조회, 수정, 삭제 제공 | `filteredProviders.forEach` | 기능 버튼과 Switch 유지 |
| ProviderType 안내 카드 | Provider 추가/수정 Dialog 안에서 선택한 ProviderType 설명 | Dialog 내부에서 표시 | ProviderType별 안내/placeholder 유지 |
| 모델 목록 Dialog | Provider에서 조회한 모델을 Model Profile로 추가 | 모델 목록 불러오기 실행 후 표시 | 중복 추가 방지 유지 |
| 삭제 확인 Dialog | Provider 삭제 전 확인 | 삭제 버튼 클릭 시 표시 | Provider와 API Key 삭제 흐름 유지 |

---

## 5. ProviderManagementScreen 버튼/액션 역할 목록

| 액션명 | 표시 위치 | 표시 조건 | enabled 조건 | onClick 역할 | 관련 상태값 | 보존 조건 |
|---|---|---|---|---|---|---|
| Provider 추가 | Provider 관리 상단 카드 | 항상 표시 | 항상 enabled | `isAddDialogOpen = true` | `isAddDialogOpen` | 버튼 삭제 금지 |
| ProviderType 필터 | Provider 목록 정리 카드 | 항상 표시 | 항상 enabled | `selectedProviderTypeFilter` 갱신 | `selectedProviderTypeFilter` | 전체/GOOGLE/OPENAI/OPENAI_COMPATIBLE/NVIDIA/LOCAL/CUSTOM 유지 |
| 활성 Provider만 | Provider 목록 정리 카드 | 항상 표시 | 항상 enabled | `showOnlyEnabledProviders` 갱신 | `showOnlyEnabledProviders` | Checkbox 조건 유지 |
| API Key 설정됨만 | Provider 목록 정리 카드 | 항상 표시 | 항상 enabled | `showOnlyApiKeyConfigured` 갱신 | `showOnlyApiKeyConfigured` | API Key 원문 표시 금지 |
| Base URL 미설정만 | Provider 목록 정리 카드 | 항상 표시 | 항상 enabled | `showOnlyMissingBaseUrl` 갱신 | `showOnlyMissingBaseUrl` | Base URL blank 기준 유지 |
| Provider 활성/비활성 Switch | Provider 개별 카드 | 항상 표시 | 항상 enabled | `store.updateProviderProfile(provider.copy(isEnabled = enabled, updatedAt = now))` 후 reload | `provider.isEnabled` | 저장 흐름 변경 금지 |
| 모델 목록 불러오기 | Provider 개별 카드 | `isModelListFetchVisible(provider.providerType)` | `isModelListFetchEnabled(provider, hasApiKey)` | `openProviderModelList(provider)` | `modelListProvider`, `modelListKind`, loading/error/message state | ProviderType 분기 변경 금지 |
| Provider 수정 | Provider 개별 카드 | 항상 표시 | 항상 enabled | `editingProvider = provider` | `editingProvider` | Dialog 진입 유지 |
| Provider 삭제 | Provider 개별 카드 | 항상 표시 | 항상 enabled | `providerToDelete = provider` | `providerToDelete` | 삭제 확인 Dialog 유지 |
| Provider 저장 | Provider 추가/수정 Dialog | Dialog 표시 시 | `displayName.trim().isNotEmpty()` | `onSave(ProviderProfile(...), apiKeyInput, shouldDeleteApiKey)` | `displayName`, `providerType`, `baseUrl`, `apiKeyInput`, `modelListEndpoint`, `isEnabled` | canSave 조건 변경 금지 |
| 저장된 API Key 삭제 | Provider 수정 Dialog | 기존 `apiKeyAlias`가 있을 때 | Checkbox | `shouldDeleteApiKey` 갱신, 체크 시 `apiKeyInput = ""` | `shouldDeleteApiKey`, `apiKeyInput` | API Key 삭제 흐름 유지 |
| Provider 삭제 확인 | 삭제 확인 Dialog | `providerToDelete != null` | 항상 enabled | `store.deleteProviderProfile(providerId)`, `store.deleteProviderApiKey(providerId)`, reload | `providerToDelete` | API Key 동시 삭제 유지 |

---

## 6. ProviderType별 모델 목록 조회 정책

| ProviderType | 버튼 표시 | enabled 조건 | 조회 함수 | Dialog | 정책 |
|---|---:|---|---|---|---|
| `GOOGLE` | 표시 | API Key 설정됨 | `openGoogleModelList(provider)` | `GoogleModelListDialog` | Google model list fetcher 사용 |
| `OPENAI` | 표시 | API Key 설정됨 | `openOpenAIModelList(provider)` | `OpenAIModelListDialog` | OpenAI 공식 `/models` 조회 |
| `OPENAI_COMPATIBLE` | 표시 | API Key 설정됨 | `openOpenAiCompatibleModelList(provider)` | `OpenAiCompatibleModelListDialog` | OpenAI-compatible `/models` 조회 |
| `LOCAL` | 표시 | `baseUrl` 또는 `modelListEndpoint` 존재 | `openOpenAiCompatibleModelList(provider)` | `OpenAiCompatibleModelListDialog` | API Key 선택, local `/models` 조회 가능 |
| `CUSTOM` | 표시 | `baseUrl` 또는 `modelListEndpoint` 존재 | `openOpenAiCompatibleModelList(provider)` | `OpenAiCompatibleModelListDialog` | API Key 선택, custom `/models` 조회 가능 |
| `NVIDIA` | 미표시 | false | 현재 UI에서 호출 경로 없음 | 정책상 미지원 | 대화모드 모델 목록 조회 미지원 유지 |

보존 규칙:

- `isModelListFetchVisible`과 `isModelListFetchEnabled`는 스타일 치환 중 변경하지 않는다.
- `openProviderModelList`의 ProviderType `when` 분기는 exhaustive 상태를 유지한다.
- `NVIDIA`는 현재 대화모드 모델 목록 조회 버튼을 표시하지 않는 정책이다.
- 후속 단계에서 NVIDIA 안내를 노출하려면 버튼 표시 정책 변경이므로 별도 설계가 필요하다.

---

## 7. Provider Dialog / Model List Dialog 액션 목록

### 7.1 Provider 추가/수정 Dialog

| 항목 | 역할 | 조건 |
|---|---|---|
| Provider 이름 | `displayName` 입력 | 저장 필수 |
| Provider Type RadioButton | `providerType` 선택 | 모든 `ProviderType.values()` 표시 |
| ProviderTypeGuideCard | 선택 타입 안내 | 항상 표시 |
| Base URL | `baseUrl` 입력 | OPENAI_COMPATIBLE/LOCAL/CUSTOM은 권장 경고 표시 |
| API Key | Password field | ProviderType별 필수/선택 안내 |
| 저장된 API Key 삭제 | 기존 Key 삭제 | 기존 `apiKeyAlias` 있을 때만 표시 |
| Model List Endpoint | 모델 목록 조회 endpoint override | optional |
| 활성화 Switch | `isEnabled` 설정 | 항상 표시 |
| 저장 | ProviderProfile 저장 | `displayName` non-blank일 때 enabled |
| 취소 | Dialog 닫기 | 항상 enabled |

### 7.2 Google Model List Dialog

| 액션 | 조건 | 역할 |
|---|---|---|
| 모델 목록 표시 | fetch 성공 후 | Google 모델 후보 표시 |
| 추가 / 이미 추가됨 | `existingModels`에 같은 `providerId + rawModelName` 존재 여부 | 중복이면 disabled, 아니면 `addGoogleModelProfile` |
| 닫기 | 항상 | Dialog 닫기 |

### 7.3 OpenAI Model List Dialog

| 액션 | 조건 | 역할 |
|---|---|---|
| 모델 목록 표시 | fetch 성공 후 | OpenAI 모델 후보 표시 |
| Model Profile 추가 / 이미 추가됨 | `providerId + rawModelName` 중복 여부 | 중복이면 disabled, 아니면 `addOpenAIModelProfile` |
| 닫기 | 항상 | Dialog 닫기 |

### 7.4 OpenAI-compatible Model List Dialog

| 액션 | 조건 | 역할 |
|---|---|---|
| 모델 목록 표시 | fetch 성공 후 | OpenAI-compatible/LOCAL/CUSTOM 모델 후보 표시 |
| Model Profile 추가 / 이미 추가됨 | `providerId + rawModelName` 중복 여부 | 중복이면 disabled, 아니면 `addOpenAiCompatibleModelProfile` |
| 닫기 | 항상 | Dialog 닫기 |

---

## 8. ModelManagementScreen 카드 역할 목록

| 카드/영역 | 역할 | 표시 조건 | 보존 조건 |
|---|---|---|---|
| Model 관리 상단 카드 | 모델 관리 제목, 설명, 모델 추가 버튼 제공 | 항상 표시 | Provider 없을 때 추가 버튼 disabled 유지 |
| 기본 대화 모델 경고 카드 | 기본 모델 없음 안내 | `models.none { it.enabled && it.isDefaultForConversation }` | 기본 지정 유도 유지 |
| Model 목록 필터 카드 | 검색, ProviderType 필터, 즐겨찾기/활성 필터, 요약 표시 | 항상 표시 | filter state와 onChange 유지 |
| Model 없음 카드 | 등록된 모델 없음 안내 | `models.isEmpty()` | 상단 추가 버튼 유지 |
| Model 필터 결과 없음 카드 | 필터 결과 없음 안내 | models는 있으나 filteredModels 비어 있음 | 필터 해제 가능해야 함 |
| Model 개별 카드 | Model 정보, 활성 Switch, 즐겨찾기, 기본 지정, 수정, 삭제 제공 | `filteredModels.forEach` | 기능 버튼 유지 |
| Model Raw Name Guide Card | ProviderType별 rawModelName 안내 | Model 추가/수정 Dialog에서 Provider 선택 시 표시 | ProviderType별 label/placeholder 유지 |
| Model 추가/수정 Dialog | Model Profile 생성/수정 | 추가/수정 버튼 클릭 시 표시 | canSave와 저장 필드 유지 |
| Model 삭제 확인 Dialog | Model 삭제 전 확인 | 삭제 버튼 클릭 시 표시 | 기본 모델 삭제 시 기본 없음 상태 유지 |

---

## 9. ModelManagementScreen 버튼/액션 역할 목록

| 액션명 | 표시 위치 | 표시 조건 | enabled 조건 | onClick 역할 | 관련 상태값 | 보존 조건 |
|---|---|---|---|---|---|---|
| 모델 추가 | Model 관리 상단 카드 | 항상 표시 | `providers.isNotEmpty()` | `isAddDialogOpen = true` | `isAddDialogOpen` | Provider 없을 때 disabled 유지 |
| ProviderType 필터 | Model 목록 필터 카드 | 항상 표시 | 항상 enabled | `selectedProviderTypeFilter` 갱신 | `selectedProviderTypeFilter` | ALL, PROVIDER_MISSING, ProviderType 전체 유지 |
| 즐겨찾기만 | Model 목록 필터 카드 | 항상 표시 | 항상 enabled | `favoriteOnly` 갱신 | `favoriteOnly` | Checkbox 유지 |
| 활성 모델만 | Model 목록 필터 카드 | 항상 표시 | 항상 enabled | `enabledOnly` 갱신 | `enabledOnly` | Checkbox 유지 |
| Model 활성/비활성 Switch | Model 개별 카드 | 항상 표시 | 항상 enabled | `saveModelWithDefaultRule(model.copy(enabled = enabled))` | `model.enabled` | 저장 흐름 변경 금지 |
| 즐겨찾기 추가/해제 | Model 개별 카드 | 항상 표시 | 항상 enabled | `saveModelWithDefaultRule(model.copy(isFavorite = !model.isFavorite))` | `model.isFavorite` | 버튼 문구와 토글 의미 유지 |
| 기본 지정 | Model 개별 카드 | 항상 표시 | `!model.isDefaultForConversation` | `setDefaultModel(model)` | `model.isDefaultForConversation` | 기본 모델 단일화 유지 |
| Model 수정 | Model 개별 카드 | 항상 표시 | 항상 enabled | `editingModel = model` | `editingModel` | Dialog 진입 유지 |
| Model 삭제 | Model 개별 카드 | 항상 표시 | 항상 enabled | `modelToDelete = model` | `modelToDelete` | 삭제 확인 Dialog 유지 |
| Model 저장 | Model 추가/수정 Dialog | Dialog 표시 시 | `displayName`, `rawModelName`, `providerId` 모두 non-blank | `onSave(ConversationModelProfile(...))` | model fields, capabilities, toggles | canSave 조건 변경 금지 |
| Model 삭제 확인 | 삭제 확인 Dialog | `modelToDelete != null` | 항상 enabled | `store.deleteModelProfile(model.modelId)`, reload | `modelToDelete` | 자동 default 재지정 없음 유지 |

---

## 10. Model 기본 지정 / 즐겨찾기 / 삭제 정책

### 10.1 기본 지정

- `setDefaultModel(model)`은 전체 모델을 순회하면서 선택된 `modelId`만 `isDefaultForConversation = true`로 만든다.
- 나머지 모델은 `isDefaultForConversation = false`가 된다.
- 기본 모델 카드의 `기본 지정` 버튼은 표시되지만 disabled 상태다.
- 기본 모델이 없으면 경고 카드가 표시된다.

### 10.2 즐겨찾기

- 즐겨찾기는 `model.isFavorite`만 토글한다.
- 즐겨찾기 변경은 기본 모델 지정 상태를 직접 변경하지 않는다.
- 카드 제목 앞의 `★` 표시는 `model.isFavorite`에 따른다.

### 10.3 삭제

- 삭제 버튼은 삭제 확인 Dialog를 연다.
- 삭제 확인 시 `store.deleteModelProfile(model.modelId)`를 호출한다.
- 기본 모델을 삭제하면 자동으로 다른 모델을 기본 지정하지 않는다.
- 이 경우 기본 대화 모델 경고 카드가 표시된다.

---

## 11. 스타일 치환 시 절대 보존해야 할 조건

1. 스타일 치환은 기능 버튼의 존재 여부를 바꾸면 안 된다.
2. 스타일 치환은 `onClick` 람다를 바꾸면 안 된다.
3. 스타일 치환은 `enabled` 조건을 바꾸면 안 된다.
4. 스타일 치환은 `visible` 조건을 바꾸면 안 된다.
5. 스타일 치환은 ProviderType별 `when` 분기를 바꾸면 안 된다.
6. 버튼 문구를 줄이더라도 기능 의미를 바꾸면 안 된다.
7. `SettingsUiComponents` 적용은 wrapper 교체 수준으로 제한한다.
8. 기능 버튼은 모바일 폭에서도 사라지면 안 된다.
9. 모델 목록 불러오기 버튼은 ProviderType 정책에 따라 유지하거나 명확한 미지원 안내를 별도 설계해야 한다.
10. Provider/Model 저장 함수 호출 경로는 변경하지 않는다.
11. API Key 원문 표시 금지 원칙을 유지한다.
12. Dialog의 저장 가능 조건은 변경하지 않는다.

---

## 12. 후속 스타일 치환 단계 분리 계획

권장 순서:

1. `ProviderManagementScreen.kt` 스타일 치환 1차
2. `ProviderManagementScreen.kt` 기능 회귀 검증
3. `ModelManagementScreen.kt` 스타일 치환 1차
4. `ModelManagementScreen.kt` 기능 회귀 검증
5. `AppRoot.kt` / Provider·Model 설정 1페이지 스타일 정합성 보정
6. 고급 하네스 화면 스타일 통일
7. Storage/Dialog 계열 스타일 통일 검토

주의:

- `ProviderManagementScreen.kt`와 `ModelManagementScreen.kt`를 한 턴에 동시에 대규모 치환하지 않는다.
- 한 파일 단위로 치환하고 빌드/기능 확인 후 다음 파일로 넘어간다.
- ProviderType 분기가 있는 화면은 단순 시각 확인만으로 통과시키지 않는다.

---

## 13. 테스트 체크리스트

### 13.1 ProviderManagementScreen

- Provider 추가 버튼 표시 및 Dialog 진입
- Provider 수정 버튼 표시 및 Dialog 진입
- Provider 삭제 버튼 표시 및 삭제 확인 Dialog 진입
- Provider 활성/비활성 Switch 저장
- ProviderType 필터: 전체, GOOGLE, OPENAI, OPENAI_COMPATIBLE, NVIDIA, LOCAL, CUSTOM
- GOOGLE 모델 목록 불러오기 버튼 표시 및 API Key 조건 확인
- OPENAI 모델 목록 불러오기 버튼 표시 및 API Key 조건 확인
- OPENAI_COMPATIBLE 모델 목록 불러오기 버튼 표시 및 API Key 조건 확인
- LOCAL 모델 목록 불러오기 버튼 표시 및 baseUrl/modelListEndpoint 조건 확인
- CUSTOM 모델 목록 불러오기 버튼 표시 및 baseUrl/modelListEndpoint 조건 확인
- NVIDIA 모델 목록 조회 미지원 정책 유지
- 모델 목록 Dialog에서 중복 모델은 disabled 표시
- 모델 목록 Dialog에서 신규 모델은 Model Profile 추가 가능
- API Key 원문 미표시

### 13.2 ModelManagementScreen

- Model 추가 버튼 표시, Provider 없으면 disabled
- Model 수정 버튼 표시 및 Dialog 진입
- Model 삭제 버튼 표시 및 삭제 확인 Dialog 진입
- Model 활성/비활성 Switch 저장
- 즐겨찾기 추가/해제 버튼 표시 및 토글
- 기본 지정 버튼 표시 및 단일 기본 모델 규칙 유지
- 기본 모델이 없을 때 경고 카드 표시
- ProviderType 필터와 Provider missing 필터 유지
- 검색 / 즐겨찾기만 / 활성 모델만 필터 유지
- Model 추가/수정 Dialog의 저장 조건 유지
- LOCAL Provider 선택 시 신규 모델 local defaults 적용

### 13.3 연결 금지 확인

- `ConversationEngine.kt` 미변경
- `ConversationViewModel.kt` 미변경
- Provider Adapter 파일 미변경
- RAG/Web Search/Tool 실행 계층 미변경
- Provider/Model 저장 schema 미변경
- Worker/Scenario 실행 미연결
- 작업모드 파일 미변경

---

## 14. 완료 기준

- Provider/Model 카드·버튼 역할 목록 정리 완료
- 표시 조건 / enabled 조건 / onClick 역할 정리 완료
- ProviderType별 모델 목록 조회 정책 정리 완료
- 후속 스타일 치환 안전 규칙 정리 완료
- 실제 Kotlin 기능 변경 없음
- 스타일 치환 재개 없음
- 빌드 영향 없음
