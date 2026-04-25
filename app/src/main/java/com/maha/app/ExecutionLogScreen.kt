// ExecutionLogScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var selectedLog by remember { mutableStateOf<ExecutionHistoryLog?>(null) }

    val sortedLogs = logs.sortedByDescending { it.executedAt }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StatusPanel(
                    title = "Execution History",
                    status = "WAITING",
                    message = "최신 로그가 위에 표시됩니다. 항목을 누르면 입력, 출력, 오류 상세를 확인할 수 있습니다."
                )
            }

            item {
                SecondaryActionButton(
                    text = "Clear Logs",
                    enabled = sortedLogs.isNotEmpty(),
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

            if (sortedLogs.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No execution logs yet.")
                }
            } else {
                items(sortedLogs) { log ->
                    ExecutionLogRow(
                        log = log,
                        onClick = {
                            selectedLog = log
                        }
                    )
                }
            }
        }
    }

    selectedLog?.let { log ->
        ExecutionLogDetailDialog(
            log = log,
            onDismiss = {
                selectedLog = null
            }
        )
    }
}

@Composable
private fun ExecutionLogRow(
    log: ExecutionHistoryLog,
    onClick: () -> Unit
) {
    val displayStatus = toExecutionStatusKorean(log)
    val uiStatus = toExecutionUiStatus(log)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = log.workerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )

                    Text(
                        text = log.executedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD3DBE7)
                    )
                }

                Surface(
                    color = getStatusColor(uiStatus).copy(alpha = 0.24f)
                ) {
                    Text(
                        text = displayStatus,
                        color = getStatusColor(uiStatus),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            InfoRow(label = "Provider", value = log.providerName)
            InfoRow(label = "Model", value = log.modelName)
            InfoRow(label = "Latency", value = "${log.latencyMs}ms")

            if (log.errorMessage.isNotBlank()) {
                Text(
                    text = "Error: ${log.errorMessage.take(120)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFCA5A5)
                )
            }
        }
    }
}

@Composable
private fun ExecutionLogDetailDialog(
    log: ExecutionHistoryLog,
    onDismiss: () -> Unit
) {
    AlertDialog(
        containerColor = Color(0xFF1A2230),
        titleContentColor = Color(0xFFF8FAFC),
        textContentColor = Color(0xFFE5ECF6),
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "닫기")
            }
        },
        title = {
            Text(
                text = "Log Detail",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DetailText(label = "실행 시간", value = log.executedAt)
                }

                item {
                    DetailText(label = "Worker", value = log.workerName)
                }

                item {
                    DetailText(label = "Provider", value = log.providerName)
                }

                item {
                    DetailText(label = "Model", value = log.modelName)
                }

                item {
                    DetailText(label = "상태", value = toExecutionStatusKorean(log))
                }

                item {
                    DetailText(label = "응답 시간", value = "${log.latencyMs}ms")
                }

                item {
                    DetailText(
                        label = "HTTP 상태 코드",
                        value = if (log.httpStatusCode >= 0) {
                            log.httpStatusCode.toString()
                        } else {
                            "기록 없음"
                        }
                    )
                }

                item {
                    DetailText(
                        label = "입력 prompt 요약",
                        value = log.inputText.ifBlank { "(empty input)" }.take(500)
                    )
                }

                item {
                    DetailText(
                        label = "출력 response 요약",
                        value = log.outputText.ifBlank { "(empty output)" }.take(500)
                    )
                }

                item {
                    DetailText(
                        label = "전체 오류 메시지",
                        value = log.errorMessage.ifBlank { "없음" }
                    )
                }
            }
        }
    )
}

@Composable
private fun DetailText(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF8FAFC)
        )

        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE5ECF6)
            )
        }
    }
}

private fun toExecutionStatusKorean(log: ExecutionHistoryLog): String {
    val text = "${log.status} ${log.errorMessage} ${log.outputText}".uppercase()

    return when {
        text.contains("TIMEOUT") -> "타임아웃"
        text.contains("RATE_LIMIT") || text.contains("RATE_LIMITED") || text.contains("429") -> "제한됨"
        text.contains("SERVER") || text.contains("500") || text.contains("502") || text.contains("503") || text.contains("504") -> "서버 오류"
        log.status == "SUCCESS" -> "성공"
        log.status == "FAILED" -> "실패"
        else -> log.status.ifBlank { "알 수 없음" }
    }
}

private fun toExecutionUiStatus(log: ExecutionHistoryLog): String {
    val text = "${log.status} ${log.errorMessage} ${log.outputText}".uppercase()

    return when {
        text.contains("TIMEOUT") -> "FAILED"
        text.contains("RATE_LIMIT") || text.contains("RATE_LIMITED") || text.contains("429") -> "RUNNING"
        text.contains("SERVER") || text.contains("500") || text.contains("502") || text.contains("503") || text.contains("504") -> "FAILED"
        log.status == "SUCCESS" -> "SUCCESS"
        log.status == "FAILED" -> "FAILED"
        else -> "WAITING"
    }
}