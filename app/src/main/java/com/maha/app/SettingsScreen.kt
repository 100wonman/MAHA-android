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
    val focusManager = LocalFocusManager.current

    var googleApiKeyInput by remember { mutableStateOf(savedGoogleApiKey) }
    var selectedProvider by remember { mutableStateOf(savedProvider) }
    var apiKeySaveMessage by remember { mutableStateOf("") }
    var providerSaveMessage by remember { mutableStateOf("") }

    LaunchedEffect(savedGoogleApiKey) {
        googleApiKeyInput = savedGoogleApiKey
    }

    LaunchedEffect(savedProvider) {
        selectedProvider = when (savedProvider) {
            ModelProviderType.GOOGLE -> ModelProviderType.GOOGLE
            else -> ModelProviderType.DUMMY
        }
    }

    val hasKey = googleApiKeyInput.isNotBlank()

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
                            description = "Google API Key가 있을 때 Gemini 호출을 사용합니다.",
                            selected = selectedProvider == ModelProviderType.GOOGLE,
                            onClick = {
                                selectedProvider = ModelProviderType.GOOGLE
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
                            status = if (selectedProvider == ModelProviderType.GOOGLE && !hasKey) {
                                "WAITING"
                            } else {
                                "SUCCESS"
                            },
                            message = when {
                                selectedProvider == ModelProviderType.DUMMY -> {
                                    "Dummy Provider가 선택되어 있습니다. 기존 더미 실행 흐름을 사용합니다."
                                }

                                selectedProvider == ModelProviderType.GOOGLE && hasKey -> {
                                    "Google Provider가 선택되어 있고 API Key가 입력되어 있습니다."
                                }

                                else -> {
                                    "Google Provider가 선택되어 있지만 API Key가 없습니다. 실행 시 GOOGLE_API_KEY_NOT_SET이 반환됩니다."
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
                            text = "Google Gemini API Key",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        Text(
                            text = "API Key는 로컬에 저장됩니다. Google Provider를 선택한 경우에만 사용됩니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
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
                            value = if (hasKey) "SET" else "NOT SET"
                        )

                        if (apiKeySaveMessage.isNotBlank()) {
                            StatusPanel(
                                title = "API Key Save Result",
                                status = "SUCCESS",
                                message = apiKeySaveMessage
                            )
                        }

                        StatusPanel(
                            title = "Google API Key Status",
                            status = if (hasKey) "SUCCESS" else "WAITING",
                            message = if (hasKey) {
                                "API Key가 입력되어 있습니다. 저장 버튼을 누르면 로컬에 저장됩니다."
                            } else {
                                "Google API Key가 아직 입력되지 않았습니다."
                            }
                        )
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
                            "API Key cleared"
                        } else {
                            "API Key saved"
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

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFFD3DBE7)
            )
        }
    }
}