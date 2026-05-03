package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ProviderManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { ProviderSettingsStore(context.applicationContext) }

    var providers by remember { mutableStateOf<List<ProviderProfile>>(emptyList()) }
    var providerApiKeyStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var providerToDelete by remember { mutableStateOf<ProviderProfile?>(null) }
    var providerSearchQuery by remember { mutableStateOf("") }
    var selectedProviderTypeFilter by remember { mutableStateOf<ProviderType?>(null) }
    var showOnlyEnabledProviders by remember { mutableStateOf(false) }
    var showOnlyApiKeyConfigured by remember { mutableStateOf(false) }
    var showOnlyMissingBaseUrl by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val googleModelListFetcher = remember { GoogleModelListFetcher() }
    val openAiCompatibleModelListFetcher = remember { OpenAiCompatibleModelListFetcher() }
    val openAIModelListFetcher = remember { OpenAIModelListFetcher() }
    var modelListProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var googleModels by remember { mutableStateOf<List<GoogleModelListItem>>(emptyList()) }
    var googleModelListError by remember { mutableStateOf<String?>(null) }
    var googleModelListMessage by remember { mutableStateOf<String?>(null) }
    var isGoogleModelListLoading by remember { mutableStateOf(false) }

    var openAiModelListProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var openAiModelCandidates by remember { mutableStateOf<List<OpenAiModelListCandidate>>(emptyList()) }
    var openAiModelListError by remember { mutableStateOf<String?>(null) }
    var openAiModelListMessage by remember { mutableStateOf<String?>(null) }
    var isOpenAiModelListLoading by remember { mutableStateOf(false) }
    var openAiModelListEndpoint by remember { mutableStateOf<String?>(null) }

    var openAIOfficialModelListProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var openAIOfficialModels by remember { mutableStateOf<List<OpenAIModelListItem>>(emptyList()) }
    var openAIOfficialModelListError by remember { mutableStateOf<String?>(null) }
    var openAIOfficialModelListMessage by remember { mutableStateOf<String?>(null) }
    var openAIOfficialModelListEndpoint by remember { mutableStateOf<String?>(null) }
    var isOpenAIOfficialModelListLoading by remember { mutableStateOf(false) }

    fun reloadProviders() {
        providers = store.loadProviderProfiles().sortedBy { it.displayName.lowercase() }
        providerApiKeyStates = providers.associate { provider ->
            provider.providerId to store.hasProviderApiKey(provider.providerId)
        }
    }

    fun openGoogleModelList(provider: ProviderProfile) {
        modelListProvider = provider
        googleModels = emptyList()
        googleModelListError = null
        googleModelListMessage = null
        isGoogleModelListLoading = true

        coroutineScope.launch {
            val apiKey = store.loadProviderApiKey(provider.providerId)
            if (apiKey.isNullOrBlank()) {
                isGoogleModelListLoading = false
                googleModelListError = "API Key가 설정되지 않았습니다. Provider 수정 화면에서 API Key를 저장하세요."
                return@launch
            }

            val result = googleModelListFetcher.fetchModels(
                apiKey = apiKey,
                endpoint = provider.modelListEndpoint
            )
            isGoogleModelListLoading = false
            if (result.success) {
                googleModels = result.models
                googleModelListError = if (result.models.isEmpty()) "조회된 모델이 없습니다." else null
            } else {
                googleModelListError = result.errorMessage ?: "모델 목록 조회에 실패했습니다."
            }
        }
    }

    fun addGoogleModelProfile(provider: ProviderProfile, item: GoogleModelListItem) {
        val rawModelName = item.rawModelName.trim()
        if (rawModelName.isBlank()) {
            googleModelListMessage = "rawModelName이 비어 있어 추가할 수 없습니다."
            return
        }

        val current = store.loadModelProfiles()
        val alreadyExists = current.any { model ->
            model.providerId == provider.providerId && model.rawModelName == rawModelName
        }
        if (alreadyExists) {
            googleModelListMessage = "이미 추가된 모델입니다: $rawModelName"
            return
        }

        val now = System.currentTimeMillis()
        val safeIdPart = rawModelName
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "google_model" }

        val metadataCapability = createGoogleMetadataCapabilityV2()
        val profile = ConversationModelProfile(
            modelId = "model_${provider.providerId}_${safeIdPart}_$now",
            providerId = provider.providerId,
            displayName = item.displayName.ifBlank { rawModelName },
            rawModelName = rawModelName,
            contextWindow = item.inputTokenLimit,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            capabilities = ConversationModelCapability(
                text = true
            ),
            isFavorite = false,
            isDefaultForConversation = false,
            lastUsedAt = null,
            enabled = true,
            capabilitiesV2 = metadataCapability,
            capabilitySource = "METADATA",
            supportedGenerationMethods = item.supportedGenerationMethods,
            inputTokenLimit = item.inputTokenLimit,
            outputTokenLimit = item.outputTokenLimit,
            metadataRawSummary = buildGoogleMetadataSummary(item),
            lastMetadataFetchedAt = now
        )
        store.addModelProfile(profile)
        googleModelListMessage = "모델을 추가했습니다: ${profile.displayName}"
    }

    fun openOpenAiCompatibleModelList(provider: ProviderProfile) {
        openAiModelListProvider = provider
        openAiModelCandidates = emptyList()
        openAiModelListError = null
        openAiModelListMessage = null
        openAiModelListEndpoint = null
        isOpenAiModelListLoading = true

        coroutineScope.launch {
            val apiKey = store.loadProviderApiKey(provider.providerId)
            val result: OpenAiCompatibleModelListResult = openAiCompatibleModelListFetcher.fetchModels(
                provider = provider,
                apiKey = apiKey
            )
            isOpenAiModelListLoading = false
            openAiModelListEndpoint = result.endpoint
            if (result.success) {
                openAiModelCandidates = result.models
                openAiModelListError = if (result.models.isEmpty()) "조회된 모델이 없습니다." else null
            } else {
                openAiModelListError = result.errorMessage ?: result.errorType ?: "모델 목록 조회에 실패했습니다."
            }
        }
    }

    fun openOpenAIOfficialModelList(provider: ProviderProfile) {
        openAIOfficialModelListProvider = provider
        openAIOfficialModels = emptyList()
        openAIOfficialModelListError = null
        openAIOfficialModelListMessage = null
        openAIOfficialModelListEndpoint = null
        isOpenAIOfficialModelListLoading = true

        coroutineScope.launch {
            val apiKey = store.loadProviderApiKey(provider.providerId)
            val result = openAIModelListFetcher.fetchModels(
                provider = provider,
                apiKey = apiKey
            )
            isOpenAIOfficialModelListLoading = false
            openAIOfficialModelListEndpoint = result.endpoint
            if (result.success) {
                openAIOfficialModels = result.models
                openAIOfficialModelListError = if (result.models.isEmpty()) "조회된 모델이 없습니다." else null
            } else {
                openAIOfficialModelListError = result.errorMessage ?: result.errorType ?: "OpenAI 공식 모델 목록 조회에 실패했습니다."
            }
        }
    }

    fun addOpenAIOfficialModelProfile(provider: ProviderProfile, item: OpenAIModelListItem) {
        val rawModelName = item.rawModelName.trim()
        if (rawModelName.isBlank()) {
            openAIOfficialModelListMessage = "rawModelName이 비어 있어 추가할 수 없습니다."
            return
        }

        val current = store.loadModelProfiles()
        val alreadyExists = current.any { model ->
            model.providerId == provider.providerId && model.rawModelName == rawModelName
        }
        if (alreadyExists) {
            openAIOfficialModelListMessage = "이미 추가된 모델입니다: $rawModelName"
            return
        }

        val now = System.currentTimeMillis()
        val safeIdPart = rawModelName
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "openai_model" }

        val profile = ConversationModelProfile(
            modelId = "model_${provider.providerId}_${safeIdPart}_$now",
            providerId = provider.providerId,
            displayName = item.displayName.ifBlank { rawModelName },
            rawModelName = rawModelName,
            contextWindow = null,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            capabilities = ConversationModelCapability(
                text = true
            ),
            isFavorite = false,
            isDefaultForConversation = false,
            lastUsedAt = null,
            enabled = true,
            capabilitiesV2 = createOpenAiCompatibleMetadataCapabilityV2(),
            capabilitySource = "METADATA",
            supportedGenerationMethods = emptyList(),
            inputTokenLimit = null,
            outputTokenLimit = null,
            metadataRawSummary = item.metadataRawSummary,
            lastMetadataFetchedAt = now
        )
        store.addModelProfile(profile)
        openAIOfficialModelListMessage = "모델을 추가했습니다: ${profile.displayName}"
    }

    fun addOpenAiCompatibleModelProfile(provider: ProviderProfile, candidate: OpenAiModelListCandidate) {
        val rawModelName = candidate.rawModelName.trim()
        if (rawModelName.isBlank()) {
            openAiModelListMessage = "rawModelName이 비어 있어 추가할 수 없습니다."
            return
        }

        val current = store.loadModelProfiles()
        val alreadyExists = current.any { model ->
            model.providerId == provider.providerId && model.rawModelName == rawModelName
        }
        if (alreadyExists) {
            openAiModelListMessage = "이미 추가된 모델입니다: $rawModelName"
            return
        }

        val now = System.currentTimeMillis()
        val safeIdPart = rawModelName
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "openai_compatible_model" }

        val profile = ConversationModelProfile(
            modelId = "model_${provider.providerId}_${safeIdPart}_$now",
            providerId = provider.providerId,
            displayName = candidate.displayName.ifBlank { rawModelName },
            rawModelName = rawModelName,
            contextWindow = candidate.contextWindow,
            inputModalities = candidate.inputModalities.ifEmpty { listOf("text") },
            outputModalities = candidate.outputModalities.ifEmpty { listOf("text") },
            capabilities = ConversationModelCapability(
                text = true
            ),
            isFavorite = false,
            isDefaultForConversation = false,
            lastUsedAt = null,
            enabled = true,
            capabilitiesV2 = createOpenAiCompatibleMetadataCapabilityV2(),
            capabilitySource = "METADATA",
            supportedGenerationMethods = emptyList(),
            inputTokenLimit = candidate.contextWindow,
            outputTokenLimit = null,
            metadataRawSummary = candidate.metadataRawSummary,
            lastMetadataFetchedAt = now
        )
        store.addModelProfile(profile)
        openAiModelListMessage = "모델을 추가했습니다: ${profile.displayName}"
    }

    LaunchedEffect(Unit) {
        store.ensureSettingsFiles()
        reloadProviders()
    }

    val normalizedSearchQuery = providerSearchQuery.trim().lowercase()
    val filteredProviders = providers
        .filter { provider ->
            val matchesSearch = providerMatchesSearch(
                provider = provider,
                normalizedSearchQuery = normalizedSearchQuery
            )

            val matchesProviderType = selectedProviderTypeFilter == null ||
                provider.providerType == selectedProviderTypeFilter
            val matchesEnabled = !showOnlyEnabledProviders || provider.isEnabled
            val matchesApiKey = !showOnlyApiKeyConfigured || providerApiKeyStates[provider.providerId] == true
            val matchesMissingBaseUrl = !showOnlyMissingBaseUrl || provider.baseUrl.isBlank()

            matchesSearch && matchesProviderType && matchesEnabled && matchesApiKey && matchesMissingBaseUrl
        }
        .sortedWith(
            compareByDescending<ProviderProfile> { if (it.isEnabled) 1 else 0 }
                .thenBy { it.providerType.ordinal }
                .thenByDescending { if (providerApiKeyStates[it.providerId] == true) 1 else 0 }
                .thenByDescending { if (it.baseUrl.isNotBlank()) 1 else 0 }
                .thenBy { it.displayName.lowercase() }
        )

    val enabledProviderCount = providers.count { it.isEnabled }
    val apiKeyConfiguredCount = providers.count { providerApiKeyStates[it.providerId] == true }
    val missingBaseUrlCount = providers.count { it.baseUrl.isBlank() }
    val isFilterApplied = providerSearchQuery.isNotBlank() ||
        selectedProviderTypeFilter != null ||
        showOnlyEnabledProviders ||
        showOnlyApiKeyConfigured ||
        showOnlyMissingBaseUrl

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF3A3F49)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Provider 관리",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "대화모드 전용 Provider 설정을 추가, 수정, 삭제합니다. Google 및 OpenAI-compatible 계열 모델 목록 조회를 지원합니다. OpenAI 공식 모델 목록 조회는 후속 지원 예정입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD0D3DA)
                )
                Button(
                    onClick = { isAddDialogOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Provider 추가")
                }
            }
        }

        ProviderListControlPanel(
            searchQuery = providerSearchQuery,
            onSearchQueryChange = { providerSearchQuery = it },
            selectedProviderType = selectedProviderTypeFilter,
            onProviderTypeSelected = { selectedProviderTypeFilter = it },
            showOnlyEnabled = showOnlyEnabledProviders,
            onShowOnlyEnabledChange = { showOnlyEnabledProviders = it },
            showOnlyApiKeyConfigured = showOnlyApiKeyConfigured,
            onShowOnlyApiKeyConfiguredChange = { showOnlyApiKeyConfigured = it },
            showOnlyMissingBaseUrl = showOnlyMissingBaseUrl,
            onShowOnlyMissingBaseUrlChange = { showOnlyMissingBaseUrl = it },
            totalCount = providers.size,
            visibleCount = filteredProviders.size,
            enabledCount = enabledProviderCount,
            apiKeyConfiguredCount = apiKeyConfiguredCount,
            missingBaseUrlCount = missingBaseUrlCount,
            isFilterApplied = isFilterApplied
        )

        if (providers.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF252A33)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "등록된 Provider가 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    color = Color(0xFFD0D3DA)
                )
            }
        } else if (filteredProviders.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF252A33)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "현재 검색/필터 조건에 맞는 Provider가 없습니다.",
                    modifier = Modifier.padding(18.dp),
                    color = Color(0xFFD0D3DA)
                )
            }
        } else {
            filteredProviders.forEach { provider ->
                ProviderProfileCard(
                    provider = provider,
                    hasApiKey = providerApiKeyStates[provider.providerId] == true,
                    onEditClick = { editingProvider = provider },
                    onDeleteClick = { providerToDelete = provider },
                    onLoadGoogleModels = { openGoogleModelList(provider) },
                    onLoadOpenAIOfficialModels = { openOpenAIOfficialModelList(provider) },
                    onLoadOpenAiCompatibleModels = { openOpenAiCompatibleModelList(provider) },
                    onEnabledChange = { enabled ->
                        val now = System.currentTimeMillis()
                        store.updateProviderProfile(
                            provider.copy(
                                isEnabled = enabled,
                                updatedAt = now
                            )
                        )
                        reloadProviders()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (isAddDialogOpen) {
        ProviderProfileEditDialog(
            initialProvider = null,
            onDismiss = { isAddDialogOpen = false },
            onSave = { profile, apiKey, shouldDeleteApiKey ->
                store.addProviderProfile(profile)
                if (shouldDeleteApiKey) {
                    store.deleteProviderApiKey(profile.providerId)
                } else if (!apiKey.isNullOrBlank()) {
                    store.saveProviderApiKey(profile.providerId, apiKey)
                    store.updateProviderProfile(profile.copy(apiKeyAlias = "stored"))
                }
                reloadProviders()
                isAddDialogOpen = false
            }
        )
    }

    editingProvider?.let { provider ->
        ProviderProfileEditDialog(
            initialProvider = provider,
            onDismiss = { editingProvider = null },
            onSave = { profile, apiKey, shouldDeleteApiKey ->
                val profileWithKeyState = when {
                    shouldDeleteApiKey -> profile.copy(apiKeyAlias = null)
                    !apiKey.isNullOrBlank() -> profile.copy(apiKeyAlias = "stored")
                    else -> profile
                }
                store.updateProviderProfile(profileWithKeyState)
                if (shouldDeleteApiKey) {
                    store.deleteProviderApiKey(profile.providerId)
                } else if (!apiKey.isNullOrBlank()) {
                    store.saveProviderApiKey(profile.providerId, apiKey)
                }
                reloadProviders()
                editingProvider = null
            }
        )
    }

    providerToDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            title = { Text(text = "Provider 삭제") },
            text = {
                Text(text = "${provider.displayName} Provider를 삭제합니다. 연결된 model_profiles 정리는 후속 단계에서 처리합니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.deleteProviderProfile(provider.providerId)
                        store.deleteProviderApiKey(provider.providerId)
                        reloadProviders()
                        providerToDelete = null
                    }
                ) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { providerToDelete = null }) {
                    Text(text = "취소")
                }
            }
        )
    }


    modelListProvider?.let { provider ->
        GoogleModelListDialog(
            provider = provider,
            isLoading = isGoogleModelListLoading,
            models = googleModels,
            errorMessage = googleModelListError,
            resultMessage = googleModelListMessage,
            existingModels = store.loadModelProfiles(),
            onDismiss = {
                modelListProvider = null
                googleModels = emptyList()
                googleModelListError = null
                googleModelListMessage = null
                isGoogleModelListLoading = false
            },
            onAddModel = { item -> addGoogleModelProfile(provider, item) }
        )
    }

    openAiModelListProvider?.let { provider ->
        OpenAiCompatibleModelListDialog(
            provider = provider,
            isLoading = isOpenAiModelListLoading,
            models = openAiModelCandidates,
            errorMessage = openAiModelListError,
            resultMessage = openAiModelListMessage,
            endpoint = openAiModelListEndpoint,
            existingModels = store.loadModelProfiles(),
            onDismiss = {
                openAiModelListProvider = null
                openAiModelCandidates = emptyList()
                openAiModelListError = null
                openAiModelListMessage = null
                openAiModelListEndpoint = null
                isOpenAiModelListLoading = false
            },
            onAddModel = { item -> addOpenAiCompatibleModelProfile(provider, item) }
        )
    }

    openAIOfficialModelListProvider?.let { provider ->
        OpenAIModelListDialog(
            provider = provider,
            isLoading = isOpenAIOfficialModelListLoading,
            models = openAIOfficialModels,
            errorMessage = openAIOfficialModelListError,
            resultMessage = openAIOfficialModelListMessage,
            endpoint = openAIOfficialModelListEndpoint,
            existingModels = store.loadModelProfiles(),
            onDismiss = {
                openAIOfficialModelListProvider = null
                openAIOfficialModels = emptyList()
                openAIOfficialModelListError = null
                openAIOfficialModelListMessage = null
                openAIOfficialModelListEndpoint = null
                isOpenAIOfficialModelListLoading = false
            },
            onAddModel = { item -> addOpenAIOfficialModelProfile(provider, item) }
        )
    }
}

