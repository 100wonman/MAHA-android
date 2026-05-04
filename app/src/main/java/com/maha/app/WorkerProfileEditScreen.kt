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
    var functionCallingOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.functionCalling) }
    var webSearchOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.webSearch) }
    var codeExecutionOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.codeExecution) }
    var structuredOutputOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.structuredOutput) }
    var thinkingSummaryOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.thinkingSummary) }
    var ragSearchOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.ragSearch) }
    var memoryRecallOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.memoryRecall) }
    var fileReadOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.fileRead) }
    var fileWriteOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.fileWrite) }
    var codeCheckOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.codeCheck) }
    var parallelExecutionOverride by remember(worker.workerProfileId) { mutableStateOf(worker.capabilityOverrides.parallelExecution) }
    var userInputOnlyPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.userInputOnly) }
    var previousWorkerOutputPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.previousWorkerOutput) }
    var ragContextAllowedPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.ragContextAllowed) }
    var memoryContextAllowedPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.memoryContextAllowed) }
    var webSearchContextAllowedPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.webSearchContextAllowed) }
    var maxInputCharsText by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.maxInputChars.toString()) }
    var includeRunHistoryPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.inputPolicy.includeRunHistory) }
    var expectedOutputTypePolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.expectedOutputType) }
    var requireJsonPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.requireJson) }
    var requireMarkdownTablePolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.requireMarkdownTable) }
    var requireCodeBlockPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.requireCodeBlock) }
    var allowPlainTextPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.allowPlainText) }
    var maxOutputCharsText by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.maxOutputChars.toString()) }
    var passToNextWorkerPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.passToNextWorker) }
    var exposeToUserPolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.exposeToUser) }
    var saveAsMemoryCandidatePolicy by remember(worker.workerProfileId) { mutableStateOf(worker.outputPolicy.saveAsMemoryCandidate) }
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
    val capabilityOverridesPreview = WorkerCapabilityOverrides(
        functionCalling = functionCallingOverride,
        webSearch = webSearchOverride,
        codeExecution = codeExecutionOverride,
        structuredOutput = structuredOutputOverride,
        thinkingSummary = thinkingSummaryOverride,
        ragSearch = ragSearchOverride,
        memoryRecall = memoryRecallOverride,
        fileRead = fileReadOverride,
        fileWrite = fileWriteOverride,
        codeCheck = codeCheckOverride,
        parallelExecution = parallelExecutionOverride,
    )
    val inputPolicyPreview = WorkerInputPolicy(
        userInputOnly = userInputOnlyPolicy,
        previousWorkerOutput = previousWorkerOutputPolicy,
        selectedWorkerOutputs = worker.inputPolicy.selectedWorkerOutputs,
        ragContextAllowed = ragContextAllowedPolicy,
        memoryContextAllowed = memoryContextAllowedPolicy,
        webSearchContextAllowed = webSearchContextAllowedPolicy,
        maxInputChars = parseWorkerPolicyInt(maxInputCharsText, worker.inputPolicy.maxInputChars),
        includeRunHistory = includeRunHistoryPolicy,
    )
    val outputPolicyPreview = WorkerOutputPolicy(
        expectedOutputType = expectedOutputTypePolicy,
        requireJson = requireJsonPolicy,
        requireMarkdownTable = requireMarkdownTablePolicy,
        requireCodeBlock = requireCodeBlockPolicy,
        allowPlainText = allowPlainTextPolicy,
        maxOutputChars = parseWorkerPolicyInt(maxOutputCharsText, worker.outputPolicy.maxOutputChars),
        passToNextWorker = passToNextWorkerPolicy,
        exposeToUser = exposeToUserPolicy,
        saveAsMemoryCandidate = saveAsMemoryCandidatePolicy,
    )

    val dirty = displayName != worker.displayName ||
            roleLabel != worker.roleLabel ||
            roleDescription != worker.roleDescription ||
            systemInstruction != worker.systemInstruction ||
            enabledPreview != worker.enabled ||
            selectedProviderId != worker.providerId ||
            selectedModelId != worker.modelId ||
            capabilityOverridesPreview != worker.capabilityOverrides ||
            inputPolicyPreview != worker.inputPolicy ||
            outputPolicyPreview != worker.outputPolicy

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WorkerEditHeaderCard(
            title = "Worker Profile 편집",
            subtitle = "기본 정보, System Instruction, Provider/Model, capability override 저장만 연결됩니다. policy, 실행 연결은 아직 제외됩니다.",
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
            WorkerEditNotice("Worker override는 실행 의도입니다. USER_ENABLED 또는 AVAILABLE이어도 실제 실행 가능 보장은 아니며, Provider/Model capability, API Key, MAHA 구현 상태를 Capability Layer가 함께 판단합니다.")
            WorkerCapabilityStatusSelector(
                label = "functionCalling",
                value = functionCallingOverride,
                onValueChange = { functionCallingOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "webSearch",
                value = webSearchOverride,
                onValueChange = { webSearchOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "codeExecution",
                value = codeExecutionOverride,
                onValueChange = { codeExecutionOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "structuredOutput",
                value = structuredOutputOverride,
                onValueChange = { structuredOutputOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "thinkingSummary",
                value = thinkingSummaryOverride,
                onValueChange = { thinkingSummaryOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "ragSearch",
                value = ragSearchOverride,
                onValueChange = { ragSearchOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "memoryRecall",
                value = memoryRecallOverride,
                onValueChange = { memoryRecallOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "fileRead",
                value = fileReadOverride,
                onValueChange = { fileReadOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "fileWrite",
                value = fileWriteOverride,
                onValueChange = { fileWriteOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "codeCheck",
                value = codeCheckOverride,
                onValueChange = { codeCheckOverride = it }
            )
            WorkerCapabilityStatusSelector(
                label = "parallelExecution",
                value = parallelExecutionOverride,
                onValueChange = { parallelExecutionOverride = it }
            )
            WorkerEditKeyValue("저장될 capabilityOverrides", capabilityOverridesPreview.toReadableSummary())
        }

        WorkerEditSection(title = "InputPolicy", initiallyExpanded = false) {
            WorkerEditNotice("InputPolicy는 이 Worker가 어떤 입력과 context를 받을 수 있는지 정의합니다. 실제 입력 전달 적용은 후속 Orchestrator/Execution 연결 단계에서 지원됩니다.")
            WorkerBooleanToggle(
                label = "userInputOnly",
                value = userInputOnlyPolicy,
                onValueChange = { userInputOnlyPolicy = it }
            )
            WorkerBooleanToggle(
                label = "previousWorkerOutput",
                value = previousWorkerOutputPolicy,
                onValueChange = { previousWorkerOutputPolicy = it }
            )
            WorkerEditKeyValue(
                label = "selectedWorkerOutputs",
                value = worker.inputPolicy.selectedWorkerOutputs.joinToString().ifBlank { "후속 multi-select 구현 예정" }
            )
            WorkerBooleanToggle(
                label = "ragContextAllowed",
                value = ragContextAllowedPolicy,
                onValueChange = { ragContextAllowedPolicy = it }
            )
            WorkerBooleanToggle(
                label = "memoryContextAllowed",
                value = memoryContextAllowedPolicy,
                onValueChange = { memoryContextAllowedPolicy = it }
            )
            WorkerBooleanToggle(
                label = "webSearchContextAllowed",
                value = webSearchContextAllowedPolicy,
                onValueChange = { webSearchContextAllowedPolicy = it }
            )
            WorkerPolicyIntField(
                label = "maxInputChars",
                value = maxInputCharsText,
                onValueChange = { maxInputCharsText = it },
                fallbackValue = worker.inputPolicy.maxInputChars
            )
            WorkerBooleanToggle(
                label = "includeRunHistory",
                value = includeRunHistoryPolicy,
                onValueChange = { includeRunHistoryPolicy = it }
            )
            WorkerEditKeyValue("저장될 inputPolicy", inputPolicyPreview.toReadableSummary())
        }

        WorkerEditSection(title = "OutputPolicy", initiallyExpanded = false) {
            WorkerEditNotice("OutputPolicy는 이 Worker 결과를 어떤 형식으로 만들고, 다음 Worker나 사용자에게 어떻게 전달할지 정의합니다. 실제 출력 전달 적용은 후속 실행 연결 단계에서 지원됩니다.")
            WorkerCapabilityTypeSelector(
                label = "expectedOutputType",
                value = expectedOutputTypePolicy,
                onValueChange = { expectedOutputTypePolicy = it }
            )
            WorkerBooleanToggle(
                label = "requireJson",
                value = requireJsonPolicy,
                onValueChange = { requireJsonPolicy = it }
            )
            WorkerBooleanToggle(
                label = "requireMarkdownTable",
                value = requireMarkdownTablePolicy,
                onValueChange = { requireMarkdownTablePolicy = it }
            )
            WorkerBooleanToggle(
                label = "requireCodeBlock",
                value = requireCodeBlockPolicy,
                onValueChange = { requireCodeBlockPolicy = it }
            )
            WorkerBooleanToggle(
                label = "allowPlainText",
                value = allowPlainTextPolicy,
                onValueChange = { allowPlainTextPolicy = it }
            )
            WorkerPolicyIntField(
                label = "maxOutputChars",
                value = maxOutputCharsText,
                onValueChange = { maxOutputCharsText = it },
                fallbackValue = worker.outputPolicy.maxOutputChars
            )
            WorkerBooleanToggle(
                label = "passToNextWorker",
                value = passToNextWorkerPolicy,
                onValueChange = { passToNextWorkerPolicy = it }
            )
            WorkerBooleanToggle(
                label = "exposeToUser",
                value = exposeToUserPolicy,
                onValueChange = { exposeToUserPolicy = it }
            )
            WorkerBooleanToggle(
                label = "saveAsMemoryCandidate",
                value = saveAsMemoryCandidatePolicy,
                onValueChange = { saveAsMemoryCandidatePolicy = it }
            )
            WorkerEditKeyValue("저장될 outputPolicy", outputPolicyPreview.toReadableSummary())
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
                        capabilityOverrides = capabilityOverridesPreview,
                        inputPolicy = inputPolicyPreview,
                        outputPolicy = outputPolicyPreview,
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
private fun WorkerCapabilityStatusSelector(
    label: String,
    value: CapabilityLayerStatus,
    onValueChange: (CapabilityLayerStatus) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkerEditKeyValue(label, value.name)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onValueChange(value.previousCapabilityLayerStatus()) },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "이전", color = Color(0xFFBFD7FF))
            }
            TextButton(
                onClick = { onValueChange(value.nextCapabilityLayerStatus()) },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF33445C), MaterialTheme.shapes.medium)
            ) {
                Text(text = "다음", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = "선택 가능한 값: ${CapabilityLayerStatus.values().joinToString { it.name }}",
            color = Color(0xFFB8BCC6)
        )
    }
}

private fun CapabilityLayerStatus.nextCapabilityLayerStatus(): CapabilityLayerStatus {
    val values = CapabilityLayerStatus.values()
    val nextIndex = (ordinal + 1) % values.size
    return values[nextIndex]
}

private fun CapabilityLayerStatus.previousCapabilityLayerStatus(): CapabilityLayerStatus {
    val values = CapabilityLayerStatus.values()
    val previousIndex = if (ordinal == 0) values.lastIndex else ordinal - 1
    return values[previousIndex]
}

@Composable
private fun WorkerBooleanToggle(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkerEditKeyValue(label, value.toString())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onValueChange(false) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (!value) "✓ false" else "false",
                    color = if (!value) Color.White else Color(0xFFBFD7FF),
                    fontWeight = if (!value) FontWeight.Bold else FontWeight.Normal
                )
            }
            TextButton(
                onClick = { onValueChange(true) },
                modifier = Modifier
                    .weight(1f)
                    .background(if (value) Color(0xFF33445C) else Color.Transparent, MaterialTheme.shapes.medium)
            ) {
                Text(
                    text = if (value) "✓ true" else "true",
                    color = if (value) Color.White else Color(0xFFBFD7FF),
                    fontWeight = if (value) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun WorkerPolicyIntField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    fallbackValue: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WorkerEditTextField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            singleLine = true
        )
        val parsed = value.trim().toIntOrNull()
        val message = when {
            value.isBlank() -> "빈 값이면 기존값 또는 기본값 ${fallbackValue.coerceAtLeast(0)}을 사용합니다."
            parsed == null -> "숫자가 아니면 기존값 또는 기본값 ${fallbackValue.coerceAtLeast(0)}을 사용합니다."
            parsed < 0 -> "음수는 0으로 보정됩니다."
            else -> "저장될 값: $parsed"
        }
        Text(text = message, color = Color(0xFFB8BCC6))
    }
}

@Composable
private fun WorkerCapabilityTypeSelector(
    label: String,
    value: CapabilityType,
    onValueChange: (CapabilityType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252E3B), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WorkerEditKeyValue(label, value.name)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onValueChange(value.previousCapabilityType()) },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "이전", color = Color(0xFFBFD7FF))
            }
            TextButton(
                onClick = { onValueChange(value.nextCapabilityType()) },
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF33445C), MaterialTheme.shapes.medium)
            ) {
                Text(text = "다음", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = "선택 가능한 값: ${CapabilityType.values().joinToString { it.name }}",
            color = Color(0xFFB8BCC6)
        )
    }
}

private fun CapabilityType.nextCapabilityType(): CapabilityType {
    val values = CapabilityType.values()
    val nextIndex = (ordinal + 1) % values.size
    return values[nextIndex]
}

private fun CapabilityType.previousCapabilityType(): CapabilityType {
    val values = CapabilityType.values()
    val previousIndex = if (ordinal == 0) values.lastIndex else ordinal - 1
    return values[previousIndex]
}

private fun parseWorkerPolicyInt(value: String, fallback: Int): Int {
    val parsed = value.trim().toIntOrNull()
    return when {
        value.isBlank() -> fallback.coerceAtLeast(0)
        parsed == null -> fallback.coerceAtLeast(0)
        parsed < 0 -> 0
        else -> parsed
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
                text = "저장 대상: displayName, roleLabel, roleDescription, systemInstruction, enabled, providerId, modelId, capabilityOverrides, inputPolicy, outputPolicy. 실행 계획은 기존 값을 보존합니다.",
                color = Color(0xFFB8BCC6)
            )
        }
    }
}
