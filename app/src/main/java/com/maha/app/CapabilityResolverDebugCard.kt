package com.maha.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * CapabilityResolverDebugCard
 *
 * 개발/진단용 UI입니다.
 * 실제 대화 전송, Provider 호출, RAG, Web Search, Tool 실행과 연결하지 않습니다.
 */
@Composable
fun CapabilityResolverDebugCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var userInput by rememberSaveable { mutableStateOf("오늘 서울 날씨를 검색해서 알려줘.") }
    var plan by remember { mutableStateOf<OrchestratorPlan?>(null) }
    var resultMode by rememberSaveable { mutableStateOf("NONE") }
    var scenarios by remember { mutableStateOf<List<ConversationScenarioProfile>>(emptyList()) }
    var workerProfiles by remember { mutableStateOf<List<ConversationWorkerProfile>>(emptyList()) }
    var selectedScenarioIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(context) {
        WorkerProfileStore.initialize(context)
        workerProfiles = WorkerProfileStore.loadWorkerProfiles(forceReload = true).workerProfiles
        scenarios = WorkerProfileStore.loadConversationScenarios(forceReload = true).scenarios
        if (selectedScenarioIndex > scenarios.size) {
            selectedScenarioIndex = 0
        }
    }

    val selectedScenario = scenarios.getOrNull(selectedScenarioIndex - 1)

    fun buildCurrentPlan(useScenario: Boolean): OrchestratorPlan {
        val scenario = selectedScenario
        return if (useScenario && scenario != null) {
            resultMode = "SCENARIO_PREVIEW"
            CapabilityResolver.buildPlanForScenario(
                userInput = userInput,
                scenario = scenario,
                workerProfiles = workerProfiles
            )
        } else {
            resultMode = "BASIC_DIAGNOSTIC"
            CapabilityResolver.buildPlan(userInput)
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Capability Resolver 진단",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.titleTextColor
            )

            Text(
                text = "이 화면은 실제 대화 실행이 아닙니다. Provider 호출, Worker 실행, RAG 검색, Web Search, Tool 실행을 하지 않고 Orchestrator가 어떤 capability와 실행 방식을 추정할지 미리 보여줍니다.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.mutedTextColor
            )

            CapabilityDebugNotice()

            CapabilityScenarioSelector(
                scenarios = scenarios,
                selectedScenarioIndex = selectedScenarioIndex,
                onSelectedScenarioIndexChange = { index ->
                    selectedScenarioIndex = index.coerceIn(0, scenarios.size)
                    plan = null
                    resultMode = "NONE"
                }
            )

            OutlinedTextField(
                value = userInput,
                onValueChange = { value -> userInput = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("사용자 요청 예시 입력") },
                minLines = 3,
                maxLines = 6
            )

            CapabilityResolverExampleButtons(
                onExampleSelected = { example ->
                    userInput = example
                    plan = if (selectedScenario != null) {
                        resultMode = "SCENARIO_PREVIEW"
                        CapabilityResolver.buildPlanForScenario(
                            userInput = example,
                            scenario = selectedScenario,
                            workerProfiles = workerProfiles
                        )
                    } else {
                        resultMode = "BASIC_DIAGNOSTIC"
                        CapabilityResolver.buildPlan(example)
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPrimaryButton(
                    text = "기본 진단",
                    onClick = {
                        plan = buildCurrentPlan(useScenario = false)
                    },
                    modifier = Modifier.weight(1f)
                )

                SettingsPrimaryButton(
                    text = "Scenario Preview",
                    enabled = selectedScenario != null,
                    onClick = {
                        plan = buildCurrentPlan(useScenario = true)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSecondaryButton(
                    text = "Scenario 목록 새로고침",
                    onClick = {
                        WorkerProfileStore.clearCache()
                        WorkerProfileStore.initialize(context)
                        workerProfiles = WorkerProfileStore.loadWorkerProfiles(forceReload = true).workerProfiles
                        scenarios = WorkerProfileStore.loadConversationScenarios(forceReload = true).scenarios
                        if (selectedScenarioIndex > scenarios.size) selectedScenarioIndex = 0
                        plan = null
                        resultMode = "NONE"
                    }
                )

                SettingsSecondaryButton(
                    text = "결과 복사",
                    onClick = {
                        val currentPlan = plan ?: buildCurrentPlan(useScenario = selectedScenario != null)
                        clipboardManager.setText(
                            AnnotatedString(
                                buildCapabilityPlanCopyText(
                                    plan = currentPlan,
                                    resultMode = resultMode,
                                    scenario = selectedScenario
                                )
                            )
                        )
                    }
                )
            }

            val currentPlan = plan
            if (currentPlan == null) {
                Text(
                    text = "기본 진단은 userInput만 분석합니다. Scenario Preview는 선택한 Scenario와 WorkerProfile 목록을 함께 사용합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsStyleTokens.mutedTextColor
                )
            } else {
                CapabilityPlanPreview(
                    plan = currentPlan,
                    resultMode = resultMode,
                    selectedScenario = selectedScenario
                )
            }
        }
    }
}

@Composable
private fun CapabilityDebugNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CapabilityDebugText("진단 전용: 실제 답변 성공/실패를 의미하지 않습니다.")
            CapabilityDebugText("미연결: Provider 호출 · Worker 실행 · RAG 실행 · Web Search 실행 · Tool 실행")
        }
    }
}