@Composable
private fun ProviderListControlPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedProviderType: ProviderType?,
    onProviderTypeSelected: (ProviderType?) -> Unit,
    showOnlyEnabled: Boolean,
    onShowOnlyEnabledChange: (Boolean) -> Unit,
    showOnlyApiKeyConfigured: Boolean,
    onShowOnlyApiKeyConfiguredChange: (Boolean) -> Unit,
    showOnlyMissingBaseUrl: Boolean,
    onShowOnlyMissingBaseUrlChange: (Boolean) -> Unit,
    totalCount: Int,
    visibleCount: Int,
    enabledCount: Int,
    apiKeyConfiguredCount: Int,
    missingBaseUrlCount: Int,
    isFilterApplied: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF252A33)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Provider 목록 정리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = buildString {
                    append("전체 Provider ").append(totalCount).append("개 · 표시 ").append(visibleCount).append("개")
                    append(" · 활성 ").append(enabledCount).append("개")
                    append(" · API Key 설정 ").append(apiKeyConfiguredCount).append("개")
                    append(" · Base URL 미설정 ").append(missingBaseUrlCount).append("개")
                    if (isFilterApplied) append(" · 필터 적용 중")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(text = "Provider 검색") },
                placeholder = { Text(text = "이름, ProviderType, Provider ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "API Key 원문은 검색하거나 표시하지 않습니다. Base URL/Endpoint는 과검색 방지를 위해 기본 검색에서 제외합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8BCC6)
            )

            Text(
                text = "ProviderType 필터",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderFilterButton(
                    text = "전체",
                    selected = selectedProviderType == null,
                    onClick = { onProviderTypeSelected(null) }
                )
                ProviderFilterButton(
                    text = ProviderType.GOOGLE.name,
                    selected = selectedProviderType == ProviderType.GOOGLE,
                    onClick = { onProviderTypeSelected(ProviderType.GOOGLE) }
                )
                ProviderFilterButton(
                    text = ProviderType.OPENAI.name,
                    selected = selectedProviderType == ProviderType.OPENAI,
                    onClick = { onProviderTypeSelected(ProviderType.OPENAI) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderFilterButton(
                    text = ProviderType.OPENAI_COMPATIBLE.name,
                    selected = selectedProviderType == ProviderType.OPENAI_COMPATIBLE,
                    onClick = { onProviderTypeSelected(ProviderType.OPENAI_COMPATIBLE) }
                )
                ProviderFilterButton(
                    text = ProviderType.NVIDIA.name,
                    selected = selectedProviderType == ProviderType.NVIDIA,
                    onClick = { onProviderTypeSelected(ProviderType.NVIDIA) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderFilterButton(
                    text = ProviderType.LOCAL.name,
                    selected = selectedProviderType == ProviderType.LOCAL,
                    onClick = { onProviderTypeSelected(ProviderType.LOCAL) }
                )
                ProviderFilterButton(
                    text = ProviderType.CUSTOM.name,
                    selected = selectedProviderType == ProviderType.CUSTOM,
                    onClick = { onProviderTypeSelected(ProviderType.CUSTOM) }
                )
            }

            ProviderBooleanFilterRow(
                label = "활성 Provider만",
                checked = showOnlyEnabled,
                onCheckedChange = onShowOnlyEnabledChange
            )
            ProviderBooleanFilterRow(
                label = "API Key 설정됨만",
                checked = showOnlyApiKeyConfigured,
                onCheckedChange = onShowOnlyApiKeyConfiguredChange
            )
            ProviderBooleanFilterRow(
                label = "Base URL 미설정만",
                checked = showOnlyMissingBaseUrl,
                onCheckedChange = onShowOnlyMissingBaseUrlChange
            )
        }
    }
}

@Composable
private fun ProviderFilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val buttonText = if (selected) "✓ $text" else text
    if (selected) {
        Button(onClick = onClick) {
            Text(text = buttonText)
        }
    } else {
        TextButton(onClick = onClick) {
            Text(text = buttonText)
        }
    }
}

