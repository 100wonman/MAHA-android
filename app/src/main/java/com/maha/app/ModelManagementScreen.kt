package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ModelManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { ProviderSettingsStore(context.applicationContext) }

    var providers by remember { mutableStateOf<List<ProviderProfile>>(emptyList()) }
    var models by remember { mutableStateOf<List<ConversationModelProfile>>(emptyList()) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ConversationModelProfile?>(null) }
    var modelToDelete by remember { mutableStateOf<ConversationModelProfile?>(null) }

    fun reload() {
        providers = store.loadProviderProfiles().sortedBy { it.displayName.lowercase() }
        models = store.loadModelProfiles()
            .sortedWith(
                compareByDescending<ConversationModelProfile> { it.isDefaultForConversation }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    fun saveModelWithDefaultRule(profile: ConversationModelProfile) {
        val current = store.loadModelProfiles()
        val next = if (profile.isDefaultForConversation) {
            val withoutCurrent = current.filterNot { it.modelId == profile.modelId }
                .map { it.copy(isDefaultForConversation = false) }
            withoutCurrent + profile
        } else {
            if (current.any { it.modelId == profile.modelId }) {
                current.map { if (it.modelId == profile.modelId) profile else it }
            } else {
                current + profile
            }
        }
        store.saveModelProfiles(next)
        reload()
    }

    fun setDefaultModel(model: ConversationModelProfile) {
        val updated = store.loadModelProfiles().map { existing ->
            existing.copy(
                isDefaultForConversation = existing.modelId == model.modelId
            )
        }
        store.saveModelProfiles(updated)
        reload()
    }

    LaunchedEffect(Unit) {
        store.ensureSettingsFiles()
        reload()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3F49)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Model 관리",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "대화모드 전용 모델을 수동으로 추가, 수정, 삭제합니다. 실제 모델 목록 조회는 후속 단계입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD0D3DA)
                )
                Button(
                    onClick = { isAddDialogOpen = true },
                    enabled = providers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "모델 추가")
                }
                if (providers.isEmpty()) {
                    Text(
                        text = "Provider를 먼저 추가해야 모델을 등록할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0D3DA)
                    )
                }
            }
        }

        if (models.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252A33)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "등록된 모델이 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    color = Color(0xFFD0D3DA)
                )
            }
        } else {
            models.forEach { model ->
                val provider = providers.firstOrNull { it.providerId == model.providerId }
                ModelProfileCard(
                    model = model,
                    providerName = provider?.displayName ?: "Provider 없음 (${model.providerId})",
                    isLocalModel = provider?.providerType == ProviderType.LOCAL,
                    onEditClick = { editingModel = model },
                    onDeleteClick = { modelToDelete = model },
                    onFavoriteClick = {
                        saveModelWithDefaultRule(model.copy(isFavorite = !model.isFavorite))
                    },
                    onDefaultClick = { setDefaultModel(model) },
                    onEnabledChange = { enabled ->
                        saveModelWithDefaultRule(model.copy(enabled = enabled))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (isAddDialogOpen) {
        ModelProfileEditDialog(
            initialModel = null,
            providers = providers,
            onDismiss = { isAddDialogOpen = false },
            onSave = { profile ->
                saveModelWithDefaultRule(profile)
                isAddDialogOpen = false
            }
        )
    }

    editingModel?.let { model ->
        ModelProfileEditDialog(
            initialModel = model,
            providers = providers,
            onDismiss = { editingModel = null },
            onSave = { profile ->
                saveModelWithDefaultRule(profile)
                editingModel = null
            }
        )
    }

    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text(text = "모델 삭제") },
            text = { Text(text = "${model.displayName} 모델을 삭제합니다. 기본 모델을 삭제하면 기본 모델 없음 상태가 됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.deleteModelProfile(model.modelId)
                        reload()
                        modelToDelete = null
                    }
                ) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text(text = "취소")
                }
            }
        )
    }
}

