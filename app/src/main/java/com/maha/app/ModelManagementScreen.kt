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
                ModelProfileCard(
                    model = model,
                    providerName = providers.firstOrNull { it.providerId == model.providerId }?.displayName
                        ?: "Provider 없음 (${model.providerId})",
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
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val capabilityText = buildCapabilitySummary(model.capabilities)

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
            ModelInfoRow(label = "Capabilities", value = capabilityText.ifBlank { "text" })

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
                            onClick = { providerId = provider.providerId }
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

                OutlinedTextField(
                    value = rawModelName,
                    onValueChange = { rawModelName = it },
                    label = { Text(text = "Raw Model Name") },
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
                            enabled = enabled
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
