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
fun WorkerProfileEditScreen(
    worker: ConversationWorkerProfile,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember(worker.workerProfileId) { mutableStateOf(worker.displayName) }
    var roleLabel by remember(worker.workerProfileId) { mutableStateOf(worker.roleLabel) }
    var roleDescription by remember(worker.workerProfileId) { mutableStateOf(worker.roleDescription) }
    var systemInstruction by remember(worker.workerProfileId) { mutableStateOf(worker.systemInstruction) }
    var enabledPreview by remember(worker.workerProfileId) { mutableStateOf(worker.enabled) }
    var saveMessage by remember(worker.workerProfileId) { mutableStateOf<String?>(null) }

    val dirty = displayName != worker.displayName ||
            roleLabel != worker.roleLabel ||
            roleDescription != worker.roleDescription ||
            systemInstruction != worker.systemInstruction ||
            enabledPreview != worker.enabled

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WorkerEditHeaderCard(
            title = "Worker Profile 편집",
            subtitle = "기본 정보와 System Instruction 저장만 연결됩니다. Provider/Model, capability, policy, 실행 연결은 아직 제외됩니다.",
            dirty = dirty,
            onBack = onCancel
        )

        WorkerEditSection(title = "기본 정보", initiallyExpanded = true) {
            WorkerEditTextField(
                label = "displayName",
                value = displayName,
                onValueChange = { displayName = it },
                singleLine = true
            )
            WorkerEditTextField(
                label = "roleLabel",
                value = roleLabel,
                onValueChange = { roleLabel = it },
                singleLine = true
            )
            WorkerEditTextField(
                label = "roleDescription",
                value = roleDescription,
                onValueChange = { roleDescription = it },
                minLines = 2
            )
            WorkerEditPlaceholderRow(
                label = "enabled placeholder",
                value = if (enabledPreview) "활성" else "비활성",
                actionLabel = if (enabledPreview) "비활성 미리보기" else "활성 미리보기",
                onAction = { enabledPreview = !enabledPreview }
            )
        }

        WorkerEditSection(title = "System Instruction", initiallyExpanded = true) {
            WorkerEditTextField(
                label = "systemInstruction",
                value = systemInstruction,
                onValueChange = { systemInstruction = it },
                minLines = 7
            )
            Text(
                text = "글자 수: ${systemInstruction.length}",
                color = Color(0xFFB8BCC6)
            )
            WorkerEditNotice("API Key, 비밀번호, 개인 민감정보를 System Instruction에 넣지 마세요.")
            WorkerEditPlaceholderRow(
                label = "기본값 복원",
                value = "후속 구현 예정",
                actionLabel = "복원 placeholder",
                onAction = { saveMessage = "기본값 복원 연결은 후속 구현 예정입니다." }
            )
        }

        WorkerEditSection(title = "Provider / Model", initiallyExpanded = true) {
            WorkerEditKeyValue("providerId 현재 값", worker.providerId ?: "Provider 미지정")
            WorkerEditKeyValue("modelId 현재 값", worker.modelId ?: "Model 미지정")
            WorkerEditPlaceholderRow(
                label = "Provider 선택",
                value = "선택 UI placeholder",
                actionLabel = "후속 구현 예정",
                onAction = { saveMessage = "Provider 선택 저장은 후속 구현 예정입니다." }
            )
            WorkerEditPlaceholderRow(
                label = "Model 선택",
                value = "선택 UI placeholder",
                actionLabel = "후속 구현 예정",
                onAction = { saveMessage = "Model 선택 저장은 후속 구현 예정입니다." }
            )
            WorkerEditNotice("WorkerProfile에는 providerId/modelId 참조만 저장해야 하며 API Key, baseUrl, rawModelName 복사 저장은 금지합니다.")
        }

        WorkerEditSection(title = "Capability Override", initiallyExpanded = false) {
            WorkerEditKeyValue("현재 capabilityOverrides", worker.capabilityOverrides.toReadableSummary())
            WorkerEditNotice("이 설정은 Worker가 사용하길 원하는 capability입니다. 실제 실행 가능 여부는 Provider/Model 상태와 MAHA 구현 상태를 함께 판단합니다.")
        }

        WorkerEditSection(title = "InputPolicy", initiallyExpanded = false) {
            WorkerEditKeyValue("현재 inputPolicy", worker.inputPolicy.toReadableSummary())
            WorkerEditNotice("입력 정책 편집 UI는 후속 구현 예정입니다.")
        }

        WorkerEditSection(title = "OutputPolicy", initiallyExpanded = false) {
            WorkerEditKeyValue("현재 outputPolicy", worker.outputPolicy.toReadableSummary())
            WorkerEditNotice("출력 정책 편집 UI는 후속 구현 예정입니다.")
        }

        WorkerEditSection(title = "실행 계획", initiallyExpanded = false) {
            WorkerEditKeyValue("executionOrder", worker.executionOrder.toString())
            WorkerEditKeyValue("canRunInParallel", worker.canRunInParallel.toString())
            WorkerEditKeyValue("dependsOnWorkerIds", worker.dependsOnWorkerIds.joinToString().ifBlank { "없음" })
            WorkerEditNotice("실행 순서, 병렬 가능 여부, 의존 Worker 편집은 후속 구현 예정입니다.")
        }

        WorkerEditActionBar(
            saveMessage = saveMessage,
            dirty = dirty,
            onSave = {
                val normalizedDisplayName = displayName.trim()
                if (normalizedDisplayName.isBlank()) {
                    saveMessage = "displayName은 비워둘 수 없습니다."
                } else {
                    val updatedProfile = worker.copy(
                        displayName = normalizedDisplayName,
                        roleLabel = roleLabel.trim(),
                        roleDescription = roleDescription,
                        systemInstruction = systemInstruction,
                        enabled = enabledPreview,
                        userModified = true,
                        updatedAt = System.currentTimeMillis(),
                    )

                    runCatching {
                        WorkerProfileStore.upsertWorkerProfile(updatedProfile)
                    }.onSuccess {
                        saveMessage = "저장 완료"
                        onSaved()
                    }.onFailure { error ->
                        saveMessage = "저장 실패: ${error.message ?: "알 수 없는 오류"}"
                    }
                }
            },
            onCancel = onCancel
        )
    }
}