@Composable
private fun ModelProfileCard(
    model: ConversationModelProfile,
    providerName: String,
    isLocalModel: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val capabilityV2 = model.capabilitiesV2 ?: model.capabilities.toModelCapabilityV2()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252A33)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            if (model.isFavorite) append("★ ")
                            append(model.displayName.ifBlank { "이름 없는 모델" })
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (model.isDefaultForConversation) "대화모드 기본 모델" else "대화모드 모델",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0D3DA)
                    )
                    if (isLocalModel) {
                        Text(
                            text = "LOCAL 모델 · 사용자 지정 capability",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9FE3B1)
                        )
                    }
                }
                Switch(
                    checked = model.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            ModelInfoRow(label = "Provider", value = providerName)
            ModelInfoRow(label = "Raw Model", value = model.rawModelName.ifBlank { "미설정" })
            ModelInfoRow(label = "Context Window", value = model.contextWindow?.toString() ?: "미설정")
            ModelInfoRow(label = "Input", value = model.inputModalities.joinToString().ifBlank { "미설정" })
            ModelInfoRow(label = "Output", value = model.outputModalities.joinToString().ifBlank { "미설정" })
            CapabilityV2Section(
                capability = capabilityV2,
                capabilitySource = model.capabilitySource
            )
            if (model.supportedGenerationMethods.isNotEmpty()) {
                ModelInfoRow(
                    label = "Generation Methods",
                    value = model.supportedGenerationMethods.joinToString()
                )
            }
            if (!model.metadataRawSummary.isNullOrBlank()) {
                ModelInfoRow(
                    label = "Metadata",
                    value = model.metadataRawSummary.take(180)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFavoriteClick) {
                    Text(text = if (model.isFavorite) "★ 해제" else "★ 추가")
                }
                TextButton(
                    onClick = onDefaultClick,
                    enabled = !model.isDefaultForConversation
                ) {
                    Text(text = "기본 지정")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEditClick) {
                    Text(text = "수정")
                }
                TextButton(onClick = onDeleteClick) {
                    Text(text = "삭제")
                }
            }
        }
    }
}

