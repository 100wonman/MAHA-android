package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ScenarioEditScreen(
    scenario: ConversationScenarioProfile,
    workers: List<ConversationWorkerProfile>,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val workersById = remember(workers) { workers.associateBy { it.workerProfileId } }
    var name by remember(scenario.scenarioId) { mutableStateOf(scenario.name) }
    var description by remember(scenario.scenarioId) { mutableStateOf(scenario.description) }
    var enabledPreview by remember(scenario.scenarioId) { mutableStateOf(scenario.enabled) }
    var executionModePreview by remember(scenario.scenarioId) { mutableStateOf(scenario.defaultExecutionMode) }
    var orchestratorPreviewId by remember(scenario.scenarioId) { mutableStateOf(scenario.orchestratorProfileId) }
    var synthesisPreviewId by remember(scenario.scenarioId) { mutableStateOf(scenario.synthesisProfileId) }
    var saveMessage by remember(scenario.scenarioId) { mutableStateOf<String?>(null) }

    val dirty = name != scenario.name ||
            description != scenario.description ||
            enabledPreview != scenario.enabled ||
            executionModePreview != scenario.defaultExecutionMode ||
            orchestratorPreviewId != scenario.orchestratorProfileId ||
            synthesisPreviewId != scenario.synthesisProfileId

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScenarioEditHeaderCard(
            dirty = dirty,
            onBack = onCancel
        )

        ScenarioEditSection(title = "기본 정보", initiallyExpanded = true) {
            ScenarioEditTextField(
                label = "name",
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
            ScenarioEditTextField(
                label = "description",
                value = description,
                onValueChange = { description = it },
                minLines = 3
            )
            ScenarioBooleanToggle(
                label = "enabled placeholder",
                value = enabledPreview,
                onValueChange = { enabledPreview = it }
            )
            ScenarioEditKeyValue("scenarioId", scenario.scenarioId)
            ScenarioEditKeyValue("userEditable", scenario.userEditable.toString())
            ScenarioEditKeyValue("isDefaultTemplate", scenario.isDefaultTemplate.toString())
            ScenarioEditKeyValue("userModified", scenario.userModified.toString())
            ScenarioEditNotice("현재 화면은 Scenario 편집 UI skeleton입니다. 실제 저장 연결과 대화 실행 연결은 후속 단계에서 지원됩니다.")
        }

        ScenarioEditSection(title = "WorkerSet", initiallyExpanded = true) {
            ScenarioEditNotice("Scenario는 WorkerProfile 자체를 복제하지 않고 workerProfileId 목록만 참조합니다. Worker 추가/제거/순서 변경 저장은 후속 구현 예정입니다.")
            ScenarioEditKeyValue("포함 Worker 수", "${scenario.workerProfileIds.size}개")
            if (scenario.workerProfileIds.isEmpty()) {
                ScenarioWarningText("Worker가 없습니다. 실제 실행 후보에서는 제한 상태가 될 수 있습니다.")
            } else {
                scenario.workerProfileIds.forEachIndexed { index, workerId ->
                    val worker = workersById[workerId]
                    ScenarioWorkerReferenceRow(
                        index = index,
                        workerId = workerId,
                        worker = worker
                    )
                }
            }
            ScenarioPlaceholderAction(label = "Worker 추가 placeholder")
            ScenarioPlaceholderAction(label = "Worker 제거 placeholder")
            ScenarioPlaceholderAction(label = "Worker 순서 변경 placeholder")
        }

        ScenarioEditSection(title = "실행 방식", initiallyExpanded = false) {
            ScenarioEditNotice("Scenario의 기본 실행 방식입니다. 실제 실행 시에는 Orchestrator가 요청 내용과 Worker 의존성을 함께 판단합니다. 현재 단계에서는 설정값 저장만 수행하고 실행 엔진에는 연결하지 않습니다.")
            ScenarioExecutionModeSelector(
                value = executionModePreview,
                onValueChange = { executionModePreview = it }
            )
            ScenarioEditKeyValue("원본 defaultExecutionMode", scenario.defaultExecutionMode.name)
            ScenarioEditKeyValue("선택 defaultExecutionMode", executionModePreview.name)
        }

        ScenarioEditSection(title = "핵심 Worker 지정", initiallyExpanded = false) {
            ScenarioEditNotice("Orchestrator / Synthesis는 고정 개체가 아니라 Scenario에서 지정 가능한 WorkerProfile 참조입니다. 이번 단계에서는 Scenario에 포함된 Worker 중에서 선택하고, 선택값만 저장합니다. 실제 실행 연결은 아직 없습니다.")
            ScenarioEditKeyValue(
                "orchestratorProfileId",
                orchestratorPreviewId ?: "Orchestrator 미지정"
            )
            ScenarioWorkerPicker(
                title = "Orchestrator 선택",
                emptyLabel = "Orchestrator 미지정",
                currentWorkerId = orchestratorPreviewId,
                scenarioWorkerIds = scenario.workerProfileIds,
                workersById = workersById,
                onSelect = { orchestratorPreviewId = it }
            )
            ScenarioEditKeyValue(
                "synthesisProfileId",
                synthesisPreviewId ?: "Synthesis 미지정"
            )
            ScenarioWorkerPicker(
                title = "Synthesis 선택",
                emptyLabel = "Synthesis 미지정",
                currentWorkerId = synthesisPreviewId,
                scenarioWorkerIds = scenario.workerProfileIds,
                workersById = workersById,
                onSelect = { synthesisPreviewId = it }
            )
        }

        ScenarioEditSection(title = "경고 / 참조 상태", initiallyExpanded = false) {
            val missingWorkerIds = scenario.workerProfileIds.filterNot { it in workersById }
            val disabledWorkerIds = scenario.workerProfileIds.filter { workersById[it]?.enabled == false }
            if (scenario.workerProfileIds.isEmpty()) {
                ScenarioWarningText("Worker 없음: 실행 후보에서는 LIMITED 또는 BLOCKED 후보입니다.")
            }
            if (missingWorkerIds.isNotEmpty()) {
                ScenarioWarningText("orphan Worker: ${missingWorkerIds.joinToString()}")
            }
            if (disabledWorkerIds.isNotEmpty()) {
                ScenarioWarningText("disabled Worker 포함: ${disabledWorkerIds.joinToString()}")
            }
            if (orchestratorPreviewId != null && orchestratorPreviewId !in workersById) {
                ScenarioWarningText("orchestratorProfileId가 존재하지 않는 Worker를 참조합니다.")
            }
            if (synthesisPreviewId != null && synthesisPreviewId !in workersById) {
                ScenarioWarningText("synthesisProfileId가 존재하지 않는 Worker를 참조합니다.")
            }
            if (orchestratorPreviewId != null && orchestratorPreviewId !in scenario.workerProfileIds) {
                ScenarioWarningText("orchestratorProfileId가 Scenario WorkerSet에 포함되지 않은 Worker를 참조합니다.")
            }
            if (synthesisPreviewId != null && synthesisPreviewId !in scenario.workerProfileIds) {
                ScenarioWarningText("synthesisProfileId가 Scenario WorkerSet에 포함되지 않은 Worker를 참조합니다.")
            }
            ScenarioEditNotice("자동 삭제는 하지 않습니다. 참조 제거 / 다른 Worker 재지정 / 일단 유지는 후속 구현 예정입니다.")
        }

        ScenarioEditActionBar(
            saveMessage = saveMessage,
            dirty = dirty,
            onSave = {
                val normalizedName = name.trim()
                if (normalizedName.isBlank()) {
                    saveMessage = "name은 비워둘 수 없습니다."
                } else {
                    val updatedScenario = scenario.copy(
                        name = normalizedName,
                        description = description,
                        enabled = enabledPreview,
                        defaultExecutionMode = executionModePreview,
                        orchestratorProfileId = orchestratorPreviewId,
                        synthesisProfileId = synthesisPreviewId,
                        userModified = true,
                        updatedAt = System.currentTimeMillis(),
                    )

                    val saved = runCatching {
                        WorkerProfileStore.upsertScenario(updatedScenario)
                    }

                    if (saved.isSuccess) {
                        saveMessage = "저장 완료"
                        onSaved()
                    } else {
                        saveMessage = "저장 실패: ${saved.exceptionOrNull()?.message ?: "알 수 없는 오류"}"
                    }
                }
            },
            onCancel = onCancel
        )
    }
}