@Composable
private fun ProviderBooleanFilterRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD0D3DA)
        )
    }
}

@Composable
private fun ProviderProfileCard(
    provider: ProviderProfile,
    hasApiKey: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLoadGoogleModels: () -> Unit,
    onLoadOpenAIOfficialModels: () -> Unit,
    onLoadOpenAiCompatibleModels: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val canLoadOpenAiCompatibleModels = canLoadOpenAiCompatibleModelList(
        provider = provider,
        hasApiKey = hasApiKey
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF252A33)
        ),
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
                        text = provider.displayName.ifBlank { "이름 없는 Provider" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.providerType.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0D3DA)
                    )
                    Text(
                        text = providerCallStyleLabel(provider.providerType),
                        style = MaterialTheme.typography.labelMedium,
                        color = providerCallStyleColor(provider.providerType)
                    )
                }
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            ProviderInfoRow(
                label = "호출 방식",
                value = providerCallStyleLabel(provider.providerType)
            )
            ProviderInfoRow(
                label = "등록 구분",
                value = if (isDefaultSeedProvider(provider)) "기본 seed Provider" else "사용자 추가 Provider"
            )
            ProviderInfoRow(
                label = "Base URL",
                value = if (provider.baseUrl.isBlank()) {
                    "미설정${if (isBaseUrlRecommended(provider.providerType)) " · 호출 전 입력 필요" else ""}"
                } else {
                    provider.baseUrl
                }
            )
            ProviderInfoRow(
                label = "API Key",
                value = buildString {
                    append(apiKeyRequirementLabel(provider.providerType))
                    append(" · ")
                    append(if (hasApiKey) "설정됨" else "미설정")
                }
            )
            if (provider.baseUrl.isBlank() && isBaseUrlRecommended(provider.providerType)) {
                Text(
                    text = "Base URL 미설정 · 호출 시 BASE_URL_MISSING으로 실패합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFD08A)
                )
            }
            ProviderInfoRow(
                label = "Model List Endpoint",
                value = provider.modelListEndpoint?.takeIf { it.isNotBlank() } ?: "미설정"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (provider.providerType == ProviderType.GOOGLE) {
                    TextButton(
                        onClick = onLoadGoogleModels,
                        enabled = hasApiKey
                    ) {
                        Text(text = "모델 목록 불러오기")
                    }
                }
                if (provider.providerType == ProviderType.OPENAI) {
                    TextButton(
                        onClick = onLoadOpenAIOfficialModels
                    ) {
                        Text(text = "모델 목록 불러오기")
                    }
                }
                if (isOpenAiCompatibleModelListProvider(provider.providerType)) {
                    TextButton(
                        onClick = onLoadOpenAiCompatibleModels,
                        enabled = canLoadOpenAiCompatibleModels
                    ) {
                        Text(text = "모델 목록 불러오기")
                    }
                }
                TextButton(onClick = onEditClick) {
                    Text(text = "수정")
                }
                TextButton(onClick = onDeleteClick) {
                    Text(text = "삭제")
                }
            }

            Text(
                text = providerCardGuideText(provider.providerType, hasApiKey),
                style = MaterialTheme.typography.bodySmall,
                color = if (hasApiKey || !isApiKeyRequiredForProvider(provider.providerType)) Color(0xFF9FE3B1) else Color(0xFFFFD08A)
            )
        }
    }
}