@Composable
private fun CapabilityScenarioSelector(
    scenarios: List<ConversationScenarioProfile>,
    selectedScenarioIndex: Int,
    onSelectedScenarioIndexChange: (Int) -> Unit
) {
    val selectedScenario = scenarios.getOrNull(selectedScenarioIndex - 1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Scenario 선택",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.titleTextColor
            )

            Text(
                text = "선택값은 저장하지 않습니다. Scenario Preview 진단에만 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.mutedTextColor
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsSecondaryButton(
                    text = "이전",
                    onClick = {
                        val next = if (selectedScenarioIndex <= 0) scenarios.size else selectedScenarioIndex - 1
                        onSelectedScenarioIndexChange(next)
                    }
                )
                SettingsSecondaryButton(
                    text = "다음",
                    onClick = {
                        val next = if (selectedScenarioIndex >= scenarios.size) 0 else selectedScenarioIndex + 1
                        onSelectedScenarioIndexChange(next)
                    }
                )
                SettingsSecondaryButton(
                    text = "미선택",
                    onClick = { onSelectedScenarioIndexChange(0) },
                    selected = selectedScenarioIndex == 0
                )
            }

            if (selectedScenario == null) {
                CapabilityDebugRow("현재 Scenario", "Scenario 미선택 · 기본 buildPlan 진단 사용")
                CapabilityDebugRow("로드된 Scenario 수", scenarios.size.toString())
            } else {
                CapabilityDebugRow("현재 Scenario", selectedScenario.name.ifBlank { selectedScenario.scenarioId })
                CapabilityDebugRow("enabled", selectedScenario.enabled.toString())
                CapabilityDebugRow("defaultExecutionMode", selectedScenario.defaultExecutionMode.name)
                CapabilityDebugRow("Worker 수", selectedScenario.workerProfileIds.size.toString())
            }
        }
    }
}

