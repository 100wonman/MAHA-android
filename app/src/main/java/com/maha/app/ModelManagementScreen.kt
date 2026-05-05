package com.maha.app

import androidx.compose.foundation.BorderStroke
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
    modifier: Modifier = Modifier,
    useInternalScroll: Boolean = true
) {
    val context = LocalContext.current
    val store = remember { ProviderSettingsStore(context.applicationContext) }

    var providers by remember { mutableStateOf<List<ProviderProfile>>(emptyList()) }
    var models by remember { mutableStateOf<List<ConversationModelProfile>>(emptyList()) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ConversationModelProfile?>(null) }
    var modelToDelete by remember { mutableStateOf<ConversationModelProfile?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderTypeFilter by remember { mutableStateOf(MODEL_PROVIDER_FILTER_ALL) }
    var favoriteOnly by remember { mutableStateOf(false) }
    var enabledOnly by remember { mutableStateOf(false) }

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

    val providerById = providers.associateBy { it.providerId }
    val providerMissingCount = models.count { providerById[it.providerId] == null }
    val defaultModel = models.firstOrNull { it.isDefaultForConversation }
    val defaultModelName = defaultModel?.let { model ->
        model.displayName.ifBlank { model.rawModelName }.ifBlank { "이름 없음" }
    }
    val normalizedSearchQuery = searchQuery.trim().lowercase()
    val filteredModels = models
        .filter { model ->
            val provider = providerById[model.providerId]
            val providerFilterMatches = when (selectedProviderTypeFilter) {
                MODEL_PROVIDER_FILTER_ALL -> true
                MODEL_PROVIDER_FILTER_MISSING -> provider == null
                else -> provider?.providerType?.name == selectedProviderTypeFilter
            }
            val favoriteMatches = !favoriteOnly || model.isFavorite
            val enabledMatches = !enabledOnly || model.enabled
            val searchMatches = normalizedSearchQuery.isBlank() || listOf(
                model.displayName,
                model.rawModelName,
                model.providerId,
                provider?.displayName.orEmpty(),
                provider?.providerType?.name.orEmpty()
            ).any { value -> value.lowercase().contains(normalizedSearchQuery) }

            providerFilterMatches && favoriteMatches && enabledMatches && searchMatches
        }
        .sortedWith(
            compareByDescending<ConversationModelProfile> { it.isDefaultForConversation }
                .thenByDescending { it.isFavorite }
                .thenByDescending { it.enabled }
                .thenBy { model -> providerById[model.providerId]?.displayName?.lowercase() ?: "zz_provider_missing" }
                .thenBy { it.displayName.lowercase() }
        )
    val isFilterApplied = searchQuery.isNotBlank() ||
        selectedProviderTypeFilter != MODEL_PROVIDER_FILTER_ALL ||
        favoriteOnly ||
        enabledOnly

    val rootModifier = if (useInternalScroll) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    } else {
        modifier.fillMaxWidth()
    }

    Column(
        modifier = rootModifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.cardBackground),
            border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.cardBorderColor),
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
                    color = SettingsStyleTokens.bodyTextColor
                )
                SettingsPrimaryButton(
                    text = "모델 추가",
                    onClick = { isAddDialogOpen = true },
                    enabled = providers.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (providers.isEmpty()) {
                    Text(
                        text = "Provider를 먼저 추가해야 모델을 등록할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsStyleTokens.bodyTextColor
                    )
                }
            }
        }

        if (models.none { it.enabled && it.isDefaultForConversation }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.cardColors(SettingsChipTone.WARNING).background),
                border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.warningBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "기본 대화 모델이 지정되지 않았습니다.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SettingsStyleTokens.warningTextColor
                    )
                    Text(
                        text = "Gemini 실제 호출을 사용하려면 모델 카드에서 기본 지정을 눌러주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsStyleTokens.warningTextColor
                    )
                }
            }
        }

        ModelListFilterPanel(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedProviderTypeFilter = selectedProviderTypeFilter,
            onProviderTypeFilterChange = { selectedProviderTypeFilter = it },
            favoriteOnly = favoriteOnly,
            onFavoriteOnlyChange = { favoriteOnly = it },
            enabledOnly = enabledOnly,
            onEnabledOnlyChange = { enabledOnly = it },
            totalCount = models.size,
            visibleCount = filteredModels.size,
            defaultModelName = defaultModelName,
            providerMissingCount = providerMissingCount,
            isFilterApplied = isFilterApplied
        )

        if (models.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground),
                border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "등록된 모델이 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    color = SettingsStyleTokens.bodyTextColor
                )
            }
        } else if (filteredModels.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground),
                border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.cardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "필터 조건에 맞는 모델이 없습니다.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "검색어 또는 필터를 해제해 다시 확인하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsStyleTokens.bodyTextColor
                    )
                }
            }
        } else {
            filteredModels.forEach { model ->
                val provider = providerById[model.providerId]
                ModelProfileCard(
                    model = model,
                    providerName = provider?.displayName ?: "Provider 없음 (${model.providerId})",
                    providerType = provider?.providerType,
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


private const val MODEL_PROVIDER_FILTER_ALL = "ALL"
private const val MODEL_PROVIDER_FILTER_MISSING = "PROVIDER_MISSING"

@Composable
private fun ModelListFilterPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedProviderTypeFilter: String,
    onProviderTypeFilterChange: (String) -> Unit,
    favoriteOnly: Boolean,
    onFavoriteOnlyChange: (Boolean) -> Unit,
    enabledOnly: Boolean,
    onEnabledOnlyChange: (Boolean) -> Unit,
    totalCount: Int,
    visibleCount: Int,
    defaultModelName: String?,
    providerMissingCount: Int,
    isFilterApplied: Boolean
) {
    val providerFilterItems = buildList {
        add(MODEL_PROVIDER_FILTER_ALL to "전체")
        ProviderType.values().forEach { providerType ->
            add(providerType.name to providerType.name)
        }
        add(MODEL_PROVIDER_FILTER_MISSING to "Provider 없음")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "모델 목록 필터",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = buildString {
                    append("전체 모델 ${totalCount}개 · 표시 ${visibleCount}개")
                    append("\n기본 모델: ${defaultModelName ?: "없음"}")
                    append("\nProvider 없음 모델 ${providerMissingCount}개")
                    if (isFilterApplied) append(" · 필터 적용 중")
                },
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.bodyTextColor
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(text = "모델 검색") },
                placeholder = { Text(text = "모델명, rawModelName, Provider명, providerId") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "ProviderType 필터",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SettingsStyleTokens.bodyTextColor
            )
            providerFilterItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (filterValue, label) ->
                        ModelFilterButton(
                            text = label,
                            selected = selectedProviderTypeFilter == filterValue,
                            onClick = { onProviderTypeFilterChange(filterValue) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            ModelFilterToggleRow(
                label = "즐겨찾기만 보기",
                checked = favoriteOnly,
                onCheckedChange = onFavoriteOnlyChange
            )
            ModelFilterToggleRow(
                label = "활성 모델만 보기",
                checked = enabledOnly,
                onCheckedChange = onEnabledOnlyChange
            )
        }
    }
}

@Composable
private fun ModelFilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSecondaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        selected = selected
    )
}