@Composable
private fun GoogleModelListDialog(
    provider: ProviderProfile,
    isLoading: Boolean,
    models: List<GoogleModelListItem>,
    errorMessage: String?,
    resultMessage: String?,
    existingModels: List<ConversationModelProfile>,
    onDismiss: () -> Unit,
    onAddModel: (GoogleModelListItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Google 모델 목록") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                if (isLoading) {
                    Text(text = "모델 목록을 불러오는 중입니다...")
                }

                errorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2222)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFFFC4C4)
                        )
                    }
                }

                resultMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF203020)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFBFECC4)
                        )
                    }
                }

                if (!isLoading && errorMessage == null && models.isEmpty()) {
                    Text(text = "표시할 모델이 없습니다.")
                }

                models.forEach { item ->
                    val alreadyAdded = existingModels.any { model ->
                        model.providerId == provider.providerId && model.rawModelName == item.rawModelName
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252A33)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.displayName.ifBlank { item.rawModelName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "rawModelName: ${item.rawModelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            if (item.description.isNotBlank()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD0D3DA),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "inputTokenLimit: ${item.inputTokenLimit ?: "unknown"} / outputTokenLimit: ${item.outputTokenLimit ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            Text(
                                text = "methods: ${item.supportedGenerationMethods.joinToString().ifBlank { "unknown" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Button(
                                onClick = { onAddModel(item) },
                                enabled = !alreadyAdded,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (alreadyAdded) "이미 추가됨" else "추가")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "닫기")
            }
        }
    )
}


