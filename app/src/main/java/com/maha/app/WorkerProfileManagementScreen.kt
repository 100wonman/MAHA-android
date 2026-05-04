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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WorkerProfileManagementScreen(
    modifier: Modifier = Modifier,
) {
    val workerEnvelope = remember { WorkerProfileStore.loadWorkerProfiles() }
    val scenarioEnvelope = remember { WorkerProfileStore.loadConversationScenarios() }
    val workers = workerEnvelope.workerProfiles.sortedWith(
        compareBy<ConversationWorkerProfile> { it.executionOrder }
            .thenBy { it.displayName.lowercase() }
    )
    val scenarios = scenarioEnvelope.scenarios.sortedBy { it.name.lowercase() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WorkerProfileInfoCard(
            workerCount = workers.size,
            scenarioCount = scenarios.size,
            schemaVersion = workerEnvelope.schemaVersion,
        )

        WorkerProfileReadOnlyNoticeCard()

        WorkerProfileSectionTitle(
            title = "Worker 목록",
            subtitle = "WorkerProfileStore in-memory skeleton 데이터입니다. 실제 저장/편집은 아직 연결되지 않았습니다."
        )

        if (workers.isEmpty()) {
            WorkerProfileEmptyCard(text = "표시할 Worker Profile이 없습니다.")
        } else {
            workers.forEach { worker ->
                WorkerProfilePreviewCard(worker = worker)
            }
        }

        WorkerProfileSectionTitle(
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
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Worker Profile 관리",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Worker Profile은 대화모드에서 사용할 AI 작업자 설정입니다. 각 Worker는 역할, System Instruction, Provider, Model, capability override를 가질 수 있습니다.",
                color = Color(0xFFD0D3DA)
            )
            Text(
                text = "현재 화면은 read-only skeleton preview입니다. 실제 저장/로드, 편집 저장, 대화 실행 연결은 후속 단계에서 지원됩니다.",
                color = Color(0xFFFFD180)
            )
            WorkerProfileTagRow(
                values = listOf(
                    "schemaVersion $schemaVersion",
                    "Worker ${workerCount}개",
                    "Scenario ${scenarioCount}개",
                    "실제 실행 미연결"
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "연결 금지 상태",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "이 화면은 Provider 호출, RAG, Web Search, Tool 실행, ConversationEngine에 연결되지 않습니다. Worker 편집과 저장 기능도 아직 비활성입니다.",
                color = Color(0xFFE6D0B8)
            )
        }
    }
}

@Composable
private fun WorkerProfileSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = subtitle,
            color = Color(0xFFB8BCC6)
        )
    }
}

@Composable
private fun WorkerProfilePreviewCard(worker: ConversationWorkerProfile) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202733)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3B4556), MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        color = Color.White
                    )
                    Text(
                        text = worker.roleLabel.ifBlank { "역할 미지정" },
                        color = Color(0xFFB8BCC6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) "접기" else "상세", color = Color(0xFFBFD7FF))
                }
            }

            Text(
                text = worker.roleDescription.ifBlank { "역할 설명 없음" },
                color = Color(0xFFD0D3DA)
            )

            WorkerProfileTagRow(
                values = listOf(
                    if (worker.enabled) "활성" else "비활성",
                    if (worker.isDefaultTemplate) "기본 템플릿" else "사용자 Worker",
                    if (worker.userModified) "사용자 수정본" else "원본",
                    if (worker.canRunInParallel) "병렬 가능" else "순차 후보",
                    "순서 ${worker.executionOrder}"
                )
            )

            WorkerProfileKeyValue("Provider", worker.providerId ?: "Provider 미지정")
            WorkerProfileKeyValue("Model", worker.modelId ?: "Model 미지정")

            SelectionContainer {
                Text(
                    text = "System Instruction 미리보기\n${worker.systemInstruction.previewText(220)}",
                    color = Color(0xFFD0D3DA)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                WorkerProfileKeyValue("dependsOnWorkerIds", worker.dependsOnWorkerIds.joinToString().ifBlank { "없음" })
                WorkerProfileKeyValue("expectedOutputType", worker.outputPolicy.expectedOutputType.name)
                WorkerProfileKeyValue("inputPolicy", worker.inputPolicy.toReadableSummary())
                WorkerProfileKeyValue("outputPolicy", worker.outputPolicy.toReadableSummary())
                WorkerProfileKeyValue("capabilityOverrides", worker.capabilityOverrides.toReadableSummary())
                TextButton(onClick = { }) {
                    Text(text = "상세/편집은 후속 구현 예정", color = Color(0xFFFFD180))
                }
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        color = Color.White
                    )
                    Text(
                        text = scenario.defaultExecutionMode.name,
                        color = Color(0xFFB8BCC6)
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) "접기" else "상세", color = Color(0xFFBFD7FF))
                }
            }

            Text(
                text = scenario.description.ifBlank { "설명 없음" },
                color = Color(0xFFD0D3DA)
            )

            WorkerProfileTagRow(
                values = listOf(
                    if (scenario.enabled) "활성" else "비활성",
                    if (scenario.isDefaultTemplate) "기본 Scenario" else "사용자 Scenario",
                    if (scenario.userModified) "사용자 수정본" else "원본",
                    "Worker ${scenario.workerProfileIds.size}개"
                )
            )

            WorkerProfileKeyValue("Orchestrator", scenario.orchestratorProfileId ?: "Orchestrator 미지정")
            WorkerProfileKeyValue("Synthesis", scenario.synthesisProfileId ?: "Synthesis 미지정")

            if (expanded) {
                WorkerProfileKeyValue("scenarioId", scenario.scenarioId)
                WorkerProfileKeyValue("workerProfileIds", scenario.workerProfileIds.joinToString().ifBlank { "없음" })
                WorkerProfileKeyValue("userEditable", scenario.userEditable.toString())
                TextButton(onClick = { }) {
                    Text(text = "Scenario 편집은 후속 구현 예정", color = Color(0xFFFFD180))
                }
            }
        }
    }
}

@Composable
private fun WorkerProfileTagRow(values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(2).forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowValues.forEach { value ->
                    Text(
                        text = value,
                        color = Color(0xFFD6E4FF),
                        modifier = Modifier
                            .background(Color(0xFF2B3442), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun String.previewText(maxLength: Int): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return "System Instruction 없음"
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength).trimEnd() + "…"
}

private fun WorkerInputPolicy.toReadableSummary(): String {
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

private fun WorkerOutputPolicy.toReadableSummary(): String {
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

private fun WorkerCapabilityOverrides.toReadableSummary(): String {
    return listOf(
        "functionCalling=${functionCalling.name}",
        "webSearch=${webSearch.name}",
        "codeExecution=${codeExecution.name}",
        "structuredOutput=${structuredOutput.name}",
        "ragSearch=${ragSearch.name}",
        "memoryRecall=${memoryRecall.name}",
        "fileRead=${fileRead.name}",
        "fileWrite=${fileWrite.name}",
        "codeCheck=${codeCheck.name}",
        "parallelExecution=${parallelExecution.name}"
    ).joinToString(" · ")
}
