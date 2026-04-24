// SettingsScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    var googleApiKeyInput by remember { mutableStateOf(savedGoogleApiKey) }
    var selectedProvider by remember { mutableStateOf(savedProvider) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF070B12))
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                        }
                    )

                    ProviderRadioRow(
                        title = "Google Gemini Provider",
                        description = "Google API Key가 있을 때 Gemini 호출을 사용합니다.",
                        selected = selectedProvider == ModelProviderType.GOOGLE,
                        onClick = {
                            selectedProvider = ModelProviderType.GOOGLE
                        }
                    )

                    InfoRow(
                        label = "Selected Provider",
                        value = selectedProvider
                    )

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
                                "Google Provider가 선택되어 있고 API Key가 저장되어 있습니다."
                            }

                            else -> {
                                "Google Provider가 선택되어 있지만 API Key가 없습니다. 실행 시 GOOGLE_API_KEY_NOT_SET이 반환됩니다."
                            }
                        }
                    )
                }
            }

            SecondaryActionButton(
                text = "Save Provider",
                enabled = true,
                onClick = {
                    onSaveProviderClick(selectedProvider)
                }
            )

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
                        onValueChange = { googleApiKeyInput = it },
                        label = { Text("Google API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    InfoRow(
                        label = "Google API Key",
                        value = if (hasKey) "SET" else "NOT SET"
                    )

                    StatusPanel(
                        title = "Google API Key Status",
                        status = if (hasKey) "SUCCESS" else "WAITING",
                        message = if (hasKey) {
                            "API Key가 저장되어 있습니다."
                        } else {
                            "Google API Key가 아직 저장되지 않았습니다."
                        }
                    )
                }
            }

            PrimaryActionButton(
                text = "Save Google API Key",
                enabled = true,
                onClick = {
                    onSaveGoogleApiKeyClick(googleApiKeyInput)
                }
            )

            SecondaryActionButton(
                text = "Back",
                enabled = true,
                onClick = onBackClick
            )
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