package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WorkerProfileManagementScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val workerEnvelope = remember(context) {
        WorkerProfileStore.initialize(context)
        WorkerProfileStore.loadWorkerProfiles(forceReload = true)
    }
    val scenarioEnvelope = remember(context) {
        WorkerProfileStore.initialize(context)
        WorkerProfileStore.loadConversationScenarios(forceReload = true)
    }
    val workers = workerEnvelope.workerProfiles.sortedWith(
        compareBy<ConversationWorkerProfile> { it.executionOrder }
            .thenBy { it.displayName.lowercase() }
    )
    val scenarios = scenarioEnvelope.scenarios.sortedBy { it.name.lowercase() }
    var selectedTab by remember { mutableStateOf(WorkerProfileManagementTab.WORKERS) }
    var editingWorker by remember { mutableStateOf<ConversationWorkerProfile?>(null) }

    val currentEditingWorker = editingWorker
    if (currentEditingWorker != null) {
        WorkerProfileEditScreen(
            worker = currentEditingWorker,
            onCancel = { editingWorker = null },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WorkerProfileInfoCard(
            workerCount = workers.size,
            scenarioCount = scenarios.size,
            schemaVersion = workerEnvelope.schemaVersion,
        )

        WorkerProfileReadOnlyNoticeCard()

        WorkerProfileTabSelector(
            selectedTab = selectedTab,
            workerCount = workers.size,
            scenarioCount = scenarios.size,
            onSelectTab = { selectedTab = it }
        )

        when (selectedTab) {
            WorkerProfileManagementTab.WORKERS -> {
                WorkerProfileCompactSectionTitle(
                    title = "Worker 목록",
                    subtitle = "요약 우선 표시입니다. 긴 설명과 System Instruction은 상세에서 확인합니다."
                )
                if (workers.isEmpty()) {
                    WorkerProfileEmptyCard(text = "표시할 Worker Profile이 없습니다.")
                } else {
                    workers.forEach { worker ->
                        WorkerProfilePreviewCard(
                            worker = worker,
                            onEdit = { editingWorker = worker }
                        )
                    }
                }
            }

            WorkerProfileManagementTab.SCENARIOS -> {
                WorkerProfileCompactSectionTitle(
                    title = "Scenario 목록",
                    subtitle = "Scenario는 WorkerProfile ID 목록을 참조하는 Worker Set 후보입니다."
                )
                if (scenarios.isEmpty()) {
                    WorkerProfileEmptyCard(text = "표시할 Conversation Scenario가 없습니다.")
                } else {
                    scenarios.forEach { scenario ->
                        ConversationScenarioPreviewCard(scenario = scenario)
                    }
                }
            }
        }
    }
}

private enum class WorkerProfileManagementTab {
    WORKERS,
    SCENARIOS
}

@Composable
private fun WorkerProfileInfoCard(
    workerCount: Int,
    scenarioCount: Int,
    schemaVersion: Int,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3340)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Worker Profile 관리",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "대화모드 Worker Profile / Scenario skeleton preview입니다.",
                color = Color(0xFFD0D3DA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            WorkerProfileTagRow(
                values = listOf(
                    "schema $schemaVersion",
                    "Worker ${workerCount}개",
                    "Scenario ${scenarioCount}개",
                    "실행 미연결"
                )
            )
        }
    }
}

