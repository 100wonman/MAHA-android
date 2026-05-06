# MAHA_GRAPHICS_SETTINGS_OPTIONS_v1

## 1. 목적

이 문서는 MAHA 대화모드 설정 UX 안정화 이후, 후속 단계에서 추가할 수 있는 **일반설정 → 그래픽 설정**의 옵션 목록과 적용 정책을 정리한다.

목표는 다음과 같다.

- 최근 안정화된 대화모드 설정 UI의 카드, 버튼, chip, Dialog, Switch, 중첩카드, back stack 정책을 유지한다.
- `SettingsStyleTokens` / `SettingsUiComponents` 기반의 정적 스타일 체계를 후속 동적 그래픽 설정으로 확장할 수 있게 한다.
- 대화모드 설정 화면과 대화방 화면이 같은 그래픽 정책을 공유하되, 화면 성격 차이를 반영할 수 있도록 token layer를 분리한다.
- 실제 구현 전 옵션 범위, 기본값, 초기화 정책, 적용 범위, 제외 범위를 명확히 한다.

이번 문서는 설계 기록이며, Kotlin 코드나 저장 schema를 변경하지 않는다.

---

## 2. 설계 전제

현재 MAHA의 대화모드 설정 UI는 다음 안정화 기준을 가진다.

- 기본은 다크테마다.
- 큰 카드 배경은 단일 dark surface 계열을 유지한다.
- 선택, 경고, 위험, 성공, 정보 상태는 큰 면 색상이 아니라 `border`, `chip`, `text` 중심으로 표현한다.
- 중첩 카드 배경 누적은 최소화한다.
- 보조 정보는 `InfoRow`, `Divider`, `ChipRow`, `InlineNotice` 중심으로 표현한다.
- Dialog / Popup / Confirm Dialog는 불투명한 dark surface와 scrim을 사용한다.
- Switch / Radio / FilterChip / 작은 선택 컨트롤도 다크테마 token을 사용한다.
- 설정 root, 하위 설정 화면, Dialog, Android 시스템 뒤로가기, settings back stack 동작은 유지한다.

그래픽 설정은 이 안정화 기준을 깨지 않고, 사용자가 일부 시각적 선호를 조정할 수 있게 하는 후속 확장으로 설계한다.

---

## 3. 그래픽 설정 위치

후속 UI 위치 후보는 다음과 같다.

```text
일반설정
└─ 그래픽 설정
   ├─ 테마
   ├─ 색상
   ├─ 카드 / 버튼 모양
   ├─ 화면 밀도
   ├─ 대화방 표시
   ├─ Dialog / Popup
   ├─ 접근성
   └─ 초기화
```

주의:

- 이번 문서 단계에서는 메뉴를 실제 구현하지 않는다.
- `일반설정 → 그래픽 설정`은 후속 단계의 UI 후보일 뿐이다.
- 기존 대화모드 설정 UI 안정화 값을 기본값으로 유지한다.

---

## 4. 그래픽 설정 v1 옵션 목록

v1 그래픽 설정은 다음 그룹으로 나눈다.

1. Theme / Color
2. Shape / Radius
3. Border / Surface
4. Density / Spacing
5. Typography / Font Size
6. Conversation Room 전용 옵션
7. Dialog / Popup / Switch
8. 접근성
9. 초기화

처음부터 모든 옵션을 구현할 필요는 없다. v1 구현에서는 회귀 위험이 낮은 옵션부터 단계적으로 연결한다.

---

## 5. Theme / Color 옵션

### 5.1 Theme 옵션

후보:

- 시스템 기본값 사용
- 다크 테마
- 라이트 테마
- 고대비 다크
- 사용자 지정 테마 후보

v1 기본값:

- `dark`

주의:

- 현재 안정화된 다크테마를 기본값으로 유지한다.
- 라이트 테마는 별도 검증 범위가 크므로 초기 구현에서는 비활성 후보로 둘 수 있다.
- 고대비 다크는 접근성 옵션과 연동할 수 있다.

### 5.2 Color 옵션

후보:

- 강조 색상 preset
- 사용자 지정 accent color 후보
- 선택 상태 색상
- 정보 / 성공 / 경고 / 위험 색상
- chip tone 강도
- 링크 / citation 색상 후보

정책:

- 큰 카드와 큰 버튼의 배경은 상태색으로 칠하지 않는다.
- accent color는 주로 action text, selected border, small chip에만 적용한다.
- danger / warning / success / info 색상은 작은 상태 표시와 border 중심으로 사용한다.
- 대화방 링크 / citation 색상은 settings action color와 동일 계열을 사용할 수 있지만 별도 token으로 분리한다.

---

## 6. Shape / Radius 옵션

후보:

- 카드 radius
- 버튼 radius
- 입력창 radius
- Dialog radius
- chip radius
- nested panel radius

v1 기본값:

- 현재 `SettingsStyleTokens` 기준값 유지

정책:

- 카드와 Dialog는 같은 계열 radius를 사용하되 Dialog는 조금 더 명확한 modal surface로 보일 수 있다.
- 버튼은 카드보다 작은 radius를 유지할 수 있다.
- chip은 작은 상태 표시이므로 pill 형태를 유지해도 된다.

---

## 7. Border / Surface 옵션

### 7.1 Border 옵션

후보:

- 카드 border 강도
- 버튼 border 강도
- 선택 border 강도
- danger / warning border 강도
- nested panel border 강도

정책:

- border 강도는 배경색보다 우선되는 구분 수단이다.
- 선택 상태는 면 배경이 아니라 selected border + selected text 중심으로 표현한다.
- danger / warning 상태도 큰 면 배경이 아니라 border / chip / text 중심으로 표현한다.

### 7.2 Surface 옵션

후보:

- 기본 surface contrast
- card surface contrast
- nested panel surface contrast
- dialog surface opacity
- scrim opacity
- 다크테마 회색감 강도

v1 기본값:

- 현재 안정화된 `cardBackground`, `nestedPanelBackground`, `dialogBackground`, `dialogScrimColor` 기준값 유지

정책:

- 카드 속 카드의 회색 면 누적은 피한다.
- nested panel은 카드보다 더 밝은 회색 면으로 뜨지 않아야 한다.
- Dialog surface는 충분히 불투명해야 한다.
- scrim은 배경 카드가 Dialog 뒤로 비치지 않도록 충분히 어둡게 처리한다.

---

## 8. Density / Spacing 옵션

후보:

- compact
- normal
- comfortable
- 카드 padding
- row spacing
- section spacing
- chip spacing
- 입력창 높이
- Dialog 내부 spacing
- 대화방 message spacing

v1 기본값:

- `normal`

정책:

- phone에서는 vertical scroll 부담을 줄이기 위해 compact density가 유용하다.
- tablet에서는 comfortable density를 허용할 수 있다.
- 설정 화면과 대화방 화면의 density는 분리할 수 있다.
- Provider/Model/Worker/Scenario처럼 카드가 많은 화면은 과도한 comfortable spacing을 피한다.

---

## 9. Typography / Font Size 옵션

후보:

- 전체 font scale
- 설정 화면 글자 크기
- 대화방 글자 크기
- 코드블록 글자 크기
- 설명문 / muted text 크기
- title 크기
- chip text 크기
- 줄간격

v1 기본값:

- `fontScale = 1.0`
- 설정 화면: 현재 안정화 기준 유지
- 대화방: 별도 확인 후 token화

정책:

- Android 시스템 font scale과 충돌하지 않게 한다.
- 사용자 지정 font scale은 시스템 font scale 위에 과도하게 곱하지 않는다.
- code block은 대화방 전용 token에서 관리하는 것이 안전하다.

---

## 10. Conversation Room 전용 옵션

대화방은 설정 화면과 성격이 다르므로 별도 token layer가 필요하다.

후보:

- 사용자 메시지 말풍선 스타일
- assistant 메시지 카드 스타일
- system / worker 메시지 표시 방식
- structured block 카드 스타일
- code block 스타일
- JSON / table block 스타일
- 실행정보 접힘 패널 밀도
- RAG context 표시 밀도
- Web Search citation 표시 밀도
- message spacing
- timestamp 표시 여부 후보
- avatar / role label 표시 여부 후보
- markdown body font size

정책:

- 대화방 UI는 `SettingsStyleTokens`를 그대로 복사하지 않는다.
- `ConversationStyleTokens` 후보를 별도로 두되, 기본 accent / surface / typography 값은 graphics settings에서 공유할 수 있다.
- 대화방 메시지 카드와 설정 카드의 시각 언어는 연결하되, 대화방 가독성을 우선한다.

