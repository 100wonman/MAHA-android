// RunDetailScreen.kt

package com.maha.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    run: Run,
    onMenuClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val successCount = run.results.count { it.status == "SUCCESS" }
    val failedCount = run.results.count { it.status == "FAILED" }
    val summaryStatus = when {
        failedCount > 0 -> "FAILED"
        successCount > 0 -> "SUCCESS"
        else -> "WAITING"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Run Detail",
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
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF070B12))
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = run.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                        )

                        StateBadge(status = summaryStatus)

                        InfoRow(label = "Run ID", value = run.runId)
                        InfoRow(label = "Time", value = run.timestamp)
                        InfoRow(label = "Results", value = run.results.size.toString())
                        InfoRow(label = "Logs", value = run.logs.size.toString())
                    }
                }
            }

            item {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            }

            if (run.results.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No results.")
                }
            } else {
                items(run.results) { result ->
                    RunResultDetailItem(result = result)
                }
            }

            item {
                HorizontalDivider(
                    color = androidx.compose.ui.graphics.Color(0xFF273244)
                )
            }

            item {
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                )
            }

            if (run.logs.isEmpty()) {
                item {
                    EmptyInfoCard(text = "No logs.")
                }
            } else {
                items(run.logs) { log ->
                    ExecutionLogDetailItem(log = log)
                }
            }

            item {
                SecondaryActionButton(
                    text = "Back",
                    enabled = true,
                    onClick = onBackClick
                )
            }
        }
    }
}

@Composable
fun RunResultDetailItem(result: RunResult) {
    Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1A2230)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RowTitleWithBadge(
                title = "${result.order}. ${result.agentName}",
                status = result.status
            )

            InfoRow(label = "Time", value = result.timestamp)

            OutlinedCard(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFF101722))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Input",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )

                    Text(
                        text = result.inputText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
                    )
                }
            }

            OutlinedCard(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFF101722))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Output",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
                    )

                    Text(
                        text = result.outputText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutionLogDetailItem(log: ExecutionLog) {
    val logStatus = when {
        log.message.contains("FAILED", ignoreCase = true) -> "FAILED"
        log.message.contains("SUCCESS", ignoreCase = true) -> "SUCCESS"
        log.message.contains("RUNNING", ignoreCase = true) -> "RUNNING"
        else -> "WAITING"
    }

    OutlinedCard(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color(0xFF121926))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowTitleWithBadge(
                title = log.timestamp,
                status = logStatus
            )

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFFE5ECF6)
            )
        }
    }
}

@Composable
fun RowTitleWithBadge(
    title: String,
    status: String
) {
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
            modifier = androidx.compose.ui.Modifier.weight(1f)
        )

        StateBadge(status = status)
    }
}