@Composable
private fun ScenarioEditHeaderCard(
    dirty: Boolean,
    onBack: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3340)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scenario 편집",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (dirty) "변경사항 있음" else "변경사항 없음",
                        color = if (dirty) Color(0xFFFFD18A) else Color(0xFFB8E6C8)
                    )
                }
                TextButton(onClick = onBack) {
                    Text(text = "목록", color = Color(0xFFBFD7FF))
                }
            }
            Text(
                text = "이번 단계는 name / description / enabled / defaultExecutionMode / Orchestrator / Synthesis 지정 저장만 연결합니다. WorkerSet / 실행 연결은 아직 없습니다.",
                color = Color(0xFFD0D3DA)
            )
        }
    }
}

@Composable
private fun ScenarioEditSection(
    title: String,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }

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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) "접기" else "펼치기", color = Color(0xFFBFD7FF))
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun ScenarioEditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ScenarioBooleanToggle(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFD0D3DA), modifier = Modifier.weight(1f))
        TextButton(onClick = { onValueChange(!value) }) {
            Text(text = if (value) "true" else "false", color = Color(0xFFBFD7FF))
        }
    }
}

@Composable
private fun ScenarioExecutionModeSelector(
    value: ExecutionMode,
    onValueChange: (ExecutionMode) -> Unit,
) {
    val values = ExecutionMode.values().toList()
    val currentIndex = values.indexOf(value).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "defaultExecutionMode",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9DB7E8)
            )
            Text(text = value.name, color = Color(0xFFD0D3DA))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onValueChange(values[(currentIndex - 1 + values.size) % values.size]) }) {
                Text(text = "이전", color = Color(0xFFBFD7FF))
            }
            TextButton(onClick = { onValueChange(values[(currentIndex + 1) % values.size]) }) {
                Text(text = "다음", color = Color(0xFFBFD7FF))
            }
        }
    }
}

