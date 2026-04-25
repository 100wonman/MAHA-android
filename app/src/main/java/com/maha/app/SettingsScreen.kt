// SettingsScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    savedGoogleApiKey: String,
    savedProvider: String,
    onMenuClick: () -> Unit,
    onSaveGoogleApiKeyClick: (String) -> Unit,
    onSaveProviderClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var googleApiKeyInput by remember { mutableStateOf(savedGoogleApiKey) }
    var nvidiaApiKeyInput by remember { mutableStateOf(ApiKeyManager.getNvidiaApiKey(context)) }
    var selectedProvider by remember { mutableStateOf(savedProvider) }

    var fallbackProvider by remember { mutableStateOf(ApiKeyManager.getFallbackProvider(context)) }
    var fallbackModel by remember { mutableStateOf(ApiKeyManager.getFallbackModel(context)) }

    var apiKeySaveMessage by remember { mutableStateOf("") }
    var nvidiaApiKeySaveMessage by remember { mutableStateOf("") }
    var providerSaveMessage by remember { mutableStateOf("") }
    var fallbackSaveMessage by remember { mutableStateOf("") }

    LaunchedEffect(savedGoogleApiKey) {
        googleApiKeyInput = savedGoogleApiKey
    }

    LaunchedEffect(savedProvider) {
        selectedProvider = sanitizeProviderForSettings(savedProvider)
    }

    val hasGoogleKey = googleApiKeyInput.isNotBlank()
    val hasNvidiaKey = nvidiaApiKeyInput.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Text(
                            text = "☰",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF111827),
                    titleContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
                    navigationIconContentColor = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color(0xFF070B12)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF070B12))
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Model Provider",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        ProviderRadioRow(
                            title = "Dummy Provider",
                            description = "기존 더미 응답을 사용합니다.",
                            selected = selectedProvider == ModelProviderType.DUMMY,
                            onClick = {
                                selectedProvider = ModelProviderType.DUMMY
                                providerSaveMessage = ""
                            }
                        )

                        ProviderRadioRow(
                            title = "Google Gemini Provider",
                            description = "Google API Key가 있을 때 Gemini / Gemma 호출을 사용합니다.",
                            selected = selectedProvider == ModelProviderType.GOOGLE,
                            onClick = {
                                selectedProvider = ModelProviderType.GOOGLE
                                providerSaveMessage = ""
                            }
                        )

                        ProviderRadioRow(
                            title = "NVIDIA Provider",
                            description = "NVIDIA API Key가 있을 때 NVIDIA OpenAI 호환 API를 사용합니다.",
                            selected = selectedProvider == ModelProviderType.NVIDIA,
                            onClick = {
                                selectedProvider = ModelProviderType.NVIDIA
                                providerSaveMessage = ""
                            }
                        )

                        InfoRow(
                            label = "Current Provider",
                            value = selectedProvider
                        )

                        if (providerSaveMessage.isNotBlank()) {
                            StatusPanel(
                                title = "Provider Save Result",
                                status = "SUCCESS",
                                message = providerSaveMessage
                            )
                        }

                        StatusPanel(
                            title = "Provider Status",
                            status = when {
                                selectedProvider == ModelProviderType.GOOGLE && !hasGoogleKey -> "WAITING"
                                selectedProvider == ModelProviderType.NVIDIA && !hasNvidiaKey -> "WAITING"
                                else -> "SUCCESS"
                            },
                            message = when {
                                selectedProvider == ModelProviderType.DUMMY -> {
                                    "Dummy Provider가 선택되어 있습니다. 기존 더미 실행 흐름을 사용합니다."
                                }

                                selectedProvider == ModelProviderType.GOOGLE && hasGoogleKey -> {
                                    "Google Provider가 선택되어 있고 Google API Key가 입력되어 있습니다."
                                }

                                selectedProvider == ModelProviderType.GOOGLE && !hasGoogleKey -> {
                                    "Google Provider가 선택되어 있지만 API Key가 없습니다. 실행 시 GOOGLE_API_KEY_NOT_SET이 반환됩니다."
                                }

                                selectedProvider == ModelProviderType.NVIDIA && hasNvidiaKey -> {
                                    "NVIDIA Provider가 선택되어 있고 NVIDIA API Key가 입력되어 있습니다."
                                }

                                else -> {
                                    "NVIDIA Provider가 선택되어 있지만 API Key가 없습니다. 실행 시 NVIDIA_API_KEY_NOT_SET이 반환됩니다."
                                }
                            }
                        )
                    }
                }
            }

            item {
                SecondaryActionButton(
                    text = "Save Provider",
                    enabled = true,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onSaveProviderClick(selectedProvider)
                        providerSaveMessage = "Current Provider: $selectedProvider"
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Fallback Model Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        ProviderRadioRow(
                            title = "Fallback Google",
                            description = "전체 Worker 일괄 변경 시 Google Provider를 사용합니다.",
                            selected = fallbackProvider == ModelProviderType.GOOGLE,
                            onClick = {
                                fallbackProvider = ModelProviderType.GOOGLE
                                fallbackSaveMessage = ""
                            }
                        )

                        ProviderRadioRow(
                            title = "Fallback NVIDIA",
                            description = "전체 Worker 일괄 변경 시 NVIDIA Provider를 사용합니다.",
                            selected = fallbackProvider == ModelProviderType.NVIDIA,
                            onClick = {
                                fallbackProvider = ModelProviderType.NVIDIA
                                fallbackSaveMessage = ""
                            }
                        )

                        ProviderRadioRow(
                            title = "Fallback Dummy",
                            description = "전체 Worker 일괄 변경 시 Dummy Provider를 사용합니다.",
                            selected = fallbackProvider == ModelProviderType.DUMMY,
                            onClick = {
                                fallbackProvider = ModelProviderType.DUMMY
                                fallbackSaveMessage = ""
                            }
                        )

                        OutlinedTextField(
                            value = fallbackModel,
                            onValueChange = {
                                fallbackModel = it
                                fallbackSaveMessage = ""
                            },
                            label = { Text("Fallback Model Name") },
                            placeholder = { Text("gemini-flash-lite-latest") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        InfoRow(
                            label = "Fallback Provider",
                            value = fallbackProvider
                        )

                        InfoRow(
                            label = "Fallback Model",
                            value = fallbackModel.ifBlank { "gemini-flash-lite-latest" }
                        )

                        if (fallbackSaveMessage.isNotBlank()) {
                            StatusPanel(
                                title = "Fallback Save Result",
                                status = "SUCCESS",
                                message = fallbackSaveMessage
                            )
                        }
                    }
                }
            }

            item {
                PrimaryActionButton(
                    text = "Save Fallback Settings",
                    enabled = true,
                    onClick = {
                        focusManager.clearFocus(force = true)

                        ApiKeyManager.saveFallbackProvider(context, fallbackProvider)
                        ApiKeyManager.saveFallbackModel(context, fallbackModel)

                        fallbackProvider = ApiKeyManager.getFallbackProvider(context)
                        fallbackModel = ApiKeyManager.getFallbackModel(context)

                        fallbackSaveMessage = "Fallback saved: $fallbackProvider / $fallbackModel"
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Google Gemini API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        OutlinedTextField(
                            value = googleApiKeyInput,
                            onValueChange = {
                                googleApiKeyInput = it
                                apiKeySaveMessage = ""
                            },
                            label = { Text("Google API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        InfoRow(
                            label = "Google API Key",
                            value = if (hasGoogleKey) "SET" else "NOT SET"
                        )

                        if (apiKeySaveMessage.isNotBlank()) {
                            StatusPanel(
                                title = "Google API Key Save Result",
                                status = "SUCCESS",
                                message = apiKeySaveMessage
                            )
                        }
                    }
                }
            }

            item {
                PrimaryActionButton(
                    text = "Save Google API Key",
                    enabled = true,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onSaveGoogleApiKeyClick(googleApiKeyInput)
                        apiKeySaveMessage = if (googleApiKeyInput.isBlank()) {
                            "Google API Key cleared"
                        } else {
                            "Google API Key saved"
                        }
                    }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "NVIDIA API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        OutlinedTextField(
                            value = nvidiaApiKeyInput,
                            onValueChange = {
                                nvidiaApiKeyInput = it
                                nvidiaApiKeySaveMessage = ""
                            },
                            label = { Text("NVIDIA API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        InfoRow(
                            label = "NVIDIA API Key",
                            value = if (hasNvidiaKey) "SET" else "NOT SET"
                        )

                        if (nvidiaApiKeySaveMessage.isNotBlank()) {
                            StatusPanel(
                                title = "NVIDIA API Key Save Result",
                                status = "SUCCESS",
                                message = nvidiaApiKeySaveMessage
                            )
                        }
                    }
                }
            }

            item {
                PrimaryActionButton(
                    text = "Save NVIDIA API Key",
                    enabled = true,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        ApiKeyManager.saveNvidiaApiKey(context, nvidiaApiKeyInput)
                        nvidiaApiKeyInput = ApiKeyManager.getNvidiaApiKey(context)
                        nvidiaApiKeySaveMessage = if (nvidiaApiKeyInput.isBlank()) {
                            "NVIDIA API Key cleared"
                        } else {
                            "NVIDIA API Key saved"
                        }
                    }
                )
            }

            item {
                SecondaryActionButton(
                    text = "Back",
                    enabled = true,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        onBackClick()
                    }
                )
            }
        }
    }
}

@Composable
fun ProviderRadioRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
            )

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
                )
            }
        }
    }
}

private fun sanitizeProviderForSettings(provider: String): String {
    return when (provider) {
        ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
        ModelProviderType.NVIDIA -> ModelProviderType.NVIDIA
        ModelProviderType.DUMMY -> ModelProviderType.DUMMY
        else -> ModelProviderType.DUMMY
    }
}