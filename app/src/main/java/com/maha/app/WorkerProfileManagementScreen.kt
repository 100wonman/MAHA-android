package com.maha.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
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
    val providerSettingsStore = remember(context) { ProviderSettingsStore(context) }
    var providerProfiles by remember(context) { mutableStateOf(providerSettingsStore.loadProviderProfiles()) }
    var modelProfiles by remember(context) { mutableStateOf(providerSettingsStore.loadModelProfiles()) }
    var workerEnvelope by remember(context) {
        mutableStateOf(
            run {
                WorkerProfileStore.initialize(context)
                WorkerProfileStore.loadWorkerProfiles(forceReload = true)
            }
        )
    }
    var scenarioEnvelope by remember(context) {
        mutableStateOf(
            run {
                WorkerProfileStore.initialize(context)
                WorkerProfileStore.loadConversationScenarios(forceReload = true)
            }
        )
    }
    val reloadWorkerProfileStore = {
        providerProfiles = providerSettingsStore.loadProviderProfiles()
        modelProfiles = providerSettingsStore.loadModelProfiles()
        workerEnvelope = WorkerProfileStore.loadWorkerProfiles(forceReload = true)
        scenarioEnvelope = WorkerProfileStore.loadConversationScenarios(forceReload = true)
    }
    val providerById = providerProfiles.associateBy { it.providerId }
    val modelById = modelProfiles.associateBy { it.modelId }
    val workers = workerEnvelope.workerProfiles.sortedWith(
        compareBy<ConversationWorkerProfile> { it.executionOrder }
            .thenBy { it.displayName.lowercase() }
    )
    val scenarios = scenarioEnvelope.scenarios.sortedBy { it.name.lowercase() }
    var selectedTab by remember { mutableStateOf(WorkerProfileManagementTab.WORKERS) }
    var editingWorker by remember { mutableStateOf<ConversationWorkerProfile?>(null) }
    var editingScenario by remember { mutableStateOf<ConversationScenarioProfile?>(null) }
    var deleteTargetScenario by remember { mutableStateOf<ConversationScenarioProfile?>(null) }
    var scenarioActionMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = deleteTargetScenario != null) {
        deleteTargetScenario = null
    }

    BackHandler(enabled = editingWorker != null) {
        editingWorker = null
    }

    BackHandler(enabled = editingScenario != null) {
        editingScenario = null
    }

    val currentEditingWorker = editingWorker
    if (currentEditingWorker != null) {
        WorkerProfileEditScreen(
            worker = currentEditingWorker,
            onCancel = { editingWorker = null },
            onSaved = {
                reloadWorkerProfileStore()
                editingWorker = null
            },
            modifier = modifier
        )
        return
    }

    val currentEditingScenario = editingScenario
    if (currentEditingScenario != null) {
        ScenarioEditScreen(
            scenario = currentEditingScenario,
            workers = workers,
            onCancel = { editingScenario = null },
            onSaved = {
                reloadWorkerProfileStore()
                editingScenario = null
            },
            modifier = modifier
        )
        return
    }

    val scenarioPendingDelete = deleteTargetScenario
    if (scenarioPendingDelete != null) {
        ScenarioDeleteConfirmDialog(
            scenario = scenarioPendingDelete,
            onDismiss = { deleteTargetScenario = null },
            onConfirmDelete = {
                val deleted = runCatching {
                    WorkerProfileStore.deleteScenario(scenarioPendingDelete.scenarioId)
                }
                scenarioActionMessage = if (deleted.isSuccess) {
                    reloadWorkerProfileStore()
                    "Scenario를 삭제했습니다. WorkerProfile은 삭제하지 않았습니다."
                } else {
                    "Scenario 삭제 실패: ${deleted.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                }
                deleteTargetScenario = null
            }
        )
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
                            providerLabel = worker.providerId?.let { providerId ->
                                providerById[providerId]?.let { provider ->
                                    "${provider.displayName} (${provider.providerType.name})"
                                } ?: "참조 Provider 없음"
                            } ?: "미지정",
                            modelLabel = worker.modelId?.let { modelId ->
                                modelById[modelId]?.let { model ->
                                    model.displayName.ifBlank { model.rawModelName.ifBlank { model.modelId } }
                                } ?: "참조 Model 없음"
                            } ?: "미지정",
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
                ScenarioCreateActionCard(
                    message = scenarioActionMessage,
                    onCreateScenario = {
                        val scenario = createNewUserScenario()
                        val saved = runCatching {
                            WorkerProfileStore.upsertScenario(scenario)
                        }
                        scenarioActionMessage = if (saved.isSuccess) {
                            reloadWorkerProfileStore()
                            editingScenario = scenario
                            "새 Scenario를 생성했습니다."
                        } else {
                            "새 Scenario 생성 실패: ${saved.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                        }
                    }
                )
                if (scenarios.isEmpty()) {
                    WorkerProfileEmptyCard(text = "표시할 Conversation Scenario가 없습니다.")
                } else {
                    val workersById = workers.associateBy { it.workerProfileId }
                    scenarios.forEach { scenario ->
                        ConversationScenarioPreviewCard(
                            scenario = scenario,
                            workersById = workersById,
                            onEdit = { editingScenario = scenario },
                            onDuplicate = {
                                val duplicated = createScenarioDuplicate(scenario)
                                val saved = runCatching {
                                    WorkerProfileStore.upsertScenario(duplicated)
                                }
                                scenarioActionMessage = if (saved.isSuccess) {
                                    reloadWorkerProfileStore()
                                    editingScenario = duplicated
                                    "Scenario 복사본을 생성했습니다."
                                } else {
                                    "Scenario 복제 실패: ${saved.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                                }
                            },
                            onToggleEnabled = {
                                val next = scenario.copy(
                                    enabled = !scenario.enabled,
                                    userModified = true,
                                    updatedAt = System.currentTimeMillis()
                                )
                                val saved = runCatching {
                                    WorkerProfileStore.upsertScenario(next)
                                }
                                scenarioActionMessage = if (saved.isSuccess) {
                                    reloadWorkerProfileStore()
                                    if (next.enabled) "Scenario를 활성화했습니다." else "Scenario를 비활성화했습니다."
                                } else {
                                    "Scenario 상태 변경 실패: ${saved.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                                }
                            },
                            onRequestDelete = { deleteTargetScenario = scenario }
                        )
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
    SettingsSectionCard(
        title = "Worker Profile 관리",
        subtitle = "대화모드 Worker Profile / Scenario preview입니다.",
        chips = listOf(
            "schema $schemaVersion" to SettingsChipTone.INFO,
            "Worker ${workerCount}개" to SettingsChipTone.NEUTRAL,
            "Scenario ${scenarioCount}개" to SettingsChipTone.NEUTRAL,
            "실행 미연결" to SettingsChipTone.WARNING
        )
    )
}

@Composable
private fun WorkerProfileReadOnlyNoticeCard() {
    SettingsSectionCard(
        title = "저장/실행 상태",
        subtitle = "Worker와 Scenario 설정 저장은 지원합니다. Provider 호출/RAG/Web Search/Tool 실행/ConversationEngine 연결은 아직 없습니다.",
        chips = listOf("저장 가능" to SettingsChipTone.SUCCESS, "실행 미연결" to SettingsChipTone.WARNING),
        tone = SettingsChipTone.WARNING
    )
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
    SettingsSecondaryButton(
        text = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    )
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
    providerLabel: String,
    modelLabel: String,
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
                        Text(text = if (expanded) "상세 닫기" else "상세 보기", color = Color(0xFFBFD7FF))
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
                leftValue = providerLabel,
                rightLabel = "Model",
                rightValue = modelLabel
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(2.dp))
                WorkerProfileReadOnlyPlaceholder(text = "Worker 상세 preview입니다. 편집 버튼에서 기본 정보, System Instruction, Provider/Model 참조를 저장할 수 있습니다.")

                WorkerProfileDetailTitle(title = "기본 정보")
                WorkerProfileKeyValue("역할 설명", worker.roleDescription.ifBlank { "역할 설명 없음" })
                WorkerProfileKeyValue("활성 상태", if (worker.enabled) "활성" else "비활성")
                WorkerProfileKeyValue("기본 템플릿", if (worker.isDefaultTemplate) "예" else "아니오")
                WorkerProfileKeyValue("사용자 수정본", if (worker.userModified) "예" else "아니오")

                WorkerProfileDetailTitle(title = "System Instruction")
                WorkerProfileKeyValue("System Instruction 전체 보기", worker.systemInstruction.previewText(1200))
                WorkerProfileReadOnlyPlaceholder(text = "System Instruction은 편집 화면에서 저장할 수 있습니다.")

                WorkerProfileDetailTitle(title = "Provider / Model 참조")
                WorkerProfileKeyValue("Provider", providerLabel)
                WorkerProfileKeyValue("providerId", worker.providerId ?: "Provider 미지정")
                WorkerProfileKeyValue("Model", modelLabel)
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

                WorkerProfileTagRow(values = listOf("preview", "기본 정보 저장 가능"))
                WorkerProfileCollapseButton(onClick = { expanded = false })
            }
        }
    }
}

@Composable
private fun ConversationScenarioPreviewCard(
    scenario: ConversationScenarioProfile,
    workersById: Map<String, ConversationWorkerProfile>,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRequestDelete: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(text = if (expanded) "상세 닫기" else "상세 보기", color = Color(0xFFBFD7FF))
                    }
                    TextButton(onClick = onEdit) {
                        Text(text = "편집", color = Color(0xFFBFD7FF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            WorkerProfileTagRow(
                values = listOf(
                    if (scenario.enabled) "활성" else "비활성",
                    "Worker ${scenario.workerProfileIds.size}개",
                    if (scenario.isDefaultTemplate) "기본" else "사용자",
                    if (scenario.userModified) "수정본" else "원본",
                    "O: ${scenario.orchestratorProfileId.toScenarioWorkerSummary(workersById, "미지정")}",
                    "S: ${scenario.synthesisProfileId.toScenarioWorkerSummary(workersById, "미지정")}"
                )
            )

            ScenarioCardActionRow(
                enabled = scenario.enabled,
                isDefaultTemplate = scenario.isDefaultTemplate,
                onDuplicate = onDuplicate,
                onToggleEnabled = onToggleEnabled,
                onRequestDelete = onRequestDelete
            )

            if (expanded) {
                WorkerProfileReadOnlyPlaceholder(text = "Scenario 상세입니다. Scenario 추가/복제/활성화/삭제와 기본 정보/WorkerSet 저장은 연결되어 있습니다. Scenario 실행은 아직 연결되지 않았습니다.")

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
                WorkerProfileKeyValue("Worker 표시", scenario.workerProfileIds.toScenarioWorkerDisplay(workersById))
                WorkerProfileKeyValue("orchestratorProfileId", scenario.orchestratorProfileId.toScenarioWorkerDetail(workersById, "Orchestrator 미지정"))
                WorkerProfileKeyValue("synthesisProfileId", scenario.synthesisProfileId.toScenarioWorkerDetail(workersById, "Synthesis 미지정"))

                WorkerProfileTagRow(values = listOf("read-only", "Scenario 편집 후속 구현 예정"))
                WorkerProfileCollapseButton(onClick = { expanded = false })
            }
        }
    }
}


@Composable
private fun ScenarioCreateActionCard(
    message: String?,
    onCreateScenario: () -> Unit,
) {
    SettingsSectionCard(
        title = "Scenario 관리",
        subtitle = "새 Scenario 추가, 복제, 비활성화/활성화, 사용자 생성 Scenario 삭제를 지원합니다. WorkerProfile은 삭제하거나 복제하지 않습니다.",
        chips = listOf("Scenario CRUD" to SettingsChipTone.INFO)
    ) {
        SettingsPrimaryButton(text = "새 Scenario 추가", onClick = onCreateScenario)
        if (!message.isNullOrBlank()) {
            SettingsStatusChip(text = message, tone = SettingsChipTone.WARNING)
        }
    }
}

@Composable
private fun ScenarioCardActionRow(
    enabled: Boolean,
    isDefaultTemplate: Boolean,
    onDuplicate: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SettingsSecondaryButton(text = "복제", onClick = onDuplicate, modifier = Modifier.weight(1f))
            SettingsSecondaryButton(text = if (enabled) "비활성화" else "활성화", onClick = onToggleEnabled, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (isDefaultTemplate) {
                SettingsSecondaryButton(text = "삭제 제한", onClick = onRequestDelete, modifier = Modifier.weight(1f))
            } else {
                SettingsDangerButton(text = "삭제", onClick = onRequestDelete, modifier = Modifier.weight(1f))
            }
            Text(
                text = if (isDefaultTemplate) "기본 Scenario는 삭제 대신 비활성화 또는 복제를 권장합니다." else "삭제 시 Scenario만 제거됩니다.",
                color = Color(0xFFB8BCC6),
                modifier = Modifier.weight(2f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScenarioDeleteConfirmDialog(
    scenario: ConversationScenarioProfile,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    if (scenario.isDefaultTemplate) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "기본 Scenario 삭제 제한") },
            text = {
                Text(
                    text = "기본 Scenario는 삭제하지 않습니다. 필요하면 비활성화하거나 복제본을 수정하세요. WorkerProfile은 변경되지 않습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "확인")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Scenario 삭제") },
            text = {
                Text(
                    text = "'${scenario.name.ifBlank { scenario.scenarioId }}' Scenario만 삭제됩니다. 포함된 WorkerProfile은 삭제되지 않습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(text = "삭제", color = Color(0xFFFFB4AB))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "취소")
                }
            }
        )
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
            Text(text = "상세 닫기", color = Color(0xFFBFD7FF), fontWeight = FontWeight.Bold)
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
    SettingsChipRow(
        values = values.map { value ->
            val tone = when {
                value.contains("활성") && !value.contains("비활성") -> SettingsChipTone.SUCCESS
                value.contains("비활성") -> SettingsChipTone.DISABLED
                value.contains("미연결") || value.contains("preview") -> SettingsChipTone.WARNING
                value.contains("기본") || value.contains("사용자") -> SettingsChipTone.INFO
                else -> SettingsChipTone.NEUTRAL
            }
            value to tone
        }
    )
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
    SettingsSectionCard(
        title = "표시할 항목 없음",
        subtitle = text,
        chips = listOf("empty" to SettingsChipTone.DISABLED)
    )
}




private fun createNewUserScenario(): ConversationScenarioProfile {
    val timestamp = System.currentTimeMillis()
    return ConversationScenarioProfile(
        scenarioId = generateScenarioId("user_scenario"),
        name = "새 Scenario",
        description = "",
        workerProfileIds = emptyList(),
        defaultExecutionMode = ExecutionMode.SINGLE,
        orchestratorProfileId = null,
        synthesisProfileId = null,
        userEditable = true,
        isDefaultTemplate = false,
        userModified = true,
        enabled = true,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

private fun createScenarioDuplicate(source: ConversationScenarioProfile): ConversationScenarioProfile {
    val timestamp = System.currentTimeMillis()
    return source.copy(
        scenarioId = generateScenarioId("scenario_copy"),
        name = source.name.ifBlank { "이름 없는 Scenario" } + " 복사본",
        userEditable = true,
        isDefaultTemplate = false,
        userModified = true,
        enabled = true,
        createdAt = timestamp,
        updatedAt = timestamp,
    )
}

private fun generateScenarioId(prefix: String): String {
    return "${prefix}_${System.currentTimeMillis()}"
}

private fun String?.toScenarioWorkerSummary(
    workersById: Map<String, ConversationWorkerProfile>,
    emptyLabel: String,
): String {
    if (this == null) return emptyLabel
    val worker = workersById[this]
    return worker?.displayName?.ifBlank { worker.workerProfileId } ?: "참조 없음"
}

private fun String?.toScenarioWorkerDetail(
    workersById: Map<String, ConversationWorkerProfile>,
    emptyLabel: String,
): String {
    if (this == null) return emptyLabel
    val worker = workersById[this]
    return if (worker == null) {
        "$this · 참조 Worker 없음"
    } else {
        "${worker.displayName.ifBlank { worker.workerProfileId }} · ${worker.roleLabel.ifBlank { "역할 미지정" }} · ${if (worker.enabled) "활성" else "비활성"} · ${worker.workerProfileId}"
    }
}

private fun List<String>.toScenarioWorkerDisplay(
    workersById: Map<String, ConversationWorkerProfile>,
): String {
    if (isEmpty()) return "없음"
    return mapIndexed { index, workerId ->
        val worker = workersById[workerId]
        if (worker == null) {
            "${index + 1}. $workerId · missing Worker"
        } else {
            "${index + 1}. ${worker.displayName.ifBlank { worker.workerProfileId }} · ${worker.roleLabel.ifBlank { "역할 미지정" }} · ${if (worker.enabled) "활성" else "비활성"}"
        }
    }.joinToString("\n")
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