@Composable
private fun OpenAIModelListDialog(
    provider: ProviderProfile,
    isLoading: Boolean,
    models: List<OpenAIModelListItem>,
    errorMessage: String?,
    resultMessage: String?,
    endpoint: String?,
    existingModels: List<ConversationModelProfile>,
    onDismiss: () -> Unit,
    onAddModel: (OpenAIModelListItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "OpenAI 공식 모델 목록") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "endpoint: ${endpoint ?: "https://api.openai.com/v1/models"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0D3DA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isLoading) {
                    Text(text = "OpenAI 공식 모델 목록을 불러오는 중입니다...")
                }

                errorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2222)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFFFC4C4)
                        )
                    }
                }

                resultMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF203020)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFBFECC4)
                        )
                    }
                }

                if (!isLoading && errorMessage == null && models.isEmpty()) {
                    Text(text = "표시할 모델이 없습니다.")
                }

                models.forEach { item ->
                    val alreadyAdded = existingModels.any { model ->
                        model.providerId == provider.providerId && model.rawModelName == item.rawModelName
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252A33)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.displayName.ifBlank { item.rawModelName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "rawModelName: ${item.rawModelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            Text(
                                text = "createdAt: ${item.createdAt ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            item.metadataRawSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD0D3DA),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { onAddModel(item) },
                                enabled = !alreadyAdded,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (alreadyAdded) "이미 추가됨" else "추가")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "닫기")
            }
        }
    )
}


