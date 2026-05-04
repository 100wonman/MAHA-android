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
import androidx.compose.ui.platform.LocalContext
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
    var selectedProviderId by remember(worker.workerProfileId) { mutableStateOf(worker.providerId) }
    var selectedModelId by remember(worker.workerProfileId) { mutableStateOf(worker.modelId) }
    var saveMessage by remember(worker.workerProfileId) { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val providerSettingsStore = remember(context) { ProviderSettingsStore(context) }
    val providerProfiles = remember(context) {
        providerSettingsStore.loadProviderProfiles()
            .sortedWith(compareBy<ProviderProfile> { it.providerType.name }.thenBy { it.displayName.lowercase() })
    }
    val modelProfiles = remember(context) {
        providerSettingsStore.loadModelProfiles()
            .sortedWith(compareBy<ConversationModelProfile> { it.providerId }.thenBy { it.displayName.lowercase() })
    }
    val providerById = remember(providerProfiles) { providerProfiles.associateBy { it.providerId } }
    val modelById = remember(modelProfiles) { modelProfiles.associateBy { it.modelId } }
    val filteredModels = remember(selectedProviderId, modelProfiles) {
        selectedProviderId?.let { providerId ->
            modelProfiles.filter { it.providerId == providerId }
        } ?: modelProfiles
    }

    val dirty = displayName != worker.displayName ||
            roleLabel != worker.roleLabel ||
            roleDescription != worker.roleDescription ||
            systemInstruction != worker.systemInstruction ||
            enabledPreview != worker.enabled ||
            selectedProviderId != worker.providerId ||
            selectedModelId != worker.modelId

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WorkerEditHeaderCard(
            title = "Worker Profile 편집",
            subtitle = "기본 정보, System Instruction, Provider/Model 참조 저장만 연결됩니다. capability, policy, 실행 연결은 아직 제외됩니다.",
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
            WorkerEditNotice("Provider/Model은 Worker가 사용할 실행 엔진 참조입니다. API Key, baseUrl, rawModelName 원문은 WorkerProfile에 복사 저장하지 않습니다.")

            WorkerProviderSelectionList(
                providers = providerProfiles,
                selectedProviderId = selectedProviderId,
                hasApiKey = { providerId -> providerSettingsStore.hasProviderApiKey(providerId) },
                onSelectProvider = { nextProviderId ->
                    selectedProviderId = nextProviderId
                    val selectedModelStillValid = nextProviderId != null &&
                            selectedModelId != null &&
                            modelProfiles.any { model ->
                                model.modelId == selectedModelId && model.providerId == nextProviderId
                            }
                    if (!selectedModelStillValid) {
                        selectedModelId = null
                    }
                    saveMessage = if (nextProviderId == null) {
                        "Provider 미지정 선택: Model도 미지정으로 초기화했습니다."
                    } else {
                        "Provider 선택 변경: 다른 Provider의 기존 Model 선택은 초기화됩니다."
                    }
                }
            )

            WorkerModelSelectionList(
                models = filteredModels,
                providersById = providerById,
                selectedProviderId = selectedProviderId,
                selectedModelId = selectedModelId,
                onSelectModel = { nextModelId ->
                    selectedModelId = nextModelId
                    if (nextModelId == null) {
                        saveMessage = "Model 미지정을 선택했습니다."
                    }
                }
            )

            val selectedProviderLabel = selectedProviderId?.let { providerId ->
                providerById[providerId]?.let { provider ->
                    "${provider.displayName} (${provider.providerType.name})"
                } ?: "참조 Provider 없음: $providerId"
            } ?: "Provider 미지정"
            val selectedModelLabel = selectedModelId?.let { modelId ->
                modelById[modelId]?.let { model ->
                    "${model.displayName} / ${model.rawModelName}"
                } ?: "참조 Model 없음: $modelId"
            } ?: "Model 미지정"
            WorkerEditKeyValue("저장될 providerId", selectedProviderId ?: "null")
            WorkerEditKeyValue("선택 Provider", selectedProviderLabel)
            WorkerEditKeyValue("저장될 modelId", selectedModelId ?: "null")
            WorkerEditKeyValue("선택 Model", selectedModelLabel)
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
                        providerId = selectedProviderId,
                        modelId = selectedModelId,
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
                        text = if (dirty) "변경사항 있음" else "변경사항 없음",
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
private fun WorkerProviderSelectionList(
    providers: List<ProviderProfile>,
    selectedProviderId: String?,
    hasApiKey: (String) -> Boolean,
    onSelectProvider: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Provider 선택",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        WorkerSelectionButton(
            selected = selectedProviderId == null,
            label = "Provider 미지정",
            description = "providerId=null · 저장 허용",
            onClick = { onSelectProvider(null) }
        )
        if (providers.isEmpty()) {
            Text(text = "등록된 Provider가 없습니다.", color = Color(0xFFFFD18A))
        } else {
            providers.forEach { provider ->
                val keyRequired = provider.providerType.name in setOf("GOOGLE", "OPENAI", "OPENAI_COMPATIBLE", "NVIDIA")
                val keyState = if (keyRequired) {
                    if (hasApiKey(provider.providerId)) "Key 설정됨" else "Key 미설정"
                } else {
                    "Key 선택"
                }
                WorkerSelectionButton(
                    selected = selectedProviderId == provider.providerId,
                    label = provider.displayName.ifBlank { provider.providerId },
                    description = "${provider.providerType.name} · $keyState · ${provider.providerId}",
                    onClick = { onSelectProvider(provider.providerId) }
                )
            }
        }
    }
}

@Composable
private fun WorkerModelSelectionList(
    models: List<ConversationModelProfile>,
    providersById: Map<String, ProviderProfile>,
    selectedProviderId: String?,
    selectedModelId: String?,
    onSelectModel: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Model 선택",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9DB7E8)
        )
        WorkerSelectionButton(
            selected = selectedModelId == null,
            label = "Model 미지정",
            description = "modelId=null · 저장 허용",
            onClick = { onSelectModel(null) }
        )
        if (models.isEmpty()) {
            val message = if (selectedProviderId == null) {
                "등록된 Model이 없습니다."
            } else {
                "선택한 Provider에 연결된 Model이 없습니다."
            }
            Text(text = message, color = Color(0xFFFFD18A))
        } else {
            models.forEach { model ->
                val provider = providersById[model.providerId]
                val providerLabel = provider?.displayName ?: "참조 Provider 없음"
                WorkerSelectionButton(
                    selected = selectedModelId == model.modelId,
                    label = model.displayName.ifBlank { model.rawModelName.ifBlank { model.modelId } },
                    description = "${model.rawModelName.ifBlank { model.modelId }} · $providerLabel · ${model.modelId}",
                    onClick = { onSelectModel(model.modelId) }
                )
            }
        }
    }
}

@Composable
private fun WorkerSelectionButton(
    selected: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) Color(0xFF33445C) else Color(0xFF1E2530)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, MaterialTheme.shapes.small)
            .border(1.dp, if (selected) Color(0xFFBFD7FF) else Color(0xFF3B4556), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (selected) "✓ $label" else label,
                color = if (selected) Color.White else Color(0xFFBFD7FF),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = description,
            color = Color(0xFFB8BCC6),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
                text = "저장 대상: displayName, roleLabel, roleDescription, systemInstruction, enabled, providerId, modelId. capability, policy, 실행 계획은 기존 값을 보존합니다.",
                color = Color(0xFFB8BCC6)
            )
        }
    }
}