@Composable
private fun CapabilityResolverExampleButtons(
    onExampleSelected: (String) -> Unit
) {
    val examples = listOf(
        "오늘 서울 날씨를 검색해서 알려줘.",
        "아래 내용을 JSON으로 출력해줘. 앱 이름은 MAHA, 모드는 대화모드야.",
        "이 Kotlin 코드를 검증하고 수정안을 코드블록으로 보여줘.",
        "이 주제를 세 가지 관점에서 비교하고 표로 정리해줘."
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "예시 입력",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = SettingsStyleTokens.bodyTextColor
        )

        examples.forEachIndexed { index, example ->
            SettingsSecondaryButton(
                text = "예시 ${index + 1}: $example",
                onClick = { onExampleSelected(example) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CapabilityPlanPreview(
    plan: OrchestratorPlan,
    resultMode: String,
    selectedScenario: ConversationScenarioProfile?
) {
    var showCapabilityDetails by rememberSaveable { mutableStateOf(false) }
    var showWorkerDetails by rememberSaveable { mutableStateOf(false) }
    var showLimitationDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CapabilityPreviewOnlyCard(
            resultMode = resultMode,
            selectedScenario = selectedScenario
        )

        CapabilityPlanSummaryCard(
            plan = plan,
            selectedScenario = selectedScenario
        )

        CapabilityExpandableSection(
            title = "Capability 요약",
            subtitle = "요구 ${plan.requestedCapabilities.size}개 · 해석 ${plan.resolvedCapabilities.size}개",
            expanded = showCapabilityDetails,
            onToggle = { showCapabilityDetails = !showCapabilityDetails }
        ) {
            if (plan.requestedCapabilities.isEmpty() && plan.resolvedCapabilities.isEmpty()) {
                CapabilityDebugText("Capability 요약 없음")
            } else {
                if (plan.requestedCapabilities.isNotEmpty()) {
                    CapabilityDebugText("요구 capability")
                    plan.requestedCapabilities.forEach { requirement ->
                        CapabilityMiniCard(
                            title = requirement.userVisibleLabel.ifBlank { requirement.capabilityType.name },
                            badge = requirement.capabilityType.name,
                            lines = listOf(
                                "필수=${requirement.required}",
                                "우선순위=${requirement.priority}",
                                "사유=${requirement.reason.ifBlank { "없음" }}"
                            )
                        )
                    }
                }

                if (plan.resolvedCapabilities.isNotEmpty()) {
                    CapabilityDebugText("해석 capability")
                    plan.resolvedCapabilities.forEach { resolution ->
                        CapabilityMiniCard(
                            title = resolution.requirement.capabilityType.name,
                            badge = resolution.status.name,
                            lines = listOf(
                                "source=${resolution.source.name}",
                                "executionAvailable=${resolution.executionAvailable}",
                                "limitation=${resolution.limitationReason?.reasonCode ?: "없음"}"
                            )
                        )
                    }
                }
            }
        }

        CapabilityExpandableSection(
            title = "WorkerPlan preview",
            subtitle = "${plan.workerPlans.size}개 Worker 후보",
            expanded = showWorkerDetails,
            onToggle = { showWorkerDetails = !showWorkerDetails }
        ) {
            if (plan.workerPlans.isEmpty()) {
                CapabilityDebugText("Worker 계획 없음")
            } else {
                plan.workerPlans.sortedBy { it.executionOrder }.forEach { worker ->
                    CapabilityWorkerPlanCard(worker = worker)
                }
            }
        }

        CapabilityExpandableSection(
            title = "제한/경고",
            subtitle = "${plan.limitationReasons.size}개",
            expanded = showLimitationDetails,
            onToggle = { showLimitationDetails = !showLimitationDetails }
        ) {
            if (plan.limitationReasons.isEmpty()) {
                CapabilityDebugText("제한 사유 없음")
            } else {
                plan.limitationReasons.forEach { reason ->
                    CapabilityLimitationCard(reason = reason)
                }
            }
        }
    }
}

@Composable
private fun CapabilityPreviewOnlyCard(
    resultMode: String,
    selectedScenario: ConversationScenarioProfile?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.cardColors(SettingsChipTone.INFO).background)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge("Preview only")
                CapabilityBadge("실제 실행 없음")
            }
            CapabilityDebugText("Provider 호출 · Worker 실행 · RAG · Web Search · Tool 실행을 수행하지 않습니다.")
            CapabilityDebugRow(
                label = "결과 모드",
                value = if (resultMode == "SCENARIO_PREVIEW") "Scenario Preview" else "기본 진단"
            )
            selectedScenario?.let { scenario ->
                CapabilityDebugRow(
                    label = "선택 Scenario",
                    value = "${scenario.name.ifBlank { scenario.scenarioId }} · ${scenario.defaultExecutionMode.name} · Worker ${scenario.workerProfileIds.size}개"
                )
            }
        }
    }
}