@Composable
private fun OpenAiCompatibleModelListDialog(
    provider: ProviderProfile,
    isLoading: Boolean,
    models: List<OpenAiModelListCandidate>,
    errorMessage: String?,
    resultMessage: String?,
    endpoint: String?,
    existingModels: List<ConversationModelProfile>,
    onDismiss: () -> Unit,
    onAddModel: (OpenAiModelListCandidate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "OpenAI-compatible 모델 목록") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "endpoint: ${endpoint ?: "확인 중"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0D3DA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (isLoading) {
                    Text(text = "모델 목록을 불러오는 중입니다...")
                }

                errorMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2222)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFFFC4C4)
                        )
                    }
                }

                resultMessage?.let { message ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF203020)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFFBFECC4)
                        )
                    }
                }

                if (!isLoading && errorMessage == null && models.isEmpty()) {
                    Text(text = "표시할 모델이 없습니다.")
                }

                models.forEach { item ->
                    val alreadyAdded = existingModels.any { model ->
                        model.providerId == provider.providerId && model.rawModelName == item.rawModelName
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF252A33)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.displayName.ifBlank { item.rawModelName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "rawModelName: ${item.rawModelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            Text(
                                text = "contextWindow: ${item.contextWindow ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            Text(
                                text = "ownedBy: ${item.ownedBy ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA)
                            )
                            Text(
                                text = "input: ${item.inputModalities.joinToString().ifBlank { "text" }} / output: ${item.outputModalities.joinToString().ifBlank { "text" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD0D3DA),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            item.description?.takeIf { it.isNotBlank() }?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD0D3DA),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { onAddModel(item) },
                                enabled = !alreadyAdded,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (alreadyAdded) "이미 추가됨" else "추가")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "닫기")
            }
        }
    )
}