@Composable
private fun ScenarioWorkerReferenceRow(
    index: Int,
    workerId: String,
    worker: ConversationWorkerProfile?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "${index + 1}. ${worker?.displayName?.ifBlank { workerId } ?: "missing Worker"}",
            fontWeight = FontWeight.Bold,
            color = if (worker == null) Color(0xFFFFB0B0) else Color.White
        )
        Text(
            text = worker?.let { "${it.roleLabel.ifBlank { "역할 미지정" }} · ${if (it.enabled) "활성" else "비활성"}" }
                ?: "이 WorkerProfile ID를 찾을 수 없습니다.",
            color = Color(0xFFD0D3DA)
        )
        Text(text = workerId, color = Color(0xFF9DB7E8))
    }
}

@Composable
private fun ScenarioWorkerPicker(
    title: String,
    emptyLabel: String,
    currentWorkerId: String?,
    scenarioWorkerIds: List<String>,
    workersById: Map<String, ConversationWorkerProfile>,
    onSelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        ScenarioSelectableWorkerRow(
            label = emptyLabel,
            description = "null 저장",
            selected = currentWorkerId == null,
            onClick = { onSelect(null) }
        )

        if (scenarioWorkerIds.isEmpty()) {
            ScenarioWarningText("Scenario에 포함된 Worker가 없습니다. 미지정만 선택할 수 있습니다.")
        }

        scenarioWorkerIds.forEach { workerId ->
            val worker = workersById[workerId]
            if (worker == null) {
                ScenarioSelectableWorkerRow(
                    label = workerId,
                    description = "참조 Worker 없음",
                    selected = currentWorkerId == workerId,
                    onClick = { onSelect(workerId) }
                )
            } else {
                ScenarioSelectableWorkerRow(
                    label = worker.displayName.ifBlank { worker.workerProfileId },
                    description = "${worker.roleLabel.ifBlank { "역할 미지정" }} · ${if (worker.enabled) "활성" else "비활성"}",
                    selected = currentWorkerId == worker.workerProfileId,
                    onClick = { onSelect(worker.workerProfileId) }
                )
            }
        }

        if (currentWorkerId != null && currentWorkerId !in scenarioWorkerIds) {
            ScenarioWarningText("현재 선택값은 Scenario WorkerSet에 포함되지 않았습니다. 미지정 또는 포함 Worker로 변경해 저장할 수 있습니다.")
        }
    }
}

@Composable
private fun ScenarioSelectableWorkerRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Color(0xFF33445C) else Color(0xFF1B222D),
                MaterialTheme.shapes.small
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = if (selected) "✓ $label" else label, color = Color.White, fontWeight = FontWeight.Bold)
            Text(text = description, color = Color(0xFFB8BCC6))
        }
    }
}

@Composable
private fun ScenarioPlaceholderAction(label: String) {
    TextButton(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B222D), MaterialTheme.shapes.small)
    ) {
        Text(text = "$label · 후속 구현 예정", color = Color(0xFFBFD7FF))
    }
}

@Composable
private fun ScenarioEditNotice(text: String) {
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
private fun ScenarioWarningText(text: String) {
    Text(
        text = text,
        color = Color(0xFFFFC3C3),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3A2525), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun ScenarioEditKeyValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        SelectionContainer {
            Text(text = value.ifBlank { "없음" }, color = Color(0xFFD0D3DA))
        }
    }
}

@Composable
private fun ScenarioEditActionBar(
    saveMessage: String?,
    dirty: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (saveMessage != null) {
                Text(text = saveMessage, color = Color(0xFFFFD18A))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF33445C), MaterialTheme.shapes.medium)
                ) {
                    Text(
                        text = if (dirty) "저장" else "변경 없음",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2B3442), MaterialTheme.shapes.medium)
                ) {
                    Text(text = "취소", color = Color(0xFFBFD7FF))
                }
            }
        }
    }
}
