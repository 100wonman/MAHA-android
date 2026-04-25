// ExecutionLogScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLogScreen(
    logs: List<ExecutionHistoryLog>,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onClearLogsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Execution Logs",
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
                    containerColor = Color(0xFF111827),
                    titleContentColor = Color(0xFFF8FAFC),
                    navigationIconContentColor = Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = Color(0xFF070B12)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B12))
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusPanel(
                    title = "Execution History",
                    status = "WAITING",
                    message = "Worker별 실행 시간, Provider, Model, 상태, 응답 시간, 오류 메시지를 확인합니다."
                )
            }

            item {
                SecondaryActionButton(
                    text = "Clear Logs",
                    enabled = logs.isNotEmpty(),
                    onClick = onClearLogsClick
                )
            }

            item {
                SecondaryActionButton(
                    text = "Back",
                    enabled = true,
                    onClick = onBackClick
                )
            }

            item {
                HorizontalDivider(color = Color(0xFF273244))
            }

            if (logs.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No execution logs yet.")
                }
            } else {
                items(logs) { log ->
                    ExecutionHistoryLogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun ExecutionHistoryLogCard(
    log: ExecutionHistoryLog
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = log.workerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            StateBadge(status = log.status)

            InfoRow(label = "Executed At", value = log.executedAt)
            InfoRow(label = "Run ID", value = log.runId)
            InfoRow(label = "Provider", value = log.providerName)
            InfoRow(label = "Model", value = log.modelName)
            InfoRow(label = "Latency", value = "${log.latencyMs}ms")

            if (log.errorMessage.isNotBlank()) {
                InfoRow(label = "Error", value = log.errorMessage)
            }

            SelectionContainer {
                Text(
                    text = "Input:\n${log.inputText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD3DBE7)
                )
            }

            SelectionContainer {
                Text(
                    text = "Output:\n${log.outputText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE5ECF6)
                )
            }
        }
    }
}