@Composable
private fun ProviderInfoRow(
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

private fun providerMatchesSearch(
    provider: ProviderProfile,
    normalizedSearchQuery: String
): Boolean {
    if (normalizedSearchQuery.isBlank()) return true

    val query = normalizedSearchQuery.trim()
    val normalizedTypeName = provider.providerType.name.lowercase()
    val readableTypeName = normalizedTypeName.replace("_", " ")
    val displayName = provider.displayName.lowercase()
    val providerId = provider.providerId.lowercase()

    // "openai" 단독 검색은 공식 OPENAI Provider만 우선 찾는다.
    // OPENAI_COMPATIBLE, baseUrl, modelListEndpoint 때문에 Groq/OpenRouter/LOCAL/CUSTOM이 과검색되지 않게 한다.
    if (query == "openai") {
        return provider.providerType == ProviderType.OPENAI ||
            displayName == "openai" ||
            displayName.startsWith("openai ") ||
            providerId == "openai" ||
            providerId.startsWith("provider_openai")
    }

    return displayName.contains(query) ||
        providerId.contains(query) ||
        normalizedTypeName == query ||
        readableTypeName == query ||
        readableTypeName.contains(query)
}

private fun isDefaultSeedProvider(provider: ProviderProfile): Boolean {
    return provider.providerId in setOf(
        "provider_google_gemini_openai",
        "provider_groq_openai",
        "provider_openrouter_openai"
    )
}

private fun providerCallStyleLabel(providerType: ProviderType): String {
    return when (providerType) {
        ProviderType.GOOGLE -> "Google Gemini"
        ProviderType.OPENAI -> "OpenAI official API"
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible"
        ProviderType.NVIDIA -> "NVIDIA API"
        ProviderType.LOCAL -> "Local OpenAI-compatible"
        ProviderType.CUSTOM -> "Custom OpenAI-compatible"
    }
}

private fun providerCallStyleColor(providerType: ProviderType): Color {
    return when (providerType) {
        ProviderType.GOOGLE -> Color(0xFF9EC7FF)
        ProviderType.OPENAI -> Color(0xFFAEC6FF)
        ProviderType.OPENAI_COMPATIBLE -> Color(0xFFB7D7FF)
        ProviderType.LOCAL -> Color(0xFF9FE3B1)
        ProviderType.CUSTOM -> Color(0xFFFFD08A)
        ProviderType.NVIDIA -> Color(0xFF9FE3B1)
    }
}

private fun isApiKeyRequiredForProvider(providerType: ProviderType): Boolean {
    return when (providerType) {
        ProviderType.OPENAI,
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.NVIDIA,
        ProviderType.GOOGLE -> true
        ProviderType.LOCAL,
        ProviderType.CUSTOM -> false
    }
}

private fun apiKeyRequirementLabel(providerType: ProviderType): String {
    return if (isApiKeyRequiredForProvider(providerType)) "필수" else "선택"
}

private fun isBaseUrlRecommended(providerType: ProviderType): Boolean {
    return providerType == ProviderType.OPENAI ||
            providerType == ProviderType.OPENAI_COMPATIBLE ||
            providerType == ProviderType.LOCAL ||
            providerType == ProviderType.CUSTOM
}

private fun baseUrlPlaceholder(providerType: ProviderType): String {
    return when (providerType) {
        ProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/openai/"
        ProviderType.OPENAI -> "https://api.openai.com/v1"
        ProviderType.OPENAI_COMPATIBLE -> "https://api.groq.com/openai/v1 또는 https://openrouter.ai/api/v1"
        ProviderType.LOCAL -> "http://192.168.0.25:1234/v1 또는 http://192.168.0.25:11434/v1"
        ProviderType.CUSTOM -> "https://your-server.example.com/v1"
        ProviderType.NVIDIA -> "NVIDIA compatible base URL"
    }
}

private fun providerCardGuideText(providerType: ProviderType, hasApiKey: Boolean): String {
    return when (providerType) {
        ProviderType.GOOGLE -> if (hasApiKey) {
            "이 API Key는 대화모드 Gemini 호출과 모델 목록 조회에 사용됩니다."
        } else {
            "모델 목록 조회와 Gemini 호출은 API Key 저장 후 사용할 수 있습니다."
        }
        ProviderType.OPENAI -> "OpenAI 공식 API Provider입니다. Web Search는 후속 Responses API adapter에서 지원 예정입니다. API Key는 필수입니다."
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible /chat/completions 엔드포인트를 사용합니다. API Key는 필수입니다."
        ProviderType.LOCAL -> "LM Studio, Ollama OpenAI-compatible 서버 등에 연결합니다. 휴대폰에서는 127.0.0.1 대신 PC의 LAN IP를 사용하세요."
        ProviderType.CUSTOM -> "OpenAI-compatible 응답 형식을 따르는 사용자 지정 서버입니다. API Key는 선택입니다."
        ProviderType.NVIDIA -> "NVIDIA API 연결 후보입니다. 현재 대화모드 실제 호출 일반화는 후속 단계에서 확정합니다."
    }
}

@Composable
private fun ProviderTypeGuideCard(providerType: ProviderType) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${providerType.name} 안내",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = providerCallStyleColor(providerType)
            )
            Text(
                text = providerCardGuideText(providerType, hasApiKey = false),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
            Text(
                text = "API Key: ${apiKeyRequirementLabel(providerType)} · Base URL 예: ${baseUrlPlaceholder(providerType)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
            if (providerType == ProviderType.LOCAL) {
                Text(
                    text = "On-device 모델 직접 실행은 로컬 모델/런타임 단계에서 별도 지원 예정입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD0D3DA)
                )
            }
        }
    }
}

