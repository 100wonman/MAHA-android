// SettingsScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    savedGoogleApiKey: String,
    onMenuClick: () -> Unit,
    onSaveGoogleApiKeyClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var googleApiKeyInput by remember { mutableStateOf(savedGoogleApiKey) }

    LaunchedEffect(savedGoogleApiKey) {
        googleApiKeyInput = savedGoogleApiKey
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
                        text = "Google Gemini API Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )

                    Text(
                        text = "현재 단계에서는 API Key 저장 여부만 확인합니다. 실제 Google Gemini API 호출은 아직 실행하지 않습니다.",
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
                        title = "Google Provider Status",
                        status = if (hasKey) "SUCCESS" else "WAITING",
                        message = if (hasKey) {
                            "API Key가 저장되어 있습니다. 실제 네트워크 호출은 아직 구현되지 않았습니다."
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