package com.maha.app

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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101821))
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
                color = Color.White
            )

            Text(
                text = "이 화면은 실제 대화 실행이 아닙니다. Provider 호출, Worker 실행, RAG 검색, Web Search, Tool 실행을 하지 않고 Orchestrator가 어떤 capability와 실행 방식을 추정할지 미리 보여줍니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8C0CC)
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
                Button(
                    onClick = {
                        plan = buildCurrentPlan(useScenario = false)
                    }
                ) {
                    Text("기본 진단")
                }

                Button(
                    enabled = selectedScenario != null,
                    onClick = {
                        plan = buildCurrentPlan(useScenario = true)
                    }
                ) {
                    Text("Scenario Preview")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        WorkerProfileStore.clearCache()
                        WorkerProfileStore.initialize(context)
                        workerProfiles = WorkerProfileStore.loadWorkerProfiles(forceReload = true).workerProfiles
                        scenarios = WorkerProfileStore.loadConversationScenarios(forceReload = true).scenarios
                        if (selectedScenarioIndex > scenarios.size) selectedScenarioIndex = 0
                        plan = null
                        resultMode = "NONE"
                    }
                ) {
                    Text("Scenario 목록 새로고침")
                }

                TextButton(
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
                ) {
                    Text("결과 복사")
                }
            }

            val currentPlan = plan
            if (currentPlan == null) {
                Text(
                    text = "기본 진단은 userInput만 분석합니다. Scenario Preview는 선택한 Scenario와 WorkerProfile 목록을 함께 사용합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8F9AAD)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF182435))
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172231))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Scenario 선택",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "선택값은 저장하지 않습니다. Scenario Preview 진단에만 사용합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8C0CC)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val next = if (selectedScenarioIndex <= 0) scenarios.size else selectedScenarioIndex - 1
                        onSelectedScenarioIndexChange(next)
                    }
                ) {
                    Text("이전")
                }
                OutlinedButton(
                    onClick = {
                        val next = if (selectedScenarioIndex >= scenarios.size) 0 else selectedScenarioIndex + 1
                        onSelectedScenarioIndexChange(next)
                    }
                ) {
                    Text("다음")
                }
                TextButton(onClick = { onSelectedScenarioIndexChange(0) }) {
                    Text("미선택")
                }
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
            color = Color(0xFFD7DEE8)
        )

        examples.forEachIndexed { index, example ->
            TextButton(
                onClick = { onExampleSelected(example) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "예시 ${index + 1}: $example",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFAEC6FF)
                )
            }
        }
    }
}

@Composable
private fun CapabilityPlanPreview(
    plan: OrchestratorPlan,
    resultMode: String,
    selectedScenario: ConversationScenarioProfile?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CapabilityDebugSection(title = "Preview 안내") {
            CapabilityDebugRow("결과 모드", if (resultMode == "SCENARIO_PREVIEW") "Scenario Preview" else "기본 진단")
            CapabilityDebugRow("previewOnly", "true")
            CapabilityDebugRow("Provider 호출", "없음")
            CapabilityDebugRow("Worker 실행", "없음")
            CapabilityDebugRow("RAG / Web Search / Tool 실행", "없음")
            selectedScenario?.let { scenario ->
                CapabilityDebugRow("선택 Scenario", scenario.name.ifBlank { scenario.scenarioId })
                CapabilityDebugRow("Scenario 기본 실행 방식", scenario.defaultExecutionMode.name)
            }
        }

        CapabilityDebugSection(title = "Plan 요약") {
            CapabilityDebugRow("실행 방식", plan.executionMode.name)
            CapabilityDebugRow("사용자 목표 상태", plan.userGoalStatus.name)
            CapabilityDebugRow("요구 capability 수", plan.requestedCapabilities.size.toString())
            CapabilityDebugRow("해석 capability 수", plan.resolvedCapabilities.size.toString())
            CapabilityDebugRow("제한 사유 수", plan.limitationReasons.size.toString())
            CapabilityDebugRow("WorkerPlan 수", plan.workerPlans.size.toString())
            CapabilityDebugRow("Provider-native 사용 후보", plan.providerNativeUsed.toString())
            CapabilityDebugRow("MAHA-native 사용 후보", plan.mahaNativeUsed.toString())
            CapabilityDebugRow("planId", plan.planId)
        }

        CapabilityDebugSection(title = "Requested Capabilities") {
            if (plan.requestedCapabilities.isEmpty()) {
                CapabilityDebugText("요구 capability 없음")
            } else {
                plan.requestedCapabilities.forEach { requirement ->
                    CapabilityDebugText(
                        "- ${requirement.userVisibleLabel} (${requirement.capabilityType.name}) · required=${requirement.required} · priority=${requirement.priority}\n" +
                                "  reason=${requirement.reason}"
                    )
                }
            }
        }

        CapabilityDebugSection(title = "Resolved Capabilities") {
            if (plan.resolvedCapabilities.isEmpty()) {
                CapabilityDebugText("해석된 capability 없음")
            } else {
                plan.resolvedCapabilities.forEach { resolution ->
                    CapabilityDebugText(
                        "- ${resolution.requirement.capabilityType.name} · status=${resolution.status.name} · source=${resolution.source.name}\n" +
                                "  executionAvailable=${resolution.executionAvailable}\n" +
                                "  limitation=${resolution.limitationReason?.reasonCode ?: "없음"}"
                    )
                }
            }
        }

        CapabilityDebugSection(title = "Limitation Reasons") {
            if (plan.limitationReasons.isEmpty()) {
                CapabilityDebugText("제한 사유 없음")
            } else {
                plan.limitationReasons.forEach { reason ->
                    CapabilityDebugText(
                        "- ${reason.capabilityType.name} · ${reason.status.name} · ${reason.reasonCode}\n" +
                                "  message=${reason.userMessage}\n" +
                                "  technical=${reason.technicalMessage.ifBlank { "없음" }}\n" +
                                "  action=${reason.suggestedAction ?: "없음"}"
                    )
                }
            }
        }

        CapabilityDebugSection(title = "Worker Plans") {
            if (plan.workerPlans.isEmpty()) {
                CapabilityDebugText("Worker 계획 없음")
            } else {
                plan.workerPlans.sortedBy { it.executionOrder }.forEach { worker ->
                    CapabilityDebugText(
                        "- ${worker.workerRole.name} · ${worker.displayName}\n" +
                                "  order=${worker.executionOrder} · parallel=${worker.canRunInParallel}\n" +
                                "  provider=${worker.assignedProviderId ?: "미지정"} · model=${worker.assignedModelId ?: "미지정"}\n" +
                                "  dependsOn=${worker.dependsOnWorkerIds.ifEmpty { listOf("없음") }.joinToString()}\n" +
                                "  expectedOutput=${worker.expectedOutputType.name}\n" +
                                "  plannedStatus=${worker.plannedStatus.name}\n" +
                                "  limitation=${worker.limitationReason?.reasonCode ?: "없음"}"
                    )
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172231))
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
                color = Color.White
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
            .background(Color(0xFF111A26), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8F9AAD)
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD7DEE8)
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
            color = Color(0xFFD7DEE8)
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