@Composable
private fun WorkerProfileReadOnlyNoticeCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF332B1F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "현재 저장소는 로드/기본 생성만 연결되어 있습니다. 편집 화면은 skeleton이며 저장/Provider 호출/RAG/Web Search/Tool 실행/ConversationEngine 연결은 아직 없습니다.",
            color = Color(0xFFE6D0B8),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun WorkerProfileTabSelector(
    selectedTab: WorkerProfileManagementTab,
    workerCount: Int,
    scenarioCount: Int,
    onSelectTab: (WorkerProfileManagementTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkerProfileTabButton(
            label = "Worker $workerCount",
            selected = selectedTab == WorkerProfileManagementTab.WORKERS,
            onClick = { onSelectTab(WorkerProfileManagementTab.WORKERS) },
            modifier = Modifier.weight(1f)
        )
        WorkerProfileTabButton(
            label = "Scenario $scenarioCount",
            selected = selectedTab == WorkerProfileManagementTab.SCENARIOS,
            onClick = { onSelectTab(WorkerProfileManagementTab.SCENARIOS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkerProfileTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .background(
                if (selected) Color(0xFF33445C) else Color(0xFF202733),
                MaterialTheme.shapes.medium
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF86B7FF) else Color(0xFF3B4556),
                shape = MaterialTheme.shapes.medium
            )
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFBFD7FF),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun WorkerProfileCompactSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = subtitle,
            color = Color(0xFFB8BCC6),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorkerProfilePreviewCard(
    worker: ConversationWorkerProfile,
    onEdit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3B4556), MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = worker.displayName.ifBlank { "이름 없는 Worker" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = worker.roleLabel.ifBlank { "역할 미지정" },
                        color = Color(0xFFB8BCC6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(text = if (expanded) "접기" else "상세", color = Color(0xFFBFD7FF))
                    }
                    TextButton(onClick = onEdit) {
                        Text(text = "편집", color = Color(0xFFBFD7FF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            WorkerProfileTagRow(
                values = listOf(
                    if (worker.enabled) "활성" else "비활성",
                    if (worker.canRunInParallel) "병렬 가능" else "순차 후보",
                    "순서 ${worker.executionOrder}",
                    if (worker.isDefaultTemplate) "기본" else "사용자"
                )
            )

            WorkerProfileInlineSummary(
                leftLabel = "Provider",
                leftValue = worker.providerId ?: "미지정",
                rightLabel = "Model",
                rightValue = worker.modelId ?: "미지정"
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(2.dp))
                WorkerProfileReadOnlyPlaceholder(text = "Worker 상세 placeholder입니다. 실제 편집 저장은 후속 구현 예정입니다. 편집 버튼은 UI skeleton 화면만 엽니다.")

                WorkerProfileDetailTitle(title = "기본 정보")
                WorkerProfileKeyValue("역할 설명", worker.roleDescription.ifBlank { "역할 설명 없음" })
                WorkerProfileKeyValue("활성 상태", if (worker.enabled) "활성" else "비활성")
                WorkerProfileKeyValue("기본 템플릿", if (worker.isDefaultTemplate) "예" else "아니오")
                WorkerProfileKeyValue("사용자 수정본", if (worker.userModified) "예" else "아니오")

                WorkerProfileDetailTitle(title = "System Instruction")
                WorkerProfileKeyValue("전체 보기 placeholder", worker.systemInstruction.previewText(1200))
                WorkerProfileReadOnlyPlaceholder(text = "System Instruction 편집 저장 기능은 아직 연결되지 않았습니다.")

                WorkerProfileDetailTitle(title = "Provider / Model 참조")
                WorkerProfileKeyValue("providerId", worker.providerId ?: "Provider 미지정")
                WorkerProfileKeyValue("modelId", worker.modelId ?: "Model 미지정")

                WorkerProfileDetailTitle(title = "실행 계획 필드")
                WorkerProfileKeyValue("executionOrder", worker.executionOrder.toString())
                WorkerProfileKeyValue("canRunInParallel", worker.canRunInParallel.toString())
                WorkerProfileKeyValue("dependsOnWorkerIds", worker.dependsOnWorkerIds.joinToString().ifBlank { "없음" })

                WorkerProfileDetailTitle(title = "Capability / Policy")
                WorkerProfileKeyValue("capabilityOverrides", worker.capabilityOverrides.toReadableSummary())
                WorkerProfileKeyValue("inputPolicy", worker.inputPolicy.toReadableSummary())
                WorkerProfileKeyValue("outputPolicy", worker.outputPolicy.toReadableSummary())
                WorkerProfileKeyValue("expectedOutputType", worker.outputPolicy.expectedOutputType.name)

                WorkerProfileTagRow(values = listOf("read-only", "편집 저장 후속 구현 예정"))
                WorkerProfileCollapseButton(onClick = { expanded = false })
            }
        }
    }
}

@Composable
private fun ConversationScenarioPreviewCard(scenario: ConversationScenarioProfile) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3B4556), MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scenario.name.ifBlank { "이름 없는 Scenario" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = scenario.defaultExecutionMode.name,
                        color = Color(0xFFB8BCC6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) "접기" else "상세", color = Color(0xFFBFD7FF))
                }
            }

            WorkerProfileTagRow(
                values = listOf(
                    if (scenario.enabled) "활성" else "비활성",
                    "Worker ${scenario.workerProfileIds.size}개",
                    if (scenario.isDefaultTemplate) "기본" else "사용자",
                    if (scenario.userModified) "수정본" else "원본"
                )
            )

            if (expanded) {
                WorkerProfileReadOnlyPlaceholder(text = "Scenario 상세 placeholder입니다. 실제 WorkerSet 편집/저장은 후속 구현 예정입니다.")

                WorkerProfileDetailTitle(title = "Scenario 기본 정보")
                WorkerProfileKeyValue("설명", scenario.description.ifBlank { "설명 없음" })
                WorkerProfileKeyValue("scenarioId", scenario.scenarioId)
                WorkerProfileKeyValue("defaultExecutionMode", scenario.defaultExecutionMode.name)
                WorkerProfileKeyValue("enabled", scenario.enabled.toString())
                WorkerProfileKeyValue("userEditable", scenario.userEditable.toString())
                WorkerProfileKeyValue("isDefaultTemplate", scenario.isDefaultTemplate.toString())
                WorkerProfileKeyValue("userModified", scenario.userModified.toString())

                WorkerProfileDetailTitle(title = "WorkerSet 참조")
                WorkerProfileKeyValue("workerProfileIds", scenario.workerProfileIds.joinToString().ifBlank { "없음" })
                WorkerProfileKeyValue("orchestratorProfileId", scenario.orchestratorProfileId ?: "Orchestrator 미지정")
                WorkerProfileKeyValue("synthesisProfileId", scenario.synthesisProfileId ?: "Synthesis 미지정")

                WorkerProfileTagRow(values = listOf("read-only", "Scenario 편집 후속 구현 예정"))
                WorkerProfileCollapseButton(onClick = { expanded = false })
            }
        }
    }
}