@Composable
private fun WorkerEditHeaderCard(
    title: String,
    subtitle: String,
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
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (dirty) "변경사항 있음 · 저장 미연결" else "변경사항 없음",
                        color = if (dirty) Color(0xFFFFD18A) else Color(0xFFB8E6C8)
                    )
                }
                TextButton(onClick = onBack) {
                    Text(text = "목록", color = Color(0xFFBFD7FF))
                }
            }
            Text(text = subtitle, color = Color(0xFFD0D3DA))
        }
    }
}

@Composable
private fun WorkerEditSection(
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
private fun WorkerEditTextField(
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
private fun WorkerEditPlaceholderRow(
    label: String,
    value: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkerEditKeyValue(label, value)
        TextButton(onClick = onAction) {
            Text(text = actionLabel, color = Color(0xFFBFD7FF))
        }
    }
}

@Composable
private fun WorkerEditKeyValue(label: String, value: String) {
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
private fun WorkerEditNotice(text: String) {
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
private fun WorkerEditActionBar(
    saveMessage: String?,
    dirty: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252E3B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!saveMessage.isNullOrBlank()) {
                Text(text = saveMessage, color = Color(0xFFFFD18A))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "취소", color = Color(0xFFBFD7FF))
                }
                TextButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF33445C), MaterialTheme.shapes.medium)
                ) {
                    Text(text = "저장", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "저장 대상: displayName, roleLabel, roleDescription, systemInstruction, enabled. Provider/Model, capability, policy, 실행 계획은 기존 값을 보존합니다.",
                color = Color(0xFFB8BCC6)
            )
        }
    }
}