@Composable
private fun CapabilityPlanSummaryCard(
    plan: OrchestratorPlan,
    selectedScenario: ConversationScenarioProfile?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Plan 요약",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.titleTextColor
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge(plan.executionMode.name)
                CapabilityBadge(plan.userGoalStatus.name)
            }
            CapabilityCompactInfoRow("Scenario 기본값", selectedScenario?.defaultExecutionMode?.name ?: "미선택")
            CapabilityCompactInfoRow("WorkerPlan", "${plan.workerPlans.size}개")
            CapabilityCompactInfoRow("Capability", "요구 ${plan.requestedCapabilities.size}개 · 해석 ${plan.resolvedCapabilities.size}개")
            CapabilityCompactInfoRow("제한/경고", "${plan.limitationReasons.size}개")
        }
    }
}

@Composable
private fun CapabilityExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SettingsStyleTokens.titleTextColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsStyleTokens.mutedTextColor
                    )
                }
                SettingsSecondaryButton(
                    text = if (expanded) "상세 닫기" else "상세 보기",
                    onClick = onToggle
                )
            }

            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun CapabilityWorkerPlanCard(worker: WorkerPlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.nestedCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge(worker.workerRole.name)
                CapabilityBadge(worker.plannedStatus.name)
            }
            Text(
                text = worker.displayName.ifBlank { worker.workerRole.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SettingsStyleTokens.bodyTextColor
            )
            CapabilityCompactInfoRow("Provider / Model", "${worker.assignedProviderId ?: "Provider 미지정"} / ${worker.assignedModelId ?: "Model 미지정"}")
            CapabilityCompactInfoRow("순서 / 병렬", "${worker.executionOrder} / ${worker.canRunInParallel}")
            CapabilityCompactInfoRow("의존 Worker", worker.dependsOnWorkerIds.joinToString().ifBlank { "없음" })
            CapabilityCompactInfoRow("예상 출력", worker.expectedOutputType.name)
            worker.limitationReason?.let { reason ->
                CapabilityCompactInfoRow("제한", compactReasonLabel(reason.reasonCode))
            }
        }
    }
}

@Composable
private fun CapabilityLimitationCard(reason: LimitationReason) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.nestedCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge(compactReasonLabel(reason.reasonCode))
                CapabilityBadge(reason.status.name)
            }
            CapabilityDebugText(reason.userMessage.ifBlank { "제한 사유 메시지 없음" })
            CapabilityCompactInfoRow("source", reason.source.name)
            CapabilityCompactInfoRow("recoverable", reason.recoverable.toString())
            reason.suggestedAction?.takeIf { it.isNotBlank() }?.let { action ->
                CapabilityCompactInfoRow("action", action)
            }
            reason.technicalMessage.takeIf { it.isNotBlank() }?.let { message ->
                CapabilityCompactInfoRow("technical", message)
            }
        }
    }
}

@Composable
private fun CapabilityMiniCard(
    title: String,
    badge: String,
    lines: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.nestedCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge(badge)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = SettingsStyleTokens.bodyTextColor
            )
            lines.forEach { line -> CapabilityDebugText(line) }
        }
    }
}

@Composable
private fun CapabilityCompactInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsStyleTokens.nestedCardBackground, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.labelSmall,
            color = SettingsStyleTokens.mutedTextColor
        )
        SelectionContainer(modifier = Modifier.weight(0.58f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.bodyTextColor
            )
        }
    }
}

@Composable
private fun CapabilityBadge(text: String) {
    val tone = when {
        text.contains("실행 없음") || text.contains("Preview", ignoreCase = true) -> SettingsChipTone.WARNING
        text.contains("SUCCESS") || text.contains("AVAILABLE") -> SettingsChipTone.SUCCESS
        text.contains("BLOCKED") || text.contains("ERROR") || text.contains("없음") -> SettingsChipTone.DANGER
        text.contains("LIMITED") || text.contains("미지정") || text.contains("비활성") -> SettingsChipTone.WARNING
        text.contains("SELECT") || text.contains("SCENARIO", ignoreCase = true) -> SettingsChipTone.INFO
        else -> SettingsChipTone.NEUTRAL
    }
    SettingsStatusChip(text = text, tone = tone)
}

private fun compactReasonLabel(reasonCode: String): String {
    return when (reasonCode) {
        "NEED_PROVIDER" -> "Provider 미지정"
        "NEED_MODEL" -> "Model 미지정"
        "WORKER_PROFILE_NOT_FOUND" -> "참조 Worker 없음"
        "WORKER_DISABLED" -> "비활성 Worker"
        "PREVIEW_ONLY" -> "실제 실행 없음"
        "SCENARIO_DISABLED" -> "Scenario 비활성"
        "SCENARIO_HAS_NO_WORKERS" -> "Worker 없음"
        else -> reasonCode
    }
}

