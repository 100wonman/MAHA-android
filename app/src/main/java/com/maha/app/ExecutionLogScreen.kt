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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class RunLogGroup(
    val runId: String,
    val executedAt: String,
    val status: String,
    val totalLatencyMs: Long,
    val workerLogs: List<ExecutionHistoryLog>
)

private data class LogStats(
    val totalRuns: Int,
    val successRuns: Int,
    val failedRuns: Int,
    val successRate: Int,
    val averageRunLatencyMs: Long,
    val averageWorkerLatencyMs: Long
)

private data class ProviderStats(
    val name: String,
    val callCount: Int,
    val successCount: Int,
    val successRate: Int,
    val averageLatencyMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLogScreen(
    logs: List<ExecutionHistoryLog>,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit,
    onClearLogsClick: () -> Unit
) {
    var selectedLog by remember { mutableStateOf<ExecutionHistoryLog?>(null) }
    val expandedRunMap = remember { mutableStateMapOf<String, Boolean>() }

    val runGroups = logs
        .groupBy { it.runId.ifBlank { "UNKNOWN_RUN" } }
        .map { entry ->
            val workerLogs = entry.value.sortedBy { it.executedAt }
            RunLogGroup(
                runId = entry.key,
                executedAt = workerLogs.maxOfOrNull { it.executedAt } ?: "",
                status = calculateRunStatus(workerLogs),
                totalLatencyMs = workerLogs.sumOf { it.latencyMs },
                workerLogs = workerLogs
            )
        }
        .sortedByDescending { it.executedAt }

    val totalStats = calculateLogStats(runGroups)
    val providerStats = calculateProviderStats(logs)
    val modelStats = calculateModelStats(logs)

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
                LogStatsCard(stats = totalStats)
            }

            item {
                ProviderStatsCard(
                    title = "Provider 통계",
                    stats = providerStats
                )
            }

            item {
                ProviderStatsCard(
                    title = "Model 통계",
                    stats = modelStats
                )
            }

            item {
                SecondaryActionButton(
                    text = "Clear Logs",
                    enabled = runGroups.isNotEmpty(),
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

            if (runGroups.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No execution logs yet.")
                }
            } else {
                items(runGroups) { group ->
                    val expanded = expandedRunMap[group.runId] ?: false

                    RunLogGroupCard(
                        group = group,
                        expanded = expanded,
                        onToggleExpand = {
                            expandedRunMap[group.runId] = !expanded
                        },
                        onWorkerLogClick = { log ->
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
private fun LogStatsCard(stats: LogStats) {
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Run 통계",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            InfoRow(label = "전체 Run 수", value = stats.totalRuns.toString())
            InfoRow(label = "성공 Run 수", value = stats.successRuns.toString())
            InfoRow(label = "실패 Run 수", value = stats.failedRuns.toString())
            InfoRow(label = "성공률", value = "${stats.successRate}%")
            InfoRow(label = "평균 Run latency", value = "${stats.averageRunLatencyMs}ms")
            InfoRow(label = "평균 Worker latency", value = "${stats.averageWorkerLatencyMs}ms")
        }
    }
}

@Composable
private fun ProviderStatsCard(
    title: String,
    stats: List<ProviderStats>
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )

            if (stats.isEmpty()) {
                Text(
                    text = "통계 없음",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD3DBE7)
                )
            } else {
                stats.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF101722))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )

                        InfoRow(label = "호출 수", value = item.callCount.toString())
                        InfoRow(label = "성공 수", value = item.successCount.toString())
                        InfoRow(label = "성공률", value = "${item.successRate}%")
                        InfoRow(label = "평균 latency", value = "${item.averageLatencyMs}ms")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunLogGroupCard(
    group: RunLogGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onWorkerLogClick: (ExecutionHistoryLog) -> Unit
) {
    val uiStatus = toRunUiStatus(group.status)
    val koreanStatus = toRunStatusKorean(group.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        text = group.runId,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )

                    Text(
                        text = group.executedAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD3DBE7)
                    )
                }

                Surface(
                    color = getStatusColor(uiStatus).copy(alpha = 0.24f)
                ) {
                    Text(
                        text = koreanStatus,
                        color = getStatusColor(uiStatus),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            InfoRow(label = "Workers", value = group.workerLogs.size.toString())
            InfoRow(label = "Total Latency", value = "${group.totalLatencyMs}ms")
            InfoRow(label = "Expand", value = if (expanded) "열림" else "닫힘")

            if (expanded) {
                HorizontalDivider(color = Color(0xFF273244))

                group.workerLogs.forEach { log ->
                    WorkerLogRow(
                        log = log,
                        onClick = {
                            onWorkerLogClick(log)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkerLogRow(
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
            containerColor = Color(0xFF101722)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
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
                text = "Worker Log Detail",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DetailText(label = "Run ID", value = log.runId)
                }

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

private fun calculateLogStats(runGroups: List<RunLogGroup>): LogStats {
    val totalRuns = runGroups.size
    val successRuns = runGroups.count { it.status == "SUCCESS" }
    val failedRuns = totalRuns - successRuns

    val successRate = if (totalRuns == 0) {
        0
    } else {
        ((successRuns.toDouble() / totalRuns.toDouble()) * 100).toInt()
    }

    val averageRunLatencyMs = if (runGroups.isEmpty()) {
        0L
    } else {
        runGroups.map { it.totalLatencyMs }.average().toLong()
    }

    val allWorkerLogs = runGroups.flatMap { it.workerLogs }

    val averageWorkerLatencyMs = if (allWorkerLogs.isEmpty()) {
        0L
    } else {
        allWorkerLogs.map { it.latencyMs }.average().toLong()
    }

    return LogStats(
        totalRuns = totalRuns,
        successRuns = successRuns,
        failedRuns = failedRuns,
        successRate = successRate,
        averageRunLatencyMs = averageRunLatencyMs,
        averageWorkerLatencyMs = averageWorkerLatencyMs
    )
}

private fun calculateProviderStats(logs: List<ExecutionHistoryLog>): List<ProviderStats> {
    return logs
        .groupBy { it.providerName.ifBlank { "UNKNOWN" } }
        .map { entry ->
            buildProviderStats(
                name = entry.key,
                logs = entry.value
            )
        }
        .sortedByDescending { it.callCount }
}

private fun calculateModelStats(logs: List<ExecutionHistoryLog>): List<ProviderStats> {
    return logs
        .groupBy {
            "${it.providerName.ifBlank { "UNKNOWN" }} / ${it.modelName.ifBlank { "UNKNOWN_MODEL" }}"
        }
        .map { entry ->
            buildProviderStats(
                name = entry.key,
                logs = entry.value
            )
        }
        .sortedByDescending { it.callCount }
}

private fun buildProviderStats(
    name: String,
    logs: List<ExecutionHistoryLog>
): ProviderStats {
    val callCount = logs.size
    val successCount = logs.count { it.status == "SUCCESS" }

    val successRate = if (callCount == 0) {
        0
    } else {
        ((successCount.toDouble() / callCount.toDouble()) * 100).toInt()
    }

    val averageLatencyMs = if (logs.isEmpty()) {
        0L
    } else {
        logs.map { it.latencyMs }.average().toLong()
    }

    return ProviderStats(
        name = name,
        callCount = callCount,
        successCount = successCount,
        successRate = successRate,
        averageLatencyMs = averageLatencyMs
    )
}

private fun calculateRunStatus(workerLogs: List<ExecutionHistoryLog>): String {
    val joined = workerLogs.joinToString(" ") {
        "${it.status} ${it.errorMessage} ${it.outputText}"
    }.uppercase()

    return when {
        joined.contains("RATE_LIMIT") || joined.contains("RATE_LIMITED") || joined.contains("429") -> "RATE_LIMITED"
        joined.contains("TIMEOUT") -> "TIMEOUT"
        joined.contains("SERVER") || joined.contains("500") || joined.contains("502") || joined.contains("503") || joined.contains("504") -> "SERVER_ERROR"
        workerLogs.any { it.status == "FAILED" } -> "FAILED"
        workerLogs.isNotEmpty() && workerLogs.all { it.status == "SUCCESS" } -> "SUCCESS"
        else -> "UNKNOWN"
    }
}

private fun toRunStatusKorean(status: String): String {
    return when (status) {
        "SUCCESS" -> "성공"
        "FAILED" -> "실패"
        "RATE_LIMITED" -> "제한됨"
        "TIMEOUT" -> "타임아웃"
        "SERVER_ERROR" -> "서버 오류"
        else -> "알 수 없음"
    }
}

private fun toRunUiStatus(status: String): String {
    return when (status) {
        "SUCCESS" -> "SUCCESS"
        "RATE_LIMITED" -> "RUNNING"
        "FAILED", "TIMEOUT", "SERVER_ERROR" -> "FAILED"
        else -> "WAITING"
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