@Composable
private fun WorkerProfileDetailTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFBFD7FF),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun WorkerProfileReadOnlyPlaceholder(text: String) {
    Text(
        text = text,
        color = Color(0xFFE6D0B8),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF332B1F), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun WorkerProfileCollapseButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onClick) {
            Text(text = "접기", color = Color(0xFFBFD7FF), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WorkerProfileInlineSummary(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WorkerProfileInlineValue(
            label = leftLabel,
            value = leftValue,
            modifier = Modifier.weight(1f)
        )
        WorkerProfileInlineValue(
            label = rightLabel,
            value = rightValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkerProfileInlineValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        Text(
            text = value.ifBlank { "미지정" },
            color = Color(0xFFD0D3DA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorkerProfileTagRow(values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        values.chunked(2).forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowValues.forEach { value ->
                    Text(
                        text = value,
                        color = Color(0xFFD6E4FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color(0xFF2B3442), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkerProfileKeyValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        SelectionContainer {
            Text(
                text = value.ifBlank { "없음" },
                color = Color(0xFFD0D3DA)
            )
        }
    }
}

@Composable
private fun WorkerProfileEmptyCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = Color(0xFFD0D3DA),
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun String.previewText(maxLength: Int): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return "System Instruction 없음"
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength).trimEnd() + "…"
}

internal fun WorkerInputPolicy.toReadableSummary(): String {
    return listOf(
        "userInputOnly=$userInputOnly",
        "previousWorkerOutput=$previousWorkerOutput",
        "ragContextAllowed=$ragContextAllowed",
        "memoryContextAllowed=$memoryContextAllowed",
        "webSearchContextAllowed=$webSearchContextAllowed",
        "includeRunHistory=$includeRunHistory",
        "maxInputChars=$maxInputChars"
    ).joinToString(" · ")
}

internal fun WorkerOutputPolicy.toReadableSummary(): String {
    return listOf(
        "expectedOutputType=${expectedOutputType.name}",
        "requireJson=$requireJson",
        "requireMarkdownTable=$requireMarkdownTable",
        "requireCodeBlock=$requireCodeBlock",
        "allowPlainText=$allowPlainText",
        "passToNextWorker=$passToNextWorker",
        "exposeToUser=$exposeToUser",
        "maxOutputChars=$maxOutputChars"
    ).joinToString(" · ")
}

internal fun WorkerCapabilityOverrides.toReadableSummary(): String {
    return listOf(
        "functionCalling=${functionCalling.name}",
        "webSearch=${webSearch.name}",
        "codeExecution=${codeExecution.name}",
        "structuredOutput=${structuredOutput.name}",
        "thinkingSummary=${thinkingSummary.name}",
        "ragSearch=${ragSearch.name}",
        "memoryRecall=${memoryRecall.name}",
        "fileRead=${fileRead.name}",
        "fileWrite=${fileWrite.name}",
        "codeCheck=${codeCheck.name}",
        "parallelExecution=${parallelExecution.name}"
    ).joinToString(" · ")
}