@Composable
private fun ModelFilterToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsStyleTokens.bodyTextColor
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelProfileCard(
    model: ConversationModelProfile,
    providerName: String,
    providerType: ProviderType?,
    isLocalModel: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDefaultClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val capabilityV2 = model.capabilitiesV2 ?: model.capabilities.toModelCapabilityV2()

    Card(
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.subCardBackground),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.cardBorderColor),
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
                        text = if (model.isDefaultForConversation) "✓ 기본 대화 모델" else "대화모드 모델",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (model.isDefaultForConversation) FontWeight.Bold else FontWeight.Normal,
                        color = if (model.isDefaultForConversation) SettingsStyleTokens.successTextColor else SettingsStyleTokens.bodyTextColor
                    )
                    if (isLocalModel) {
                        Text(
                            text = "LOCAL 모델 · 사용자 지정 capability",
                            style = MaterialTheme.typography.labelMedium,
                            color = SettingsStyleTokens.successTextColor
                        )
                    }
                }
                Switch(
                    checked = model.enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (model.isDefaultForConversation) {
                Text(
                    text = "기본 모델 · 대화 호출에 사용됨",
                    style = MaterialTheme.typography.labelMedium,
                    color = SettingsStyleTokens.successTextColor
                )
            }

            ModelInfoRow(label = "Provider", value = providerName)
            ModelInfoRow(label = "Provider Type", value = providerType?.name ?: "UNKNOWN")
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
            color = SettingsStyleTokens.mutedTextColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = SettingsStyleTokens.bodyTextColor,
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
                                color = SettingsStyleTokens.mutedTextColor
                            )
                        }
                    }
                }

                selectedProvider?.let { provider ->
                    ModelRawNameGuideCard(provider.providerType)
                }

                OutlinedTextField(
                    value = rawModelName,
                    onValueChange = { rawModelName = it },
                    label = { Text(text = rawModelNameLabel(selectedProvider?.providerType)) },
                    placeholder = { Text(text = rawModelNamePlaceholder(selectedProvider?.providerType)) },
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
                Text(
                    text = "입력 → 출력 형식으로 모델 기능을 표시합니다. 체크한 기능은 호출 시 참고 정보이며, 실제 지원 여부는 Provider/모델 정책에 따라 달라질 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsStyleTokens.mutedTextColor
                )

                CapabilityGroupHeader(text = "입력 → 출력")
                CapabilityCheckbox(label = "text → text", checked = textCap, onCheckedChange = { textCap = it })
                CapabilityCheckbox(label = "text → code", checked = codeCap, onCheckedChange = { codeCap = it })
                CapabilityCheckbox(label = "text → json", checked = jsonModeCap, onCheckedChange = { jsonModeCap = it })
                CapabilityCheckbox(label = "image → text", checked = visionCap, onCheckedChange = { visionCap = it })
                CapabilityCheckbox(label = "audio → text", checked = audioCap, onCheckedChange = { audioCap = it })
                CapabilityCheckbox(label = "video → text", checked = videoCap, onCheckedChange = { videoCap = it })
                CapabilityCheckbox(label = "text → image", checked = imageGenerationCap, onCheckedChange = { imageGenerationCap = it })

                CapabilityGroupHeader(text = "도구")
                CapabilityCheckbox(label = "tool calling", checked = toolCallingCap, onCheckedChange = { toolCallingCap = it })
                CapabilityCheckbox(label = "function calling", checked = functionCallingCap, onCheckedChange = { functionCallingCap = it })
                CapabilityCheckbox(label = "web search", checked = webSearchCap, onCheckedChange = { webSearchCap = it })
                CapabilityCheckbox(label = "structured output", checked = structuredOutputCap, onCheckedChange = { structuredOutputCap = it })

                CapabilityGroupHeader(text = "추론")
                Text(
                    text = "thinking summary: 현재 저장 필드 없음 / 후속 단계에서 지원 예정",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsStyleTokens.mutedTextColor
                )

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

private fun rawModelNameLabel(providerType: ProviderType?): String {
    return when (providerType) {
        ProviderType.LOCAL -> "Local Server Model ID"
        ProviderType.OPENAI -> "OpenAI Model ID"
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible Model ID"
        ProviderType.CUSTOM -> "Custom Server Model ID"
        ProviderType.GOOGLE -> "Google Gemini Model ID"
        ProviderType.NVIDIA -> "NVIDIA Model ID"
        null -> "Raw Model Name"
    }
}

private fun rawModelNamePlaceholder(providerType: ProviderType?): String {
    return when (providerType) {
        ProviderType.GOOGLE -> "gemini-2.5-flash 또는 gemma-4-31b-it"
        ProviderType.OPENAI -> "gpt-4.1, gpt-4.1-mini, gpt-4o-mini"
        ProviderType.OPENAI_COMPATIBLE -> "llama-3.1-8b-instant, llama-3.3-70b-versatile, openrouter/free"
        ProviderType.LOCAL -> "LM Studio 모델 ID, llama3.2, gemma3, qwen2.5"
        ProviderType.CUSTOM -> "서버가 요구하는 model id"
        ProviderType.NVIDIA -> "NVIDIA model id"
        null -> "model id"
    }
}

private fun rawModelNameGuideText(providerType: ProviderType?): String {
    return when (providerType) {
        ProviderType.GOOGLE -> "Google 모델 목록에서 추가한 모델은 rawModelName이 자동 저장됩니다. 수동 입력 시 Gemini 호출에 사용할 모델 ID를 입력하세요."
        ProviderType.OPENAI -> "OpenAI Responses Provider에서 사용할 모델 ID를 입력합니다. 예시는 안내용이며 실제 사용 가능 여부는 OpenAI 계정/정책에 따릅니다."
        ProviderType.OPENAI_COMPATIBLE -> "Groq, OpenRouter 등 OpenAI-compatible 서버가 요구하는 model id를 입력합니다. 예시는 안내용이며 자동 저장되지 않습니다."
        ProviderType.LOCAL -> "Local Server에 실제 로드된 모델 ID를 입력합니다. 휴대폰에서 PC 서버를 사용할 경우 Provider의 Base URL은 PC LAN IP를 사용해야 합니다."
        ProviderType.CUSTOM -> "사용자 지정 서버가 요구하는 model id를 입력합니다. 서버별 규칙을 확인하세요."
        ProviderType.NVIDIA -> "NVIDIA 모델 ID 후보입니다. 실제 지원 여부는 Provider 정책에 따라 달라질 수 있습니다."
        null -> "Provider를 먼저 선택하면 rawModelName 안내가 표시됩니다."
    }
}

@Composable
private fun ModelRawNameGuideCard(providerType: ProviderType) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SettingsStyleTokens.nestedCardBackground),
        border = BorderStroke(SettingsStyleTokens.cardBorderWidth, SettingsStyleTokens.subtleBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${providerType.name} 모델명 안내",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (providerType == ProviderType.LOCAL) SettingsStyleTokens.successTextColor else SettingsStyleTokens.infoTextColor
            )
            Text(
                text = rawModelNameGuideText(providerType),
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.bodyTextColor
            )
            Text(
                text = "예: ${rawModelNamePlaceholder(providerType)}",
                style = MaterialTheme.typography.bodySmall,
                color = SettingsStyleTokens.bodyTextColor
            )
            if (providerType == ProviderType.LOCAL) {
                Text(
                    text = "LOCAL 기본 capability는 text/code=true, 나머지는 false입니다. 필요하면 직접 수정하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsStyleTokens.bodyTextColor
                )
            }
        }
    }
}

@Composable
private fun CapabilityGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = SettingsStyleTokens.bodyTextColor,
        modifier = Modifier.padding(top = 8.dp)
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

@Composable
private fun CapabilityV2Section(
    capability: ModelCapabilityV2,
    capabilitySource: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Capability V2 · $capabilitySource",
            style = MaterialTheme.typography.labelMedium,
            color = SettingsStyleTokens.mutedTextColor
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
            color = SettingsStyleTokens.mutedTextColor
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
        CapabilityStatus.USER_ENABLED -> SettingsStyleTokens.infoTextColor
        CapabilityStatus.UNKNOWN -> SettingsStyleTokens.bodyTextColor
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