@Composable
private fun ProviderProfileEditDialog(
    initialProvider: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (ProviderProfile, String?, Boolean) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val providerId = remember(initialProvider) {
        initialProvider?.providerId ?: "provider_${System.currentTimeMillis()}"
    }

    var displayName by remember(initialProvider) { mutableStateOf(initialProvider?.displayName.orEmpty()) }
    var providerType by remember(initialProvider) { mutableStateOf(initialProvider?.providerType ?: ProviderType.GOOGLE) }
    var baseUrl by remember(initialProvider) { mutableStateOf(initialProvider?.baseUrl.orEmpty()) }
    var apiKeyInput by remember(initialProvider) { mutableStateOf("") }
    var shouldDeleteApiKey by remember(initialProvider) { mutableStateOf(false) }
    var modelListEndpoint by remember(initialProvider) { mutableStateOf(initialProvider?.modelListEndpoint.orEmpty()) }
    var isEnabled by remember(initialProvider) { mutableStateOf(initialProvider?.isEnabled ?: true) }

    val canSave = displayName.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (initialProvider == null) "Provider 추가" else "Provider 수정")
        },
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
                    label = { Text(text = "Provider 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Provider Type",
                    style = MaterialTheme.typography.labelLarge
                )

                ProviderType.values().forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = providerType == type,
                            onClick = { providerType = type }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = type.name)
                    }
                }

                Divider()

                ProviderTypeGuideCard(providerType)

                if (baseUrl.isBlank() && isBaseUrlRecommended(providerType)) {
                    Text(
                        text = "Base URL이 비어 있습니다. 저장은 가능하지만 호출 시 BASE_URL_MISSING으로 실패합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFD08A)
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(text = if (isBaseUrlRecommended(providerType)) "Base URL" else "Base URL (optional)") },
                    placeholder = { Text(text = baseUrlPlaceholder(providerType)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        if (it.isNotBlank()) shouldDeleteApiKey = false
                    },
                    label = { Text(text = "API Key (${apiKeyRequirementLabel(providerType)})") },
                    placeholder = {
                        Text(
                            text = if (initialProvider?.apiKeyAlias.isNullOrBlank()) {
                                "미설정"
                            } else {
                                "기존 설정 유지"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialProvider?.apiKeyAlias?.isNotBlank() == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = shouldDeleteApiKey,
                            onCheckedChange = {
                                shouldDeleteApiKey = it
                                if (it) apiKeyInput = ""
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "저장된 API Key 삭제")
                    }
                }

                OutlinedTextField(
                    value = modelListEndpoint,
                    onValueChange = { modelListEndpoint = it },
                    label = { Text(text = "Model List Endpoint (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "활성화",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val createdAt = initialProvider?.createdAt ?: now
                    val updatedAt = System.currentTimeMillis()
                    val nextApiKeyAlias = when {
                        shouldDeleteApiKey -> null
                        apiKeyInput.isNotBlank() -> "stored"
                        initialProvider?.apiKeyAlias.isNullOrBlank() -> null
                        else -> initialProvider?.apiKeyAlias
                    }

                    onSave(
                        ProviderProfile(
                            providerId = providerId,
                            displayName = displayName.trim(),
                            providerType = providerType,
                            baseUrl = baseUrl.trim(),
                            apiKeyAlias = nextApiKeyAlias,
                            modelListEndpoint = modelListEndpoint.trim().ifBlank { null },
                            isEnabled = isEnabled,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        ),
                        apiKeyInput.takeIf { it.isNotBlank() },
                        shouldDeleteApiKey
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



private fun isOpenAiCompatibleModelListProvider(providerType: ProviderType): Boolean {
    return providerType == ProviderType.OPENAI_COMPATIBLE ||
        providerType == ProviderType.LOCAL ||
        providerType == ProviderType.CUSTOM
}

private fun canLoadOpenAiCompatibleModelList(
    provider: ProviderProfile,
    hasApiKey: Boolean
): Boolean {
    val hasEndpoint = provider.modelListEndpoint?.isNotBlank() == true || provider.baseUrl.isNotBlank()
    val hasRequiredApiKey = provider.providerType != ProviderType.OPENAI_COMPATIBLE || hasApiKey
    return isOpenAiCompatibleModelListProvider(provider.providerType) && hasEndpoint && hasRequiredApiKey
}

private fun createOpenAiCompatibleMetadataCapabilityV2(): ModelCapabilityV2 {
    return ModelCapabilityV2(
        input = ModelInputCapability(
            text = CapabilityStatus.SUPPORTED,
            image = CapabilityStatus.UNKNOWN,
            audio = CapabilityStatus.UNKNOWN,
            video = CapabilityStatus.UNKNOWN,
            file = CapabilityStatus.UNKNOWN
        ),
        output = ModelOutputCapability(
            text = CapabilityStatus.SUPPORTED,
            code = CapabilityStatus.UNKNOWN,
            json = CapabilityStatus.UNKNOWN,
            image = CapabilityStatus.UNKNOWN,
            audio = CapabilityStatus.UNKNOWN,
            video = CapabilityStatus.UNKNOWN
        ),
        tools = ModelToolCapability(
            functionCalling = CapabilityStatus.UNKNOWN,
            webSearch = CapabilityStatus.UNKNOWN,
            codeExecution = CapabilityStatus.UNKNOWN,
            structuredOutput = CapabilityStatus.UNKNOWN
        ),
        reasoning = ModelReasoningCapability(
            thinking = CapabilityStatus.UNKNOWN,
            thinkingSummary = CapabilityStatus.UNKNOWN,
            chainOfThoughtRawAllowed = CapabilityStatus.UNSUPPORTED
        )
    )
}

private fun createGoogleMetadataCapabilityV2(): ModelCapabilityV2 {
    return ModelCapabilityV2(
        input = ModelInputCapability(
            text = CapabilityStatus.SUPPORTED,
            image = CapabilityStatus.UNKNOWN,
            audio = CapabilityStatus.UNKNOWN,
            video = CapabilityStatus.UNKNOWN,
            file = CapabilityStatus.UNKNOWN
        ),
        output = ModelOutputCapability(
            text = CapabilityStatus.SUPPORTED,
            code = CapabilityStatus.UNKNOWN,
            json = CapabilityStatus.UNKNOWN,
            image = CapabilityStatus.UNKNOWN,
            audio = CapabilityStatus.UNKNOWN,
            video = CapabilityStatus.UNKNOWN
        ),
        tools = ModelToolCapability(
            functionCalling = CapabilityStatus.UNKNOWN,
            webSearch = CapabilityStatus.UNKNOWN,
            codeExecution = CapabilityStatus.UNKNOWN,
            structuredOutput = CapabilityStatus.UNKNOWN
        ),
        reasoning = ModelReasoningCapability(
            thinking = CapabilityStatus.UNKNOWN,
            thinkingSummary = CapabilityStatus.UNKNOWN,
            chainOfThoughtRawAllowed = CapabilityStatus.UNSUPPORTED
        )
    )
}

private fun buildGoogleMetadataSummary(item: GoogleModelListItem): String {
    return buildString {
        append("name=").append(item.name)
        append("\ndisplayName=").append(item.displayName)
        if (item.description.isNotBlank()) {
            append("\ndescription=").append(item.description.take(300))
        }
        append("\nsupportedGenerationMethods=").append(item.supportedGenerationMethods.joinToString())
        append("\ninputTokenLimit=").append(item.inputTokenLimit ?: "unknown")
        append("\noutputTokenLimit=").append(item.outputTokenLimit ?: "unknown")
    }
}