@Composable
private fun ModelInfoRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB8BCC6)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD0D3DA),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModelProfileEditDialog(
    initialModel: ConversationModelProfile?,
    providers: List<ProviderProfile>,
    onDismiss: () -> Unit,
    onSave: (ConversationModelProfile) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val modelId = remember(initialModel) {
        initialModel?.modelId ?: "model_${System.currentTimeMillis()}"
    }

    var displayName by remember(initialModel) { mutableStateOf(initialModel?.displayName.orEmpty()) }
    var providerId by remember(initialModel, providers) {
        mutableStateOf(initialModel?.providerId ?: providers.firstOrNull()?.providerId.orEmpty())
    }
    var rawModelName by remember(initialModel) { mutableStateOf(initialModel?.rawModelName.orEmpty()) }
    var contextWindowText by remember(initialModel) { mutableStateOf(initialModel?.contextWindow?.toString().orEmpty()) }
    var inputModalitiesText by remember(initialModel) { mutableStateOf(initialModel?.inputModalities?.joinToString(", ") ?: "text") }
    var outputModalitiesText by remember(initialModel) { mutableStateOf(initialModel?.outputModalities?.joinToString(", ") ?: "text") }
    var isFavorite by remember(initialModel) { mutableStateOf(initialModel?.isFavorite ?: false) }
    var isDefaultForConversation by remember(initialModel) { mutableStateOf(initialModel?.isDefaultForConversation ?: false) }
    var enabled by remember(initialModel) { mutableStateOf(initialModel?.enabled ?: true) }

    var textCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.text ?: true) }
    var codeCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.code ?: false) }
    var visionCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.vision ?: false) }
    var audioCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.audio ?: false) }
    var videoCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.video ?: false) }
    var toolCallingCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.toolCalling ?: false) }
    var functionCallingCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.functionCalling ?: false) }
    var webSearchCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.webSearch ?: false) }
    var jsonModeCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.jsonMode ?: false) }
    var imageGenerationCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.imageGeneration ?: false) }
    var structuredOutputCap by remember(initialModel) { mutableStateOf(initialModel?.capabilities?.structuredOutput ?: false) }

    val selectedProvider = providers.firstOrNull { it.providerId == providerId }
    val isLocalProvider = selectedProvider?.providerType == ProviderType.LOCAL

    fun applyLocalDefaults() {
        textCap = true
        codeCap = true
        visionCap = false
        audioCap = false
        videoCap = false
        toolCallingCap = false
        functionCallingCap = false
        webSearchCap = false
        jsonModeCap = false
        imageGenerationCap = false
        structuredOutputCap = false
        inputModalitiesText = "text"
        outputModalitiesText = "text"
    }

    val canSave = displayName.trim().isNotEmpty() && rawModelName.trim().isNotEmpty() && providerId.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (initialModel == null) "모델 추가" else "모델 수정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(text = "모델 표시 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Provider 선택", style = MaterialTheme.typography.labelLarge)
                providers.forEach { provider ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = providerId == provider.providerId,
                            onClick = {
                                providerId = provider.providerId
                                if (provider.providerType == ProviderType.LOCAL && initialModel == null) {
                                    applyLocalDefaults()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = provider.displayName)
                            Text(
                                text = provider.providerType.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB8BCC6)
                            )
                        }
                    }
                }

                if (isLocalProvider) {
                    LocalModelGuideCard()
                }

                OutlinedTextField(
                    value = rawModelName,
                    onValueChange = { rawModelName = it },
                    label = { Text(text = if (isLocalProvider) "Local Model Name / File Name" else "Raw Model Name") },
                    placeholder = {
                        if (isLocalProvider) {
                            Text(text = "gemma-4-e2b-q4, llama-3.2-3b-instruct, local-model")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contextWindowText,
                    onValueChange = { contextWindowText = it.filter { char -> char.isDigit() } },
                    label = { Text(text = "Context Window (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputModalitiesText,
                    onValueChange = { inputModalitiesText = it },
                    label = { Text(text = "Input Modalities") },
                    placeholder = { Text(text = "text, image") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = outputModalitiesText,
                    onValueChange = { outputModalitiesText = it },
                    label = { Text(text = "Output Modalities") },
                    placeholder = { Text(text = "text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()
                Text(text = "Capabilities", style = MaterialTheme.typography.labelLarge)

                CapabilityCheckbox(label = "Text", checked = textCap, onCheckedChange = { textCap = it })
                CapabilityCheckbox(label = "Code", checked = codeCap, onCheckedChange = { codeCap = it })
                CapabilityCheckbox(label = "Vision", checked = visionCap, onCheckedChange = { visionCap = it })
                CapabilityCheckbox(label = "Audio", checked = audioCap, onCheckedChange = { audioCap = it })
                CapabilityCheckbox(label = "Video", checked = videoCap, onCheckedChange = { videoCap = it })
                CapabilityCheckbox(label = "Tool Calling", checked = toolCallingCap, onCheckedChange = { toolCallingCap = it })
                CapabilityCheckbox(label = "Function Calling", checked = functionCallingCap, onCheckedChange = { functionCallingCap = it })
                CapabilityCheckbox(label = "Web Search", checked = webSearchCap, onCheckedChange = { webSearchCap = it })
                CapabilityCheckbox(label = "JSON Mode", checked = jsonModeCap, onCheckedChange = { jsonModeCap = it })
                CapabilityCheckbox(label = "Image Generation", checked = imageGenerationCap, onCheckedChange = { imageGenerationCap = it })
                CapabilityCheckbox(label = "Structured Output", checked = structuredOutputCap, onCheckedChange = { structuredOutputCap = it })

                Divider()
                ToggleRow(label = "즐겨찾기", checked = isFavorite, onCheckedChange = { isFavorite = it })
                ToggleRow(label = "대화모드 기본 모델", checked = isDefaultForConversation, onCheckedChange = { isDefaultForConversation = it })
                ToggleRow(label = "활성화", checked = enabled, onCheckedChange = { enabled = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ConversationModelProfile(
                            modelId = modelId,
                            providerId = providerId,
                            displayName = displayName.trim(),
                            rawModelName = rawModelName.trim(),
                            contextWindow = contextWindowText.toIntOrNull(),
                            inputModalities = splitCommaValues(inputModalitiesText).ifEmpty { listOf("text") },
                            outputModalities = splitCommaValues(outputModalitiesText).ifEmpty { listOf("text") },
                            capabilities = ConversationModelCapability(
                                text = textCap,
                                code = codeCap,
                                vision = visionCap,
                                audio = audioCap,
                                video = videoCap,
                                toolCalling = toolCallingCap,
                                functionCalling = functionCallingCap,
                                webSearch = webSearchCap,
                                jsonMode = jsonModeCap,
                                imageGeneration = imageGenerationCap,
                                structuredOutput = structuredOutputCap
                            ),
                            isFavorite = isFavorite,
                            isDefaultForConversation = isDefaultForConversation,
                            lastUsedAt = initialModel?.lastUsedAt,
                            enabled = enabled,
                            capabilitiesV2 = ConversationModelCapability(
                                text = textCap,
                                code = codeCap,
                                vision = visionCap,
                                audio = audioCap,
                                video = videoCap,
                                toolCalling = toolCallingCap,
                                functionCalling = functionCallingCap,
                                webSearch = webSearchCap,
                                jsonMode = jsonModeCap,
                                imageGeneration = imageGenerationCap,
                                structuredOutput = structuredOutputCap
                            ).toModelCapabilityV2(),
                            capabilitySource = "USER",
                            supportedGenerationMethods = initialModel?.supportedGenerationMethods ?: emptyList(),
                            inputTokenLimit = initialModel?.inputTokenLimit,
                            outputTokenLimit = initialModel?.outputTokenLimit,
                            metadataRawSummary = initialModel?.metadataRawSummary,
                            lastMetadataFetchedAt = initialModel?.lastMetadataFetchedAt
                        )
                    )
                },
                enabled = canSave
            ) {
                Text(text = "저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소")
            }
        }
    )
}

@Composable
private fun LocalModelGuideCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "LOCAL 모델 안내",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9FE3B1)
            )
            Text(
                text = "rawModelName은 Local Server에서 사용하는 모델명 또는 후속 로컬 모델 파일명 후보입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
            Text(
                text = "LOCAL 기본 capability는 text/code=true, 나머지는 false입니다. 필요하면 직접 수정하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
        }
    }
}

@Composable
private fun CapabilityCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CapabilityV2Section(
    capability: ModelCapabilityV2,
    capabilitySource: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Capability V2 · $capabilitySource",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB8BCC6)
        )
        CapabilityBadgeGroup(
            title = "입력",
            items = listOf(
                "Text" to capability.input.text,
                "Image" to capability.input.image,
                "Audio" to capability.input.audio,
                "Video" to capability.input.video,
                "File" to capability.input.file
            )
        )
        CapabilityBadgeGroup(
            title = "출력",
            items = listOf(
                "Text" to capability.output.text,
                "Code" to capability.output.code,
                "JSON" to capability.output.json,
                "Image" to capability.output.image,
                "Audio" to capability.output.audio,
                "Video" to capability.output.video
            )
        )
        CapabilityBadgeGroup(
            title = "도구",
            items = listOf(
                "Function" to capability.tools.functionCalling,
                "Web Search" to capability.tools.webSearch,
                "Code Exec" to capability.tools.codeExecution,
                "Structured" to capability.tools.structuredOutput
            )
        )
        CapabilityBadgeGroup(
            title = "추론",
            items = listOf(
                "Thinking" to capability.reasoning.thinking,
                "Summary" to capability.reasoning.thinkingSummary
            )
        )
    }
}