@Composable
private fun CapabilityDebugSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.titleTextColor
            )
            content()
        }
    }
}

@Composable
private fun CapabilityDebugRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SettingsStyleTokens.nestedCardBackground, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SettingsStyleTokens.mutedTextColor
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.bodyTextColor
            )
        }
    }
}

@Composable
private fun CapabilityDebugText(text: String) {
    SelectionContainer {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = SettingsStyleTokens.bodyTextColor
        )
    }
}

private fun buildCapabilityPlanCopyText(
    plan: OrchestratorPlan,
    resultMode: String,
    scenario: ConversationScenarioProfile?
): String {
    return buildString {
        appendLine("[Capability Resolver Plan]")
        appendLine("resultMode=$resultMode")
        appendLine("diagnosticOnly=true")
        appendLine("previewOnly=true")
        appendLine("providerCall=false")
        appendLine("workerExecution=false")
        appendLine("ragExecution=false")
        appendLine("webSearchExecution=false")
        appendLine("toolExecution=false")
        scenario?.let {
            appendLine("scenarioId=${it.scenarioId}")
            appendLine("scenarioName=${it.name}")
            appendLine("scenarioEnabled=${it.enabled}")
            appendLine("scenarioDefaultExecutionMode=${it.defaultExecutionMode.name}")
            appendLine("scenarioWorkerCount=${it.workerProfileIds.size}")
        } ?: appendLine("scenarioId=NONE")
        appendLine("planId=${plan.planId}")
        appendLine("requestId=${plan.requestId}")
        appendLine("executionMode=${plan.executionMode.name}")
        appendLine("userGoalStatus=${plan.userGoalStatus.name}")
        appendLine("providerNativeUsed=${plan.providerNativeUsed}")
        appendLine("mahaNativeUsed=${plan.mahaNativeUsed}")
        appendLine()

        appendLine("[Requested Capabilities]")
        plan.requestedCapabilities.forEach { requirement ->
            appendLine("- ${requirement.capabilityType.name} required=${requirement.required} priority=${requirement.priority} label=${requirement.userVisibleLabel}")
            appendLine("  reason=${requirement.reason}")
        }
        appendLine()

        appendLine("[Resolved Capabilities]")
        plan.resolvedCapabilities.forEach { resolution ->
            appendLine("- ${resolution.requirement.capabilityType.name} status=${resolution.status.name} source=${resolution.source.name} executionAvailable=${resolution.executionAvailable}")
            resolution.limitationReason?.let { reason ->
                appendLine("  limitation=${reason.reasonCode} message=${reason.userMessage}")
                appendLine("  action=${reason.suggestedAction ?: "없음"}")
            }
        }
        appendLine()

        appendLine("[Limitation Reasons]")
        plan.limitationReasons.forEach { reason ->
            appendLine("- ${reason.capabilityType.name} status=${reason.status.name} code=${reason.reasonCode}")
            appendLine("  message=${reason.userMessage}")
            appendLine("  technical=${reason.technicalMessage}")
            appendLine("  action=${reason.suggestedAction ?: "없음"}")
        }
        appendLine()

        appendLine("[Worker Plans]")
        plan.workerPlans.sortedBy { it.executionOrder }.forEach { worker ->
            appendLine("- ${worker.workerRole.name} name=${worker.displayName} order=${worker.executionOrder} parallel=${worker.canRunInParallel}")
            appendLine("  provider=${worker.assignedProviderId ?: "미지정"} model=${worker.assignedModelId ?: "미지정"}")
            appendLine("  dependsOn=${worker.dependsOnWorkerIds.joinToString().ifBlank { "없음" }}")
            appendLine("  expectedOutput=${worker.expectedOutputType.name} plannedStatus=${worker.plannedStatus.name}")
            appendLine("  limitation=${worker.limitationReason?.reasonCode ?: "없음"}")
        }
    }.trim()
}