---

## 11. Dialog / Popup / Switch 옵션

후보:

- Dialog surface opacity
- Dialog scrim 강도
- Dialog border 강도
- Dialog radius
- Popup surface contrast
- Switch 색상 강도
- Switch checked 강조 강도
- Switch unchecked 대비
- Radio / FilterChip 선택 표시 강도

정책:

- Dialog는 항상 충분히 불투명해야 한다.
- Dialog 뒤 설정 카드가 비치지 않아야 한다.
- 위험 Dialog도 배경색을 빨갛게 칠하지 않고 danger border / danger text / danger button으로 구분한다.
- Switch는 Material 기본 밝은 색상이 튀지 않도록 token 기반 색상을 사용한다.

---

## 12. 접근성 옵션

후보:

- 고대비 모드
- 큰 글자 모드
- 애니메이션 최소화
- 터치 대상 크기 확대
- 색상 의존도 낮추기
- 테두리 강조 모드
- chip text 강화
- reduced transparency
- Dialog scrim 강화

정책:

- 접근성 옵션은 일반 그래픽 옵션보다 우선 적용될 수 있다.
- 색상 의존도 낮추기 모드에서는 상태를 color만으로 구분하지 않고 label / icon / border를 함께 사용한다.
- reduced transparency 옵션은 Dialog, nested panel, chip background alpha를 조정할 수 있다.

---

## 13. SettingsStyleTokens 연결 방침

현재 `SettingsStyleTokens`는 정적 token이다.

후속 구현에서는 다음 구조를 고려한다.

```text
GraphicsSettings
→ GraphicsThemeResolver
→ SettingsStyleTokens runtime instance
→ SettingsUiComponents
→ 대화모드 설정 화면
```

원칙:

- 화면 파일은 개별 색상값을 직접 들고 있으면 안 된다.
- `SettingsUiComponents`는 token을 참조한다.
- `SettingsStyleTokens`는 정적 object에서 runtime token provider로 확장될 수 있다.
- 초기 구현에서는 기존 정적 token을 fallback default로 유지한다.
- 그래픽 설정이 없어도 현재 안정화된 UI가 그대로 보여야 한다.

---

## 14. 향후 GraphicsSettingsStore 후보

후보 파일:

- `GraphicsSettingsModels.kt`
- `GraphicsSettingsStore.kt`
- `GraphicsThemeResolver.kt`
- `SettingsStyleTokens.kt`
- `ConversationStyleTokens.kt`
- `SettingsUiComponents.kt`
- `ConversationUiComponents.kt`

후보 data model:

```kotlin
data class GraphicsSettings(
    val themeMode: GraphicsThemeMode,
    val accentPreset: GraphicsAccentPreset,
    val density: GraphicsDensity,
    val fontScale: Float,
    val cardRadiusLevel: GraphicsRadiusLevel,
    val buttonRadiusLevel: GraphicsRadiusLevel,
    val borderIntensity: GraphicsIntensity,
    val surfaceContrast: GraphicsIntensity,
    val dialogOpacity: GraphicsIntensity,
    val scrimIntensity: GraphicsIntensity,
    val chipToneIntensity: GraphicsIntensity,
    val conversationDensity: GraphicsDensity,
    val highContrast: Boolean,
    val reduceTransparency: Boolean,
    val reduceMotion: Boolean,
    val largeTouchTargets: Boolean
)
```

저장 위치 후보:

```text
MAHA/settings/graphics_settings.json
```

주의:

- 실제 저장 schema 추가는 후속 단계다.
- provider_api_keys.json 등 민감정보와 무관해야 한다.
- 설정 백업 대상 포함 여부는 별도 검토한다.

---

## 15. 기본값 / 초기화 정책

기본값 후보:

- 기본 theme: `dark`
- 기본 density: `normal`
- 기본 card radius: 현재 `SettingsStyleTokens` 기준
- 기본 button radius: 현재 `SettingsStyleTokens` 기준
- 기본 border intensity: 현재 안정화 값
- 기본 dialog opacity: 현재 보정된 불투명 dark surface
- 기본 scrim: 현재 보정된 scrim
- 기본 font scale: `1.0`
- 기본 conversation density: `normal`
- 기본 high contrast: `false`
- 기본 reduce transparency: `false`

