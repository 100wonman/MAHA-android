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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

    fun reloadProviders() {
        providers = store.loadProviderProfiles().sortedBy { it.displayName.lowercase() }
        providerApiKeyStates = providers.associate { provider ->
            provider.providerId to store.hasProviderApiKey(provider.providerId)
        }
    }

    LaunchedEffect(Unit) {
        store.ensureSettingsFiles()
        reloadProviders()
    }

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
                    text = "대화모드 전용 Provider 설정을 추가, 수정, 삭제합니다. 실제 연결 테스트와 모델 목록 조회는 후속 단계입니다.",
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
        } else {
            providers.forEach { provider ->
                ProviderProfileCard(
                    provider = provider,
                    hasApiKey = providerApiKeyStates[provider.providerId] == true,
                    onEditClick = { editingProvider = provider },
                    onDeleteClick = { providerToDelete = provider },
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
}

@Composable
private fun ProviderProfileCard(
    provider: ProviderProfile,
    hasApiKey: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
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
                    if (provider.providerType == ProviderType.LOCAL) {
                        Text(
                            text = "LOCAL · Local Server",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9FE3B1)
                        )
                    }
                }
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            ProviderInfoRow(label = "Base URL", value = provider.baseUrl.ifBlank { "미설정" })
            ProviderInfoRow(
                label = "API Key",
                value = when {
                    hasApiKey -> "설정됨"
                    provider.providerType == ProviderType.LOCAL -> "선택 사항 / 미설정"
                    else -> "미설정"
                }
            )
            if (provider.providerType == ProviderType.LOCAL) {
                ProviderInfoRow(
                    label = "Runtime",
                    value = "Local Server 방식 준비 중"
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

@Composable
private fun LocalProviderGuideCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "LOCAL Provider 안내",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9FE3B1)
            )
            Text(
                text = "현재 LOCAL은 Local Server 방식 설정용입니다. 예: LM Studio, Ollama Gateway, OpenAI-compatible local server",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
            Text(
                text = "API Key는 서버 설정에 따라 선택 사항입니다. On-device 모델 직접 실행은 후속 단계에서 지원 예정입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0D3DA)
            )
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

                if (providerType == ProviderType.LOCAL) {
                    LocalProviderGuideCard()
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(text = "Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        if (it.isNotBlank()) shouldDeleteApiKey = false
                    },
                    label = { Text(text = if (providerType == ProviderType.LOCAL) "API Key (optional)" else "API Key") },
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