@Composable
private fun CapabilityBadgeGroup(
    title: String,
    items: List<Pair<String, CapabilityStatus>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB8BCC6)
        )
        items.chunked(3).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { (label, status) ->
                    CapabilityStatusBadge(label = label, status = status)
                }
            }
        }
    }
}

@Composable
private fun CapabilityStatusBadge(
    label: String,
    status: CapabilityStatus
) {
    val displayText = when (status) {
        CapabilityStatus.SUPPORTED -> label
        CapabilityStatus.UNSUPPORTED -> "$label off"
        CapabilityStatus.UNKNOWN -> "$label?"
        CapabilityStatus.USER_ENABLED -> "$label · 사용자"
        CapabilityStatus.USER_DISABLED -> "$label off · 사용자"
    }
    val backgroundColor = when (status) {
        CapabilityStatus.SUPPORTED -> Color(0xFF1E4D36)
        CapabilityStatus.USER_ENABLED -> Color(0xFF1E3F66)
        CapabilityStatus.UNKNOWN -> Color(0xFF3B414D)
        CapabilityStatus.UNSUPPORTED,
        CapabilityStatus.USER_DISABLED -> Color(0xFF2C3038)
    }
    val textColor = when (status) {
        CapabilityStatus.SUPPORTED -> Color(0xFFB7F7CB)
        CapabilityStatus.USER_ENABLED -> Color(0xFFB7D7FF)
        CapabilityStatus.UNKNOWN -> Color(0xFFD0D3DA)
        CapabilityStatus.UNSUPPORTED,
        CapabilityStatus.USER_DISABLED -> Color(0xFF8E94A1)
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = Modifier
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun buildCapabilitySummary(capability: ConversationModelCapability): String {
    val values = buildList {
        if (capability.text) add("text")
        if (capability.code) add("code")
        if (capability.vision) add("vision")
        if (capability.audio) add("audio")
        if (capability.video) add("video")
        if (capability.toolCalling) add("tool")
        if (capability.functionCalling) add("function")
        if (capability.webSearch) add("web")
        if (capability.jsonMode) add("json")
        if (capability.imageGeneration) add("image")
        if (capability.structuredOutput) add("structured")
    }
    return values.joinToString()
}

private fun splitCommaValues(value: String): List<String> {
    return value.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