초기화 후보:

- 전체 그래픽 설정 초기화
- 색상만 초기화
- 카드 / 버튼 모양만 초기화
- 대화방 표시만 초기화
- 접근성 옵션만 초기화

정책:

- 사용자가 조정하지 않아도 현재 안정화된 다크테마가 그대로 유지되어야 한다.
- 그래픽 설정 도입으로 기존 화면이 갑자기 바뀌면 안 된다.
- 초기화는 현재 안정화된 token 값으로 돌아가야 한다.

---

## 16. 적용 범위와 제외 범위

### 16.1 초기 적용 후보

- 대화모드 설정 화면
- Provider / Model / API 설정
- Memory / RAG / Storage 설정
- 고급 하네스 설정
- Dialog / Popup / Switch
- 대화방 메시지 카드
- 대화방 입력창
- 실행정보 패널
- RAG / Web Search 표시 영역

### 16.2 초기 제외 후보

- 작업모드 실행 UI
- AgentList / AgentDetail / RunDetail
- ModelCatalog
- 앱 전체 통합 테마

제외 이유:

- 작업모드까지 동시에 확장하면 회귀 위험이 크다.
- 먼저 대화모드 계열에서 안정화한 뒤 앱 전체로 확장한다.
- 작업모드는 별도 UI 계층과 실행 흐름을 가진다.

---

## 17. 후속 구현 단계

권장 순서:

1. 그래픽 설정 옵션 설계 문서 작성
2. 대화방 UX/UI 현황 점검
3. `ConversationRoomScreen` 스타일 token 후보 정리
4. `GraphicsSettingsModels` / `GraphicsSettingsStore` 설계
5. Graphics 설정 UI skeleton 작성
6. `SettingsStyleTokens` 동적 token 연결
7. `ConversationStyleTokens` 후보 연결
8. 사용자 설정 저장 / 초기화 구현
9. phone / tablet / landscape 검증
10. 이후 작업모드 확장 여부 검토

주의:

- 대화방 token화와 Settings token 동적화는 한 번에 진행하지 않는 것이 안전하다.
- 저장 schema 추가 전 백업/복원 정책을 먼저 정리해야 한다.
- 작업모드 확장은 대화모드 안정화 후 별도 지시문으로 분리한다.

---

## 18. 마이그레이션 전 안정 지점

현재 안정 지점:

- 대화모드 설정 카드맵 정리 완료
- Provider / Model / API 1페이지 / 2페이지 구조 정리 완료
- ProviderManagementScreen / ModelManagementScreen 스타일 1차 보정 완료
- 모델 목록 Dialog 검색창 복구 완료
- SettingsStyleTokens / SettingsUiComponents 기반 카드 / 버튼 / chip 정리 완료
- Dialog / Popup / Switch 다크테마 보정 완료
- Dialog opacity / scrim 보정 완료
- Memory / RAG / Storage 설정 placeholder 제거 완료
- StorageManagementScreen 스타일 보정 완료
- 고급 하네스 설정 스타일 1차 보정 완료
- WorkerProfileEditScreen Capability / OutputPolicy 직접 선택 UI 보정 완료
- MainMenuScreen 작업모드 / 대화모드 카드 스타일 정리 완료

마이그레이션 전 금지 상태:

- ConversationEngine 연결 금지 유지
- Worker / Scenario 실제 실행 연결 금지 유지
- Provider 호출 로직 변경 금지 유지
- RAG / Web Search / Tool 실행 연결 금지 유지
- 저장 schema 변경 금지 유지
- 작업모드 수정 금지 유지

---

## 19. 다음 단계 진입 기준

그래픽 설정 실제 구현으로 넘어가기 전 다음 조건을 만족해야 한다.

- 현재 대화모드 설정 UX 안정화 상태가 빌드 성공 상태여야 한다.
- Provider / Model / Storage / Worker / Scenario / Capability Resolver 기능 회귀가 없어야 한다.
- Dialog / Switch / back stack / Drawer 닫기 동작이 확인되어야 한다.
- `MAHA_CHAT_SETTINGS_UX_STABILIZATION_v1.md`와 본 문서가 함께 기준 문서로 정리되어 있어야 한다.
- Graphics 설정 구현 지시문은 문서 설계와 별도로 작성되어야 한다.
